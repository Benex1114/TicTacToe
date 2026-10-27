package com.example.tictactoe.online;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.json.JsonMapper;

/**
 * The WebSocket endpoint (/ws/game). Kept deliberately thin: it turns JSON text into maps,
 * hands them to the OnlineGameCoordinator, and sends each reply to the right connection.
 * Each connection's session id is used as the player's id.
 */
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameSocketHandler.class);

    // Allows a send to wait up to 5 s / buffer up to 64 KB if a phone's connection is slow
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final OnlineGameCoordinator coordinator;
    private final JsonMapper json;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public GameSocketHandler(OnlineGameCoordinator coordinator, JsonMapper json) {
        this.coordinator = coordinator;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Two players can trigger messages to the same session at once; the decorator sends them one at a time
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Map<String, Object> incoming;
        try {
            incoming = json.readValue(message.getPayload(), Map.class);
        } catch (RuntimeException e) {
            incoming = Map.of("type", "INVALID");
        }
        send(coordinator.handle(session.getId(), incoming));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        send(coordinator.disconnected(session.getId()));
    }

    private void send(List<OnlineGameCoordinator.Outgoing> outgoing) {
        for (OnlineGameCoordinator.Outgoing out : outgoing) {
            WebSocketSession target = sessions.get(out.playerId());
            if (target == null || !target.isOpen()) {
                continue; // that player has already gone
            }
            try {
                target.sendMessage(new TextMessage(json.writeValueAsString(out.message())));
            } catch (IOException | RuntimeException e) {
                log.warn("Could not send to {}: {}", out.playerId(), e.getMessage());
            }
        }
    }
}
