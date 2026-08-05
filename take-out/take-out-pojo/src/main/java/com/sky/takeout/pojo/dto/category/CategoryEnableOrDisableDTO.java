package com.sky.takeout.pojo.dto.category;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public final class CategoryEnableOrDisableDTO {
    
    @NotNull(message = "状态不能为空")
    @Schema(description = "状态", example = "1")
    private Integer status;
}
