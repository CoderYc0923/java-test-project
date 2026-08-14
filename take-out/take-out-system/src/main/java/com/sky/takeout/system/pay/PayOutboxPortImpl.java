package com.sky.takeout.system.pay;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sky.takeout.pay.mq.OrderPaidProducer;
import com.sky.takeout.pay.port.PayOutboxPort;
import com.sky.takeout.pojo.dto.mq.OrderPaidMessage;
import com.sky.takeout.pojo.entity.PayOutbox;
import com.sky.takeout.pojo.enums.PayOutboxEventType;
import com.sky.takeout.pojo.enums.PayOutboxStatus;
import com.sky.takeout.system.mapper.PayOutboxMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * Outbox 端口实现：落在 system（有 Mapper），供 pay 通过接口调用。
 * <p>
 * 不要把本类放进 take-out-pay：pay 不能依赖 system（system 已依赖 pay，会形成环）。
 */
@Component
public class PayOutboxPortImpl implements PayOutboxPort {

    private static final Logger log = LoggerFactory.getLogger(PayOutboxPortImpl.class);

    private final PayOutboxMapper payOutboxMapper;
    private final ObjectMapper objectMapper;
    private final OrderPaidProducer orderPaidProducer;

    public PayOutboxPortImpl(PayOutboxMapper payOutboxMapper, ObjectMapper objectMapper, OrderPaidProducer orderPaidProducer) {
        this.payOutboxMapper = payOutboxMapper;
        this.objectMapper = objectMapper;
        this.orderPaidProducer = orderPaidProducer;
    }

    /**
     * 插入支付出站消息
     * 
     * @param orderId     订单ID
     * @param orderNumber 订单号
     */
    @Override
    public void insertOrderPaid(Long orderId, String orderNumber) {
        if (orderId == null || !StringUtils.hasText(orderNumber)) {
            // 若参数为空，抛出 IllegalArgumentException：这是参数异常
            throw new IllegalArgumentException("orderId and orderNumber cannot be null");
        }

        // 构建消息体
        String eventId = UUID.randomUUID().toString();
        OrderPaidMessage message = OrderPaidMessage.builder()
                .eventId(eventId)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .occurredAt(LocalDateTime.now().toString())
                .build();

        // 写入数据库
        try {
            PayOutbox row = new PayOutbox();
            row.setEventId(eventId);
            row.setOrderId(orderId);
            row.setOrderNumber(orderNumber);
            row.setEventType(PayOutboxEventType.ORDER_PAID);
            row.setStatus(PayOutboxStatus.NEW);
            row.setPayload(objectMapper.writeValueAsString(message));
            row.setRetryCount(0);

            payOutboxMapper.insert(row);
            log.info("支付出站消息写入成功，eventId={}, orderId={}", eventId, orderId);
        } catch (Exception e) {
            // 让外层事务回滚：入账与Outbox必须同步。IllegalStateException是运行时异常：表示当前对象/系统状态不允许执行这个操作
            throw new IllegalStateException("insert pay_outbox failed", e);
        }
    }

    /**
     * 发布该订单下所有待处理的支付消息
     * 
     * @param orderId 订单ID
     */
    @Override
    public void publishPendingForOrder(Long orderId) {

        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }

        List<PayOutbox> pendingRows = payOutboxMapper.selectList(
                new LambdaQueryWrapper<PayOutbox>()
                        .eq(PayOutbox::getOrderId, orderId)
                        .eq(PayOutbox::getEventType, PayOutboxEventType.ORDER_PAID)
                        .eq(PayOutbox::getStatus, PayOutboxStatus.NEW)
                        .orderByAsc(PayOutbox::getId));

        for (PayOutbox row : pendingRows) {
            trySendAndMark(row);
        }
    }


    /**
     * 发布订单退款消息
     * @param orderId 订单ID
     */
    @Override
    public void publishRefundForOrder(Long orderId) {

        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }

        



    }

    /**
     * 供定时扫描调用，批量发布待处理的支付消息
     * @param limit 限制数量
     * @return
     */
    @Override
    public int publishBatchNew(int limit) {
        List<PayOutbox> pendingRows = payOutboxMapper.selectList(
            new LambdaQueryWrapper<PayOutbox>()
                .eq(PayOutbox::getEventType, PayOutboxEventType.ORDER_PAID)
                .eq(PayOutbox::getStatus, PayOutboxStatus.NEW)
                .orderByAsc(PayOutbox::getId)
                .last("limit " + Math.max(1, limit))
        );

        int ok = 0;
        for (PayOutbox row : pendingRows) {
            if (trySendAndMark(row)) {
                ok++;
            }
        };

        return ok;
    }

    /**
     * 尝试发送并标记消息
     * 
     * @param row
     */
    private boolean trySendAndMark(PayOutbox row) {

        try {
            // 生产者发消息
            orderPaidProducer.send(row.getPayload(), row.getOrderId(), row.getEventId());

            // 标记消息为已发送
            int updated = payOutboxMapper.update(null, 
                new LambdaUpdateWrapper<PayOutbox>()
                    .eq(PayOutbox::getId, row.getId())
                    .eq(PayOutbox::getStatus, PayOutboxStatus.NEW)
                    .set(PayOutbox::getStatus, PayOutboxStatus.SENT)
                );

            if (updated == 1) {
                log.info("支付出站消息发送成功，id={}, eventId={}", row.getId(), row.getEventId());
                return true;
            }
            return false;
        } catch (Exception e) {
            // 不抛给afterCommit去回滚，留NEW让扫描器去扫
            payOutboxMapper.update(null, new LambdaUpdateWrapper<PayOutbox>().eq(PayOutbox::getId, row.getId())
                    .setSql("retry_count = retry_count + 1"));
            log.warn("发送支付出站消息失败，重试次数+1，eventId={}, orderId={}", row.getEventId(), row.getOrderId());
            return false;
        }
    }

}
