package com.sky.takeout.pojo.dto.setmeal;

import com.sky.takeout.pojo.enums.SaleStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public final class SetmealEnableOrDisableDTO {

    @NotNull(message = "状态不能为空")
    @Schema(description = "售卖状态：0 停售，1 启售", example = "1")
    private SaleStatus status;
}
