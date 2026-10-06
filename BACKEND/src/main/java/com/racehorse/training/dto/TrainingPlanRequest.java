package com.racehorse.training.dto;

import com.racehorse.training.entity.SurfaceType;
import com.racehorse.training.entity.WorkloadIntensity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TrainingPlanRequest(
        @NotNull UUID horseId,
        @NotBlank @Size(max = 200) String name,
        boolean active,
        @NotEmpty List<@Valid Phase> phases
) {
    public record Phase(
            @NotBlank @Size(max = 200) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0.01") BigDecimal targetDistanceMeters,
            @NotNull WorkloadIntensity workloadIntensity,
            @NotNull SurfaceType surfaceType,
            List<@NotNull LocalDate> restDays
    ) {
        public Phase(String name, LocalDate startDate, LocalDate endDate, BigDecimal targetDistanceMeters,
                WorkloadIntensity workloadIntensity, SurfaceType surfaceType) {
            this(name, startDate, endDate, targetDistanceMeters, workloadIntensity, surfaceType, List.of());
        }
    }
}
