package org.unicam.intermediate.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.unicam.intermediate.websocket.GpsWebSocketHandler;
import org.unicam.intermediate.websocket.GpsHandshakeInterceptor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final GpsWebSocketHandler gpsWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        // Per test ora ci va bene cos=
        registry.addHandler(gpsWebSocketHandler, "/ws/gps")
                .addInterceptors(new GpsHandshakeInterceptor())
                .setAllowedOrigins("*")
                .setAllowedOriginPatterns("*");
    }
}