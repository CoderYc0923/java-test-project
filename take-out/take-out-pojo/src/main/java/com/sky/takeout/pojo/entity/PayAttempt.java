package com.sky.takeout.pojo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.takeout.pojo.enums.PayAttemptStatus;

import lombok.Data;

@Data
@TableName("pay_attempt")
public class PayAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNumber;

    private String outTradeNo;

    private String channel;

    /** 库中存枚举 code（VARCHAR），读写由 {@link PayAttemptStatus} 的 @EnumValue 完成 */
    private PayAttemptStatus status;

    private BigDecimal amount;

    private String prepayId;

    private Integer payingFlag;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;
}
