package com.sky.takeout.system.mq.producers;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pojo.dto.mq.OrderStatusMessage;

@Component
public class OrderStatusProducer {

    private final RocketMQTemplate rocketMQTemplate;

    private final ObjectMapper objectMapper;

    private final TakeoutMqProperties takeoutMqProperties;

    public OrderStatusProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper, TakeoutMqProperties takeoutMqProperties) {
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

            // syncSendOrderly是干嘛的？
            // 顺序消息，保证消息的顺序性，避免消息的乱序问题
            rocketMQTemplate.syncSendOrderly(destination, json);

        } catch (Exception e) {
           throw new IllegalStateException("send order status message failed, orderId=" + body.getOrderId(), e);
        }
    }
}
