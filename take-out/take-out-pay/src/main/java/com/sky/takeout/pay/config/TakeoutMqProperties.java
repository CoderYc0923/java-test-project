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

    /** 厨房失败次数 */
    private Integer kitchenFailTimes = 0;

    /** 补偿主题 */
    private String compensateTopic = "takeout-pay-compensate";

    /** 补偿关闭标签 */
    private String compensateCloseTag = "CLOSE_CHANNEL";

    /** 补偿退款标签 */
    private String compensateRefundTag = "REFUND";

    /** 补偿消费者组 */
    private String compensateConsumerGroup = "take-pay-compensate-consumer";
}
