package com.jaworski.serialprotocol.service.impl;

import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WSSessionManager;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WebSocketPublisherImpl implements WebSocketPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketPublisherImpl.class);
    private final WSSessionManager wsSessionManager;

    @Override
    public void publishForAllClients(String message) {
        wsSessionManager.getWebSocketSessions().values().stream()
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

    @Override
    public void publishForAllClients(String message, SessionType sessionType) {
      wsSessionManager.getWebSocketSessions().values().stream()
              .filter(Objects::nonNull)
              .filter(WebSocketSession::isOpen)
              .filter(webSocketSession -> webSocketSession.getUri() != null)
              .filter(webSocketSession -> webSocketSession.getUri().toString().contains(sessionType.getName()))
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
    public long sessionsCount() {
        return wsSessionManager.getWebSocketSessions().values().stream()
                .filter(Objects::nonNull)
                .filter(WebSocketSession::isOpen)
                .count();
    }

  @Override
  public long sessionsCount(SessionType sessionType) {
    return wsSessionManager.getWebSocketSessions().values().stream()
            .filter(Objects::nonNull)
            .filter(WebSocketSession::isOpen)
            .filter(webSocketSession -> webSocketSession.getUri() != null)
            .filter(webSocketSession -> webSocketSession.getUri().toString().contains(sessionType.getName()))
            .count();
  }
}
