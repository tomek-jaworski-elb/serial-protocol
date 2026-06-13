package com.jaworski.serialprotocol.udp.server;

import java.net.SocketAddress;
import java.time.Instant;

public record DatagramPacket(
        byte[] payload,
        SocketAddress sender,
        Instant receivedAt
) {}
