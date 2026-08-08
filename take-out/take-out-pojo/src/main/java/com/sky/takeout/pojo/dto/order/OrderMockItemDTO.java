package com.sky.takeout.pojo.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模拟下单的一行：dishId / setmealId 二选一（在 Service 里校验）
 */
@Data
public final class OrderMockItemDTO {

    @Schema(description = "菜品 id（与 setmealId 二选一）")
    private Long dishId;

    @Schema(description = "套餐 id（与 dishId 二选一）")
    private Long setmealId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    @Schema(description = "数量", example = "1")
    private Integer number;

    @Schema(description = "口味备注（仅菜品）", example = "微辣")
    private String dishFlavor;
}