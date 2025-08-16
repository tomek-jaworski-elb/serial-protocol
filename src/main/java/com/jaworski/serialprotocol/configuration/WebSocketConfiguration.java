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
        container.setMaxSessionIdleTimeout(Duration.ofSeconds(60).toMillis());
        container.setMaxTextMessageBufferSize(60_000);
        return container;
    }
}
