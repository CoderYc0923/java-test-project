package com.sky.takeout.pojo.dto.dish;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改菜品
 */
@Data
public final class DishUpdateDTO {

    @NotNull(message = "菜品id不能为空")
    @Schema(description = "菜品 id")
    private Long id;

    @NotBlank(message = "菜品名称不能为空")
    @Size(min = 2, max = 32, message = "菜品名称长度必须在2到32之间")
    @Schema(description = "菜品名称", example = "宫保鸡丁")
    private String name;

    @NotNull(message = "分类id不能为空")
    @Schema(description = "分类 id", example = "18")
    private Long categoryId;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Schema(description = "价格", example = "38.00")
    private BigDecimal price;

    @NotBlank(message = "图片不能为空")
    @Schema(description = "图片 OSS 路径（objectKey），非签名 URL")
    private String imageOssPath;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "售卖状态：0 停售，1 启售")
    private Integer status;

    @Valid
    @Schema(description = "口味列表（覆盖更新）")
    private List<DishFlavorDTO> flavors;
}
