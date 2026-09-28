package com.racehorse.health.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MedicalExaminationDetailResponse(
        UUID id,
        UUID horseId,
        String horseCode,
        String horseName,
        UUID veterinarianId,
        String veterinarianName,
        LocalDate examinationDate,
        String symptoms,
        String diagnosis,
        String treatmentProtocol,
        String medicationNotes,
        LocalDate followUpDate,
        String healthStatusAfter,
        OffsetDateTime createdAt,
        List<AddendumView> addenda
) {
    public record AddendumView(
            UUID id,
            UUID authoredByUserId,
            String authorName,
            String notes,
            String healthStatusAfter,
            OffsetDateTime createdAt
    ) {}
}
