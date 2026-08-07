package com.sky.takeout.pojo.dto.setmeal;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public final class SetmealSaveDTO {

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "套餐分类 id")
    private Long categoryId;

    @Schema(description = "套餐价格")
    private BigDecimal price;

    @Schema(description = "图片 OSS 路径（objectKey）")
    private String imageOssPath;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "售卖状态：0 停售，1 启售；新增前端默认 0")
    private Integer status;

    @Schema(description = "套餐菜品列表")
    private List<SetmealDishDTO> setmealDishes;
}
