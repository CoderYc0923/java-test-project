package com.sky.takeout.pojo.dto.mq;

import com.sky.takeout.pojo.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusMessage {
    
    private String eventId;

    private Long orderId;

    private String orderNumber;

    private OrderStatus fromStatus;

    private OrderStatus toStatus;

    private String occurredAt;
}
