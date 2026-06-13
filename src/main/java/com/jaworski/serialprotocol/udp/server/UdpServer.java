package com.jaworski.serialprotocol.udp.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UdpServer implements SmartLifecycle {

    private final UdpProperties props;
    private final UdpDispatcher dispatcher;
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);

    private volatile boolean running = false;
    private DatagramChannel channel;
    private Thread serverThread;

    @Override
    public void start() {
        try {
            channel = DatagramChannel.open();
            channel.bind(new InetSocketAddress(props.getPort()));
            channel.configureBlocking(true);
            running = true;

            serverThread = Thread.ofVirtual()
                    .name("udp-server-main")
                    .start(this::receiveLoop);

            LOG.info("UDP server started on :{}", props.getPort());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start UDP server", e);
        }
    }

    private void receiveLoop() {
        ByteBuffer buf = ByteBuffer.allocateDirect(props.getBufferSize());
        while (running) {
            try {
                buf.clear();
                SocketAddress sender = channel.receive(buf);
                if (sender == null) continue;

                buf.flip();
                byte[] raw = new byte[buf.remaining()];
                buf.get(raw);


                DatagramPacket packet = new DatagramPacket(raw, sender, Instant.now());
                Thread.ofVirtual().start(() -> dispatcher.dispatch(packet));

            } catch (AsynchronousCloseException e) {
                // normalny shutdown
            } catch (IOException e) {
                if (running) LOG.error("Receive error: {}", e.getMessage());
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (channel != null) channel.close();
            if (serverThread != null) serverThread.join(5_000);
        } catch (Exception e) {
            LOG.warn("Shutdown issue: {}", e.getMessage());
        }
        LOG.info("UDP server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
