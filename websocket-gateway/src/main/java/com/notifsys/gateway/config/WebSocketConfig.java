package com.notifsys.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Exposes a STOMP-over-WebSocket (with SockJS fallback) endpoint at /ws.
 * Clients subscribe to /topic/notifications/{userId} and receive whatever
 * NotificationRedisListener relays from Redis pub/sub.
 *
 * Note on horizontal scaling: this uses Spring's *simple* in-memory broker,
 * which only delivers to sessions connected to THIS JVM. That's fine here
 * because every gateway instance independently subscribes to the SAME Redis
 * pub/sub channels - Redis itself is what fans the message out to every
 * instance, and whichever instance is actually holding the user's session is
 * the one whose convertAndSend() call has anywhere to go. For a much larger
 * deployment you'd swap the simple broker for a full STOMP broker relay
 * (e.g. RabbitMQ) - left as a clearly-marked extension point.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
