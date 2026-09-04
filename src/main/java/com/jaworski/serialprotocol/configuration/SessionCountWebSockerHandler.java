package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WSSessionManager;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Component
public class SessionCountWebSockerHandler extends TextWebSocketHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SessionCountWebSockerHandler.class);
  /**
   * When each page was last heard FROM.
   *
   * <p>Not the same as when its connection was last used. The container closes a connection idle
   * in either direction, but this application writes to every open page whenever any other page
   * opens or closes — and that write resets the idle clock for pages that are already gone. A
   * window killed by a crash or a forced quit never sends a close frame, so without this it would
   * be counted for as long as anybody else kept using the site: the number would only ever climb,
   * which is exactly what was reported.</p>
   *
   * <p>What a page cannot fake is talking back. Pages send a keep-alive of their own every thirty
   * seconds; one that has stopped is one that is gone.</p>
   */
  private final Map<String, Instant> lastHeardFrom = new ConcurrentHashMap<>();

  private final WSSessionManager wsSessionManager;
  private final WebSocketPublisher webSocketPublisher;

  @Value("${ws.session.silence-limit}")
  private Duration silenceLimit;

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    lastHeardFrom.put(session.getId(), Instant.now());
    // Keep-alives from the page, a few times a minute per open tab. At INFO they would bury the
    // log; their only job is to be traffic, so that the container does not close the connection
    // as idle and so that a page which has died stops holding one.
    LOG.debug("Connection {} read {} bytes", session.getId(), message.getPayloadLength());
  }

  /**
   * Closes connections belonging to pages that have stopped talking.
   *
   * <p>Closing them, rather than merely leaving them out of the count, is what keeps the registry
   * from filling up with the dead over a long uptime. The handler's own close callback then
   * removes them the ordinary way.</p>
   */
  @Scheduled(fixedDelayString = "${ws.session.silence-check}")
  public void closeSilentPages() {
    Instant cutoff = Instant.now().minus(silenceLimit);
    wsSessionManager.getWebSocketSessions().values().stream()
            .filter(WebSocketSession::isOpen)
            .filter(session -> session.getUri() != null)
            .filter(session -> session.getUri().toString().contains(SessionType.SESSION_COUNT.getName()))
            .filter(session -> lastHeardFrom.getOrDefault(session.getId(), Instant.EPOCH).isBefore(cutoff))
            .forEach(this::closeQuietly);
  }

  private void closeQuietly(WebSocketSession session) {
    try {
      LOG.info("Closing {}: no keep-alive for over {}", session.getId(), silenceLimit);
      session.close();
    } catch (Exception e) {
      LOG.warn("Could not close silent connection {}", session.getId(), e);
    }
  }

  /**
   * Sends the number of open pages to every page showing it.
   *
   * <p>Reads the count from the registry rather than from what add/remove returned. Those return
   * the size of the whole map — every channel, not just this one — which is how the footer came to
   * show two tabs as three.</p>
   */
  private void broadcastOpenPageCount() {
    webSocketPublisher.publishForAllClients(
            String.valueOf(webSocketPublisher.openPageCount()), SessionType.SESSION_COUNT);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    LOG.info("Connection closed: {} with status {}", session.getId(), status);
    lastHeardFrom.remove(session.getId());
    wsSessionManager.removeSession(session);
    broadcastOpenPageCount();
    super.afterConnectionClosed(session, status);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    LOG.info("Connection established: {}", session.getId());
    lastHeardFrom.put(session.getId(), Instant.now());
    wsSessionManager.addSession(session);
    broadcastOpenPageCount();
    super.afterConnectionEstablished(session);
  }
}
