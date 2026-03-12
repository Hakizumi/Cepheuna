package org.sempiria.cepheuna.config;

import org.jspecify.annotations.NonNull;
import org.sempiria.cepheuna.controller.ServerWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for browser clients.
 *
 * <p>The handler is exposed on {@code /ws/voice-agent}. The browser demo page added
 * in this patch connects to the same endpoint.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
@Configuration
public class WebConfig implements WebSocketConfigurer, WebMvcConfigurer {
    private final ServerWebSocketHandler frontendConnectWebSocketHandler;

    public WebConfig(ServerWebSocketHandler frontendConnectWebSocketHandler) {
        this.frontendConnectWebSocketHandler = frontendConnectWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(frontendConnectWebSocketHandler, "/ws/cepheuna")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        registry.addViewController("/{path:[^\\.]*}")
                .setViewName("forward:/{path}.html");
    }
}
