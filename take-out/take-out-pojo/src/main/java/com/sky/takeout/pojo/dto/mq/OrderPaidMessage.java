package com.sky.takeout.pojo.dto.mq;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送到 Topic 的订单已支付消息体。
 * <p>
 * 不用仅依赖 Lombok {@code @NoArgsConstructor}：手写无参/全参构造，
 * 保证 tools.jackson 反序列化一定能 new 出来（避免只编了 module 未 install 到 .m2 时更难排查）。
 */
@Getter
@Setter
@Builder
public class OrderPaidMessage {

    private String eventId;

    private Long orderId;

    private String orderNumber;

    /** 事件发生时间 */
    private String occurredAt;

    /** Jackson 反序列化必需 */
    public OrderPaidMessage() {
    }

    public OrderPaidMessage(String eventId, Long orderId, String orderNumber, String occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.occurredAt = occurredAt;
    }
}
