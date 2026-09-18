package com.racehorse.reporting.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public record OwnerReportResponse(
        UUID id,
        UUID ownerId,
        LocalDate periodStart,
        LocalDate periodEnd,
        OffsetDateTime generatedAt,
        UUID generatedByUserId,
        UUID pdfFileId,
        List<UUID> horseIds,
        List<UUID> transactionIds
) {}
