package com.notifsys.producer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload a client sends to trigger a notification.
 * Kept deliberately generic so any upstream system (orders, social, billing...)
 * can reuse the same producer API.
 */
public record NotificationRequest(
        @NotBlank(message = "userId is required") String userId,
        @NotBlank(message = "type is required") String type,
        @NotBlank(message = "title is required") String title,
        @NotBlank(message = "message is required") String message
) {
}
