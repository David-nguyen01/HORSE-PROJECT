package com.racehorse.stable.controller;

import com.racehorse.stable.service.HorseIntakeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/horse-intake")
public class HorseIntakeController {
    private final HorseIntakeService service;

    public HorseIntakeController(HorseIntakeService service) { this.service = service; }

    @GetMapping("/waiting-room/pending")
    @PreAuthorize("hasAnyRole('STABLE_STAFF','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<HorseIntakeService.IntakeHorse> pendingWaitingRoom() { return service.pendingWaitingRoom(); }

    @PostMapping("/{horseId}/waiting-room")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STABLE_STAFF','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public HorseIntakeService.IntakeHorse admit(@PathVariable UUID horseId, @AuthenticationPrincipal Jwt jwt) {
        return service.admitToWaitingRoom(horseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/stall/pending")
    @PreAuthorize("hasAnyRole('STABLE_STAFF','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<HorseIntakeService.IntakeHorse> pendingStall() { return service.pendingStall(); }
}
