package com.racehorse.training.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TrackLaneAvailabilityResponse(UUID laneId, int laneNumber, LocalDate date) {
}
