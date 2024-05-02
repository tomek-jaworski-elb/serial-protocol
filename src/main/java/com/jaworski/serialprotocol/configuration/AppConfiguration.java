package com.jaworski.serialprotocol.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.ws.config.annotation.DelegatingWsConfiguration;

@Component
public class AppConfiguration {
    @Bean
    public DelegatingWsConfiguration delegatingWsConfiguration() {
        return new DelegatingWsConfiguration();
    }
}
