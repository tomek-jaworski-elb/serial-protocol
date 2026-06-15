package com.jaworski.serialprotocol.udp.server;

import java.net.SocketAddress;
import java.time.Instant;

public record UdpPacket(
        byte[] payload,
        SocketAddress sender,
        Instant receivedAt
) {}
