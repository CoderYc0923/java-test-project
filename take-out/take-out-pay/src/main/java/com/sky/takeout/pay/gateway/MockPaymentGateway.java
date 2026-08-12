package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.client.MockWechatHttpClient;
import com.sky.takeout.pay.client.dto.TransactionResponse;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pay.sign.HmacPaySignUtil;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.entity.PayAttempt;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayAttemptStatus;
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
    private final PayOutboxPort payOutboxPort;
    private final PayAttemptPort payAttemptPort;
    private final PayProperties payProperties;
    private final RedisIdempotentHelper redisIdempotentHelper;
    private final MockWechatHttpClient mockWechatHttpClient;
    private final PayNotifyTxService payNotifyTxService;

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    /** 回调入账互斥 */
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";
    /** 渠道通知nonce 去重 */
    private static final String NONCE_KEY_PREFIX = "order:pay:nonce:";
    /** 请求支付锁，防止同一订单并发重复点支付 */
    private static final String REQUEST_LOCK_PREFIX = "order:pay:request:lock:";

    public MockPaymentGateway(OrderPayPort orderPayPort, PayProperties payProperties,
            RedisIdempotentHelper redisIdempotentHelper, MockWechatHttpClient mockWechatHttpClient,
            PayOutboxPort payOutboxPort, PayNotifyTxService payNotifyTxService, PayAttemptPort payAttemptPort) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.redisIdempotentHelper = redisIdempotentHelper;
        this.mockWechatHttpClient = mockWechatHttpClient;
        this.payOutboxPort = payOutboxPort;
        this.payNotifyTxService = payNotifyTxService;
        this.payAttemptPort = payAttemptPort;
    }

    /**
     * 用户支付接口
     * 防止并发支付：
     * 1. 通过redis分布式锁防止短时间用户连点支付按钮，导致同一订单的并发支付。
     * 2. 若用户正常多开浏览器导致并发支付，则判断是否已有PAYING状态的支付尝试，若有则直接返回；否则创建一条支付尝试。
     * @return 当前订单
     */
    public Order requestPay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单id不能为空");
        }

        // 第一次查库：已支付就直接返回
        Order order = requireOrderById(orderId);

        if (isPaid(order)) {
            log.info("订单{}已支付，直接返回", orderId);
            return order;
        }

        // 验证订单状态
        validateOrder(order);

        // 请求支付锁
        String lockKey = REQUEST_LOCK_PREFIX + orderId;
        Long ttl = resolvePayLockTtl();
        String token = redisIdempotentHelper.tryLock(lockKey, ttl);
        if (token == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付发起中，请勿重复点击");
        }

        try {
            // 锁内再读：防止刚被人付掉 / 状态变了
            order = requireOrderById(orderId);

            if (isPaid(order)) {
                return order;
            }

            // 验证订单状态
            validateOrder(order);

            // 查询是否已有PAYING状态的支付尝试
            PayAttempt payAttempt = payAttemptPort.findPayingByOrderId(orderId);
            if (payAttempt != null) {
                // 复用：可再次调用native（商户单号相同），以降低重复支付的概率
                TransactionResponse resp = mockWechatHttpClient.createNativePay(order, payAttempt.getOutTradeNo());
                payAttemptPort.updatePrepayId(payAttempt.getId(), resp.getPrepayId());
                log.info("复用进行中的支付尝试单 orderId={} outTradeNo={}", orderId, payAttempt.getOutTradeNo());
                return order;
            }

            // 新建支付尝试
            String outTradeNo = order.getNumber() + "-A" + System.currentTimeMillis();
            PayAttempt createPayAttempt = new PayAttempt();
            createPayAttempt.setOrderId(orderId);
            createPayAttempt.setOrderNumber(order.getNumber());
            createPayAttempt.setOutTradeNo(outTradeNo);
            createPayAttempt.setChannel("WECHAT");
            createPayAttempt.setStatus(PayAttemptStatus.PAYING);
            createPayAttempt.setAmount(order.getAmount());
            createPayAttempt.setPayingFlag(1);


            // 调用微信支付
            TransactionResponse response = mockWechatHttpClient.createNativePay(order);

            log.info("用户请求微信支付 orderId={} number={} prepayId={}", orderId, order.getNumber(), response.getPrepayId());
            return order;
        } finally {
            // 下单HTTP与DB入账无关：这里用普通的finally即可，不必afterCommit
            redisIdempotentHelper.unlock(lockKey, token);
        }

    }

    /**
     * 处理微信支付回调
     * 验签失败抛错；重复nonce / 已支付 -> 当作成功（渠道重试友好）
     * 
     * @param dto 微信支付回调参数
     * @return 当前订单
     */
    public Order handlePayNotify(MockPayNotifyDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不能为空");
        }

        verifySignAndTimeWindow(dto);

        
        /**
         * key 必须带「随机 nonce」，不能只用订单号。
         * nonce 只挡「同一条通知重放」。
         */
        Long nonceTtl = payProperties.getNonceTtlSeconds() == null ? 600L : payProperties.getNonceTtlSeconds();
        String nonceKey = NONCE_KEY_PREFIX + dto.getNonce();
        boolean firstNonce = redisIdempotentHelper.trySetNx(nonceKey, dto.getOrderNumber(), nonceTtl);

        if (!firstNonce) {
            // 见过这条通知：已付 → 对渠道成功；未付 → 可能仍在处理 / 上次失败，让渠道稍后重试
            log.info("回调 nonce 重复，orderNumber={} nonce={}", dto.getOrderNumber(), dto.getNonce());
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

        Order previewOrder = orderPayPort.findOrderByNumber(dto.getOrderNumber());
        if (previewOrder == null) {
            // 占用了nonce但单不存在：可删nonce以便修数据后重试，生产常留坑 + 告警
            redisIdempotentHelper.delete(nonceKey);  // 唯一建议删 nonce 的情况：单根本不存在，允许修好后再用同通知试
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }

        /**
         * 支付锁：防止同一订单在回调入账时被并发处理
         * 例子：1.微信几乎同时推了两次notify或重试叠上 2.多实例部署时，两个节点同时收到同单回调
         * 
         */
        String lockKey = PAY_LOCK_PREFIX + previewOrder.getId();
        String lockToken = redisIdempotentHelper.tryLock(lockKey, resolvePayLockTtl());
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请稍后重试");
        }

        try {
            return payNotifyTxService.markPaidInShrotTx(dto, lockKey, lockToken);
        } catch (RuntimeException e) {
             // 若短事务根本没开起来就失败，Synchronization 可能没注册 → 这里兜底解锁
            // 正常路径：TxService 第一行已注册 afterCompletion，这里再 unlock 会因 token 校验变 no-op 或解两次需幂等
            // 更干净：仅在「确认未注册」时解锁。简化起见 TxService 用 try/finally 保证注册失败也解锁。
            throw e;
        }

    }

    /**
     * 验签和时间窗口
     * @param dto 微信支付回调参数
     */
    private void verifySignAndTimeWindow(MockPayNotifyDTO dto) {
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
                dto.getSign());

        if (!ok) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验签失败");
        }

        /**
         * 时间窗口（防过期重放）
         * 因为渠道回调可能延迟，所以需要一个时间窗口来防止重放
         * 重放是攻击者重放之前已经处理过的请求，以达到重复支付的目的
         */
        Long skew = payProperties.getTimestampSkewSeconds() == null ? 300L : payProperties.getTimestampSkewSeconds();
        Long now = System.currentTimeMillis() / 1000;
        // 时间窗口过期
        if (Math.abs(now - dto.getTimestamp()) > skew) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时间窗口过期");
        }


    }
    
    /**
     * 解析支付锁过期时间
     * 
     * @return 支付锁过期时间
     */
    private Long resolvePayLockTtl() {
        Long ttl = payProperties.getPayLockTtlSeconds();

        return (ttl == null || ttl <= 0) ? 10L : ttl;
    }

    /**
     * 查库，返回最新状态的订单
     * 
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

    private void validateOrder(Order order) {
        if (order == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }
        // 状态机：只有「待付款 + 未支付」才能付 → 之后 CAS 成「待接单 + 已支付」
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPayStatus() != PayStatus.UNPAID) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
        }
    }
}
