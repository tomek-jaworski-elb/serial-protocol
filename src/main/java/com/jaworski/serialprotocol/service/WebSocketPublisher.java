package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.serial.SessionType;

public interface WebSocketPublisher {

    void publishForAllClients(String message);
    void publishForAllClients(String message, SessionType sessionType);
    long sessionsCount();
    long sessionsCount(SessionType sessionType);
}
