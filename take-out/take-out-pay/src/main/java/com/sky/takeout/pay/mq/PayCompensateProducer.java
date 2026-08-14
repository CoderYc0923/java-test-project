package com.sky.takeout.pay.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pojo.dto.mq.PayCompensateMessage;
import com.sky.takeout.pojo.enums.PayOutboxEventType;

import tools.jackson.databind.ObjectMapper;

/**
 * 支付补偿命令生产者：CLOSE_CHANNEL / REFUND。
 */
@Component
public class PayCompensateProducer {

    private static final Logger log = LoggerFactory.getLogger(PayCompensateProducer.class);

    private final TakeoutMqProperties takeoutMqProperties;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;

    public PayCompensateProducer(TakeoutMqProperties takeoutMqProperties,
            RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.takeoutMqProperties = takeoutMqProperties;
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendClose(PayCompensateMessage msg) {
        send(takeoutMqProperties.getCompensateCloseTag(), msg);
    }

    public void sendRefund(PayCompensateMessage msg) {
        send(takeoutMqProperties.getCompensateRefundTag(), msg);
    }

    private void send(String tag, PayCompensateMessage msg) {
        try {
            if (msg.getAction() == null) {
                if (takeoutMqProperties.getCompensateCloseTag().equals(tag)) {
                    msg.setAction(PayOutboxEventType.CLOSE_CHANNEL);
                } else {
                    msg.setAction(PayOutboxEventType.REFUND);
                }
            }

            String json = objectMapper.writeValueAsString(msg);
            String destination = takeoutMqProperties.getCompensateTopic() + ":" + tag;
            /**
             * 发送补偿消息
             * 设置消息键为商户订单号
             * 设置消息命令ID给消费者做幂等
             */
            rocketMQTemplate.syncSend(destination,
                MessageBuilder.withPayload(json)
                    .setHeader("KEYS", msg.getOutTradeNo())
                    .setHeader("commandId", msg.getCommandId())
                    .build()
            );
            log.info("{}消息已发送, destination={}", msg.getAction(), destination);
        } catch (Exception e) {
            throw new IllegalStateException("send compensate failed tag=" + tag, e);
        }
    }
}
