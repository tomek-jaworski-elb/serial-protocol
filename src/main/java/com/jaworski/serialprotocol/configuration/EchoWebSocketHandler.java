package com.jaworski.serialprotocol.configuration;

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
public class EchoWebSocketHandler extends TextWebSocketHandler {

    private final WSSessionManager wsSessionManager;
    private final WebSocketPublisher webSocketPublisher;

    private static final Logger LOG = LogManager.getLogger(EchoWebSocketHandler.class);

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        LOG.info("Connection {} read {} bytes", session.getId(), message.getPayloadLength());
        session.sendMessage(new TextMessage(payload.toUpperCase()));
        webSocketPublisher.publish(payload);
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
        wsSessionManager.addSession(session);
        super.afterConnectionEstablished(session);
    }
}
