package com.racehorse.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateHorseHealthStatusRequest(@NotBlank String status,
                                             @Size(max=30) String sourceType,
                                             UUID sourceId) {}
