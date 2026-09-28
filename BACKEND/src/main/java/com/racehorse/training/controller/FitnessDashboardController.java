package com.racehorse.training.controller;

import com.racehorse.training.dto.FitnessDashboardResponse;
import com.racehorse.training.entity.FitnessStatus;
import com.racehorse.training.service.FitnessDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/fitness")
@PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
public class FitnessDashboardController {

    private final FitnessDashboardService service;

    public FitnessDashboardController(FitnessDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    FitnessDashboardResponse dashboard(
            @RequestParam(required = false) UUID horseId,
            @RequestParam(required = false) UUID phaseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) FitnessStatus fitnessStatus,
            @RequestParam(required = false) String query) {
        return service.get(horseId, phaseId, from, to, fitnessStatus, query);
    }
}
