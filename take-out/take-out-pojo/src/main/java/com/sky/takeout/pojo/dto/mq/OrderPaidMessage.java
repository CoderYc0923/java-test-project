package com.sky.takeout.pojo.dto.mq;

import lombok.Builder;
import lombok.Data;

/**
 * 发送到 Topic "pay-order-paid" 的消息体
 * OrderPaidMessage
 */
@Data
@Builder
public class OrderPaidMessage {

    private String eventId;

    private Long orderId;

    private String orderNumber;

    /** 事件发生时间 */
    private String occurredAt;
}
