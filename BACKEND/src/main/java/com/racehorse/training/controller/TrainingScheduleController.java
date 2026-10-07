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
    TrainingScheduleResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTrainingScheduleRequest request) {
        return service.create(userId(jwt), request);
    }

    @PostMapping("/{scheduleId}/publish")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TrainingScheduleResponse publish(@PathVariable UUID scheduleId) {
        return service.publish(scheduleId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    List<TrainingScheduleResponse> calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID horseId) {
        LocalDate actualFrom = date != null ? date : from;
        LocalDate actualTo = date != null ? date : to;
        return service.calendar(actualFrom, actualTo, horseId);
    }

    @GetMapping("/open")
    @PreAuthorize("hasAuthority('PERM_TRAINING_CLAIM') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")
    List<TrainingScheduleResponse> openSessions(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.findOpenSessions(userId(jwt), isVet(authentication), from, to);
    }

    @PostMapping("/{scheduleId:[0-9a-fA-F\\-]{36}}/claim")
    @PreAuthorize("hasAuthority('PERM_TRAINING_CLAIM') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")
    TrainingScheduleResponse claim(
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.claim(scheduleId, userId(jwt), isVet(authentication));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")
    List<TrainingScheduleResponse> mine(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.mine(userId(jwt), isVet(authentication), from, to);
    }

    @GetMapping("/{scheduleId:[0-9a-fA-F\\-]{36}}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','STABLE_STAFF')")
    TrainingScheduleResponse get(@PathVariable UUID scheduleId, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return service.get(scheduleId, userId(jwt), isPrivileged(authentication));
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_CLUB_MANAGER")
                || authority.getAuthority().equals("ROLE_HEAD_TRAINER")
                || authority.getAuthority().equals("ROLE_VETERINARIAN"));
    }

    private boolean isVet(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_VETERINARIAN"));
    }
}
