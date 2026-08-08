package com.sky.takeout.pojo.dto.setmeal;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public final class SetmealDishDTO {

    @NotNull(message = "菜品id不能为空")
    @Schema(description = "菜品 id")
    private Long dishId;

    @Schema(description = "菜品名称（冗余）")
    private String name;

    @Schema(description = "菜品单价（冗余）")
    private BigDecimal price;

    @NotNull(message = "份数不能为空")
    @Min(value = 1, message = "份数至少为1")
    @Schema(description = "份数")
    private Integer copies;
}
