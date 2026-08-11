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
import com.sky.takeout.pay.port.PayOutboxPort;
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
    private final PayOutboxPort payOutboxPort;
    private final PayProperties payProperties;
    private final RedisIdempotentHelper redisIdempotentHelper;
    private final MockWechatHttpClient mockWechatHttpClient;

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    /** 回调入账互斥 */
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";
    /** 渠道通知nonce 去重 */
    private static final String NONCE_KEY_PREFIX = "order:pay:nonce:";
    /** 请求支付锁，防止同一订单并发重复点支付 */
    private static final String REQUEST_LOCK_PREFIX = "order:pay:request:lock:";

    public MockPaymentGateway(OrderPayPort orderPayPort, PayProperties payProperties,
            RedisIdempotentHelper redisIdempotentHelper, MockWechatHttpClient mockWechatHttpClient,
            PayOutboxPort payOutboxPort) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.redisIdempotentHelper = redisIdempotentHelper;
        this.mockWechatHttpClient = mockWechatHttpClient;
        this.payOutboxPort = payOutboxPort;
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

        // 生产增强（本文不实现）：此处再 insert pay_notify_log(nonce) 唯一索引；
        // 冲突则同样走「已付成功 / 未付 429」。Redis 只是热路径加速。

        // 同单入账锁
        Order previewOrder = orderPayPort.findOrderByNumber(dto.getOrderNumber());
        if (previewOrder == null) {
            // 占用了nonce但单不存在：可删nonce以便修数据后重试，生产常留坑 + 告警
            redisIdempotentHelper.delete(nonceKey);
            throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
        }

        String lockKey = PAY_LOCK_PREFIX + previewOrder.getId();
        String lockToken = redisIdempotentHelper.tryLock(lockKey, resolvePayLockTtl());
        if (lockToken == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "支付处理中，请稍后重试");
        }

        // 解锁挂到事务结束之后，以防止CAS入账前释放锁被其他请求占用
        registerUnlockAndSideEffects(lockKey, lockToken, previewOrder.getId());

        try {
            return markPaidInShrotTx(dto);
        } catch (RuntimeException e) {
             // 4) 业务失败：默认不删 nonce（与现网 delete 相反）
            //    原因：验签已通过，留下「见过这条通知」；渠道应换策略（查单/新通知）或等 429 退避
            //    仅「明显可安全重放」的数据错误（如订单不存在）才在上面删过 nonce
            //redisIdempotentHelper.delete(nonceKey);
            throw e;
        }

    }

    /**
     * 注册事务同步
     * 如果当前没有事务同步，则立即解锁
     * 如果当前有事务同步，则注册事务同步,事务结束后执行解锁和投递
     * @param lockKey 锁key
     * @param lockToken 锁token
     * @param orderId 订单id
     */
    private void registerUnlockAndSideEffects(String lockKey, String lockToken, Long orderId) {
        Runnable unlock = () -> redisIdempotentHelper.unlock(lockKey, lockToken);
        Runnable publish = () -> {
            try {
                payOutboxPort.publishPendingForOrder(orderId);
            } catch (Exception e) {
                // 不回滚已经提交的入账；留给对账/定时扫outbox
                log.warn("afterCommit 投递 Outbox 失败，orderId={}", orderId);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 还没有事务同步：先起段事务时Spring会开启同步
            // 若此处仍inactive，说明调用链有问题：调用方try/fianlly解锁
            // 推荐写法：先开启事务再锁，或把【加锁+注册】放进一个带事务的门面
            //
            // 本推荐结构是【锁在事务外，短事务在锁内】
            // 进入markPaidInShortTx才会avtive synchronization
            // 因此这里用【延迟到短事务方法开头再注册】更稳
            log.warn("无事务同步，回退为立即解锁 orderId={}", orderId);
            unlock.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }

            @Override
            public void afterCompletion(int status) {
                unlock.run();
            }
        });
    }

    /**
     * 短事务：查单、核金额、CAS、写 Outbox。
     * <p>
     * 必须由 Spring 代理调用（同类 self 调用会让 @Transactional 失效）。
     * 若网关自调用，请拆到独立 @Component（如 PayNotifyTxService）或注入 self。
     * 
     * @param dto
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Order markPaidInShrotTx(MockPayNotifyDTO dto) {
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

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPayStatus() != PayStatus.UNPAID) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
        }

        int rows = orderPayPort.casMarkPaid(order.getId());
        if (rows == 0) {
            Order latest = requireOrderById(order.getId());
            if (isPaid(latest)) {
                return latest; // 并发下别人已支付成功
            }
            throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
        }

        // 与入账同事务写入Outbox；真正发送在afterCommit
        payOutboxPort.insertOrderPaid(order.getId(), order.getNumber());

        log.info("订单{}回调支付成功，插入订单ORDER_PAID消息", order.getId());
        return requireOrderById(order.getId());

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
