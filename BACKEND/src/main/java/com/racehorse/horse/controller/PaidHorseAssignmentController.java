package com.racehorse.horse.controller;

import com.racehorse.horse.service.PaidHorseAssignmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/trainer/pending")
    @PreAuthorize("hasRole('HEAD_TRAINER') and hasAuthority('PERM_TRAINING_EDIT')")
    public List<PaidHorseAssignmentService.PaidHorse> pendingTrainer() {
        return service.pendingTrainer();
    }

    @PostMapping("/trainer/{horseId}")
    @PreAuthorize("hasRole('HEAD_TRAINER') and hasAuthority('PERM_TRAINING_EDIT')")
    public PaidHorseAssignmentService.PaidHorse assignTrainer(
            @PathVariable UUID horseId, @AuthenticationPrincipal Jwt jwt) {
        return service.assignTrainer(horseId, UUID.fromString(jwt.getSubject()));
    }
}
