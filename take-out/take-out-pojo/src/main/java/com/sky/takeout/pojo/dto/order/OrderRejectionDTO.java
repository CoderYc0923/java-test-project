package com.sky.takeout.pojo.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 拒单入参。仅待接单(2)可拒；原因写入 rejection_reason。
 */
@Data
public final class OrderRejectionDTO {

    @Schema(description = "订单主键 id", example = "1001")
    @NotNull(message = "订单id不能为空")
    private Long id;

    @Schema(description = "拒单原因", example = "订单量过多，无法接单")
    @NotBlank(message = "拒单原因不能为空")
    private String rejectionReason;
}