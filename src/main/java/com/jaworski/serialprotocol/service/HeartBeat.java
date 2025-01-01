package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.serial.SessionType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class HeartBeat {

    private static final Logger LOG = LoggerFactory.getLogger(HeartBeat.class);
    private final WebSocketPublisher webSocketPublisher;

    @Scheduled(fixedDelayString = "${ws.heartbeat.interval}") // 1000 milliseconds = 1 second
    public void beat() {
        if (webSocketPublisher.sessionsCount(SessionType.HEARTBEAT) > 0) {
            LOG.info("Timestamp {}", System.currentTimeMillis());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM, yyyy HH:mm:ss");
            webSocketPublisher.publishForAllClients(LocalDateTime.now().format(formatter), SessionType.HEARTBEAT);
        }
    }
}
