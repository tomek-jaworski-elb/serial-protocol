package com.jaworski.serialprotocol.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CustomEncoder {

    @Bean
    public PasswordEncoder encoder() {
        SecureRandom random = new SecureRandom();
        return new BCryptPasswordEncoder(4, random);
    }
}
