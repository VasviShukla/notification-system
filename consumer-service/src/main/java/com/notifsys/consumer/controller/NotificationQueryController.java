package com.notifsys.consumer.controller;

import com.notifsys.consumer.model.Notification;
import com.notifsys.consumer.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationQueryController {

    private final NotificationService notificationService;

    public NotificationQueryController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Paginated history, served from Postgres - the durable source of truth. */
    @GetMapping("/{userId}")
    public Page<Notification> getHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getHistory(userId, page, size);
    }

    /** Fast badge count, served from a Redis counter (avoids a COUNT(*) query per page load). */
    @GetMapping("/{userId}/unread-count")
    public Map<String, Long> getUnreadCount(@PathVariable String userId) {
        return Map.of("unreadCount", notificationService.getUnreadCount(userId));
    }

    @PostMapping("/{userId}/read")
    public Map<String, String> markRead(@PathVariable String userId) {
        notificationService.markAllRead(userId);
        return Map.of("status", "ok");
    }

    @GetMapping("/health")
    public String health() {
        return "consumer-service is up";
    }
}
