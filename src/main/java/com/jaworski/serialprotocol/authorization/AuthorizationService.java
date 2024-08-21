package com.jaworski.serialprotocol.authorization;

import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthorizationService {

    private final Resources resources;
    private final PasswordEncoder passwordEncoder;


    public boolean authorize(@NonNull String password) {
        return passwordEncoder.matches(password, resources.getNameServicePassword());
    }
}
