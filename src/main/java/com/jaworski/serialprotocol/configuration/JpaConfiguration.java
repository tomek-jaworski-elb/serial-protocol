package com.jaworski.serialprotocol.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories("com.jaworski.serialprotocol.repository")
public class JpaConfiguration {
}
