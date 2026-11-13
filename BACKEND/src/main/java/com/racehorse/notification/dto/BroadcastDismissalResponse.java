package com.racehorse.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record BroadcastDismissalResponse(
        UUID notificationId,
        boolean dismissed,
        Instant dismissedAt
) {
}
