package com.jaworski.serialprotocol.authorization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CustomEncoder {

    /**
     * Hashes carry their algorithm as an {@code {id}} prefix, so a later switch (e.g. to Argon2) can
     * add an encoder here without touching stored rows. Only bcrypt is mapped: {@code {noop}} and the
     * other legacy ids are rejected. OWASP asks for a bcrypt cost of at least 10; tests lower it.
     */
    @Bean
    public PasswordEncoder encoder(@Value("${custom.security.bcrypt-strength:10}") int strength) {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(strength)));
    }
}
