package com.jaworski.serialprotocol.serial;

import lombok.Getter;

@Getter
public enum SessionType {
    RS("/rs"),
    JSON("/json"),
    HEARTBEAT("/heartbeat"),
    SESSION_COUNT("/session");

    private final String name;

    SessionType(String name) {
        this.name = name;
    }

}
