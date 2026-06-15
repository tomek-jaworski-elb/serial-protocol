package com.jaworski.serialprotocol.udp.server;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.utils.JsonMapperService;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Component
@ConditionalOnBooleanProperty(prefix = "udp.server", name = "enabled", havingValue = true, matchIfMissing = false)
public class UdpPacketHandlerImpl implements UdpPacketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UdpPacketHandlerImpl.class);
    private final WebSocketPublisher webSocketPublisher;
    private final JsonMapperService jsonMapperService;
    private final MessageTranslator messageTranslator;

    @Override
    public boolean supports(UdpPacket packet) {
        byte[] p = packet.payload();
        return p.length > 2;
    }

    @Override
    public void handle(UdpPacket packet) {
        try {
            byte[] payload = packet.payload();
            String raw = new String(payload, StandardCharsets.UTF_8);
            ModelTrackDTO dto = messageTranslator.getDTO(payload);
            String jsonString = jsonMapperService.toJsonString(dto);
            webSocketPublisher.publishForAllClients(jsonString, SessionType.JSON);
            LOG.info("UDP packet from {}: {}", packet.sender(), raw);
        } catch (Exception e) {
            LOG.warn("Unprocessable UDP packet from {}: {}", packet.sender(), e.getMessage());
        }
    }
}
