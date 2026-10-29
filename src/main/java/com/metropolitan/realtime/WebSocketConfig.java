package com.metropolitan.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final RealtimeHub hub;

    public WebSocketConfig(RealtimeHub hub) {
        this.hub = hub;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Origins are open because every connection must present a one-time ticket that only an
        // authenticated API call can obtain, so a foreign page cannot open a useful socket.
        registry.addHandler(hub, "/ws").setAllowedOriginPatterns("*");
    }
}
