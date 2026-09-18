package com.racehorse.racing.controller;

import com.racehorse.racing.dto.CreateRaceEventRequest;
import com.racehorse.racing.dto.RaceEventResponse;
import com.racehorse.racing.dto.RaceRegistrationResponse;
import com.racehorse.racing.dto.RegisterHorseRequest;
import com.racehorse.racing.service.RaceEventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/race-events")
public class RaceEventController {

    private final RaceEventService service;

    public RaceEventController(RaceEventService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_RACING_EDIT') and hasRole('CLUB_MANAGER')")
    public ResponseEntity<RaceEventResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRaceEventRequest request) {
        RaceEventResponse response = service.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/race-events/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_RACING_EDIT') and hasRole('CLUB_MANAGER')")
    public RaceEventResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRaceEventRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_RACING_VIEW')")
    public Page<RaceEventResponse> upcoming(Pageable pageable) {
        return service.getUpcoming(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_RACING_VIEW')")
    public RaceEventResponse detail(@PathVariable UUID id) {
        return service.getDetail(id);
    }

    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public RaceRegistrationResponse register(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody RegisterHorseRequest request) {
        return service.registerHorse(id, userId(jwt), privileged(authentication), request);
    }

    @DeleteMapping("/{id}/registrations/{registrationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public void withdraw(
            @PathVariable UUID id,
            @PathVariable UUID registrationId,
            @AuthenticationPrincipal Jwt jwt) {
        service.withdrawRegistration(id, registrationId, userId(jwt));
    }

    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasAuthority('PERM_RACING_VIEW')")
    public List<RaceRegistrationResponse> getRegistrations(@PathVariable UUID id) {
        return service.getRegistrations(id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean privileged(Authentication a) {
        return a.getAuthorities().stream().anyMatch(x -> !x.getAuthority().equals("ROLE_HORSE_OWNER") && x.getAuthority().startsWith("ROLE_"));
    }
}
