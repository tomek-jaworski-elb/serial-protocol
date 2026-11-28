package com.jaworski.serialprotocol.service.impl;

import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WSSessionManager;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Service
public class WebSocketPublisherImpl implements WebSocketPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketPublisherImpl.class);
    private final WSSessionManager wsSessionManager;
    private final ThreadPoolExecutor executorService;

    public WebSocketPublisherImpl(WSSessionManager wsSessionManager, @Qualifier("customThreadPoolExecutor") ThreadPoolExecutor executorService) {
      this.wsSessionManager = wsSessionManager;
      this.executorService = executorService;
    }

  @Override
    public void publishForAllClients(String message) {
        wsSessionManager.getWebSocketSessions().values().stream()
                .filter(Objects::nonNull)
                .filter(WebSocketSession::isOpen)
                .forEach(session -> executorService.submit(() -> sendMessage(session, () -> message)));
    }

    @Override
    public void publishForAllClients(String message, SessionType sessionType) {
        publishForAllClients(() -> message, sessionType);
    }

    @Override
    public void publishForAllClients(Supplier<String> message, SessionType sessionType) {
        openSessions(sessionType).forEach(session -> executorService.submit(() -> sendMessage(session, message)));
    }

    private Stream<WebSocketSession> openSessions(SessionType sessionType) {
        return wsSessionManager.getWebSocketSessions().values().stream()
                .filter(Objects::nonNull)
                .filter(WebSocketSession::isOpen)
                .filter(s -> s.getUri() != null)
                .filter(s -> s.getUri().toString().contains(sessionType.getName()));
    }

    private void sendMessage(WebSocketSession session, Supplier<String> message) {
        try {
            String text;
            synchronized (session) {
                // Evaluated under the session lock: tasks may run in any order, so the last write must read the latest state.
                text = message.get();
                session.sendMessage(new TextMessage(text));
            }
            LOG.debug("Message sent to client {}: {}", session.getId(), text);
        } catch (IOException e) {
            // Expected when a page goes away mid-write (closed tab, lost network); Tomcat then closes the session itself.
            LOG.warn("Error sending message to client {}: {}", session.getId(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public long liveMapViewerCount() {
        return openSessions(SessionType.SESSION_COUNT)
                .filter(s -> Boolean.TRUE.equals(s.getAttributes().get(WATCHING_LIVE_MAP)))
                .count();
    }

    @Override
    public long sessionsCount(SessionType sessionType) {
        return openSessions(sessionType).count();
    }
}
