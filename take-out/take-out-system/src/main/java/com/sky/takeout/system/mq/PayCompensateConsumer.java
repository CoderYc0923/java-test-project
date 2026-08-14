package com.sky.takeout.system.mq;

import java.time.Duration;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sky.takeout.pay.client.MockWechatHttpClient;
import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.mq.PayCompensateMessage;
import com.sky.takeout.pojo.enums.PayAttemptStatus;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RocketMQMessageListener(topic = "${mq.compensate-topic:takeout-pay-compensate}", consumerGroup = "${mq.compensate-consumer-group:take-pay-compensate-consumer}", maxReconsumeTimes = 3)
public class PayCompensateConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(PayCompensateConsumer.class);
    private static final String IDEMPOTENT_PREFIX = "mq:consume:pay-compensate:";

    private final ObjectMapper objectMapper;
    private final RedisIdempotentHelper redis;
    private final MockWechatHttpClient mockWechatHttpClient;
    private final PayAttemptPort payAttemptPort;

    public PayCompensateConsumer(ObjectMapper objectMapper, RedisIdempotentHelper redis,
            MockWechatHttpClient mockWechatHttpClient, PayAttemptPort payAttemptPort) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.mockWechatHttpClient = mockWechatHttpClient;
        this.payAttemptPort = payAttemptPort;
    }

    @Override
    public void onMessage(String body) {
        PayCompensateMessage msg;
        try {
            msg = objectMapper.readValue(body, PayCompensateMessage.class);
        } catch (Exception e) {
            log.error("解析支付补偿消息失败，body={}", body, e);
            return;
        }

        String idemKey = IDEMPOTENT_PREFIX + msg.getCommandId();
        if (StringUtils.hasText(redis.get(idemKey))) {
            log.info("幂等短路，消息已消费 commandId={}", msg.getCommandId());
            return;
        }

        if (msg.getAction() == null) {
            log.error("支付补偿消息缺少动作，丢弃 body={}", body);
            return;
        }

        try {
            switch (msg.getAction()) {
                case CLOSE_CHANNEL:
                    closeChannel(msg);
                    break;
                case REFUND:
                    refund(msg);
                    break;
                default:
                    log.error("不支持的支付补偿动作: {}", msg.getAction());
                    return;
            }
        } catch (RuntimeException e) {
            log.error("处理支付补偿消息{}失败，commandId={}", msg.getAction(), msg.getCommandId(), e);
            throw e;
        }

        redis.trySetNx(idemKey, "SUCCESS", Duration.ofDays(7).getSeconds());
    }

    private void closeChannel(PayCompensateMessage msg) {
        mockWechatHttpClient.close(msg.getOutTradeNo());
        casCloseLocal(msg);
    }

    private void refund(PayCompensateMessage msg) {
        mockWechatHttpClient.refund(msg.getOutTradeNo(), msg.getReason());
        casRefundLocal(msg);
    }

    /**
     * 渠道关单成功后，CAS 把本地 attempt 标为 CLOSED。
     */
    private void casCloseLocal(PayCompensateMessage msg) {
        if (msg.getPayAttemptId() == null) {
            log.warn("CLOSE_CHANNEL 消息缺少 payAttemptId, outTradeNo={}", msg.getOutTradeNo());
            return;
        }

        PayAttemptStatus fromStatus = PayAttemptStatus.PAYING;
        if (StringUtils.hasText(msg.getStatusFrom())) {
            try {
                fromStatus = PayAttemptStatus.fromCode(msg.getStatusFrom());
            } catch (Exception e) {
                log.warn("statusFrom 无法解析，回退 PAYING statusFrom={}", msg.getStatusFrom());
                fromStatus = PayAttemptStatus.PAYING;
            }
        }
        if (fromStatus == null) {
            fromStatus = PayAttemptStatus.PAYING;
        }

        int rows = payAttemptPort.updateStatus(
                msg.getPayAttemptId(), fromStatus, PayAttemptStatus.CLOSED, null);
        if (rows == 0) {
            log.warn("标 CLOSED 未更新到行 payAttemptId={} from={}", msg.getPayAttemptId(), fromStatus);
        } else {
            log.info("本地已标 CLOSED payAttemptId={} outTradeNo={}",
                    msg.getPayAttemptId(), msg.getOutTradeNo());
        }
    }

    /**
     * CAS更新库中的退款状态
     */
    private void casRefundLocal(PayCompensateMessage msg) {
        if (msg.getPayAttemptId() == null) {
            log.warn("REFUND消息缺少payAttemptId,outTradeNo={}", msg.getOutTradeNo());
            return;
        }

        PayAttemptStatus fromStatus = msg.getStatusFrom() == null ? PayAttemptStatus.REFUNDING
                : PayAttemptStatus.fromCode(msg.getStatusFrom());
        int rows = payAttemptPort.updateStatus(msg.getPayAttemptId(), fromStatus, PayAttemptStatus.REFUNDED, null);

        if (rows == 0) {
            log.warn("CAS更新退款状态失败,payAttemptId={}", msg.getPayAttemptId());
        } else {
            log.info("CAS更新退款状态成功,payAttemptId={}", msg.getPayAttemptId());
        }
    }
}
