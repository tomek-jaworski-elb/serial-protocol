package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WSSessionManager;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class JsonWebSocketHandler extends TextWebSocketHandler {

    private final WSSessionManager wsSessionManager;
    private final WebSocketPublisher webSocketPublisher;

    private static final Logger LOG = LogManager.getLogger(JsonWebSocketHandler.class);

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        LOG.info("Connection {} read {} bytes", session.getId(), message.getPayloadLength());
        // For testing purpose
        LOG.info("Message: {}", message.getPayload());
        webSocketPublisher.publishForAllClients(message.getPayload(), SessionType.JSON);
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
