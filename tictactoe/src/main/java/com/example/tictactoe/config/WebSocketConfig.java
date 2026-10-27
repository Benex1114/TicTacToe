package com.example.tictactoe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.tictactoe.online.GameSocketHandler;

/**
 * Registers the online-game WebSocket at /ws/game.
 * Only the frontend's own origins may connect (same list as CORS for the REST API).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameSocketHandler handler;
    private final String[] allowedOrigins;

    public WebSocketConfig(GameSocketHandler handler, @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/game").setAllowedOrigins(allowedOrigins);
    }
}
