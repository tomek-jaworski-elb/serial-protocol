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

    public void addSession(WebSocketSession session) {
      WebSocketSession webSocketSession = webSocketSessions.put(session.getId(), session);
      if (webSocketSession == null) {
        String agent = Optional.ofNullable(session.getHandshakeHeaders().get("user-agent"))
                .filter(strings -> !strings.isEmpty())
                .map(string -> string.get(0))
                .orElse("");
        LOG.info("Session added. Session count: {}.Remote IP: {}. User-Agent: {}",
                webSocketSessions.size(),
                session.getRemoteAddress(),
                agent);
        countService.setCounter(webSocketSessions.size());
      } else {
        LOG.warn("Session already exists. Session count: {}", webSocketSessions.size());
      }
    }

    public void removeSession(WebSocketSession session) {
      WebSocketSession removed = webSocketSessions.remove(session.getId());
      if (removed != null) {
        LOG.info("Session removed. Session count: {}", webSocketSessions.size());
        countService.setCounter(webSocketSessions.size());
      } else {
        LOG.warn("Session not found. Session count: {}", webSocketSessions.size());
      }
    }
}
