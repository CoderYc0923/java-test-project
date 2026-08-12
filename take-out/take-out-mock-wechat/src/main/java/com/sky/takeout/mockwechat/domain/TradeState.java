package com.sky.takeout.mockwechat.domain;

/**
 * 渠道交易状态（形似微信 trade_state；教学扩展 CLOSED / REFUND）。
 */
public enum TradeState {
    /** 未支付 */
    NOTPAY,
    /** 支付成功 */
    SUCCESS,
    /** 已关单（未付关闭） */
    CLOSED,
    /** 已退款（教学简化：整单退） */
    REFUND
}
