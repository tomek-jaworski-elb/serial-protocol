package com.jaworski.serialprotocol.udp.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UdpDispatcher {

    private final List<UdpPacketHandler> handlers;
    private static final Logger LOG = LoggerFactory.getLogger(UdpDispatcher.class);

    public void dispatch(DatagramPacket packet) {
        for (UdpPacketHandler handler : handlers) {
            if (handler.supports(packet)) {
                try {
                    handler.handle(packet);
                } catch (Exception e) {
                    LOG.error("Handler {} failed for {}: {}",
                            handler.getClass().getSimpleName(),
                            packet.sender(), e.getMessage());
                }
                return;
            }
        }
        LOG.warn("No handler for packet from {}", packet.sender());
    }
}
