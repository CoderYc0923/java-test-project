package com.sky.takeout.pojo.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 取消入参。管理端取消写入 cancel_reason + cancel_time。
 */
@Data
public final class OrderCancelDTO {

    @Schema(description = "订单主键 id", example = "1001")
    @NotNull(message = "订单id不能为空")
    private Long id;

    @Schema(description = "取消原因", example = "菜品已售完")
    @NotBlank(message = "取消原因不能为空")
    private String cancelReason;
}