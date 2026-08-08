package com.sky.takeout.pojo.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分类查询DTO
 * @author Cyrus
 * @since 2026-08-06
 */
@Data
public final class CategoryQueryDTO {

    @Min(value = 1, message = "页码至少为1")
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为1")
    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "分类名称（模糊查询）", example = "美食")
    private String name;

    @Schema(description = "分类类型", example = "1：菜品分类，2：套餐分类")
    private Integer type;
}
