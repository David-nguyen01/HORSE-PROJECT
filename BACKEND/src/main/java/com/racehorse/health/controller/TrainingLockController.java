package com.racehorse.health.controller;

import com.racehorse.health.dto.CreateTrainingLockRequest;
import com.racehorse.health.dto.ReleaseTrainingLockRequest;
import com.racehorse.health.dto.TrainingLockResponse;
import com.racehorse.health.service.TrainingLockService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TrainingLockController {

    private final TrainingLockService service;

    public TrainingLockController(TrainingLockService service) {
        this.service = service;
    }

    @PostMapping("/horses/{horseId}/training-locks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasAnyRole('VETERINARIAN','CLUB_MANAGER')")
    TrainingLockResponse lock(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTrainingLockRequest request) {
        return service.lock(horseId, userId(jwt), request);
    }

    @PostMapping("/training-locks/{lockId}/release")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasRole('VETERINARIAN')")
    TrainingLockResponse release(
            @PathVariable UUID lockId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReleaseTrainingLockRequest request) {
        return service.release(lockId, userId(jwt), request);
    }

    @GetMapping("/horses/{horseId}/training-locks/current")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    public TrainingLockResponse current(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {
        return service.current(horseId, userId(jwt), privileged(auth));
    }

    @GetMapping("/horses/{horseId}/training-locks")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    public List<TrainingLockResponse> history(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication auth) {
        return service.history(horseId, userId(jwt), privileged(auth));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean privileged(Authentication a) {
        return a.getAuthorities().stream().anyMatch(
                x -> !x.getAuthority().equals("ROLE_HORSE_OWNER") && x.getAuthority().startsWith("ROLE_"));
    }
}
