package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.serial.SessionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the number in the footer counts.
 *
 * <p>It counts open pages, and it can do that because the footer is on every page and opens exactly
 * one {@code /session} connection. What it must not count is subscriptions: a page opens one to
 * three of those depending on what it displays, so the chart alone used to make one tab look like
 * two and two tabs look like three.</p>
 *
 * <p>Real connections, against a real port, because the whole rule is about which channel a
 * connection is on — a mocked session would let the rule be wrong and the test still pass.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenPageCountTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebSocketPublisher publisher;

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
    void eachPageCountsOnce() throws Exception {
        connect(SessionType.SESSION_COUNT);
        connect(SessionType.SESSION_COUNT);

        assertThat(waitForCount(2)).isEqualTo(2);
    }

    /**
     * The defect this fix is for. A page on the chart holds a {@code /json} connection as well as
     * the one in its footer; counting both made two open tabs report three.
     */
    @Test
    void aDataChannelIsNotAPage() throws Exception {
        connect(SessionType.SESSION_COUNT);
        long onePage = waitForCount(1);

        connect(SessionType.JSON);
        connect(SessionType.RS);
        connect(SessionType.HEARTBEAT);

        assertThat(publisher.openPageCount())
                .as("three more connections, still one page")
                .isEqualTo(onePage);
    }

    @Test
    void closingAPageDropsItFromTheCount() throws Exception {
        connect(SessionType.SESSION_COUNT);
        WebSocketSession leaving = connect(SessionType.SESSION_COUNT);
        waitForCount(2);

        leaving.close();

        assertThat(waitForCount(1)).isEqualTo(1);
    }

    private WebSocketSession connect(SessionType channel) throws Exception {
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new AbstractWebSocketHandler() {
                }, "ws://localhost:" + port + channel.getName())
                .get(5, TimeUnit.SECONDS);
        opened.add(session);
        return session;
    }

    /**
     * Connections are registered on the server's own thread, so the count is not necessarily right
     * the instant {@code execute} returns. Polls briefly rather than sleeping a fixed amount.
     */
    private long waitForCount(long expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        long actual = publisher.openPageCount();
        while (actual != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
            actual = publisher.openPageCount();
        }
        return actual;
    }
}
