-- 支付出站消息表: 与入账同事务写入，提交后再发MQ
CREATE TABLE pay_outbox (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL COMMENT '事件ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_number VARCHAR(64) NOT NULL COMMENT '订单号',
    event_type VARCHAR(32) NOT NULL COMMENT '事件类型,如ORDER_PAID',
    payload TEXT NOT NULL COMMENT 'JSON消息体：事件内容',
    status VARCHAR(16) NOT NULL COMMENT '状态: NEW-待发送,SENT-发送中,FAILED-发送失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at DATETIME NULL COMMENT '下次重试时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_status_created (status, created_at),
    KEY idx_order_id (order_id)
) comment '支付出站消息表';