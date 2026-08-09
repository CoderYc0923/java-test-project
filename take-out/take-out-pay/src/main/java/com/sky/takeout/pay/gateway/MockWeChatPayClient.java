package com.sky.takeout.pay.gateway;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pay.port.OrderPayPort;
import com.sky.takeout.pay.sign.HmacPaySignUtil;
import com.sky.takeout.pojo.dto.order.MockPayNotifyDTO;
import com.sky.takeout.pojo.entity.Order;

/**
 * 模拟微信支付客户端: 延迟后带着签名打我们的notify
 * 真项目这一步是微信机房的http请求
 * MockWeChatPayClient
 */
@Component
public class MockWeChatPayClient {
    private final MockPaymentGateway mockPaymentGateway;

    private static final Logger log = LoggerFactory.getLogger(MockWeChatPayClient.class);

    private final PayProperties payProperties;
    private final OrderPayPort orderPayPort;

    public MockWeChatPayClient(PayProperties payProperties, OrderPayPort orderPayPort, MockPaymentGateway mockPaymentGateway) {
        this.orderPayPort = orderPayPort;
        this.payProperties = payProperties;
        this.mockPaymentGateway = mockPaymentGateway;
    }

    /**
     * 异步回调模拟微信支付回调
     * @param orderId
     */
    @Async
    public void sendPaidNotifyAsync(Long orderId) {

        Long delay = payProperties.getNotifyDelayMs() == null ? 1500L : payProperties.getNotifyDelayMs();

        /**模拟延迟回调 */
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        Order order = orderPayPort.findOrderById(orderId);
        if (order == null) {
            log.warn("模拟微信回调：订单不存在id={}", orderId);
            return;
        }

        String secret = payProperties.getMockSecret();
        if (secret == null || secret.isBlank()) {
            log.error("pay.mock-secret 未配置，无法签名");
            return;
        }

        /**
         * 获取时间戳和nonce，并签名
         */
        Long timestamp =  System.currentTimeMillis() / 1000;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String sign = HmacPaySignUtil.sign(order.getNumber(), order.getAmount(), timestamp, nonce, secret);

        /**
         * 构建通知参数
         */
        MockPayNotifyDTO dto = new MockPayNotifyDTO();
        dto.setOrderNumber(order.getNumber());
        dto.setAmount(order.getAmount());
        dto.setTimestamp(timestamp);
        dto.setNonce(nonce);
        dto.setSign(sign);

        log.info("模拟微信发起回调 orderNumber={}, delayMs={}", order.getNumber(), delay);

        try {
            // 进程内调用 = 真项目的HTTP POST /notify
            /**
             * 调用进程内模拟回调
             */
            mockPaymentGateway.handlePayNotify(dto);
        } catch (Exception e) {
            // 真微信会重试
            log.warn("模拟回调处理失败 orderNumber={}: {}", order.getNumber(), e.getMessage());
        }
    }
}
