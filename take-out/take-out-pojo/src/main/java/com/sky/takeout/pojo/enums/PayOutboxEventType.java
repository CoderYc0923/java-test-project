package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

/**
 * 支付出站消息事件类型
 * PayOutboxEventType
 */
@Getter
public enum PayOutboxEventType {

    ORDER_PAID("ORDER_PAID", "订单支付");

    @EnumValue
    @JsonValue
    private final String code;
    private final String message;

    PayOutboxEventType(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonCreator
    public static PayOutboxEventType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (PayOutboxEventType eventType : values()) {
            if (eventType.code.equals(code)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Invalid pay outbox event type code: " + code);
    }
}
