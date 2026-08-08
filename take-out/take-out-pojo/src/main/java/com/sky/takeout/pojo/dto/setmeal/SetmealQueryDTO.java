package com.sky.takeout.pojo.dto.setmeal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public final class SetmealQueryDTO {

    @Min(value = 1, message = "页码至少为1")
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为1")
    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "套餐名称（模糊）")
    private String name;

    @Schema(description = "套餐分类 id")
    private Long categoryId;

    @Schema(description = "售卖状态：0 停售，1 启售")
    private Integer status;
}
