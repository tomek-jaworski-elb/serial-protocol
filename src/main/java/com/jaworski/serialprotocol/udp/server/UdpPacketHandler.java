package com.jaworski.serialprotocol.udp.server;

public interface UdpPacketHandler {

    boolean supports(UdpPacket packet);

    void handle(UdpPacket packet);
}
