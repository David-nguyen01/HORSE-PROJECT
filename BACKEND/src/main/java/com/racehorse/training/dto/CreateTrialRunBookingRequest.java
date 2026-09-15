package com.racehorse.training.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateTrialRunBookingRequest(@NotNull UUID trainingScheduleId,
                                           @NotNull UUID racetrackLaneId,
                                           @NotNull LocalDate date,
                                           @NotNull LocalTime startTime) {}
