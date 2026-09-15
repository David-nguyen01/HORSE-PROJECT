package com.racehorse.training.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record RacetrackResponse(UUID id, String name, String location, boolean active,
                                LocalTime opensAt, LocalTime closesAt, boolean closesNextDay,
                                int slotMinutes, List<Lane> lanes) {
    public record Lane(UUID id, int laneNumber, boolean active) {}
}
