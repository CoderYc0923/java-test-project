package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@ConfigurationProperties(prefix = "mq")
@Data
public class TakeoutMqProperties {

    /** 订单支付成功消息主题 */
    private String orderPaidTopic = "takeout-order-paid";

    /** 订单支付成功消息标签 */
    private String orderPaidTag = "ORDER_PAID";

    /** 订单支付成功消息消费者组 */
    private String orderPaidConsumerGroup = "take-kitchen-consumer";

    /** 出箱扫描延迟时间 */
    private Long outboxScanDelayMs = 15000L;
}
