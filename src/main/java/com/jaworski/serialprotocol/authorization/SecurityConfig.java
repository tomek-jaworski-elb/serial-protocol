package com.jaworski.serialprotocol.authorization;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomEncoder customEncoder;

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername("user")
                .password("user")
                .passwordEncoder(customEncoder.encoder()::encode)
                .accountExpired(false)
                .accountLocked(false)
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(userDetails);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorizeHttpRequests) ->
                        authorizeHttpRequests
                                .requestMatchers("/").anonymous()
                                .requestMatchers("/about,").anonymous()
                                .requestMatchers("/index").anonymous()
                                .requestMatchers("/index.htm").anonymous()
                                .requestMatchers("/index.html").anonymous()
                                .requestMatchers("/chart").anonymous()
                                .requestMatchers("/error").anonymous()
                                .requestMatchers("/terminal").anonymous()
                                .requestMatchers("/img/**").anonymous()
                                .requestMatchers("/js/**").anonymous()
                                .requestMatchers("/bootstrap-5-3-3/**").anonymous()
                                .requestMatchers("/name-service").hasRole("USER")
                                .requestMatchers("/api/").hasRole("USER")
                );
        return http.build();
    }
}
