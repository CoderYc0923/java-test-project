package com.sky.takeout.system.mq;

import java.time.Duration;

import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.mq.OrderStatusMessage;
import com.sky.takeout.system.notify.KitchenNotifyService;

import tools.jackson.databind.ObjectMapper;

/**
 * 订单状态变更 → WebSocket 通知管理端（与厨房来单同一通道）。
 * <p>
 * 先推送再写幂等键；推送失败抛错，便于顺序消费重试。
 */
@Component
@RocketMQMessageListener(
    topic = "${mq.take-order-status-topic:takeout-order-status}",
    consumerGroup = "${mq.take-order-status-consumer-group:take-order-status-consumer}",
    consumeMode = ConsumeMode.ORDERLY,
    maxReconsumeTimes = 3
)
public class OrderStatusChangedConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedConsumer.class);

    private static final String IDEMPOTENCY_KEY_PREFIX = "order_status_changed:";

    private final ObjectMapper objectMapper;
    private final RedisIdempotentHelper redis;
    private final KitchenNotifyService kitchenNotifyService;

    public OrderStatusChangedConsumer(ObjectMapper objectMapper, RedisIdempotentHelper redisIdempotentHelper,
            KitchenNotifyService kitchenNotifyService) {
        this.objectMapper = objectMapper;
        this.redis = redisIdempotentHelper;
        this.kitchenNotifyService = kitchenNotifyService;
    }

    @Override
    public void onMessage(String body) {

        OrderStatusMessage msg;

        try {
            msg = objectMapper.readValue(body, OrderStatusMessage.class);
        } catch (Exception e) {
            log.error("订单状态消息无法解析: {}", body, e);
            return;
        }

        if (msg.getOrderId() == null || !StringUtils.hasText(msg.getEventId())) {
            log.error("订单状态消息缺少必要字段: {}", msg);
            return;
        }

        String idemKey = IDEMPOTENCY_KEY_PREFIX + msg.getEventId();
        if (StringUtils.hasText(redis.get(idemKey))) {
            log.info("订单状态消息已处理: eventId={}, orderId={}", msg.getEventId(), msg.getOrderId());
            return;
        }

        try {
            doHandle(msg);
        } catch (RuntimeException e) {
            log.warn("订单状态消息处理失败: eventId={}, orderId={}", msg.getEventId(), msg.getOrderId(), e);
            throw e;
        }

        redis.trySetNx(idemKey, "SUCCESS", Duration.ofDays(7).toSeconds());

    }

    private void doHandle(OrderStatusMessage msg) {
        kitchenNotifyService.notifyOrderStatusChanged(msg);
    }
}
