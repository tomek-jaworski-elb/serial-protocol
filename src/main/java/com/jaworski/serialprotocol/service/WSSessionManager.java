package com.jaworski.serialprotocol.service;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Getter
public class WSSessionManager {

    private static final Logger LOG = LogManager.getLogger(WSSessionManager.class);
    private final ConcurrentHashMap<String ,WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        WebSocketSession webSocketSession = webSocketSessions.put(session.getId(), session);
        if (webSocketSession != null) {
        LOG.info("Session added. Session count: {}", webSocketSessions.size());
        } else {
            LOG.warn("Session already exists. Session count: {}", webSocketSessions.size());
        }
    }

    public void removeSession(WebSocketSession session) {
        WebSocketSession removed = webSocketSessions.remove(session.getId());
        if (removed != null) {
            LOG.info("Session removed. Session count: {}", webSocketSessions.size());
        } else {
            LOG.warn("Session not found. Session count: {}", webSocketSessions.size());
        }
    }
}
