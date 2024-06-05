package com.jaworski.serialprotocol.service;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Getter
public class WSSessionManager {

    private static final Logger LOG = LogManager.getLogger(WSSessionManager.class);
    private final List<WebSocketSession> webSocketSessions = Collections.synchronizedList(new ArrayList<>());

    public void addSession(WebSocketSession session) {
        webSocketSessions.add(session);
        LOG.info("Session added. Session count: {}", webSocketSessions.size());
    }

    public void removeSession(WebSocketSession session) {
        webSocketSessions.removeIf(wsSession -> wsSession.equals(session));
        LOG.info("Session removed. Session count: {}", webSocketSessions.size());
    }
}
