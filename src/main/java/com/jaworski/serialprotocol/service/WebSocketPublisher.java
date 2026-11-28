package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.serial.SessionType;

import java.util.function.Supplier;

public interface WebSocketPublisher {

    /** {@code /session} connection attribute: Boolean, true while its /chart tab is visible. */
    String WATCHING_LIVE_MAP = "watchingLiveMap";

    void publishForAllClients(String message);
    void publishForAllClients(String message, SessionType sessionType);
    /**
     * Like the String overload, but the message is evaluated per session at send time, inside that
     * session's lock, so of two racing broadcasts the last one written carries the latest value.
     */
    void publishForAllClients(Supplier<String> message, SessionType sessionType);

    /**
     * How many /chart tabs are open and visible right now (live map viewers).
     *
     * <p>Counts {@code /session} connections (one per page, from the footer) whose page reported
     * {@code watching} and has not since reported {@code hidden}. Only /chart reports, so every
     * other page, and a hidden chart tab, counts 0. Counting every channel would count
     * subscriptions instead, and a page opens one to three of those.</p>
     */
    long liveMapViewerCount();

    long sessionsCount(SessionType sessionType);
}
