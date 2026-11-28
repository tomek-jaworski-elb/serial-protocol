package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.configuration.SessionCountWebSockerHandler;
import com.jaworski.serialprotocol.serial.SessionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the number in the footer counts: visible /chart tabs. A /session connection counts only
 * after it sent {@code watching} and until it sends {@code hidden}; data channels never count.
 *
 * <p>Real connections, against a real port, because the rule is about channels and messages.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveMapViewerCountTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebSocketPublisher publisher;

    @Autowired
    private SessionCountWebSockerHandler handler;

    private final List<WebSocketSession> opened = new ArrayList<>();

    @AfterEach
    void closeEverything() throws Exception {
        for (WebSocketSession session : opened) {
            if (session.isOpen()) {
                session.close();
            }
        }
        opened.clear();
        waitForCount(0);
    }

    @Test
    void aConnectionThatSentNothingIsNotCounted() throws Exception {
        connect(SessionType.SESSION_COUNT);
        connect(SessionType.SESSION_COUNT);

        assertThat(waitForCount(0)).isZero();
    }

    @Test
    void watchingCountsAndHiddenDropsItAndWatchingRestoresIt() throws Exception {
        WebSocketSession page = connect(SessionType.SESSION_COUNT);

        page.sendMessage(new TextMessage("watching"));
        assertThat(waitForCount(1)).isEqualTo(1);

        page.sendMessage(new TextMessage("hidden"));
        assertThat(waitForCount(0)).isZero();

        page.sendMessage(new TextMessage("watching"));
        assertThat(waitForCount(1)).isEqualTo(1);
    }

    // Called on the handler directly: over a real socket "nothing changed" can only be shown by waiting.
    @Test
    void aKeepAliveOrAnUnknownPayloadDoesNotChangeTheState() throws Exception {
        WebSocketSession page = mock(WebSocketSession.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        when(page.getId()).thenReturn("direct");
        when(page.getAttributes()).thenReturn(attributes);

        handler.handleMessage(page, new TextMessage("."));
        handler.handleMessage(page, new TextMessage("foo"));
        assertThat(attributes).doesNotContainKey(WebSocketPublisher.WATCHING_LIVE_MAP);

        handler.handleMessage(page, new TextMessage("watching"));
        handler.handleMessage(page, new TextMessage("."));
        handler.handleMessage(page, new TextMessage("foo"));
        assertThat(attributes).containsEntry(WebSocketPublisher.WATCHING_LIVE_MAP, true);

        handler.handleMessage(page, new TextMessage("hidden"));
        handler.handleMessage(page, new TextMessage("."));
        assertThat(attributes).containsEntry(WebSocketPublisher.WATCHING_LIVE_MAP, false);
    }

    @Test
    void aListeningPageReceivesTheNewCountOnEveryChange() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        connect(SessionType.SESSION_COUNT, received);
        WebSocketSession chart = connect(SessionType.SESSION_COUNT);

        chart.sendMessage(new TextMessage("watching"));
        awaitMessage(received, "1");
        chart.sendMessage(new TextMessage("hidden"));
        awaitMessage(received, "0");
    }

    @Test
    void aDataChannelIsNeverCounted() throws Exception {
        WebSocketSession page = connect(SessionType.SESSION_COUNT);
        page.sendMessage(new TextMessage("watching"));
        assertThat(waitForCount(1)).isEqualTo(1);

        for (SessionType channel : new SessionType[]{SessionType.JSON, SessionType.RS, SessionType.HEARTBEAT}) {
            connect(channel).sendMessage(new TextMessage("watching"));
        }
        Thread.sleep(200);

        assertThat(publisher.liveMapViewerCount()).isEqualTo(1);
    }

    @Test
    void closingAWatchingPageDropsItFromTheCount() throws Exception {
        connect(SessionType.SESSION_COUNT).sendMessage(new TextMessage("watching"));
        WebSocketSession leaving = connect(SessionType.SESSION_COUNT);
        leaving.sendMessage(new TextMessage("watching"));
        waitForCount(2);

        leaving.close();

        assertThat(waitForCount(1)).isEqualTo(1);
    }

    private WebSocketSession connect(SessionType channel) throws Exception {
        return connect(channel, new LinkedBlockingQueue<>());
    }

    private WebSocketSession connect(SessionType channel, BlockingQueue<String> received) throws Exception {
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new AbstractWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        received.add(message.getPayload());
                    }
                }, "ws://localhost:" + port + channel.getName())
                .get(5, TimeUnit.SECONDS);
        opened.add(session);
        return session;
    }

    /** Skips broadcasts of earlier changes (e.g. from connects) until the expected one arrives. */
    private static void awaitMessage(BlockingQueue<String> received, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        String message;
        do {
            message = received.poll(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        } while (message != null && !message.equals(expected));
        assertThat(message).isEqualTo(expected);
    }

    /**
     * Connections are registered on the server's own thread, so the count is not necessarily right
     * the instant {@code execute} returns. Polls briefly rather than sleeping a fixed amount.
     */
    private long waitForCount(long expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        long actual = publisher.liveMapViewerCount();
        while (actual != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
            actual = publisher.liveMapViewerCount();
        }
        return actual;
    }
}
