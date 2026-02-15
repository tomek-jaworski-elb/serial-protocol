package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.service.impl.WSSessionCountService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@RequiredArgsConstructor
public class WSSessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(WSSessionManager.class);
    @Getter
    private final ConcurrentHashMap<String ,WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();
    private final WSSessionCountService countService;

    /**
     * Dodaje sesję i zwraca aktualną liczbę sesji.
     * @param session sesja WebSocket
     * @return aktualna liczba sesji
     */
    public int addSession(WebSocketSession session) {
      WebSocketSession webSocketSession = webSocketSessions.put(session.getId(), session);
      int size = webSocketSessions.size();
      if (webSocketSession == null) {
        String agent = Optional.ofNullable(session.getHandshakeHeaders().get("user-agent"))
                .filter(strings -> !strings.isEmpty())
                .map(List::getFirst)
                .orElse("");
        LOG.info("Session added. Session count: {}. Remote IP: {}. User-Agent: {}",
                size,
                session.getRemoteAddress(),
                agent);
        countService.setCounter(size);
      } else {
        LOG.warn("Session already exists. Session count: {}", size);
      }
      return size;
    }

    /**
     * Usuwa sesję i zwraca aktualną liczbę sesji.
     * @param session sesja WebSocket
     * @return aktualna liczba sesji
     */
    public int removeSession(WebSocketSession session) {
      WebSocketSession removed = webSocketSessions.remove(session.getId());
      int size = webSocketSessions.size();
      if (removed != null) {
        LOG.info("Session removed. Session count: {}", size);
        countService.setCounter(size);
      } else {
        LOG.warn("Session not found. Session count: {}", size);
      }
      return size;
    }
}
