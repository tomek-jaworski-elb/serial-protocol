package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.authorization.UserService;
import com.jaworski.serialprotocol.dto.User;
import com.jaworski.serialprotocol.exception.CustomRestException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.security.KeyStoreException;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RestTemplateClient {

    private final UserService userService;

    @Bean
    public RestTemplate restClient() throws KeyStoreException, CustomRestException {
        User user = userService.getUser();
        return new RestTemplateBuilder()
                .basicAuthentication(user.getName(), user.getPassword())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .setConnectTimeout(Duration.ofSeconds(20))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

}
