package com.racehorse.training.controller;

import com.racehorse.training.dto.CreateTrainingScheduleRequest;
import com.racehorse.training.dto.TrainingScheduleResponse;
import com.racehorse.training.service.TrainingScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/training-schedules")
public class TrainingScheduleController {

    private final TrainingScheduleService service;

    public TrainingScheduleController(TrainingScheduleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    List<TrainingScheduleResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTrainingScheduleRequest request, Authentication authentication) {
        return service.create(userId(jwt), request,
                hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"));
    }

    @PatchMapping("/{scheduleId}/ready")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','STABLE_STAFF')")
    TrainingScheduleResponse ready(@PathVariable UUID scheduleId, @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.markReady(scheduleId, userId(jwt), hasRole(authentication, "CLUB_MANAGER"));
    }

    @PostMapping("/{scheduleId}/start")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasRole('HEAD_TRAINER')")
    TrainingScheduleResponse start(@PathVariable UUID scheduleId, @AuthenticationPrincipal Jwt jwt) {
        return service.startTraining(scheduleId, userId(jwt));
    }

    @PostMapping("/{scheduleId}/finish")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasRole('HEAD_TRAINER')")
    TrainingScheduleResponse finish(@PathVariable UUID scheduleId, @AuthenticationPrincipal Jwt jwt) {
        return service.finishTraining(scheduleId, userId(jwt));
    }

    @PostMapping("/{scheduleId}/publish")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TrainingScheduleResponse publish(@PathVariable UUID scheduleId, @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.publish(scheduleId, userId(jwt),
                hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"));
    }

    @GetMapping("/{scheduleId}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','STABLE_STAFF')")
    TrainingScheduleResponse get(@PathVariable UUID scheduleId, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return service.get(scheduleId, userId(jwt),
                hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"), false);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    List<TrainingScheduleResponse> calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID horseId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return service.calendar(from, to, horseId, userId(jwt),
                hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"), false);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasRole('STABLE_STAFF')")
    List<TrainingScheduleResponse> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.mine(userId(jwt), from, to);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_" + role));
    }
}
