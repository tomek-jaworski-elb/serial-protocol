package com.jaworski.serialprotocol.udp.server;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.utils.JsonMapperService;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UdpPacketHandlerImpl implements UdpPacketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UdpPacketHandlerImpl.class);
    private final WebSocketPublisher webSocketPublisher;
    private final JsonMapperService jsonMapperService;
    private final MessageTranslator messageTranslator;

    @Override
    public boolean supports(DatagramPacket packet) {
        byte[] p = packet.payload();
        return p.length > 2;
    }

    @Override
    public void handle(DatagramPacket packet) {
        try {
            byte[] payload = packet.payload();
            String str = new String(payload);
            ModelTrackDTO dto = messageTranslator.getDTO(payload);
            String jsonString = jsonMapperService.toJsonString(dto);
            webSocketPublisher.publishForAllClients(jsonString, SessionType.JSON);
            LOG.info("gNMI from {}: {} updates", packet.sender(), str);
        } catch (Exception e) {
            LOG.warn("Bad protobuf from {}", packet.sender());
        }
    }
}
