package com.sky.takeout.pojo.vo.setmeal;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public final class SetmealDishVO {

    private Long id;
    private Long setmealId;
    private Long dishId;
    private String name;
    private BigDecimal price;
    private Integer copies;
}
