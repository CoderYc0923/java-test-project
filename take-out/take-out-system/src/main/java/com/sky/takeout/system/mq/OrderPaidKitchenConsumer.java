package com.sky.takeout.system.mq;

import java.time.Duration;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.mq.OrderPaidMessage;
import com.sky.takeout.system.notify.KitchenNotifyService;

import tools.jackson.databind.ObjectMapper;

/**
 * 支付成功消息 → 厨房 WebSocket 来单提醒。
 * <p>
 * 幂等顺序贴生产：先推送，成功后再标记；推送失败不写键，便于 MQ 重试。
 */
@Component
@RocketMQMessageListener(
        topic = "${mq.order-paid-topic:takeout-order-paid}",
        consumerGroup = "${mq.order-paid-consumer-group:take-kitchen-consumer}",
        selectorExpression = "${mq.order-paid-tag:ORDER_PAID}")
public class OrderPaidKitchenConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidKitchenConsumer.class);

    private static final String IDEMPOTENT_PREFIX = "mq:consume:order-paid:";

    private final ObjectMapper objectMapper;
    private final RedisIdempotentHelper redis;
    private final KitchenNotifyService kitchenNotifyService;

    public OrderPaidKitchenConsumer(ObjectMapper objectMapper, RedisIdempotentHelper redis,
            KitchenNotifyService kitchenNotifyService) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.kitchenNotifyService = kitchenNotifyService;
    }

    @Override
    public void onMessage(String body) {
        OrderPaidMessage msg;
        try {
            msg = objectMapper.readValue(body, OrderPaidMessage.class);
        } catch (Exception e) {
            // 毒消息：解析失败不应无限重试
            log.error("解析支付消息失败，body={}", body, e);
            return;
        }

        if (!StringUtils.hasText(msg.getEventId())) {
            log.error("支付消息缺少 eventId，丢弃 body={}", body);
            return;
        }

        String idemKey = IDEMPOTENT_PREFIX + msg.getEventId();
        if (StringUtils.hasText(redis.get(idemKey))) {
            log.info("幂等短路，消息已消费 eventId={}", msg.getEventId());
            return;
        }

        // 厨房处理：推管理端（失败抛错 → 不写幂等 → MQ 重试）
        kitchenNotifyService.notifyNewOrder(msg);

        boolean first = redis.trySetNx(idemKey, "SUCCESS", Duration.ofDays(7).getSeconds());
        if (!first) {
            log.info("幂等标记时发现已存在 eventId={}", msg.getEventId());
        }
    }
}
