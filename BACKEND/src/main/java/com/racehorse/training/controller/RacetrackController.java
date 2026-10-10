package com.racehorse.training.controller;

import com.racehorse.training.dto.*;
import com.racehorse.training.service.RacetrackService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
public class RacetrackController {
    private final RacetrackService service;
    public RacetrackController(RacetrackService service) { this.service = service; }

    @PostMapping("/api/racetracks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasRole('CLUB_MANAGER')")
    RacetrackResponse create(@Valid @RequestBody CreateRacetrackRequest request) { return service.create(request); }

    @GetMapping("/api/racetracks")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    List<RacetrackResponse> list() { return service.list(); }

    @GetMapping("/api/racetracks/{racetrackId}/availability")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    List<TrackLaneAvailabilityResponse> availability(@PathVariable UUID racetrackId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime slotStart) {
        return service.availability(racetrackId, date, slotStart);
    }

    @PostMapping("/api/trial-run-bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TrialRunBookingResponse book(@Valid @RequestBody CreateTrialRunBookingRequest request,
            @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return service.book(request, userId(jwt), hasRole(auth, "CLUB_MANAGER"), hasRole(auth, "HEAD_TRAINER"));
    }

    @GetMapping("/api/trial-run-bookings")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    List<TrialRunBookingResponse> bookings(@RequestParam(required=false) UUID racetrackId,
            @RequestParam(required=false) UUID horseId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="false") boolean includeCancelled,
            @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return service.bookings(racetrackId, horseId, from, to, includeCancelled, userId(jwt),
                hasRole(auth, "CLUB_MANAGER"), hasRole(auth, "HEAD_TRAINER"));
    }

    @DeleteMapping("/api/trial-run-bookings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    void cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        service.cancelBooking(id, userId(jwt), hasRole(auth, "CLUB_MANAGER"), hasRole(auth, "HEAD_TRAINER"));
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
