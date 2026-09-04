package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.serial.SessionType;

public interface WebSocketPublisher {

    void publishForAllClients(String message);
    void publishForAllClients(String message, SessionType sessionType);
    /**
     * How many pages of this application are open.
     *
     * <p>Exactly one {@code /session} connection is opened per page, from the footer, which is on
     * every page. Counting that channel is therefore counting pages — whereas counting every
     * channel counts subscriptions, and a page opens between one and three of those depending on
     * what it displays. That is the difference this method exists to keep straight.</p>
     */
    long openPageCount();

    long sessionsCount(SessionType sessionType);
}
