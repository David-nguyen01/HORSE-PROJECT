package com.racehorse.media.dto;

import java.time.Instant;
import java.util.UUID;

public record HorsePhotoResponse(
        UUID id,
        UUID horseId,
        UUID storedFileId,
        String secureUrl,
        String originalFilename,
        String contentType,
        long fileSize,
        int displayOrder,
        String caption,
        Instant uploadedAt
) {
}
