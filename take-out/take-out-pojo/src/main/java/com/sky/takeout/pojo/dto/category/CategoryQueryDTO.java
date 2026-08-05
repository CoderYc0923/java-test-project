package com.sky.takeout.pojo.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public final class CategoryQueryDTO {

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "分类名称（模糊查询）", example = "美食")
    private String name;

    @Schema(description = "分类类型", example = "1：菜品分类，2：套餐分类")
    private Integer type;
}
