package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddExaminationAddendumRequest(
        @NotBlank @Size(max = 4000) String content
) {}
