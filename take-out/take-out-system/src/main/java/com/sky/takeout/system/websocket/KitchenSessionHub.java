package com.sky.takeout.system.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 管理端厨房 WebSocket 会话表（单机 ConcurrentHashMap）。
 * 多实例部署时需改为 Redis Pub/Sub，本期不做。
 */
@Component
public class KitchenSessionHub {

    private static final Logger log = LoggerFactory.getLogger(KitchenSessionHub.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String clientId, WebSocketSession session) {
        sessions.put(clientId, session);
        log.info("厨房 WS 接入 clientId={}，当前在线={}", clientId, sessions.size());
    }

    public void remove(String clientId) {
        sessions.remove(clientId);
        log.info("厨房 WS 断开 clientId={}，当前在线={}", clientId, sessions.size());
    }

    /**
     * 向所有在线管理端广播文本消息。
     *
     * @return 成功发出的会话数（0 表示无人在线，按产品约定仍算「推送完成」）
     */
    public int broadcast(String json) {
        TextMessage message = new TextMessage(json);
        int sent = 0;
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                sessions.remove(entry.getKey(), session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
                sent++;
            } catch (IOException e) {
                log.warn("厨房 WS 发送失败 clientId={}", entry.getKey(), e);
                sessions.remove(entry.getKey(), session);
                try {
                    session.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
        return sent;
    }

    public int onlineCount() {
        return sessions.size();
    }
}
