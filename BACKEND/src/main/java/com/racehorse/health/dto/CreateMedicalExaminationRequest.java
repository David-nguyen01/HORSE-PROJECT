package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateMedicalExaminationRequest(
        @NotNull LocalDate examinationDate,
        @NotBlank @Size(max = 4000) String symptoms,
        @NotBlank @Size(max = 4000) String diagnosis,
        @NotBlank @Size(max = 4000) String treatmentPlan,
        @Size(max = 4000) String medicationAndDosage,
        LocalDate followUpDate,
        @NotBlank @Pattern(regexp = "FIT|MONITOR|INJURED|QUARANTINED") String healthStatusAfter
) {}
