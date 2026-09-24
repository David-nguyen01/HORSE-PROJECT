package com.racehorse.health.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicalExaminationResponse(
        UUID id,
        UUID horseId,
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
        int addendaCount
) {
}
