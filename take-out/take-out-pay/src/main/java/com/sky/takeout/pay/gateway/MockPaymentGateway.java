package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.client.MockWechatHttpClient;
import com.sky.takeout.pay.client.dto.TransactionResponse;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pay.sign.HmacPaySignUtil;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

/**
 * 模拟支付网关
 * 职责：
 * 1. 校验订单是否可支付
 * 2. 用redis锁防止同一订单并发重复点支付
 * 3. 调用模拟微信支付客户端，模拟微信支付回调
 * 4. 通过OrderPayPort做DB CAS：待付款+未支付 -> 待接单+已支付
 * 
 * 注意：本类不写 SQL、不直接依赖 Mapper；改库只走端口（system 实现）
 * MockPaymentGateway
 */
@Component
public class MockPaymentGateway {

    private final OrderPayPort orderPayPort;
    private final PayProperties payProperties;
    private final RedisIdempotentHelper redisIdempotentHelper;
    private final MockWechatHttpClient mockWechatHttpClient;

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";
    private static final String NONCE_KEY_PREFIX = "order:pay:nonce:";

    public MockPaymentGateway(OrderPayPort orderPayPort, PayProperties payProperties,
            RedisIdempotentHelper redisIdempotentHelper, MockWechatHttpClient mockWechatHttpClient) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.redisIdempotentHelper = redisIdempotentHelper;
        this.mockWechatHttpClient = mockWechatHttpClient;
    }

    /**
     * 用户支付接口
     * 
     * @return 当前订单
     */
    public Order requestPay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单id不能为空");
        }

        // 第一次查库：已支付就直接返回
        Order order = requireOrder(orderId);
        if (isPaid(order)) {
            log.info("订单{}已支付，直接返回", orderId);
            return order;
        }

        // 状态机：只有「待付款 + 未支付」才能付 → 之后 CAS 成「待接单 + 已支付」
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPayStatus() != PayStatus.UNPAID) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
        }

        // 调用微信支付
        TransactionResponse response = mockWechatHttpClient.createNativePay(order);

        log.info("用户请求微信支付 orderId={} number={} prepayId={}", orderId, order.getNumber(), response.getPrepayId());
        
        return order;
    }

    /**
     * 处理微信支付回调
     * 验签失败抛错；重复nonce / 已支付 ->  当作成功（渠道重试友好）
     * @param dto 微信支付回调参数
     * @return 当前订单
     */
    @Transactional
    public Order handlePayNotify(MockPayNotifyDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不能为空");
        }

        String secret = payProperties.getMockSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(ErrorCode.ERROR, "pay.mock-secret 未配置，无法验签");
        }

        // 验签
        boolean ok = HmacPaySignUtil.verify(
            dto.getOrderNumber(),
            dto.getAmount(),
            dto.getTimestamp(),
            dto.getNonce(),
            secret,
            dto.getSign()
        );

        if (!ok) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验签失败");
        }

        /**
         * 时间窗口（防重放）
         * 因为渠道回调可能延迟，所以需要一个时间窗口来防止重放
         * 重放是攻击者重放之前已经处理过的请求，以达到重复支付的目的
         */
        Long skew = payProperties.getTimestampSkewSeconds() == null ? 300L : payProperties.getTimestampSkewSeconds();
        Long now = System.currentTimeMillis() / 1000;
        // 时间窗口过期
        if (Math.abs(now - dto.getTimestamp()) > skew) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时间窗口过期");
        }

        /**
         * nonce 去重：key 必须带「随机 nonce」，不能只用订单号。
         * 同单并发应靠下面的支付锁 + CAS；nonce 只挡「同一条通知重放」。
         */
        Long nonceTtl = payProperties.getNonceTtlSeconds() == null ? 600L : payProperties.getNonceTtlSeconds();
        String nonceKey = NONCE_KEY_PREFIX + dto.getNonce();
        boolean firstNonce = redisIdempotentHelper.trySetNx(nonceKey, dto.getOrderNumber(), nonceTtl);
        if (!firstNonce) {
            log.info("回调 nonce 重复，幂等返回 orderNumber={} nonce={}", dto.getOrderNumber(), dto.getNonce());
            Order existed = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (existed == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            // 仅当库已是已支付时，才对渠道宣称成功；否则让渠道带新请求/重试更安全
            if (!isPaid(existed)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请稍后重试");
            }
            return existed;
        }

        try {
            // 查单
            Order order = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (order == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }

            // 核对金额
            if (order.getAmount() == null || order.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额与订单不一致");
            }

            // 已支付：业务幂等，回成功
            if (isPaid(order)) {
                return order;
            }

            // 锁 + CAS
            return markPaidWithLock(order.getId());

        } catch (RuntimeException e) {
            // 业务失败时删除nonce，允许渠道用新请求重试
            // 真项目更常见：验签通过后的失败也留nonce + 记失败流水
            redisIdempotentHelper.delete(nonceKey);
            throw e;
        }

    }

    /**
     * 锁 + CAS
     * @param orderId
     * @return 当前订单
     */
    public Order markPaidWithLock (Long orderId) {
        Order order = requireOrder(orderId);
        if (isPaid(order)) {
            return order;
        }

        String lockKey = PAY_LOCK_PREFIX + orderId;
        // 锁 TTL 用 pay-lock-ttl-seconds（短）；勿误用 nonce-ttl（默认 600，宕机后同单会锁太久）
        Long ttl = payProperties.getPayLockTtlSeconds();
        if (ttl == null || ttl <= 0) {
            ttl = 10L;
        }
        String lockToken = redisIdempotentHelper.tryLock(lockKey, ttl);
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请勿重复回调");
        }

        try {
            order = requireOrderById(orderId);
            if (isPaid(order)) {
                return order;
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            int rows = orderPayPort.casMarkPaid(orderId);
            if (rows == 0) {
                Order latest = requireOrderById(orderId);
                if (isPaid(latest)) {
                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }
            log.info("回调支付成功 orderId={}", orderId);
            return requireOrderById(orderId);
        } finally {
            redisIdempotentHelper.unlock(lockKey, lockToken);
        }

    }

    /**
     * 查库，返回最新状态的订单
     * @param orderId
     * @return 当前订单
     */
    private Order requireOrderById(Long orderId) {
        Order order = orderPayPort.findOrderById(orderId);

        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return order;
    }

    /**
     * 业务上的「已支付」：待接单 + 已支付（支付成功后的目标态）
     */
    private boolean isPaid(Order order) {
        return order.getStatus() == OrderStatus.TO_BE_CONFIRMED
                && order.getPayStatus() == PayStatus.PAID;
    }

    private Order requireOrder(Long orderId) {
        Order order = orderPayPort.findOrderById(orderId);

        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        return order;
    }
}
