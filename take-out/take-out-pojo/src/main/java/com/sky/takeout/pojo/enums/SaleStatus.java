package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

/**
 * 启售 / 停售状态（菜品、套餐等）
 */
@Getter
public enum SaleStatus {
    DISABLE(0, "停售"),
    ENABLE(1, "启售");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String message;

    SaleStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonCreator
    public static SaleStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SaleStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid sale status code: " + code);
    }
}
