package com.jaworski.serialprotocol.udp.server;

import com.jaworski.serialprotocol.service.ingest.FrameIngress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The UDP adapter: a datagram is a frame, and the only thing the handler may do is hand it on.
 */
@ExtendWith(MockitoExtension.class)
class UdpPacketHandlerImplTest {

    private static final byte[] FRAME = {119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10};
    private static final InetSocketAddress SENDER = new InetSocketAddress("127.0.0.1", 40000);

    @Mock
    private FrameIngress frameIngress;

    @Test
    void supportsOnlyPayloadsOfAtLeastThreeBytes() {
        UdpPacketHandlerImpl handler = new UdpPacketHandlerImpl(frameIngress);

        assertFalse(handler.supports(packet(new byte[]{1, 2})));
        assertTrue(handler.supports(packet(new byte[]{1, 2, 3})));
    }

    @Test
    void aPacketIsHandedToTheIngressWithTheSenderAsOrigin() {
        UdpPacketHandlerImpl handler = new UdpPacketHandlerImpl(frameIngress);

        handler.handle(packet(FRAME));

        verify(frameIngress).ingest(FRAME, SENDER.toString());
        verifyNoMoreInteractions(frameIngress);
    }

    private static UdpPacket packet(byte[] payload) {
        return new UdpPacket(payload, SENDER, Instant.now());
    }
}
