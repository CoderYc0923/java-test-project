package com.sky.takeout.pojo.dto.dish;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新增菜品
 */
@Data
public final class DishSaveDTO {

    @Schema(description = "菜品名称", example = "宫保鸡丁")
    private String name;

    @Schema(description = "分类 id", example = "18")
    private Long categoryId;

    @Schema(description = "价格", example = "38.00")
    private BigDecimal price;

    @Schema(description = "图片地址")
    private String image;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "售卖状态：0 停售，1 启售；新增前端默认 0")
    private Integer status;

    @Schema(description = "口味列表")
    private List<DishFlavorDTO> flavors;
}
