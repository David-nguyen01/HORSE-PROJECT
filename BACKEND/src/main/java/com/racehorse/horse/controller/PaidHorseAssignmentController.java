package com.racehorse.horse.controller;

import com.racehorse.horse.service.PaidHorseAssignmentService;
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
@RequestMapping("/api/paid-horse-assignments")
public class PaidHorseAssignmentController {
    private final PaidHorseAssignmentService service;

    public PaidHorseAssignmentController(PaidHorseAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/caretaker/pending")
    @PreAuthorize("hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public List<PaidHorseAssignmentService.PaidHorse> pendingCaretaker() {
        return service.pendingCaretaker();
    }

    @PostMapping("/caretaker/{horseId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public PaidHorseAssignmentService.PaidHorse assignCaretaker(
            @PathVariable UUID horseId, @AuthenticationPrincipal Jwt jwt) {
        return service.assignCaretaker(horseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/diagnosing-vet/pending")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public List<PaidHorseAssignmentService.PaidHorse> pendingDiagnosingVet() {
        return service.pendingDiagnosingVet();
    }

    @PostMapping("/diagnosing-vet/{horseId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    public PaidHorseAssignmentService.PaidHorse assignDiagnosingVet(
            @PathVariable UUID horseId, @AuthenticationPrincipal Jwt jwt) {
        return service.assignDiagnosingVet(horseId, UUID.fromString(jwt.getSubject()));
    }
}
