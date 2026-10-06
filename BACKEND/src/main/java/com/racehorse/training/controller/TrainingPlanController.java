package com.racehorse.training.controller;

import com.racehorse.training.dto.CopyTrainingPlanRequest;
import com.racehorse.training.dto.CopyTrainingPlanResponse;
import com.racehorse.training.dto.TrainingPlanRequest;
import com.racehorse.training.dto.TrainingPlanResponse;
import com.racehorse.training.service.TrainingPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
public class TrainingPlanController {

    private final TrainingPlanService service;

    public TrainingPlanController(TrainingPlanService service) {
        this.service = service;
    }

    @PostMapping("/training-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TrainingPlanResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrainingPlanRequest request, Authentication authentication) {
        return service.create(userId(jwt), request, hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"));
    }

    @PutMapping("/training-plans/{planId}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TrainingPlanResponse update(@PathVariable UUID planId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrainingPlanRequest request, Authentication authentication) {
        return service.update(planId, userId(jwt), request, hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"));
    }

    @PostMapping("/training-plans/{planId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    CopyTrainingPlanResponse copy(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CopyTrainingPlanRequest request,
            Authentication authentication) {
        return service.copy(planId, userId(jwt), request, hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"));
    }

    @GetMapping("/training-plans/{planId}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    TrainingPlanResponse get(
            @PathVariable UUID planId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.get(planId, userId(jwt), hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"), hasRole(authentication, "HORSE_OWNER"));
    }

    @GetMapping("/horses/{horseId}/training-plans")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    List<TrainingPlanResponse> listForHorse(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.listForHorse(horseId, userId(jwt), hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"), hasRole(authentication, "HORSE_OWNER"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
