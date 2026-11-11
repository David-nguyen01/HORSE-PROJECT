package com.racehorse.health.dto;

import java.util.UUID;

public record MedicalAttachmentResponse(
        UUID fileId,
        String url,
        String filename,
        Long fileSize,
        String contentType
) {}
