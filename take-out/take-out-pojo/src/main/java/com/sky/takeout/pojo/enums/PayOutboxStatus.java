package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

/**
 * 支付出站消息状态
 * PayOutboxStatus
 */
@Getter
public enum PayOutboxStatus {
    NEW("NEW", "待发送"),
    SENT("SENT", "发送中"),
    FAILED("FAILED", "发送失败");

    @EnumValue
    @JsonValue
    private final String code;
    private final String message;

    PayOutboxStatus(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonCreator
    public static PayOutboxStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (PayOutboxStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid pay outbox status code: " + code);
    }
}
