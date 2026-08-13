package com.sky.takeout.pay.mq;

import org.springframework.stereotype.Component;

/**
 * 订单支付消息生产者
 * OrderPaidProducer
 */
@Component
public class OrderPaidProducer {


    /**
     * 发送订单支付消息
     * @param jsonPayload 消息体
     * @param orderId     订单ID
     * @param eventId     事件ID
     */
    public void send(String jsonPayload, Long orderId, String eventId) {
        
    }
}
