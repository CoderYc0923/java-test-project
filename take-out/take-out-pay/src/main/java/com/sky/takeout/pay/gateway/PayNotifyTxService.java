package com.sky.takeout.pay.gateway;

import com.sky.takeout.pay.client.MockWechatHttpClient;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.entity.PayAttempt;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayAttemptStatus;
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

    private final MockWechatHttpClient mockWechatHttpClient;

    private static final Logger log = LoggerFactory.getLogger(PayNotifyTxService.class);

    private final OrderPayPort orderPayPort;
    private final PayAttemptPort payAttemptPort;
    private final PayOutboxPort payOutboxPort;
    private final RedisIdempotentHelper redisIdempotentHelper;

    public PayNotifyTxService(OrderPayPort orderPayPort, PayOutboxPort payOutboxPort,
            RedisIdempotentHelper redisIdempotentHelper, MockWechatHttpClient mockWechatHttpClient,
            PayAttemptPort payAttemptPort) {
        this.orderPayPort = orderPayPort;
        this.payOutboxPort = payOutboxPort;
        this.redisIdempotentHelper = redisIdempotentHelper;
        this.mockWechatHttpClient = mockWechatHttpClient;
        this.payAttemptPort = payAttemptPort;
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
    public Order markPaidOrRufundInShortTx(MockPayNotifyDTO dto, Long payAttemptId, String lockKey, String lockToken) {

        /** 是否已注册解锁同步 */
        AtomicBoolean unlockRegistered = new AtomicBoolean(false);
        /** 提交后执行的动作 */
        AtomicReference<String> afterCommitAction = new AtomicReference<>();
        /** 订单ID */
        AtomicReference<Long> orderIdHolder = new AtomicReference<>();
        /** 商户订单号 */
        AtomicReference<String> outTradeNoHolder = new AtomicReference<>();

        try {
            /**
             * 注册解锁同步
             * TransactionSynchronizationManager.registerSynchronization：是 Spring 提供的钩子在「当前线程正在进行的数据库事务」结束时，按约定回调你注册的代码。
             * registerSynchronization(...) 本身不开启事务，只是往当前事务上挂一个监听器。
             */
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    String action = afterCommitAction.get();
                    Long orderId = orderIdHolder.get();
                    String outTradeNo = outTradeNoHolder.get();

                    /** 若动作、订单ID、商户订单号为空，则直接返回 */
                    if (action == null || orderId == null || outTradeNo == null) {
                        return;
                    }
                    /* 关闭其他未支付的支付尝试 */
                    if ("CLOSE_OTHERS".equals(action)) {
                        closeOtherUnpaidAttempts(orderId, outTradeNo);
                        try {
                            payOutboxPort.publishPendingForOrder(orderId);
                        } catch (Exception e) {
                            log.warn("Outbox 投递失败，留待补偿 orderId={}", orderId, e);
                        }
                    } else if ("REFUND".equals(action)) {
                        /* 退款 */
                        try {
                            mockWechatHttpClient.refund(outTradeNo, "duplicate_pay");

                            // 退款成功后再把本地标REFUND
                            payAttemptPort.updateStatus(payAttemptId, PayAttemptStatus.REFUNDING,
                                    PayAttemptStatus.REFUNDED, null);
                        } catch (Exception e) {
                            log.warn("退款失败，留待补偿 outTradeNo={}", outTradeNo, e);
                        }
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
            unlockRegistered.set(true);

            // 查单
            PayAttempt payAttempt = payAttemptPort.findByOutTradeNo(dto.getOrderNumber());
            if (payAttempt == null || !payAttempt.getId().equals(payAttemptId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付单不存在");
            }

            Order order = orderPayPort.findOrderById(payAttempt.getOrderId());
            if (order == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "订单不存在");
            }
            if (order.getAmount() == null || order.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额有误");
            }
            if (payAttempt.getAmount() == null || payAttempt.getAmount().compareTo(dto.getAmount()) != 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "支付金额有误");
            }
           
            /** 如果支付单已支付，则直接返回 */
            if(payAttempt.getStatus() == PayAttemptStatus.SUCCESS) {
                return order;
            }

            /* 如果支付单退款中或已退款，则直接返回 */
            if (payAttempt.getStatus() == PayAttemptStatus.REFUNDED || payAttempt.getStatus() == PayAttemptStatus.REFUNDING) {
                return order;
            }

            /**
             * 如果订单已支付，但是支付单状态不是已支付，则代表本单为重复支付单
             * 将支付单状态改为退款中，并执行退款
             */
            if (isPaid(order)) {
                payAttemptPort.updateStatus(payAttempt.getId(), payAttempt.getStatus(), PayAttemptStatus.REFUNDING, null);
                afterCommitAction.set("REFUND");
                orderIdHolder.set(order.getId());
                outTradeNoHolder.set(dto.getOrderNumber());
                log.warn("重复支付，将退款 orderId={}, outTradeNo={}", order.getId(), payAttempt.getOutTradeNo());
                return order;
            }

            /** 如果订单状态不是待支付或未支付 */
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }
            
            /** 如果支付单状态不是支付中且不是已关闭，则抛出异常 */
            if (payAttempt.getStatus() != PayAttemptStatus.PAYING && payAttempt.getStatus() != PayAttemptStatus.CLOSED){
                throw new BusinessException(ErrorCode.CONFLICT, "支付单状态有误");
            }

            // CAS入账
            int rows = orderPayPort.casMarkPaid(order.getId());
            if (rows == 0) {
                // 最新订单状态
                Order latest = orderPayPort.findOrderById(order.getId());
                // 如果最新订单状态已支付，那本支付单就变成了重复支付，返回最新订单
                if (latest != null && isPaid(latest)) {
                    payAttemptPort.updateStatus(payAttempt.getId(), payAttempt.getStatus(), PayAttemptStatus.REFUNDING, null);

                    afterCommitAction.set("REFUND");
                    orderIdHolder.set(order.getId());
                    outTradeNoHolder.set(dto.getOrderNumber());

                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，支付失败");
            }

            /** 将支付单状态改为已支付 */
            payAttemptPort.updateStatus(payAttempt.getId(), payAttempt.getStatus(), PayAttemptStatus.SUCCESS, null);

            // 与入账同事务写入Outbox；真正发送在afterCommit
            payOutboxPort.insertOrderPaid(order.getId(), order.getNumber());

            /** 本支付单成功支付，则关闭其他未支付的支付单 */
            afterCommitAction.set("CLOSE_OTHERS");
            orderIdHolder.set(order.getId());
            outTradeNoHolder.set(payAttempt.getOrderNumber());
            log.info("CAS 入账成功 orderId={}", order.getId());
            // 返回订单
            return orderPayPort.findOrderById(order.getId());
        } finally {
            // 若短事务根本没开起来就失败，Synchronization 可能没注册 → 这里兜底解锁
            if (!unlockRegistered.get()) {
                redisIdempotentHelper.unlock(lockKey, lockToken);
            }
        }
    }

    /**
     * 关闭其他未支付的支付尝试
     * @param orderId
     * @param winnerOutTradeNo
     */
    private void closeOtherUnpaidAttempts(Long orderId, String winnerOutTradeNo) {
        List<PayAttempt> attempts = payAttemptPort.listByOrderId(orderId);
        for (PayAttempt attempt : attempts) {
            if (winnerOutTradeNo.equals(attempt.getOutTradeNo())) {
                continue;
            }
            if (attempt.getStatus() == PayAttemptStatus.SUCCESS
                    || attempt.getStatus() == PayAttemptStatus.REFUNDED
                    || attempt.getStatus() == PayAttemptStatus.REFUNDING) {
                continue;
            }
            try {
                mockWechatHttpClient.close(attempt.getOutTradeNo());
            } catch (Exception e) {
                log.warn("关闭其他未支付的支付尝试失败，留待补偿 outTradeNo={}", attempt.getOutTradeNo(), e);
            }

            try {
                payAttemptPort.updateStatus(attempt.getId(), attempt.getStatus(), PayAttemptStatus.CLOSED, null);
            } catch (Exception e) {
                log.warn("本地标 CLOSED 失败 attemptId={}", attempt.getId(), e);
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
