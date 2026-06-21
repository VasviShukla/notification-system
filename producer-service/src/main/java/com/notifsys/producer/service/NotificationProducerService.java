package com.notifsys.producer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifsys.producer.dto.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationProducerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.kafka.topic:notifications}")
    private String topic;

    public NotificationProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public NotificationEvent publish(NotificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            // Key by userId: guarantees all events for the same user land on the
            // same partition and are processed in order by the consumer.
            kafkaTemplate.send(topic, event.userId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish event {} for user {}: {}",
                                    event.eventId(), event.userId(), ex.getMessage());
                        } else {
                            log.info("Published event {} for user {} to partition {}",
                                    event.eventId(), event.userId(),
                                    result.getRecordMetadata().partition());
                        }
                    });
            return event;
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize notification event", e);
        }
    }
}
