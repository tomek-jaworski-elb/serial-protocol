package com.jaworski.serialprotocol.service;

public interface WebSocketPublisher {

    void publishForAllClients(String message);
    int sessionsCount();
}
