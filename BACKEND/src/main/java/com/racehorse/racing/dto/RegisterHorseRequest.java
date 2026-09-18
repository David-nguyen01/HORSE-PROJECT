package com.racehorse.racing.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterHorseRequest(
        @NotNull UUID horseId
) {}
