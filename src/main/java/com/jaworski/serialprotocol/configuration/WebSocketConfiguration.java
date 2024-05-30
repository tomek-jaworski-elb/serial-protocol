package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfiguration implements WebSocketConfigurer {


    private final EchoWebSocketHandler echoWebSocketHandler;
    private final Resources resources;
    private final RSWebsocketHandler rsWebsocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoWebSocketHandler, resources.getWsEndpoint())
                .setAllowedOrigins("*");
        registry.addHandler(rsWebsocketHandler,"/rs")
                .setAllowedOrigins("*");
    }

}
