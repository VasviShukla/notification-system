package com.notifsys.producer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The event contract published onto the "notifications" Kafka topic.
 * Both producer-service and consumer-service know this shape independently
 * (they are separately deployable services, not sharing a JAR) - the topic
 * + JSON schema IS the contract between them.
 */
public record NotificationEvent(
        String eventId,
        String userId,
        String type,
        String title,
        String message,
        Instant createdAt
) {
    public static NotificationEvent from(NotificationRequest request) {
        return new NotificationEvent(
                UUID.randomUUID().toString(),
                request.userId(),
                request.type(),
                request.title(),
                request.message(),
                Instant.now()
        );
    }
}
