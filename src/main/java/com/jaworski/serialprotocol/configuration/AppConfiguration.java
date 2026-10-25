package com.jaworski.serialprotocol.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.ws.config.annotation.DelegatingWsConfiguration;

import java.time.Clock;

@Component
public class AppConfiguration {
    @Bean
    public DelegatingWsConfiguration delegatingWsConfiguration() {
        return new DelegatingWsConfiguration();
    }

    /** The clock the sync services stamp exports and name backups with; a test may replace it. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
