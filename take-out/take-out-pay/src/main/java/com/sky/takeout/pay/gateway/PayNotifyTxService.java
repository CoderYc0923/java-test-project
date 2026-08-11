package com.sky.takeout.pay.gateway;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;

/**
 * 支付回调事务服务
 * 负责处理支付回调事务，包括查单、核金额、CAS、写 Outbox 等操作
 * 需要由 Spring 代理调用（同类 self 调用会让 @Transactional 失效）
 * 若网关自调用，请拆到独立 @Component（如 PayNotifyTxService）或注入 self
 * 
 */
@Component
public class PayNotifyTxService {

    private static final Logger log = LoggerFactory.getLogger(PayNotifyTxService.class);

    private final OrderPayPort orderPayPort;
    private final PayOutboxPort payOutboxPort;
    private final RedisIdempotentHelper redisIdempotentHelper;

    public PayNotifyTxService(OrderPayPort orderPayPort, PayOutboxPort payOutboxPort, RedisIdempotentHelper redisIdempotentHelper) {
        this.orderPayPort = orderPayPort;
        this.payOutboxPort = payOutboxPort;
        this.redisIdempotentHelper = redisIdempotentHelper;
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
    public Order markPaidInShrotTx(MockPayNotifyDTO dto, String lockKey, String lockToken) {
        // 是否已注册解锁同步
        final boolean[] unlockRegistered = {false};
        // 已支付订单ID
        final Long[] paidOrderId = {null};

        try {
            // 注册解锁同步
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Long id = paidOrderId[0];

                    // 如果订单ID为空，则直接返回
                    if (id == null) {
                        return;
                    }
                    // 投递Outbox
                    try {
                        payOutboxPort.publishPendingForOrder(id);
                    } catch (Exception e) {
                        log.warn("Outbox 投递失败，留待补偿 orderId={}", id, e);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    // COMMITTED / ROLLED_BACK / UNKNOWN：都释放锁
                    // 这样 T2 不会在 T1 未 commit 时抢到应用锁（仍可能等行锁，但密度更低）
                    redisIdempotentHelper.unlock(lockKey, lockToken);
                }
            });

            // 标记已注册解锁同步
            unlockRegistered[0] = true;

            // 查单
            Order order = orderPayPort.findOrderByNumber(dto.getOrderNumber());
            if (order == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            if (order.getAmount() == null || order.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额与订单不一致");
            }
            if (isPaid(order)) {
                return order;
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            // CAS入账
            int rows = orderPayPort.casMarkPaid(order.getId());
            if (rows == 0) {
                Order latest = orderPayPort.findOrderById(order.getId());
                // 如果订单状态已变更，则直接返回
                if (latest != null && isPaid(latest)) {
                    return latest; // 并发下别人已支付成功
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }

            // 与入账同事务写入Outbox；真正发送在afterCommit
            payOutboxPort.insertOrderPaid(order.getId(), order.getNumber());
            // 设置已支付订单ID
            paidOrderId[0] = order.getId();
            log.info("CAS 入账成功 orderId={}", order.getId());
            // 返回订单
            return orderPayPort.findOrderById(order.getId());
        } finally {
            // 若短事务根本没开起来就失败，Synchronization 可能没注册 → 这里兜底解锁
            if (!unlockRegistered[0]) {
                redisIdempotentHelper.unlock(lockKey, lockToken);
            }
        }
    }

    /**
     * 业务上的「已支付」：待接单 + 已支付（支付成功后的目标态）
     */
    private boolean isPaid(Order order) {
        return order.getStatus() == OrderStatus.TO_BE_CONFIRMED
                && order.getPayStatus() == PayStatus.PAID;
    }

}
