package com.sky.takeout.system.mq;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pojo.dto.mq.OrderPaidMessage;
import com.sky.takeout.pojo.entity.MqFail;
import com.sky.takeout.system.mapper.MqFailMapper;

import tools.jackson.databind.ObjectMapper;

@Component
@RocketMQMessageListener(topic = "%DLQ%take-kitchen-consumer", consumerGroup = "take-kitchen-dlq-archiver")
public class OrderPaidDlqArchiver implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidDlqArchiver.class);

    private final MqFailMapper mqFailMapper;
    private final TakeoutMqProperties mqProperties;
    private final ObjectMapper objectMapper;

    public OrderPaidDlqArchiver(MqFailMapper mqFailMapper, TakeoutMqProperties mqProperties,
            ObjectMapper objectMapper) {
        this.mqProperties = mqProperties;
        this.mqFailMapper = mqFailMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(String body) {
        if (!StringUtils.hasText(body)) {
            log.error("OrderPaidDlqArchiver 死信消息body为空，丢弃");
            return;
        }

        // 入库，定期归档
        MqFail row = new MqFail();
        // 填原来那个失败的消费组
        row.setConsumerGroup(mqProperties.getOrderPaidConsumerGroup());
        row.setTopic(mqProperties.getOrderPaidTopic());
        row.setTag(mqProperties.getOrderPaidTag());
        row.setPayload(body);
        row.setErrorMessage("moved to DLQ after max reconsumeTimes");
        row.setResonsumeTimes(0);

        try {
            OrderPaidMessage msg = objectMapper.readValue(body, OrderPaidMessage.class);
            if (msg != null) {
                row.setEventId(msg.getEventId());
                if (msg.getOrderId() != null) {
                    row.setBizKey(String.valueOf(msg.getOrderId()));
                }
            }
        } catch (Exception e) {
            log.warn("OrderPaidDlqArchiver 死信消息解析失败", e);
        }

        try {
            mqFailMapper.insert(row);
            log.error("OrderPaidDlqArchiver 死信消息入库，id={}", row.getId());
        } catch (RuntimeException e) {
            // 入库失败必须抛出，否则Broker认为归档成功，死信记录就丢了
            log.error("OrderPaidDlqArchiver 死信消息入库失败，body={}", body, e);
            throw e;
        }

    }

}