package com.jaworski.serialprotocol.authorization;

import lombok.Getter;

@Getter
public enum SecurityRoles {

    // Declared lowest first: the declaration order is the ranking (UserAccountService.highestRole).
    ROLE_OPERATOR("ROLE_OPERATOR", "OPERATOR"),
    ROLE_USER("ROLE_USER", "USER"),
    ROLE_ADMIN("ROLE_ADMIN", "ADMIN");

    private final String role;
    private final String name;

    SecurityRoles(String role, String name) {
        this.role = role;
        this.name = name;
    }
}
