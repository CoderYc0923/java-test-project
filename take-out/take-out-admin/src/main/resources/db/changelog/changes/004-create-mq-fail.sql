CREATE TABLE mq_fail (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    consumer_group VARCHAR(128) NOT NULL COMMENT '消费者组',
    topic VARCHAR(128) NOT NULL COMMENT '主题',
    tag VARCHAR(64) NULL COMMENT '标签',
    event_id VARCHAR(64) NULL COMMENT '事件ID',
    biz_key VARCHAR(64) NULL COMMENT '业务键:如order_id / outTradeNo',
    payload TEXT NOT NULL COMMENT '消息体',
    error_message VARCHAR(512) NULL COMMENT '错误信息',
    resonsume_times INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    create_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_created (create_at),
    KEY idx_event_id (event_id)
) COMMENT 'MQ消费失败记录表';