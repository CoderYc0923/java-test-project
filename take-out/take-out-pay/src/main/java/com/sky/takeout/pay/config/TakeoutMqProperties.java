package com.sky.takeout.pay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.pojo.enums.PayOutboxEventType;

import lombok.Data;

@ConfigurationProperties(prefix = "mq")
@Data
public class TakeoutMqProperties {

    /** 订单支付成功消息主题 */
    private String orderPaidTopic = "takeout-order-paid";

    /** 订单支付成功消息标签 */
    private String orderPaidTag = PayOutboxEventType.ORDER_PAID.getCode();

    /** 订单支付成功消息消费者组 */
    private String orderPaidConsumerGroup = "take-kitchen-consumer";

    /** 出箱扫描延迟时间 */
    private Long outboxScanDelayMs = 15000L;

    /** 厨房失败次数 */
    private Integer kitchenFailTimes = 0;

    /** 补偿主题 */
    private String compensateTopic = "takeout-pay-compensate";

    /** 补偿关闭标签 */
    private String compensateCloseTag = PayOutboxEventType.CLOSE_CHANNEL.getCode();

    /** 补偿退款标签 */
    private String compensateRefundTag = PayOutboxEventType.REFUND.getCode();

    /** 补偿消费者组 */
    private String compensateConsumerGroup = "take-pay-compensate-consumer";

    /** 订单状态主题 */
    private String takeOrderStatusTopic = "takeout-order-status";

    /** 订单状态消费者组 */
    private String takeOrderStatusConsumerGroup = "take-order-status-consumer";

    /** 订单确认标签 */
    private String takeOrderConfirmedTag = OrderStatus.CONFIRMED.name();

    /** 订单派送标签 */
    private String takeOrderDeliveringTag = OrderStatus.DELIVERY_IN_PROGRESS.name();

    /** 订单完成标签 */
    private String takeOrderCompletedTag = OrderStatus.COMPLETED.name();

    /** 订单取消标签 */
    private String takeOrderCancelledTag = OrderStatus.CANCELLED.name();
}
