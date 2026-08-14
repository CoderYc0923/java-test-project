package com.sky.takeout.system.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pojo.dto.mq.PayCompensateMessage;
import com.sky.takeout.pojo.entity.MqFail;
import com.sky.takeout.pojo.enums.PayOutboxEventType;
import com.sky.takeout.system.mapper.MqFailMapper;

import tools.jackson.databind.ObjectMapper;

@Component
@RocketMQMessageListener(topic = "%DLQ%take-pay-compensate-consumer", consumerGroup = "take-pay-compensate-dlq-archiver")
public class PayCompensateDlqArchiver implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(PayCompensateDlqArchiver.class);

    private final MqFailMapper mqFailMapper;
    private final TakeoutMqProperties mqProperties;
    private final ObjectMapper objectMapper;

    public PayCompensateDlqArchiver(MqFailMapper mqFailMapper, TakeoutMqProperties mqProperties,
            ObjectMapper objectMapper) {
        this.mqProperties = mqProperties;
        this.mqFailMapper = mqFailMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String body) {
        if (!StringUtils.hasText(body)) {
            log.error("PayCompensateDlqArchiver 死信消息body为空，丢弃");
            return;
        }

        // 入库，定期归档
        MqFail row = new MqFail();
        // 填原来那个失败的消费组
        row.setConsumerGroup(mqProperties.getCompensateConsumerGroup());
        row.setTopic(mqProperties.getCompensateTopic());
        row.setPayload(body);
        row.setErrorMessage("moved to DLQ after max reconsumeTimes");
        row.setResonsumeTimes(0);

        try {
            PayCompensateMessage msg = objectMapper.readValue(body, PayCompensateMessage.class);
            if (msg != null) {
                row.setEventId(msg.getCommandId());
                // 若补偿动作为空，则默认关闭渠道
                String tag = msg.getAction() == null ? PayOutboxEventType.CLOSE_CHANNEL.getCode() : msg.getAction().getCode();
                row.setTag(tag);
                if (msg.getOrderId() != null) {
                    row.setBizKey(String.valueOf(msg.getOrderId()));
                }
            }
        } catch (Exception e) {
            log.warn("PayCompensateDlqArchiver 死信消息解析失败", e);
        }

        try {
            mqFailMapper.insert(row);
            log.error("PayCompensateDlqArchiver 死信消息入库，id={}", row.getId());
        } catch (RuntimeException e) {
            // 入库失败必须抛出，否则Broker认为归档成功，死信记录就丢了
            log.error("PayCompensateDlqArchiver 死信消息入库失败，body={}", body, e);
            throw e;
        }

    }

}