package com.notifsys.gateway.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class NotificationRedisListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRedisListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${notification.redis.live-channel-prefix:notif:live:}")
    private String liveChannelPrefix;

    public NotificationRedisListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        if (!channel.startsWith(liveChannelPrefix)) {
            return;
        }
        String userId = channel.substring(liveChannelPrefix.length());

        // Every gateway instance receives this (Redis pub/sub broadcasts to all
        // subscribers). Only the instance that actually holds userId's live
        // STOMP session will have anywhere for convertAndSend to deliver to -
        // the others are harmless no-ops. This is what lets you run N gateway
        // replicas behind a load balancer without sticky sessions for fan-out.
        String destination = "/topic/notifications/" + userId;
        messagingTemplate.convertAndSend(destination, payload);
        log.info("Relayed live notification for user {} to {}", userId, destination);
    }
}
