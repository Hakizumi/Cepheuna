package org.sempiria.cepheuna.config;

import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.controller.ServerWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for browser clients.
 *
 * <p>The handler is exposed on {@code /ws/voice-agent}. The browser demo page added
 * in this patch connects to the same endpoint.
 *
 * @since 3.0.0
 * @version 1.0.0
 * @author Sempiria
 */
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    private final ServerWebSocketHandler frontendConnectWebSocketHandler;

    public WebSocketConfig(ServerWebSocketHandler frontendConnectWebSocketHandler) {
        this.frontendConnectWebSocketHandler = frontendConnectWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(frontendConnectWebSocketHandler, "/ws/voice-agent")
                .setAllowedOriginPatterns("*");
    }
}
