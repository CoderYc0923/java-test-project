package com.sky.takeout.pojo.vo.order;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public final class OrderMockVO {

    @Schema(description = "订单 id")
    private Long id;

    @Schema(description = "订单号")
    private String number;

    @Schema(description = "实付金额")
    private BigDecimal amount;
}