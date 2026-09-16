package com.racehorse.training.controller;

import com.racehorse.training.dto.*;
import com.racehorse.training.service.RacetrackService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @GetMapping("/api/racetracks/{racetrackId}/slots")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    List<TrackSlotResponse> slots(@PathVariable UUID racetrackId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.slots(racetrackId, date);
    }

    @PostMapping("/api/trial-run-bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TrialRunBookingResponse book(@Valid @RequestBody CreateTrialRunBookingRequest request) { return service.book(request); }
}
