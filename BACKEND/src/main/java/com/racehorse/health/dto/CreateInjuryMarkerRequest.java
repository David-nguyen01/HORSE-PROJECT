package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateInjuryMarkerRequest(
        @NotBlank @Size(max = 100) String modelName,
        @NotBlank @Size(max = 150) String bodyPartLabel,
        @NotNull BigDecimal coordinateX,
        @NotNull BigDecimal coordinateY,
        @NotNull BigDecimal coordinateZ,
        @NotBlank @Size(max = 100) String initialSeverity
) {}
