package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.service.WSSessionManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class JsonWebSocketHandler extends TextWebSocketHandler {

    private final WSSessionManager wsSessionManager;

    private static final Logger LOG = LoggerFactory.getLogger(JsonWebSocketHandler.class);

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        LOG.warn("Rejected inbound message from client {} ({} bytes) — endpoint is read-only",
                session.getId(), message.getPayloadLength());
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        LOG.info("Connection closed: {} with status {}", session.getId(), status);
        wsSessionManager.removeSession(session);
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        LOG.info("Connection established: {}", session.getId());
        session.sendMessage(new TextMessage("Connected JSON"));
        wsSessionManager.addSession(session);
        super.afterConnectionEstablished(session);
    }
}
