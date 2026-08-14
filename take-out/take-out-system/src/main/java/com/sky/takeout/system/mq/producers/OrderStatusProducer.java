package com.sky.takeout.system.mq.producers;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pojo.dto.mq.OrderStatusMessage;

@Component
public class OrderStatusProducer {

    private final RocketMQTemplate rocketMQTemplate;

    private final ObjectMapper objectMapper;

    private final TakeoutMqProperties takeoutMqProperties;

    private static final Logger log = LoggerFactory.getLogger(OrderStatusProducer.class);

    public OrderStatusProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper,
            TakeoutMqProperties takeoutMqProperties) {
        this.takeoutMqProperties = takeoutMqProperties;
        this.objectMapper = objectMapper;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void send(OrderStatusMessage body) {
        if (body.getEventId() == null) {
            throw new IllegalArgumentException("eventId is required");
        }

        if (body.getOrderId() == null) {
            throw new IllegalArgumentException("orderId is required");
        }

        if (body.getToStatus() == null) {
            throw new IllegalArgumentException("toStatus is required");
        }

        try {
            String json = objectMapper.writeValueAsString(body);

            // Tag 用枚举名（TO_BE_CONFIRMED）；不要用 toString/拼接枚举本身——@JsonValue 绑的是 code(2)
            String destination = takeoutMqProperties.getTakeOrderStatusTopic()
                    + ":" + body.getToStatus().name();

            // rocketMQ有哪几种发送方式？
            // 1. syncSendOrderly 顺序消息，保证消息的顺序性，避免消息的乱序问题
            // 2. syncSend 同步消息，发送消息后，等待消息发送成功后，再返回
            // 3. asyncSend 异步消息，发送消息后，不等待消息发送成功后，直接返回
            // 4. onewaySend 单向消息，发送消息后，不等待消息发送成功后，直接返回
            SendResult result = rocketMQTemplate.syncSendOrderly(
                    destination,
                    MessageBuilder.withPayload(json)
                            .setHeader("KEYS", String.valueOf(body.getOrderId()))
                            .setHeader("eventId", body.getEventId())
                            .build(),
                    String.valueOf(body.getOrderId()) // 分片键，用于将同一订单的消息发送到同一个队列
            );

            log.info("send order status message success, orderId={}, eventId={}, toStatus={}, msgId={}",
                    body.getOrderId(), body.getEventId(), body.getToStatus(), result.getMsgId());

        } catch (Exception e) {
            throw new IllegalStateException("send order status message failed, orderId=" + body.getOrderId(), e);
        }
    }
}
