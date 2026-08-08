package com.sky.takeout.pojo.dto.order;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public final class OrderMockDTO {

    @Schema(description = "备注")
    private String remark;

    @NotEmpty(message = "请至少选择一件商品")
    @Valid
    @Schema(description = "明细行")
    private List<OrderMockItemDTO> items;
}