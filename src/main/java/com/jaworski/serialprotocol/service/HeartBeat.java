package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.service.impl.HeartBeatWebSocketRawPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class HeartBeat {

    private static final Logger LOG = LogManager.getLogger(HeartBeat.class);
    private final HeartBeatWebSocketRawPublisher heartBeatWebSocketRawPublisher;

    @Scheduled(fixedDelayString = "${ws.heartbeat.interval}") // 1000 milliseconds = 1 second
    public void beat() {
        if (heartBeatWebSocketRawPublisher.sessionsCount() > 0) {
            LOG.info("Heartbeat {}", System.currentTimeMillis());
            heartBeatWebSocketRawPublisher.publishForAllClients(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }
}
