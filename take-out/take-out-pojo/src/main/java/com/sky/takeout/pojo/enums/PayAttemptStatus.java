package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

/**
 * 支付尝试状态。
 * <p>
 * 库字段 {@code pay_attempt.status} 为 VARCHAR，存 code 字符串
 * （PAYING / SUCCESS / CLOSED / REFUNDING / REFUNDED）。
 */
@Getter
public enum PayAttemptStatus {
    PAYING("PAYING", "支付中"),
    SUCCESS("SUCCESS", "支付成功"),
    CLOSED("CLOSED", "已关闭"),
    REFUNDING("REFUNDING", "退款中"),
    REFUNDED("REFUNDED", "已退款");

    /** MyBatis-Plus 读写库用此值 */
    @EnumValue
    @JsonValue
    private final String code;
    private final String message;

    PayAttemptStatus(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonCreator
    public static PayAttemptStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (PayAttemptStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid pay attempt status code: " + code);
    }
}
