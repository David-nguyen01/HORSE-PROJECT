package com.racehorse.training.controller;

import com.racehorse.training.dto.OwnerTrainingScheduleResponse;
import com.racehorse.training.service.TrainingScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class OwnerTrainingScheduleController {
    private final TrainingScheduleService service;

    public OwnerTrainingScheduleController(TrainingScheduleService service) {
        this.service = service;
    }

    @GetMapping("/horses/{horseId}/training-schedules")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasRole('HORSE_OWNER')")
    public List<OwnerTrainingScheduleResponse> listForHorse(
            @PathVariable UUID horseId,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        return service.ownerSchedules(horseId, UUID.fromString(jwt.getSubject()), from, to);
    }

    @GetMapping("/training-schedules/{scheduleId}/owner-view")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasRole('HORSE_OWNER')")
    public OwnerTrainingScheduleResponse getOwnerView(
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.ownerSchedule(scheduleId, UUID.fromString(jwt.getSubject()));
    }
}
