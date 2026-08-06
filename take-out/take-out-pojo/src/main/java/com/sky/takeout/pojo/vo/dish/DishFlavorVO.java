package com.sky.takeout.pojo.vo.dish;

import lombok.Data;

@Data
public final class DishFlavorVO {

    private Long id;
    private Long dishId;
    private String name;
    private String value;
}
