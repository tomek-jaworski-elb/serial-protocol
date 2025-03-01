package com.jaworski.serialprotocol.authorization;

import lombok.Getter;

@Getter
public enum SecurityRoles {

    ROLE_USER(1, "ROLE_USER", "USER"),
    ROLE_ADMIN(2, "ROLE_ADMIN", "ADMIN");

    private final String role;
    private final String name;
    private final int value;

    SecurityRoles( int value, String role, String name) {
        this.role = role;
        this.name = name;
        this.value = value;
    }
}
