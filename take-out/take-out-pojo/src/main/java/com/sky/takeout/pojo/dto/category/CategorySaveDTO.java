package com.sky.takeout.pojo.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public final class CategorySaveDTO {

    @NotEmpty(message = "分类名称不能为空")
    @Size(max = 20, min = 2, message = "分类名称长度必须在2-20个字符之间")
    @Schema(description = "分类名称", example = "美食")
    private String name;

    @NotNull(message = "分类类型不能为空")
    @Schema(description = "分类类型", example = "1：菜品分类，2：套餐分类")
    private Integer type;

    @NotNull(message = "排序不能为空")
    @Size(min = 0, max = 99, message = "排序必须在0-99之间")
    @Schema(description = "排序", example = "1")
    private Integer sort;
}
