package com.racehorse.training.dto;

import java.time.Instant;
import java.util.UUID;

public record TrialRunBookingResponse(UUID id, UUID trainingScheduleId, UUID horseId,
                                      UUID racetrackId, String racetrackName, UUID laneId,
                                      int laneNumber, Instant bookedStart, Instant bookedEnd) {}
