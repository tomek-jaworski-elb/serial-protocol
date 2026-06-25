package com.jaworski.serialprotocol.udp.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty(prefix = "udp.server", name = "enabled", havingValue = true, matchIfMissing = false)
public class UdpServer implements SmartLifecycle {

    private static final int MAX_CONCURRENT_DISPATCHES = 256;
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);

    private final UdpProperties props;
    private final UdpDispatcher dispatcher;

    private volatile boolean running = false;
    private volatile DatagramChannel channel;
    private volatile Thread serverThread;
    private ExecutorService dispatchExecutor;
    private final Semaphore dispatchPermits = new Semaphore(MAX_CONCURRENT_DISPATCHES);

    @Override
    public void start() {
        if (running) {
            LOG.warn("UDP server already running, ignoring start()");
            return;
        }
        try {
            dispatchExecutor = Executors.newVirtualThreadPerTaskExecutor();
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


                UdpPacket packet = new UdpPacket(raw, sender, Instant.now());
                if (dispatchPermits.tryAcquire()) {
                    dispatchExecutor.submit(() -> {
                        try {
                            dispatcher.dispatch(packet);
                        } finally {
                            dispatchPermits.release();
                        }
                    });
                } else {
                    LOG.warn("Packet from {} dropped – max concurrent dispatches ({}) reached", sender, MAX_CONCURRENT_DISPATCHES);
                }

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
            if (dispatchExecutor != null) {
                dispatchExecutor.shutdown();
                if (!dispatchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dispatchExecutor.shutdownNow();
                }
            }
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
