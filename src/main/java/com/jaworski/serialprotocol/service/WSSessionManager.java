package com.jaworski.serialprotocol.service;

import lombok.Getter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Getter
public class WSSessionManager {


    private final List<WebSocketSession> webSocketSessions = new ArrayList<>();
    private int sessionType;

    public void addSession(WebSocketSession session, int sessionType) {
        webSocketSessions.add(session);
        this.sessionType = sessionType;
    }

    public void removeSession(WebSocketSession session) {
        webSocketSessions.removeIf(wsSession -> wsSession.equals(session));
    }

    public int getSessionType() {
        return sessionType;
    }
}
