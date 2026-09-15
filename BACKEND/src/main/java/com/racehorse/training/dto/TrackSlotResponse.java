package com.racehorse.training.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TrackSlotResponse(UUID laneId, int laneNumber, LocalDate date, LocalTime startTime,
                                LocalTime endTime, boolean endsNextDay, boolean available,
                                UUID bookingId, UUID trainingScheduleId) {}
