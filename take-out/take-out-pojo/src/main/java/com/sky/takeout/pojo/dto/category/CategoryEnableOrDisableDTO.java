package com.sky.takeout.pojo.dto.category;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 启用禁用分类DTO
 * @param status 启用禁用状态
 * @example 1:启用, 0:禁用
 * @author Cyrus
 * @since 2026-08-06
 */
@Data
public final class CategoryEnableOrDisableDTO {
    
    @NotNull(message = "状态不能为空")
    @Schema(description = "状态", example = "1")
    private Integer status;
}
