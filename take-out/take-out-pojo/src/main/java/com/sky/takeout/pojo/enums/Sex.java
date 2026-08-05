package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum Sex {
    FEMALE("0", "女"),
    MALE("1", "男");

    @EnumValue // MyBatis-Plus ↔ 数据库
    @JsonValue // 序列化输出 "0"/"1"
    private final String code;
    private final String message;

    Sex(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 反序列化：JSON 的 "0"/"1" → 枚举 */
    @JsonCreator
    public static Sex fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        for (Sex sex : values()) {
            if (sex.code.equals(code)) {
                return sex;
            }
        }

        throw new IllegalArgumentException("Invalid sex code: " + code);
    }
}
