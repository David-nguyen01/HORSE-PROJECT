package com.racehorse.health.controller;

import com.racehorse.health.dto.CreateInjuryMarkerRequest;
import com.racehorse.health.dto.CreateInjuryUpdateRequest;
import com.racehorse.health.dto.InjuryMarkerResponse;
import com.racehorse.health.service.InjuryMarkerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * FR10 – 3D injury markers and recovery timeline.
 *
 * POST  /api/horses/{horseId}/injuries             → create new marker pin
 * GET   /api/horses/{horseId}/injuries             → list all markers for a horse
 * GET   /api/injuries/{markerId}                   → get single marker with full timeline
 * POST  /api/injuries/{markerId}/updates           → add a recovery progress update
 */
@RestController
@RequestMapping("/api")
public class InjuryMarkerController {

    private final InjuryMarkerService service;

    public InjuryMarkerController(InjuryMarkerService service) {
        this.service = service;
    }

    @PostMapping("/horses/{horseId}/injuries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasRole('VETERINARIAN')")
    InjuryMarkerResponse create(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInjuryMarkerRequest request) {
        return service.create(horseId, userId(jwt), request);
    }

    @GetMapping("/horses/{horseId}/injuries")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    List<InjuryMarkerResponse> listByHorse(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt) {
        boolean privileged = jwt.getClaimAsStringList("roles") != null &&
                jwt.getClaimAsStringList("roles").stream()
                        .anyMatch(r -> r.equals("VETERINARIAN") || r.equals("CLUB_MANAGER") || r.equals("HEAD_TRAINER"));
        return service.listByHorse(horseId, userId(jwt), privileged);
    }

    @GetMapping("/injuries/{markerId}")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    InjuryMarkerResponse getById(@PathVariable UUID markerId) {
        return service.getById(markerId);
    }

    @PostMapping("/injuries/{markerId}/updates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasRole('VETERINARIAN')")
    InjuryMarkerResponse addUpdate(
            @PathVariable UUID markerId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInjuryUpdateRequest request) {
        return service.addUpdate(markerId, userId(jwt), request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
