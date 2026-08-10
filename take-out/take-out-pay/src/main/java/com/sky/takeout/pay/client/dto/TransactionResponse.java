package com.sky.takeout.pay.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * 微信支付响应
 * TransactionResponse
 */
@Data
@Builder
public class TransactionResponse {

    /** 商户订单号 = orders.number */
    @JsonProperty("out_trade_no")
    private String outTradeNo;

    /** 预支付 id，形如 wx_prepay_...；本期可只打日志，不必落库 */
    @JsonProperty("prepay_id")
    private String prepayId;

    /** 商品描述 */
    private String description;

    /** 金额 */
    private BigDecimal amount;

    /** 货币类型 */
    private String currency;

    /** 交易状态：NOTPAY（未支付） / SUCCESS（支付成功） */
    @JsonProperty("trade_state")
    private String tradeState;
}
