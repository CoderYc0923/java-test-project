package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum PayStatus {
    UNPAID(1, "未支付"),
    PAID(2, "已支付"),
    REFUNDED(3, "退款");

    @EnumValue // MyBatis-Plus 读写库用 code
    @JsonValue // 接口 JSON 吐数字
    private final Integer code;
    private final String message;

    PayStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonCreator // 入参数字能反序列化成枚举
    public static PayStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PayStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Invalid order status code: " + code);
    }
}
