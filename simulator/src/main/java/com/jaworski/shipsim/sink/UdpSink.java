package com.jaworski.shipsim.sink;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * One datagram per frame to the application's {@code UdpServer} (default port 2222), which hands
 * it to the same pipeline as a serial frame.
 */
public final class UdpSink implements Sink {

    private final DatagramChannel channel;
    private final InetSocketAddress target;

    public UdpSink(String host, int port) throws IOException {
        this.target = new InetSocketAddress(host, port);
        if (target.isUnresolved()) {
            throw new IOException("cannot resolve " + host);
        }
        this.channel = DatagramChannel.open();
        this.channel.connect(target);
    }

    @Override
    public void send(byte[] frame) throws IOException {
        channel.write(ByteBuffer.wrap(frame));
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public String toString() {
        return "udp://" + target.getHostString() + ":" + target.getPort();
    }
}
