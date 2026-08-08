package com.sky.takeout.pojo.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 接单入参。前端 PUT /order/confirm，body: { "id": 1001 }
 */
@Data
public final class OrderConfirmDTO {

    @Schema(description = "订单主键 id", example = "1001")
    @NotNull(message = "订单id不能为空")
    private Long id;
}