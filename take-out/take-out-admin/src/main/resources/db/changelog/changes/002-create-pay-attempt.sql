CREATE TABLE IF NOT EXISTS pay_attempt (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '业务单ID',
    order_number VARCHAR(64) NOT NULL COMMENT '业务单单号',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '渠道商户单号',
    channel VARCHAR(32) NOT NULL DEFAULT 'WECHAT' COMMENT '支付渠道',
    status VARCHAR(32) NOT NULL COMMENT '支付状态：PAYING/SUCCESS/CLOSED/REFUNDING/REFUNDED',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    prepay_id VARCHAR(128) NULL COMMENT '预支付ID',
    paying_flag TINYINT NULL COMMENT '是否正在支付：1=进行中，否则就是null',
    create_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    UNIQUE KEY uk_order_paying (order_id, paying_flag),
    KEY idx_order_id (order_id)
) COMMENT='支付尝试表';