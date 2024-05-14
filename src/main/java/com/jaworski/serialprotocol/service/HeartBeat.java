package com.jaworski.serialprotocol.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HeartBeat {

    private static final Logger LOG = LogManager.getLogger(HeartBeat.class);
    private final WebSocketPublisher webSocketPublisher;

    @Scheduled(fixedDelay = 1000) // 1000 milliseconds = 1 second
    public void beat() {
        if (webSocketPublisher.sessionsCount() > 0) {
            LOG.info("Heartbeat {}", System.currentTimeMillis());
            webSocketPublisher.publish(System.currentTimeMillis() + "");
        }
    }
}
