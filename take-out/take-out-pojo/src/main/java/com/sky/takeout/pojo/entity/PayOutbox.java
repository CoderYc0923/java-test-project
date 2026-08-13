package com.sky.takeout.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sky.takeout.pojo.enums.PayOutboxEventType;
import com.sky.takeout.pojo.enums.PayOutboxStatus;

import java.time.LocalDateTime;

import lombok.Data;

@Data
@TableName("pay_outbox")
public class PayOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件ID */
    private String eventId;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNumber;

    /** 事件类型 */
    private PayOutboxEventType eventType;

    /** 消息体 */
    private String payload;

    /** 状态 */
    private PayOutboxStatus status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
