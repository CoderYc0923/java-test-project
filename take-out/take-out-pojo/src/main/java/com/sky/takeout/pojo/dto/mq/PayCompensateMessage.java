package com.sky.takeout.pojo.dto.mq;

import com.sky.takeout.pojo.enums.PayOutboxEventType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayCompensateMessage {

    /** 补偿命令id */
    private String commandId;

    /** 补偿动作: CLOSE_CHANNEL / REFUND */
    private PayOutboxEventType action;

    /** 订单id */
    private Long orderId;

    /** 商户订单号 */
    private String outTradeNo;

    /** 支付尝试id */
    private Long payAttemptId;

    /** 期望迁出状态 */
    private String statusFrom;

    /** 补偿原因 */
    private String reason;

    /** 事件发生时间 */
    private String occurredAt;
}
