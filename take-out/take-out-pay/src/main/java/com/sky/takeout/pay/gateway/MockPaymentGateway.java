package com.sky.takeout.pay.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.entity.Order;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayStatus;

/**
 * 模拟支付网关
 * 职责：
 * 1. 校验订单是否可支付
 * 2. 用redis锁防止同一订单并发重复点支付
 * 3. 通过OrderPayPort做DB CAS：待付款+未支付 -> 待接单+已支付
 * 
 * 注意：本类不写 SQL、不直接依赖 Mapper；改库只走端口（system 实现）
 * MockPaymentGateway
 */
@Component
public class MockPaymentGateway {

    private final OrderPayPort orderPayPort;
    private final PayProperties payProperties;
    private final RedisIdempotentHelper redisIdempotentHelper;

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final String PAY_LOCK_PREFIX = "order:pay:lock:";

    public MockPaymentGateway(OrderPayPort orderPayPort, PayProperties payProperties, RedisIdempotentHelper redisIdempotentHelper) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.redisIdempotentHelper = redisIdempotentHelper;
    }

    /**
     * 模拟支付成功
     * 
     * 流程：
     * 查单 -> 已付则直接返回（业务幂等）
     *      ->抢redis锁（防连点）
     *      ->锁内再查一次 + 状态校验
     *      ->CAS更新支付状态
     *      ->finally释放锁
     */
    @Transactional
    public Order pay(Long orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单id不能为空");
        }

        // 第一次查库：已支付就直接返回
        Order order = requireOrder(orderId);
        if (isPaid(order)) {
            log.info("订单{}已支付，直接返回", orderId);
            return order;
        }

        // 抢支付锁：同一订单同一时刻只允许一个线程进
        String lockKey = PAY_LOCK_PREFIX + orderId;
        Long ttl = payProperties.getPayLockTtlSeconds();
        if (ttl == null || ttl <= 0) {
            ttl = 10L; // yml 没配时兜底，避免 NPE
        }
        String lockToken = redisIdempotentHelper.tryLock(lockKey, ttl);
        if (lockToken == null) {
            // 抢锁失败：同一订单并发支付
            throw new BusinessException(ErrorCode.CONFLICT, "支付繁忙，请稍后再试");
        }

        try {
            // 锁内再查一次：拿到锁前可能已经被人支付了
            order = requireOrder(orderId);
            if (isPaid(order)) {
                log.info("订单{}已支付，直接返回", orderId);
                return order;
            }

            // 状态机：只有「待付款 + 未支付」才能付 → 之后 CAS 成「待接单 + 已支付」
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || order.getPayStatus() != PayStatus.UNPAID) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前订单不可支付");
            }

            // CAS更新支付状态:UPDATE ... WHERE status=待付款 AND pay_status=未支付
            //  rows=1 成功；rows=0 说明并发下状态已变
            int rows = orderPayPort.casMarkPaid(orderId);
            if (rows == 0) {
                Order latest = requireOrder(orderId);
                if (isPaid(latest)) {
                    // 并发下状态已变，但已支付，直接返回
                    log.info("订单{}已支付，直接返回", orderId);
                    return latest;
                }
                throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变更，无法支付，请稍后重试");
            }

            log.info("订单{}已支付", orderId);
            //返回最新订单（待接单 + 已支付）
            return requireOrder(orderId);

        } finally {
            // 释放锁
            redisIdempotentHelper.unlock(lockKey, lockToken);
        }

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
