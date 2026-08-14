package com.sky.takeout.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * MQ消费失败记录表
 * AllArgsConstructor 全参构造函数
 * MqFail
 */
@Data
@TableName("mq_fail")
public class MqFail {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消费者组
     */
    private String consumerGroup;

    /**
     * 主题
     */
    private String topic;

    /**
     * 标签
     */
    private String tag;

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 业务键
     */
    private String bizKey;

    /**
     * 消息体
     */
    private String payload;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 重试次数
     */
    private Integer resonsumeTimes;

    /**
     * 创建时间
     */
    private LocalDateTime createAt;

}
