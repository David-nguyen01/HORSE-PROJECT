package com.racehorse.training.controller;

import com.racehorse.training.dto.CreateTrainingEvaluationRequest;
import com.racehorse.training.dto.TrainingEvaluationResponse;
import com.racehorse.training.service.TrainingEvaluationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TrainingEvaluationController {

    private final TrainingEvaluationService service;

    public TrainingEvaluationController(TrainingEvaluationService service) {
        this.service = service;
    }

    @PostMapping("/training-evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasRole('HEAD_TRAINER')")
    TrainingEvaluationResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTrainingEvaluationRequest request, Authentication authentication) {
        return service.create(userId(jwt), request,
                hasRole(authentication, "CLUB_MANAGER"), hasRole(authentication, "HEAD_TRAINER"));
    }

    @GetMapping("/training-evaluations/{evaluationId}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    TrainingEvaluationResponse get(
            @PathVariable UUID evaluationId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return service.getById(evaluationId, userId(jwt), hasRole(authentication, "CLUB_MANAGER"),
                hasRole(authentication, "HEAD_TRAINER"), hasRole(authentication, "HORSE_OWNER"));
    }

    @GetMapping("/horses/{horseId}/training-evaluations")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    Page<TrainingEvaluationResponse> listForHorse(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listForHorse(horseId, userId(jwt), hasRole(authentication, "CLUB_MANAGER"),
                hasRole(authentication, "HEAD_TRAINER"), hasRole(authentication, "HORSE_OWNER"), pageable);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_" + role));
    }
}
