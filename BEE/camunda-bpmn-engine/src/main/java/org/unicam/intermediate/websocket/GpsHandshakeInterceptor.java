// src/main/java/org/unicam/intermediate/websocket/WebSocketHandshakeInterceptor.java

package org.unicam.intermediate.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
public class GpsHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

            // Estrai userId dalla query string
            String userId = servletRequest.getParameter("userId");
            String businessKey = servletRequest.getParameter("businessKey");

            log.info("[WS Handshake] Incoming connection - userId: {}, businessKey: {}", userId, businessKey);

            if (userId != null && !userId.isBlank()) {
                attributes.put("userId", userId);

                if (businessKey != null && !businessKey.isBlank()) {
                    attributes.put("businessKey", businessKey);
                }

                log.info("[WS Handshake] Attributes set - userId: {}, businessKey: {}", userId, businessKey);
                return true;
            } else {
                log.error("[WS Handshake] Missing userId in request parameters");
                return false;
            }
        }

        log.error("[WS Handshake] Invalid request type");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}