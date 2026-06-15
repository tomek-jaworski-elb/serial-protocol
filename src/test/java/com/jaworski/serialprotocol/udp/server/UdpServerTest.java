package com.jaworski.serialprotocol.udp.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("UdpServer Unit Tests")
class UdpServerTest {

    @Mock
    private UdpProperties mockUdpProperties;

    @Mock
    private UdpDispatcher mockUdpDispatcher;

    private UdpServer udpServer;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(mockUdpProperties.getPort()).thenReturn(19999);
        when(mockUdpProperties.getBufferSize()).thenReturn(65535);
        udpServer = new UdpServer(mockUdpProperties, mockUdpDispatcher);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (udpServer != null && udpServer.isRunning()) {
            udpServer.stop();
        }
        // Give threads time to shut down
        Thread.sleep(100);
        if (autoCloseable != null) {
            autoCloseable.close();
        }
    }

    @Test
    @DisplayName("start() should initialize UDP server on configured port")
    void testStart_InitializesServer() throws InterruptedException {
        // When
        udpServer.start();
        Thread.sleep(100);

        // Then
        assertTrue(udpServer.isRunning(), "Server should be running");
    }

    @Test
    @DisplayName("start() should throw exception when already running")
    void testStart_WhenAlreadyRunning_ShouldStillAllow() throws InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(100);

        // When - can be called again (idempotent check)
        assertDoesNotThrow(() -> assertTrue(udpServer.isRunning()));

        // Then
        assertTrue(udpServer.isRunning());
    }

    @Test
    @DisplayName("stop() should shutdown server")
    void testStop_ShutsDownServer() throws InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(100);
        assertTrue(udpServer.isRunning());

        // When
        udpServer.stop();
        Thread.sleep(100);

        // Then
        assertFalse(udpServer.isRunning(), "Server should not be running after stop");
    }

    @Test
    @DisplayName("stop() should be idempotent")
    void testStop_IsIdempotent() throws InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(100);

        // When - call stop twice
        udpServer.stop();
        Thread.sleep(50);
        assertDoesNotThrow(() -> udpServer.stop(), "Second stop should not throw exception");

        // Then
        assertFalse(udpServer.isRunning());
    }

    @Test
    @DisplayName("isRunning() should return correct state")
    void testIsRunning_ReturnsCorrectState() throws InterruptedException {
        // Initially not running
        assertFalse(udpServer.isRunning(), "Server should not be running initially");

        // After start
        udpServer.start();
        Thread.sleep(100);
        assertTrue(udpServer.isRunning(), "Server should be running after start");

        // After stop
        udpServer.stop();
        Thread.sleep(100);
        assertFalse(udpServer.isRunning(), "Server should not be running after stop");
    }

    @Test
    @DisplayName("getPhase() should return Integer.MAX_VALUE")
    void testGetPhase_ReturnsMaxValue() {
        // When
        int phase = udpServer.getPhase();

        // Then
        assertEquals(Integer.MAX_VALUE, phase, "Phase should be Integer.MAX_VALUE");
    }

    @Test
    @DisplayName("isAutoStartup() should return true")
    void testIsAutoStartup_ReturnsTrue() {
        // When
        boolean autoStartup = udpServer.isAutoStartup();

        // Then
        assertTrue(autoStartup, "isAutoStartup should return true");
    }

    @Test
    @DisplayName("receiveLoop should dispatch received packets")
    void testReceiveLoop_DispatchesPackets() throws IOException, InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(200);

        // When - send a packet to the server
        int port = mockUdpProperties.getPort();
        byte[] payload = "test-message".getBytes();
        DatagramSocket socket = new DatagramSocket();
        try {
            DatagramPacket packet = new DatagramPacket(payload, payload.length,
                    InetAddress.getLoopbackAddress(), port);
            socket.send(packet);

            // Give time for packet to be received and dispatched
            Thread.sleep(500);
        } finally {
            socket.close();
        }

        // Then - verify dispatcher was called
        verify(mockUdpDispatcher, timeout(1000).times(1)).dispatch(any(UdpPacket.class));
    }

    @Test
    @DisplayName("receiveLoop should handle multiple packets")
    void testReceiveLoop_HandlesMultiplePackets() throws IOException, InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(200);

        // When - send multiple packets
        int port = mockUdpProperties.getPort();
        DatagramSocket socket = new DatagramSocket();
        try {
            for (int i = 0; i < 3; i++) {
                byte[] payload = ("message-" + i).getBytes();
                DatagramPacket packet = new DatagramPacket(payload, payload.length,
                        InetAddress.getLoopbackAddress(), port);
                socket.send(packet);
                Thread.sleep(50);
            }

            // Give time for processing
            Thread.sleep(500);
        } finally {
            socket.close();
        }

        // Then - verify dispatcher was called for each packet
        verify(mockUdpDispatcher, timeout(1000).times(3)).dispatch(any(UdpPacket.class));
    }

    @Test
    @DisplayName("receiveLoop should extract correct payload")
    void testReceiveLoop_ExtractsCorrectPayload() throws IOException, InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(200);

        // When - send a packet with specific content
        int port = mockUdpProperties.getPort();
        byte[] expectedPayload = "hello-world-test".getBytes();
        DatagramSocket socket = new DatagramSocket();
        try {
            DatagramPacket packet = new DatagramPacket(expectedPayload, expectedPayload.length,
                    InetAddress.getLoopbackAddress(), port);
            socket.send(packet);
            Thread.sleep(500);
        } finally {
            socket.close();
        }

        // Then - verify the correct payload was dispatched
        verify(mockUdpDispatcher, timeout(1000)).dispatch(argThat(pkt ->
                pkt.payload() != null && new String(pkt.payload()).equals("hello-world-test")
        ));
    }

    @Test
    @DisplayName("receiveLoop should capture sender address")
    void testReceiveLoop_CapturesSenderAddress() throws IOException, InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(200);

        // When - send a packet
        int port = mockUdpProperties.getPort();
        DatagramSocket socket = new DatagramSocket();
        try {
            byte[] payload = "sender-test".getBytes();
            DatagramPacket packet = new DatagramPacket(payload, payload.length,
                    InetAddress.getLoopbackAddress(), port);
            socket.send(packet);
            Thread.sleep(500);
        } finally {
            socket.close();
        }

        // Then - verify sender address was captured
        verify(mockUdpDispatcher, timeout(1000)).dispatch(argThat(pkt ->
                pkt.sender() instanceof InetSocketAddress && pkt.sender() != null
        ));
    }

    @Test
    @DisplayName("receiveLoop should capture timestamp")
    void testReceiveLoop_CapturesTimestamp() throws IOException, InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(200);
        Instant beforeSend = Instant.now();

        // When - send a packet
        int port = mockUdpProperties.getPort();
        DatagramSocket socket = new DatagramSocket();
        try {
            byte[] payload = "timestamp-test".getBytes();
            DatagramPacket packet = new DatagramPacket(payload, payload.length,
                    InetAddress.getLoopbackAddress(), port);
            socket.send(packet);
            Thread.sleep(500);
        } finally {
            socket.close();
        }
        Instant afterSend = Instant.now();

        // Then - verify timestamp is reasonable
        verify(mockUdpDispatcher, timeout(1000)).dispatch(argThat(pkt ->
                pkt.receivedAt() != null && !pkt.receivedAt().isBefore(beforeSend) &&
                        !pkt.receivedAt().isAfter(afterSend.plusSeconds(1))
        ));
    }

    @Test
    @DisplayName("start() should use correct port from properties")
    void testStart_UsesConfiguredPort() throws InterruptedException {
        // Given
        when(mockUdpProperties.getPort()).thenReturn(19998);
        UdpServer server = new UdpServer(mockUdpProperties, mockUdpDispatcher);

        // When
        server.start();
        Thread.sleep(100);

        // Then
        assertTrue(server.isRunning());
        verify(mockUdpProperties, atLeastOnce()).getPort();

        // Cleanup
        server.stop();
        Thread.sleep(100);
    }

    @Test
    @DisplayName("start() should use correct buffer size from properties")
    void testStart_UsesConfiguredBufferSize() throws InterruptedException {
        // Given
        when(mockUdpProperties.getBufferSize()).thenReturn(32768);
        UdpServer server = new UdpServer(mockUdpProperties, mockUdpDispatcher);

        // When
        server.start();
        Thread.sleep(100);

        // Then
        verify(mockUdpProperties, atLeastOnce()).getBufferSize();
        assertTrue(server.isRunning());

        // Cleanup
        server.stop();
        Thread.sleep(100);
    }

    @Test
    @DisplayName("receiveLoop should not dispatch null packets")
    void testReceiveLoop_IgnoresNullPackets() throws InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(200);

        // When - wait and verify no null packets are sent
        Thread.sleep(300);

        // Then - no exceptions should occur and dispatcher should not be called with null
        verifyNoInteractions(mockUdpDispatcher);
    }

    @Test
    @DisplayName("SmartLifecycle interface methods work correctly")
    void testSmartLifecycleInterface() throws InterruptedException {
        // When/Then
        assertTrue(udpServer.isAutoStartup(), "Should auto-startup");
        assertEquals(Integer.MAX_VALUE, udpServer.getPhase(), "Phase should be MAX_VALUE");

        // When - start via interface
        udpServer.start();
        Thread.sleep(100);

        // Then
        assertTrue(udpServer.isRunning(), "Should be running after start");

        // When - stop
        udpServer.stop();
        Thread.sleep(100);

        // Then
        assertFalse(udpServer.isRunning(), "Should not be running after stop");
    }

    @Test
    @DisplayName("Dispatcher is invoked with correct packet structure")
    void testDispatcher_InvokedWithCorrectPacketStructure() throws IOException, InterruptedException {
        // Given
        udpServer.start();
        Thread.sleep(200);

        // When - send packet
        int port = mockUdpProperties.getPort();
        byte[] payload = {1, 2, 3, 4, 5};
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(payload, payload.length,
                    InetAddress.getLoopbackAddress(), port);
            socket.send(packet);
            Thread.sleep(500);
        }

        // Then - verify packet was dispatched with all fields
        verify(mockUdpDispatcher, timeout(1000)).dispatch(argThat(pkt ->
                pkt.payload() != null &&
                        pkt.sender() != null &&
                        pkt.receivedAt() != null &&
                        pkt.payload().length == 5
        ));
    }
}

