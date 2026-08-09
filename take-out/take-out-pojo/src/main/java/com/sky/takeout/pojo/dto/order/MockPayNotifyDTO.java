package com.sky.takeout.pojo.dto.order;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模拟微信支付回调DTO
 * MockPayNotifyDTO
 */
@Data
public class MockPayNotifyDTO {

    @NotBlank
    @Schema(description = "商户订单号，对应 orders.number")
    private String orderNumber;

    @NotNull
    @Schema(description = "渠道声称的实付金额，必须与订单 amount 一致")
    private BigDecimal amount;

    @NotNull
    @Schema(description = "秒级时间戳，防重放")    
    private Long timestamp;

    @NotBlank
    @Schema(description = "一次性随机串，Redis 去重")
    private String nonce;

    @NotBlank
    @Schema(description = "HMAC-SHA256 十六进制签名")
    private String sign;
}
