package com.sky.takeout.pojo.dto.setmeal;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public final class SetmealDishDTO {

    @Schema(description = "菜品 id")
    private Long dishId;

    @Schema(description = "菜品名称（冗余）")
    private String name;

    @Schema(description = "菜品单价（冗余）")
    private BigDecimal price;

    @Schema(description = "份数")
    private Integer copies;
}
