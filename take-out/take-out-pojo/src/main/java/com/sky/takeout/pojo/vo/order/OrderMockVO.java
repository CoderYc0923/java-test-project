package com.sky.takeout.pojo.vo.order;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public final class OrderMockVO {

    @Schema(description = "订单 id")
    private Long id;

    @Schema(description = "业务订单号 ORD...")
    private String number;

    @Schema(description = "实付金额")
    private BigDecimal amount;

    @Schema(description = "当前支付尝试的 out_trade_no；未发起支付时可为 null")
    private String outTradeNo;

    @Schema(description = "假微信收银台 URL；requestPay 后写入")
    private String checkoutUrl;
}
