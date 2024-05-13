package com.jaworski.serialprotocol.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WebSocketPublisher {

    private static final Logger LOG = LogManager.getLogger(WebSocketPublisher.class);
    private final WSSessionManager wsSessionManager;

    public void publish(String message) {
        wsSessionManager.getWebSocketSessions().stream()
                .filter(Objects::nonNull)
                .filter(WebSocketSession::isOpen)
                .forEach(session -> {
            try {
                session.sendMessage(new TextMessage(message));
                LOG.info("Message sent to client {}: {}", session.getId(), message);
            } catch (IOException e) {
                LOG.error("Error sending message to client {}", session.getId(), e);
            }
        });
    }
}
