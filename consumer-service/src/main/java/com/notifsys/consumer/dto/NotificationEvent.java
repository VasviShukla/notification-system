package com.notifsys.consumer.dto;

import java.time.Instant;

/**
 * This is the consumer-service's own view of the event contract published on
 * the "notifications" Kafka topic. It is intentionally a separate class from
 * producer-service's NotificationEvent - the two services do not share a JAR.
 * The JSON shape on the topic IS the contract; if it changes, both sides need
 * a coordinated (ideally backward-compatible) update.
 */
public record NotificationEvent(
        String eventId,
        String userId,
        String type,
        String title,
        String message,
        Instant createdAt
) {
}
