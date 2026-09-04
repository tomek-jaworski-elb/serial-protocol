package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.serial.SessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.time.Duration;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfiguration implements WebSocketConfigurer {


    private final JsonWebSocketHandler jsonWebSocketHandler;
    private final RSWebsocketHandler rsWebsocketHandler;
    private final HeartBeatWebSocketHandler heartBeatWebSocketHandler;
    private final SessionCountWebSockerHandler sessionCountWebSockerHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(jsonWebSocketHandler, SessionType.JSON.getName())
                .setAllowedOrigins("*");
        registry.addHandler(rsWebsocketHandler,SessionType.RS.getName())
                .setAllowedOrigins("*");
        registry.addHandler(heartBeatWebSocketHandler,SessionType.HEARTBEAT.getName())
                .setAllowedOrigins("*");
        registry.addHandler(sessionCountWebSockerHandler, SessionType.SESSION_COUNT.getName());
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        /*
         * Raised from 60s to leave room for keep-alives from a page whose tab is in the background:
         * browsers throttle its timers to about once a minute, which at 60s would have been exactly
         * on the boundary.
         *
         * The timeout is what notices a page that went away without saying so -- a crash, a forced
         * quit, a suspended laptop. Nothing else does. That is why the keep-alive is sent by the
         * page and not by the server: a page that stops existing stops sending, and this closes its
         * connection. Sending from the server instead would reset this clock for the dead page too,
         * and it would sit in the count for ever.
         */
        container.setMaxSessionIdleTimeout(Duration.ofMinutes(3).toMillis());
        container.setMaxTextMessageBufferSize(60_000);
        return container;
    }
}
