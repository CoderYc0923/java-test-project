package com.sky.takeout.pay.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sky.takeout.pay.config.TakeoutMqProperties;

/**
 * 订单支付消息生产者
 * OrderPaidProducer
 */
@Component
public class OrderPaidProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidProducer.class);

    private final TakeoutMqProperties takeoutMqProperties;

    private final RocketMQTemplate rocketMQTemplate;

    public OrderPaidProducer(TakeoutMqProperties takeoutMqProperties, RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.takeoutMqProperties = takeoutMqProperties;
    }


    /**
     * 发送订单支付消息
     * @param jsonPayload 消息体
     * @param orderId     订单ID
     * @param eventId     事件ID
     */
    public void send(String jsonPayload, Long orderId, String eventId) {
        String destination = takeoutMqProperties.getOrderPaidTopic() + ":" + takeoutMqProperties.getOrderPaidTag();

        rocketMQTemplate.syncSend(
            destination,
            MessageBuilder.withPayload(jsonPayload)
                .setHeader("KEYS", String.valueOf(orderId))  // 部分版本用 keys
                .setHeader("eventId", eventId)
                .build()
        );

        log.info("发送订单支付消息成功，destination={}, orderId={}, eventId={}", destination, orderId, eventId);
    }
}
