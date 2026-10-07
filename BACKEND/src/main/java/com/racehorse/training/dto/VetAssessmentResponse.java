package com.racehorse.training.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VetAssessmentResponse(
        UUID id,
        UUID scheduleId,
        UUID horseId,
        String horseCode,
        String horseName,
        UUID veterinarianId,
        String veterinarianName,
        String overallCondition,
        Integer heartRateRecoveryBpm,
        String respiratoryStatus,
        String gaitStatus,
        String muscleCondition,
        String clinicalConclusion,
        String recommendation,
        boolean requiresMedicalIntervention,
        OffsetDateTime createdAt
) {
}
