package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddMedicalExaminationAddendumRequest(
        @NotBlank @Size(max = 2000) String notes,
        @Size(max = 30) String healthStatusAfter
) {
}
