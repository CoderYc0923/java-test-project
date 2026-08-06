package com.sky.takeout.pojo.dto.dish;

import com.sky.takeout.pojo.enums.SaleStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启售 / 停售菜品
 */
@Data
public final class DishEnableOrDisableDTO {

    @NotNull(message = "状态不能为空")
    @Schema(description = "售卖状态：0 停售，1 启售", example = "1")
    private SaleStatus status;
}
