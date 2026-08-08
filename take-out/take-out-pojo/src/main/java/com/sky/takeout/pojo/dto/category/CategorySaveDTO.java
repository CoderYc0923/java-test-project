package com.sky.takeout.pojo.dto.category;

import com.sky.takeout.pojo.enums.CategoryType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增分类DTO
 * @author Cyrus
 * @since 2026-08-06
 */
@Data
public final class CategorySaveDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(min = 2, max = 20, message = "分类名称长度必须在2-20个字符之间")
    @Schema(description = "分类名称", example = "美食")
    private String name;

    @NotNull(message = "分类类型不能为空")
    @Schema(description = "分类类型", example = "1：菜品分类，2：套餐分类")
    private CategoryType type;

    @NotNull(message = "排序不能为空")
    @Min(value = 0, message = "排序必须在0-99之间")
    @Max(value = 99, message = "排序必须在0-99之间")
    @Schema(description = "排序", example = "1")
    private Integer sort;
}
