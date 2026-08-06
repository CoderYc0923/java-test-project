package com.sky.takeout.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum CategoryType {
    DISH(1, "菜品"),
    SETMEAL(2, "套餐");

    @EnumValue // MyBatis-Plus ↔ 数据库
    @JsonValue // 序列化输出 "1"/"2"
    private final Integer code;
    private final String message;

    CategoryType(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 反序列化：JSON 的 "1"/"2" → 枚举 */
    @JsonCreator
    public static CategoryType fromCode(Integer code) {
        if (code == null) {
            return null;
        }

        for (CategoryType categoryType : values()) {
            if (categoryType.code.equals(code)) {
                return categoryType;
            }
        }

        throw new IllegalArgumentException("Invalid category type code: " + code);
    }


}
