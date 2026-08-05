package com.sky.takeout.pojo.dto.employee;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Data
public final class EmployeeEnableOrDisableDTO {

    @NotNull(message = "启用禁用状态不能为空")
    @Schema(description = "启用禁用状态", example = "1:启用, 0:禁用")
    private Integer status;
    
}
