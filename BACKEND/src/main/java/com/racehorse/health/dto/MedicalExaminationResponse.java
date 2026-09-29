package com.racehorse.health.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MedicalExaminationResponse(
        UUID id,
        VetSummary horse,
        VetSummary veterinarian,
        LocalDate examinationDate,
        String symptoms,
        String diagnosis,
        String treatmentPlan,
        String medicationAndDosage,
        LocalDate followUpDate,
        String healthStatusAfter,
        Instant submittedAt,
        List<AddendumResponse> addenda
) {
    public record VetSummary(UUID id, String name) {}
    public record AddendumResponse(UUID id, String content, VetSummary authoredBy, Instant createdAt) {}
}
