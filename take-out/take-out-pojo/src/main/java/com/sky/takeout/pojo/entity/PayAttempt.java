package com.sky.takeout.pojo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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

    private String status;

    private BigDecimal amount;

    private String prepayId;

    private Integer payingFlag;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;
}
