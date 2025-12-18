package com.example.webapp.BidNow.Configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * This conf file is for establishing an open
 * continuous connection between backend - frontend,
 * for real time events.
 *
 * This configuration is only possible to perform in single instance applications.
 * todo: For now 2 instances: one sitting when other crashes later the second starts immediately
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // This is the endpoint that user's browser makes the connection with
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:5173"
                )
                .withSockJS();                   // Fallback sockjs, polling

//       For Mobile apps
//        registry.addEndpoint("/ws")
//                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinations starting with /topic are used for server-to-client broadcasting.
        registry.enableSimpleBroker("/topic");

        // Destinations starting with /topic are used for server-to-client broadcasting.
        registry.setApplicationDestinationPrefixes("/app");
    }
}

