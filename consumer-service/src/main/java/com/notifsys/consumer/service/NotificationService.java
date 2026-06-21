package com.notifsys.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifsys.consumer.dto.NotificationEvent;
import com.notifsys.consumer.model.Notification;
import com.notifsys.consumer.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_CACHED_PER_USER = 50;
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final NotificationRepository repository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.redis.live-channel-prefix:notif:live:}")
    private String liveChannelPrefix;

    @Value("${notification.redis.history-key-prefix:notif:history:}")
    private String historyKeyPrefix;

    @Value("${notification.redis.unread-key-prefix:notif:unread:}")
    private String unreadKeyPrefix;

    public NotificationService(NotificationRepository repository,
                                RedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles one event consumed off Kafka. This is where the "fan-out" happens:
     *  1. Durable write to Postgres (source of truth, survives Redis restarts)
     *  2. Cache-aside write to Redis (fast reads for the "recent feed")
     *  3. Unread counter bump in Redis (fast badge count, avoids a COUNT query)
     *  4. Pub/Sub publish to Redis (real-time fan-out to whichever websocket-gateway
     *     instance currently holds that user's live connection)
     */
    @Transactional
    public void process(NotificationEvent event) {
        UUID id = UUID.fromString(event.eventId());

        // Idempotency: Kafka delivers at-least-once, so a redelivered event must not
        // be persisted twice or double-counted in the unread badge.
        if (repository.existsById(id)) {
            log.info("Event {} already processed, skipping (at-least-once redelivery)", event.eventId());
            return;
        }

        Notification notification = new Notification(
                id, event.userId(), event.type(), event.title(), event.message(), event.createdAt());
        repository.save(notification);

        try {
            String json = objectMapper.writeValueAsString(event);

            String historyKey = historyKeyPrefix + event.userId();
            redisTemplate.opsForList().leftPush(historyKey, json);
            redisTemplate.opsForList().trim(historyKey, 0, MAX_CACHED_PER_USER - 1);
            redisTemplate.expire(historyKey, CACHE_TTL);

            String unreadKey = unreadKeyPrefix + event.userId();
            redisTemplate.opsForValue().increment(unreadKey);
            redisTemplate.expire(unreadKey, CACHE_TTL);

            String channel = liveChannelPrefix + event.userId();
            redisTemplate.convertAndSend(channel, json);

            log.info("Processed event {} for user {} (persisted + cached + published)",
                    event.eventId(), event.userId());
        } catch (Exception e) {
            // The DB write already committed - we deliberately don't fail the whole
            // transaction just because the real-time push failed. The client will
            // still see it on next history fetch / app open.
            log.error("Cache/pubsub step failed for event {}: {}", event.eventId(), e.getMessage());
        }
    }

    public Page<Notification> getHistory(String userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public long getUnreadCount(String userId) {
        String value = redisTemplate.opsForValue().get(unreadKeyPrefix + userId);
        return value != null ? Long.parseLong(value) : 0L;
    }

    @Transactional
    public void markAllRead(String userId) {
        repository.markAllAsReadForUser(userId);
        redisTemplate.delete(unreadKeyPrefix + userId);
    }
}
