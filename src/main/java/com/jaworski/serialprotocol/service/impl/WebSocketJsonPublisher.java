package com.jaworski.serialprotocol.service.impl;

import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WSSessionManager;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WebSocketJsonPublisher implements WebSocketPublisher {

    private static final Logger LOG = LogManager.getLogger(WebSocketJsonPublisher.class);
    private final WSSessionManager wsSessionManager;

    @Override
    public void publishForAllClients(String message) {
        wsSessionManager.getWebSocketSessions().stream()
                .filter(Objects::nonNull)
                .filter(WebSocketSession::isOpen)
                .filter(webSocketSession -> webSocketSession.getUri().toString().contains(SessionType.RS.getName()))
                .forEach(session -> {
                    try {
                        session.sendMessage(new TextMessage(message));
                        LOG.info("Message sent to client {}: {}", session.getId(), message);
                    } catch (IOException e) {
                        LOG.error("Error sending message to client {}", session.getId(), e);
                    }
                });
    }

    @Override
    public int sessionsCount() {
        return (int) wsSessionManager.getWebSocketSessions().stream()
                .filter(Objects::nonNull)
                .filter(WebSocketSession::isOpen)
                .filter(webSocketSession -> webSocketSession.getUri().toString().contains(SessionType.RS.getName()))
                .count();
    }
}
