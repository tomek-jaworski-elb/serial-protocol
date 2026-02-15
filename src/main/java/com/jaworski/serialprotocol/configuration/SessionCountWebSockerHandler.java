package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WSSessionManager;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@RequiredArgsConstructor
@Component
public class SessionCountWebSockerHandler extends TextWebSocketHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SessionCountWebSockerHandler.class);
  private final WSSessionManager wsSessionManager;
  private final WebSocketPublisher webSocketPublisher;

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    LOG.info("Connection {} read {} bytes", session.getId(), message.getPayloadLength());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    LOG.info("Connection closed: {} with status {}", session.getId(), status);
    int sessionCount = wsSessionManager.removeSession(session);
    webSocketPublisher.publishForAllClients(String.valueOf(sessionCount), SessionType.SESSION_COUNT);
    super.afterConnectionClosed(session, status);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    LOG.info("Connection established: {}", session.getId());
    int sessionCount = wsSessionManager.addSession(session);
    webSocketPublisher.publishForAllClients(String.valueOf(sessionCount), SessionType.SESSION_COUNT);
    super.afterConnectionEstablished(session);
  }
}
