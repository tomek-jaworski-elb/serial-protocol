package com.jaworski.shipsim.sink;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UdpSinkTest {

    private static final byte[] FRAME = {119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10};

    @Test
    void everyFrameArrivesAsOneDatagramOfExactlyItsBytes() throws IOException {
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            receiver.setSoTimeout(2_000);

            try (UdpSink sink = new UdpSink("127.0.0.1", receiver.getLocalPort())) {
                sink.send(FRAME);
                sink.send(FRAME);
                assertEquals("udp://127.0.0.1:" + receiver.getLocalPort(), sink.toString());
            }

            for (int i = 0; i < 2; i++) {
                byte[] buffer = new byte[128];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiver.receive(packet);
                assertArrayEquals(FRAME, Arrays.copyOf(packet.getData(), packet.getLength()));
            }
        }
    }
}
