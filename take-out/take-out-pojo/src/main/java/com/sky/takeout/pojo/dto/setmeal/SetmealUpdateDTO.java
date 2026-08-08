package com.sky.takeout.pojo.dto.setmeal;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public final class SetmealUpdateDTO {

    @NotNull(message = "套餐id不能为空")
    @Schema(description = "套餐 id")
    private Long id;

    @NotBlank(message = "套餐名称不能为空")
    @Size(min = 2, max = 32, message = "套餐名称长度必须在2到32之间")
    @Schema(description = "套餐名称")
    private String name;

    @NotNull(message = "分类id不能为空")
    @Schema(description = "套餐分类 id")
    private Long categoryId;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Schema(description = "套餐价格")
    private BigDecimal price;

    @NotBlank(message = "图片不能为空")
    @Schema(description = "图片 OSS 路径（objectKey）")
    private String imageOssPath;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "售卖状态：0 停售，1 启售")
    private Integer status;

    @NotEmpty(message = "套餐菜品不能为空")
    @Valid
    @Schema(description = "套餐菜品列表（覆盖更新）")
    private List<SetmealDishDTO> setmealDishes;
}
