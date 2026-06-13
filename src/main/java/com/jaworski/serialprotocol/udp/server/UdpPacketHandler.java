package com.jaworski.serialprotocol.udp.server;

public interface UdpPacketHandler {

    boolean supports(DatagramPacket packet);

    void handle(DatagramPacket packet);
}
