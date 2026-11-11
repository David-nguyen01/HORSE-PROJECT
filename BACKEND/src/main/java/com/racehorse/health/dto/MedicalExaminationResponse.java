package com.racehorse.health.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
        int addendaCount,
        Integer heartRateBpm,
        Integer bodyLengthCm,
        BigDecimal temperatureCelsius,
        Integer heightCm,
        BigDecimal weightKg,
        List<MedicalAttachmentResponse> attachments
) {
    public MedicalExaminationResponse {
        if (attachments == null) {
            attachments = List.of();
        }
    }

    public MedicalExaminationResponse(
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
            int addendaCount,
            Integer heartRateBpm,
            Integer bodyLengthCm,
            BigDecimal temperatureCelsius,
            Integer heightCm,
            BigDecimal weightKg
    ) {
        this(id, horseId, veterinarianId, veterinarianName, examinationDate, symptoms, diagnosis, treatmentProtocol, medicationNotes, followUpDate, healthStatusAfter, createdAt, addendaCount, heartRateBpm, bodyLengthCm, temperatureCelsius, heightCm, weightKg, List.of());
    }

    public MedicalExaminationResponse(
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
        this(id, horseId, veterinarianId, veterinarianName, examinationDate, symptoms, diagnosis, treatmentProtocol, medicationNotes, followUpDate, healthStatusAfter, createdAt, addendaCount, null, null, null, null, null, List.of());
    }
}
