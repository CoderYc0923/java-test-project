package com.sky.takeout.pojo.dto.dish;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品口味（新增 / 修改时嵌套提交）
 */
@Data
public final class DishFlavorDTO {

    @Schema(description = "口味 id（修改时可选）")
    private Long id;

    @Schema(description = "口味名称", example = "辣度")
    private String name;

    @Schema(description = "口味选项 JSON 字符串", example = "[\"不辣\",\"微辣\",\"中辣\"]")
    private String value;
}
