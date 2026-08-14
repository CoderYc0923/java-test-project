package com.sky.takeout.system.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sky.takeout.pay.client.MockWechatHttpClient;
import com.sky.takeout.pay.config.TakeoutMqProperties;
import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pay.redis.RedisIdempotentHelper;
import com.sky.takeout.pojo.dto.mq.OrderPaidMessage;
import com.sky.takeout.pojo.dto.mq.PayCompensateMessage;
import com.sky.takeout.pojo.entity.MqFail;
import com.sky.takeout.pojo.enums.PayAttemptStatus;
import com.sky.takeout.pojo.enums.PayOutboxEventType;
import com.sky.takeout.system.mapper.MqFailMapper;
import com.sky.takeout.system.notify.KitchenNotifyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * F4 / F5 消费者小单测：幂等、失败重试语义、补偿 CAS、死信入库。
 */
@ExtendWith(MockitoExtension.class)
class MqF4F5ConsumerTest {

    @Mock
    private RedisIdempotentHelper redis;
    @Mock
    private KitchenNotifyService kitchenNotifyService;
    @Mock
    private MockWechatHttpClient mockWechatHttpClient;
    @Mock
    private PayAttemptPort payAttemptPort;
    @Mock
    private MqFailMapper mqFailMapper;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private OrderPaidKitchenConsumer kitchenConsumer;
    private PayCompensateConsumer compensateConsumer;
    private PayCompensateDlqArchiver compensateDlqArchiver;

    @BeforeEach
    void setUp() {
        kitchenConsumer = new OrderPaidKitchenConsumer(objectMapper, redis, kitchenNotifyService);
        compensateConsumer = new PayCompensateConsumer(objectMapper, redis, mockWechatHttpClient, payAttemptPort);

        TakeoutMqProperties props = new TakeoutMqProperties();
        props.setCompensateConsumerGroup("take-pay-compensate-consumer");
        props.setCompensateTopic("takeout-pay-compensate");
        compensateDlqArchiver = new PayCompensateDlqArchiver(mqFailMapper, props, objectMapper);
    }

    // ---------- F4 厨房 ----------

    @Test
    void kitchen_poisonPayload_shouldAckWithoutRetry() {
        assertDoesNotThrow(() -> kitchenConsumer.onMessage("{not-json"));
        verify(kitchenNotifyService, never()).notifyNewOrder(any());
        verify(redis, never()).trySetNx(anyString(), anyString(), anyLong());
    }

    @Test
    void kitchen_notifyFails_shouldRethrowForMqRetry() {
        OrderPaidMessage msg = OrderPaidMessage.builder()
                .eventId("e-1")
                .orderId(1L)
                .orderNumber("O1")
                .build();
        when(redis.get("mq:consume:order-paid:e-1")).thenReturn(null);
        doThrow(new IllegalStateException("ws down")).when(kitchenNotifyService).notifyNewOrder(any());

        assertThrows(IllegalStateException.class,
                () -> kitchenConsumer.onMessage(objectMapper.writeValueAsString(msg)));
        verify(redis, never()).trySetNx(anyString(), anyString(), anyLong());
    }

    @Test
    void kitchen_success_shouldSetIdempotentAfterNotify() {
        OrderPaidMessage msg = OrderPaidMessage.builder()
                .eventId("e-2")
                .orderId(2L)
                .orderNumber("O2")
                .build();
        when(redis.get("mq:consume:order-paid:e-2")).thenReturn(null);
        when(redis.trySetNx(eq("mq:consume:order-paid:e-2"), eq("SUCCESS"), anyLong())).thenReturn(true);

        kitchenConsumer.onMessage(objectMapper.writeValueAsString(msg));

        verify(kitchenNotifyService).notifyNewOrder(any(OrderPaidMessage.class));
        verify(redis).trySetNx(eq("mq:consume:order-paid:e-2"), eq("SUCCESS"), anyLong());
    }

    // ---------- F5 补偿 ----------

    @Test
    void compensate_alreadyDone_shouldSkipSideEffects() {
        PayCompensateMessage msg = PayCompensateMessage.builder()
                .commandId("c-1")
                .action(PayOutboxEventType.REFUND)
                .outTradeNo("OUT1")
                .build();
        when(redis.get("mq:consume:pay-compensate:c-1")).thenReturn("SUCCESS");

        compensateConsumer.onMessage(objectMapper.writeValueAsString(msg));

        verify(mockWechatHttpClient, never()).refund(anyString(), any());
        verify(payAttemptPort, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void compensate_refund_shouldCallChannelThenCasLocal() {
        PayCompensateMessage msg = PayCompensateMessage.builder()
                .commandId("c-2")
                .action(PayOutboxEventType.REFUND)
                .outTradeNo("OUT2")
                .payAttemptId(88L)
                .statusFrom("REFUNDING")
                .reason("duplicate_pay")
                .build();
        when(redis.get("mq:consume:pay-compensate:c-2")).thenReturn(null);
        when(payAttemptPort.updateStatus(88L, PayAttemptStatus.REFUNDING, PayAttemptStatus.REFUNDED, null))
                .thenReturn(1);
        when(redis.trySetNx(eq("mq:consume:pay-compensate:c-2"), eq("SUCCESS"), anyLong())).thenReturn(true);

        compensateConsumer.onMessage(objectMapper.writeValueAsString(msg));

        verify(mockWechatHttpClient).refund("OUT2", "duplicate_pay");
        verify(payAttemptPort).updateStatus(88L, PayAttemptStatus.REFUNDING, PayAttemptStatus.REFUNDED, null);
        verify(redis).trySetNx(eq("mq:consume:pay-compensate:c-2"), eq("SUCCESS"), anyLong());
    }

    @Test
    void compensate_close_shouldCallChannelThenCasClosed() {
        PayCompensateMessage msg = PayCompensateMessage.builder()
                .commandId("c-3")
                .action(PayOutboxEventType.CLOSE_CHANNEL)
                .outTradeNo("OUT3")
                .payAttemptId(99L)
                .statusFrom("PAYING")
                .build();
        when(redis.get("mq:consume:pay-compensate:c-3")).thenReturn(null);
        when(payAttemptPort.updateStatus(99L, PayAttemptStatus.PAYING, PayAttemptStatus.CLOSED, null))
                .thenReturn(1);
        when(redis.trySetNx(eq("mq:consume:pay-compensate:c-3"), eq("SUCCESS"), anyLong())).thenReturn(true);

        compensateConsumer.onMessage(objectMapper.writeValueAsString(msg));

        verify(mockWechatHttpClient).close("OUT3");
        verify(payAttemptPort).updateStatus(99L, PayAttemptStatus.PAYING, PayAttemptStatus.CLOSED, null);
    }

    @Test
    void compensate_channelFails_shouldRethrowAndNotSetIdempotent() {
        PayCompensateMessage msg = PayCompensateMessage.builder()
                .commandId("c-4")
                .action(PayOutboxEventType.CLOSE_CHANNEL)
                .outTradeNo("OUT4")
                .payAttemptId(100L)
                .statusFrom("PAYING")
                .build();
        when(redis.get("mq:consume:pay-compensate:c-4")).thenReturn(null);
        when(mockWechatHttpClient.close("OUT4")).thenThrow(new IllegalStateException("http 500"));

        assertThrows(IllegalStateException.class,
                () -> compensateConsumer.onMessage(objectMapper.writeValueAsString(msg)));
        verify(payAttemptPort, never()).updateStatus(any(), any(), any(), any());
        verify(redis, never()).trySetNx(anyString(), anyString(), anyLong());
    }

    // ---------- F5 死信归档 ----------

    @Test
    void compensateDlq_shouldInsertMqFailWithActionTag() {
        PayCompensateMessage msg = PayCompensateMessage.builder()
                .commandId("cmd-dlq")
                .action(PayOutboxEventType.REFUND)
                .orderId(7L)
                .outTradeNo("OUT7")
                .build();

        compensateDlqArchiver.onMessage(objectMapper.writeValueAsString(msg));

        ArgumentCaptor<MqFail> captor = ArgumentCaptor.forClass(MqFail.class);
        verify(mqFailMapper).insert(captor.capture());
        MqFail row = captor.getValue();
        assertEquals("take-pay-compensate-consumer", row.getConsumerGroup());
        assertEquals("takeout-pay-compensate", row.getTopic());
        assertEquals("REFUND", row.getTag());
        assertEquals("cmd-dlq", row.getEventId());
        assertEquals("7", row.getBizKey());
    }

    @Test
    void compensateDlq_insertFails_shouldRethrow() {
        when(mqFailMapper.insert(any(MqFail.class))).thenThrow(new RuntimeException("db down"));
        assertThrows(RuntimeException.class, () -> compensateDlqArchiver.onMessage("{\"commandId\":\"x\"}"));
    }
}
