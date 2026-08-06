package com.sky.takeout.pojo.dto.dish;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品分页查询
 */
@Data
public final class DishQueryDTO {

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "菜品名称（模糊查询）", example = "宫保鸡丁")
    private String name;

    @Schema(description = "分类 id", example = "18")
    private Long categoryId;

    @Schema(description = "售卖状态：0 停售，1 启售", example = "1")
    private Integer status;
}
