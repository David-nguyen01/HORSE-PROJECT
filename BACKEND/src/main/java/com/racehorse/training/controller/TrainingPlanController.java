package com.racehorse.training.controller;

import com.racehorse.training.dto.CopyTrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanResponse;
import com.racehorse.training.service.TrainingPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('CLUB_MANAGER', 'HEAD_TRAINER')")
public class TrainingPlanController {

    private final TrainingPlanService service;

    public TrainingPlanController(TrainingPlanService service) {
        this.service = service;
    }

    @PostMapping("/training-plans")
    @ResponseStatus(HttpStatus.CREATED)
    TrainingPlanResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrainingPlanRequest request) {
        return service.create(userId(jwt), request);
    }

    @PutMapping("/training-plans/{planId}")
    TrainingPlanResponse update(@PathVariable UUID planId, @Valid @RequestBody TrainingPlanRequest request) {
        return service.update(planId, request);
    }

    @PostMapping("/training-plans/{planId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    TrainingPlanResponse copy(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CopyTrainingPlanRequest request) {
        return service.copy(planId, userId(jwt), request);
    }

    @GetMapping("/training-plans/{planId}")
    TrainingPlanResponse get(@PathVariable UUID planId) {
        return service.get(planId);
    }

    @GetMapping("/horses/{horseId}/training-plans")
    List<TrainingPlanResponse> listForHorse(@PathVariable UUID horseId) {
        return service.listForHorse(horseId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
