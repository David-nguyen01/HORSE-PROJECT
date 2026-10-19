package com.racehorse.health.dto;

import java.math.BigDecimal;
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
        List<AddendumView> addenda,
        List<UUID> preventiveScheduleIds,
        Integer heartRateBpm,
        Integer bodyLengthCm,
        BigDecimal temperatureCelsius,
        Integer heightCm,
        BigDecimal weightKg
) {
    public MedicalExaminationDetailResponse(
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
            List<AddendumView> addenda,
            List<UUID> preventiveScheduleIds
    ) {
        this(id, horseId, horseCode, horseName, veterinarianId, veterinarianName,
                examinationDate, symptoms, diagnosis, treatmentProtocol, medicationNotes,
                followUpDate, healthStatusAfter, createdAt, addenda, preventiveScheduleIds,
                null, null, null, null, null);
    }

    public MedicalExaminationDetailResponse(
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
        this(id, horseId, horseCode, horseName, veterinarianId, veterinarianName,
                examinationDate, symptoms, diagnosis, treatmentProtocol, medicationNotes,
                followUpDate, healthStatusAfter, createdAt, addenda, List.of(),
                null, null, null, null, null);
    }

    public record AddendumView(
            UUID id,
            UUID authoredByUserId,
            String authorName,
            String notes,
            String healthStatusAfter,
            OffsetDateTime createdAt
    ) {}
}

