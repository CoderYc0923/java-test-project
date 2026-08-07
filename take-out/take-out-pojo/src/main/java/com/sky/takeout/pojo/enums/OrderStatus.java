package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING_PAYMENT(1, "待付款"),
    TO_BE_CONFIRMED(2, "待接单"), // 种子数据就是这个
    CONFIRMED(3, "已接单"), // 前端叫「待派送」
    DELIVERY_IN_PROGRESS(4, "派送中"),
    COMPLETED(5, "已完成"),
    CANCELLED(6, "已取消"),
    REFUND(7, "退款"); // 一期几乎不用，先放着对齐表注释

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String message;

    OrderStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonCreator
    public static OrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Invalid order status code: " + code);
    }
}
