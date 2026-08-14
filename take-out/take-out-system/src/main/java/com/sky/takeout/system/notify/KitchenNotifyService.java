package com.sky.takeout.system.notify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.sky.takeout.pojo.dto.mq.OrderPaidMessage;
import com.sky.takeout.pojo.dto.mq.OrderStatusMessage;
import com.sky.takeout.pojo.enums.OrderStatus;
import com.sky.takeout.system.websocket.KitchenSessionHub;

import tools.jackson.databind.ObjectMapper;

/**
 * 厨房 / 订单状态通知：组装与 Navbar 约定的 JSON，经 WebSocket 广播。
 * <p>
 * 无人在线时仍视为成功（打 warn），避免收银员未开后台时 MQ 无限重试。
 */
@Service
public class KitchenNotifyService {

    private static final Logger log = LoggerFactory.getLogger(KitchenNotifyService.class);

    /** 与前端约定：1=待接单/来单 */
    public static final int TYPE_NEW_ORDER = 1;
    /** 与前端约定：2=催单（预留，本期未用） */
    public static final int TYPE_REMINDER = 2;
    /** 与前端约定：3=订单状态变更 */
    public static final int TYPE_STATUS_CHANGED = 3;

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

        broadcast(TYPE_NEW_ORDER, msg.getOrderId(), orderNumber, content, msg.getEventId(), "厨房来单");
    }

    /**
     * 订单状态变更提醒（接单 / 派送 / 完成 / 取消等）。
     */
    public void notifyOrderStatusChanged(OrderStatusMessage msg) {
        if (msg == null || msg.getOrderId() == null) {
            throw new IllegalArgumentException("OrderStatusMessage/orderId 不能为空");
        }

        String orderNumber = StringUtils.hasText(msg.getOrderNumber())
                ? msg.getOrderNumber()
                : String.valueOf(msg.getOrderId());
        String fromText = statusText(msg.getFromStatus());
        String toText = statusText(msg.getToStatus());
        String content = "订单（" + orderNumber + "）状态：" + fromText + " → " + toText;

        broadcast(TYPE_STATUS_CHANGED, msg.getOrderId(), orderNumber, content, msg.getEventId(), "订单状态");
    }

    private void broadcast(int type, Long orderId, String orderNumber, String content, String eventId,
            String logTag) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            payload.put("orderId", orderId);
            payload.put("orderNumber", orderNumber);
            payload.put("content", content);
            if (StringUtils.hasText(eventId)) {
                payload.put("eventId", eventId);
            }
            String json = objectMapper.writeValueAsString(payload);
            int sent = sessionHub.broadcast(json);
            if (sent == 0) {
                log.warn("{}已广播但当前无在线管理端 orderId={} eventId={}", logTag, orderId, eventId);
            } else {
                log.info("{}已推送 onlineSent={} orderId={} eventId={}", logTag, sent, orderId, eventId);
            }
        } catch (Exception e) {
            throw new IllegalStateException(logTag + " WebSocket 推送失败 orderId=" + orderId, e);
        }
    }

    private static String statusText(OrderStatus status) {
        return status == null ? "?" : status.getMessage();
    }
}
