package com.notifsys.consumer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifsys.consumer.dto.NotificationEvent;
import com.notifsys.consumer.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${notification.kafka.topic:notifications}",
            groupId = "${spring.kafka.consumer.group-id:notification-consumer-group}"
    )
    public void consume(String message) {
        try {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);
            notificationService.process(event);
        } catch (Exception e) {
            // In a production system this would go to a dead-letter topic instead
            // of just logging - left as a clearly-marked extension point.
            log.error("Failed to process message [{}]: {}", message, e.getMessage());
        }
    }
}
