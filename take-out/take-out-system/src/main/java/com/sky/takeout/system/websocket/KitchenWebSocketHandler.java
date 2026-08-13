package com.sky.takeout.system.websocket;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 路径：/ws/{clientId}，与前端 VUE_APP_SOCKET_URL + clientId 对齐。
 */
@Component
public class KitchenWebSocketHandler extends TextWebSocketHandler {

    private final KitchenSessionHub sessionHub;

    public KitchenWebSocketHandler(KitchenSessionHub sessionHub) {
        this.sessionHub = sessionHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientId = resolveClientId(session);
        if (clientId == null) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
                // ignore
            }
            return;
        }
        session.getAttributes().put("clientId", clientId);
        sessionHub.register(clientId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object clientId = session.getAttributes().get("clientId");
        if (clientId != null) {
            sessionHub.remove(String.valueOf(clientId));
        }
    }

    private static String resolveClientId(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object fromInterceptor = attrs.get("clientId");
        if (fromInterceptor != null) {
            return String.valueOf(fromInterceptor);
        }
        // 兜底：从 URI 最后一段解析 /ws/{clientId}
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            return null;
        }
        return path.substring(idx + 1);
    }
}
