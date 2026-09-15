package com.racehorse.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRacetrackRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 300) String location,
        @Positive int laneCount) {}
