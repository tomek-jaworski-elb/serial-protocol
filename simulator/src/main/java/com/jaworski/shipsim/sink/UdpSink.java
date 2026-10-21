package com.jaworski.shipsim.sink;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * One datagram per frame to the application's {@code UdpServer} (default port 2222), which hands
 * it to the same pipeline as a serial frame.
 *
 * <p>The channel is deliberately <em>not</em> connected: a connected UDP socket turns the ICMP
 * "port unreachable" that comes back while nothing listens into an exception on the next write,
 * which would end a run just because the application was started a moment later or restarted
 * in the middle. Unconnected, frames sent to a closed port are simply lost until it opens.</p>
 */
public final class UdpSink implements Sink {

    private final DatagramChannel channel;
    private final InetSocketAddress target;
    private boolean warned;

    public UdpSink(String host, int port) throws IOException {
        this.target = new InetSocketAddress(host, port);
        if (target.isUnresolved()) {
            throw new IOException("cannot resolve " + host);
        }
        this.channel = DatagramChannel.open();
    }

    @Override
    public void send(byte[] frame) throws IOException {
        try {
            channel.send(ByteBuffer.wrap(frame), target);
        } catch (PortUnreachableException e) {
            if (!warned) {
                System.err.println("Warning: nothing listens on " + this + " - frames are dropped until it does");
                warned = true;
            }
        }
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
