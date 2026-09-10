package com.racehorse.media.dto;

import com.racehorse.media.entity.ProfileFileStatus;

import java.time.Instant;
import java.util.UUID;

public record HorseProfileFileResponse(
        UUID id,
        UUID horseId,
        UserSummary uploadedBy,
        UserSummary approvedBy,
        String originalFilename,
        String contentType,
        String format,
        long size,
        String secureUrl,
        ProfileFileStatus status,
        Instant approvedAt,
        Instant createdAt
) {
    public record UserSummary(UUID id, String username) {
    }
}
