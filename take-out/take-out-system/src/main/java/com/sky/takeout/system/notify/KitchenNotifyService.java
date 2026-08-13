package com.sky.takeout.system.notify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.sky.takeout.pojo.dto.mq.OrderPaidMessage;
import com.sky.takeout.system.websocket.KitchenSessionHub;

import tools.jackson.databind.ObjectMapper;

/**
 * 厨房来单通知：组装与黑马 Navbar 兼容的 JSON，经 WebSocket 广播。
 * <p>
 * 无人在线时仍视为成功（打 warn），避免收银员未开后台时 MQ 无限重试。
 */
@Service
public class KitchenNotifyService {

    private static final Logger log = LoggerFactory.getLogger(KitchenNotifyService.class);

    /** 与前端约定：1=待接单/来单 */
    public static final int TYPE_NEW_ORDER = 1;

    private final KitchenSessionHub sessionHub;
    private final ObjectMapper objectMapper;

    public KitchenNotifyService(KitchenSessionHub sessionHub, ObjectMapper objectMapper) {
        this.sessionHub = sessionHub;
        this.objectMapper = objectMapper;
    }

    public void notifyNewOrder(OrderPaidMessage msg) {
        if (msg == null || msg.getOrderId() == null) {
            throw new IllegalArgumentException("OrderPaidMessage/orderId 不能为空");
        }

        String orderNumber = StringUtils.hasText(msg.getOrderNumber())
                ? msg.getOrderNumber()
                : String.valueOf(msg.getOrderId());
        String content = "厨房：您有一笔新的订单（" + orderNumber + "），请接单";

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", TYPE_NEW_ORDER);
            payload.put("orderId", msg.getOrderId());
            payload.put("orderNumber", orderNumber);
            payload.put("content", content);
            if (StringUtils.hasText(msg.getEventId())) {
                payload.put("eventId", msg.getEventId());
            }
            String json = objectMapper.writeValueAsString(payload);
            int sent = sessionHub.broadcast(json);
            if (sent == 0) {
                log.warn("厨房来单已广播但当前无在线管理端 orderId={} eventId={}",
                        msg.getOrderId(), msg.getEventId());
            } else {
                log.info("厨房来单已推送 onlineSent={} orderId={} eventId={}",
                        sent, msg.getOrderId(), msg.getEventId());
            }
        } catch (Exception e) {
            throw new IllegalStateException("厨房 WebSocket 推送失败 orderId=" + msg.getOrderId(), e);
        }
    }
}
