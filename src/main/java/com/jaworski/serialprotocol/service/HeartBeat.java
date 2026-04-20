package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.serial.SessionType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HeartBeat {

    private static final Logger LOG = LoggerFactory.getLogger(HeartBeat.class);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM, yyyy HH:mm:ss", Locale.ROOT);

    private final WebSocketPublisher webSocketPublisher;

    @Scheduled(fixedDelayString = "${ws.heartbeat.interval}")
    public void beat() {
        if (webSocketPublisher.sessionsCount(SessionType.HEARTBEAT) > 0) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            LOG.debug("Heartbeat: {}", timestamp);
            webSocketPublisher.publishForAllClients(timestamp, SessionType.HEARTBEAT);
        }
    }
}
