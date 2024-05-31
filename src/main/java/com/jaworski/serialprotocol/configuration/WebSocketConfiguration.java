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


    private final JsonWebSocketHandler jsonWebSocketHandler;
    private final Resources resources;
    private final RSWebsocketHandler rsWebsocketHandler;
    private final HeartBeatWebSocketHandler heartBeatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(jsonWebSocketHandler, "/json")
                .setAllowedOrigins("*");
        registry.addHandler(rsWebsocketHandler,"/rs")
                .setAllowedOrigins("*");
        registry.addHandler(heartBeatWebSocketHandler,"/heartbeat")
                .setAllowedOrigins("*");
    }

}
