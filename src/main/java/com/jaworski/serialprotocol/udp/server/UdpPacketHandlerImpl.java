package com.jaworski.serialprotocol.udp.server;

import com.jaworski.serialprotocol.service.ingest.FrameIngress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * UDP adapter: one datagram is one frame, handed to {@link FrameIngress} as it is. Nothing is
 * published from here, and nothing is decided about the frame either - the pipeline does that.
 */
@RequiredArgsConstructor
@Component
@ConditionalOnBooleanProperty(prefix = "udp.server", name = "enabled", havingValue = true, matchIfMissing = false)
public class UdpPacketHandlerImpl implements UdpPacketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UdpPacketHandlerImpl.class);
    private static final int MIN_PAYLOAD_LENGTH = 3;
    private final FrameIngress frameIngress;

    @Override
    public boolean supports(UdpPacket packet) {
        return packet.payload().length >= MIN_PAYLOAD_LENGTH;
    }

    @Override
    public void handle(UdpPacket packet) {
        byte[] payload = packet.payload();
        LOG.info("UDP packet from {}: {}", packet.sender(), payload);
        frameIngress.ingest(payload, String.valueOf(packet.sender()));
    }
}
