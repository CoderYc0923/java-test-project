package com.sky.takeout.system.websocket;

import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSocket
public class KitchenWebSocketConfig implements WebSocketConfigurer {

    private final KitchenWebSocketHandler kitchenWebSocketHandler;

    public KitchenWebSocketConfig(KitchenWebSocketHandler kitchenWebSocketHandler) {
        this.kitchenWebSocketHandler = kitchenWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(kitchenWebSocketHandler, "/ws/{clientId}")
                .addInterceptors(clientIdInterceptor())
                .setAllowedOrigins("*");
    }

    private static HandshakeInterceptor clientIdInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
                if (request instanceof ServletServerHttpRequest servletRequest) {
                    HttpServletRequest http = servletRequest.getServletRequest();
                    // Spring 会把路径变量放到 request attribute；再兜底解析 URI
                    Object pathClientId = http.getAttribute(
                            org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                    if (pathClientId instanceof Map<?, ?> map) {
                        Object clientId = map.get("clientId");
                        if (clientId != null) {
                            attributes.put("clientId", String.valueOf(clientId));
                            return true;
                        }
                    }
                }
                String path = request.getURI().getPath();
                int idx = path == null ? -1 : path.lastIndexOf('/');
                if (idx >= 0 && idx < path.length() - 1) {
                    attributes.put("clientId", path.substring(idx + 1));
                    return true;
                }
                return false;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler wsHandler, Exception exception) {
                // no-op
            }
        };
    }
}
