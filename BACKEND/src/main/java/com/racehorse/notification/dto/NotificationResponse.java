package com.racehorse.notification.dto;

import com.racehorse.notification.entity.NotificationPriority;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        NotificationPriority priority,
        String title,
        String message,
        String relatedEntityType,
        UUID relatedEntityId,
        Instant createdAt,
        Instant readAt
) {
}
