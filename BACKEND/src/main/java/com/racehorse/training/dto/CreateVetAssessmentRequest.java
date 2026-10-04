package com.racehorse.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateVetAssessmentRequest(
        @NotBlank String overallCondition,
        @NotNull @PositiveOrZero Integer heartRateRecoveryBpm,
        @NotBlank String respiratoryStatus,
        @NotBlank String gaitStatus,
        @NotBlank String muscleCondition,
        @NotBlank String clinicalConclusion,
        String recommendation,
        Boolean requiresMedicalIntervention
) {
}
