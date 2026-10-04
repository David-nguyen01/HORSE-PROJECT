package com.racehorse.training.controller;

import com.racehorse.training.dto.CreateTrainingEvaluationRequest;
import com.racehorse.training.dto.CreateVetAssessmentRequest;
import com.racehorse.training.dto.TrainingEvaluationResponse;
import com.racehorse.training.dto.VetAssessmentResponse;
import com.racehorse.training.service.TrainingEvaluationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;
import java.util.List;
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
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TrainingEvaluationResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTrainingEvaluationRequest request) {
        return service.create(userId(jwt), request);
    }

    @GetMapping("/training-evaluations/{evaluationId}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    TrainingEvaluationResponse get(
            @PathVariable UUID evaluationId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return service.getById(evaluationId, userId(jwt), isPrivileged(authentication));
    }

    @GetMapping("/horses/{horseId}/training-evaluations")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
    Page<TrainingEvaluationResponse> listForHorse(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listForHorse(horseId, userId(jwt), isPrivileged(authentication), pageable);
    }

    @PostMapping("/training-evaluations/{scheduleId}/vet-assessment")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasRole('VETERINARIAN')")
    VetAssessmentResponse saveVetAssessment(
            @PathVariable UUID scheduleId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateVetAssessmentRequest request) {
        return service.saveVetAssessment(scheduleId, userId(jwt), request);
    }

    @GetMapping("/training-evaluations/{scheduleId}/vet-assessment")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    ResponseEntity<VetAssessmentResponse> getVetAssessment(@PathVariable UUID scheduleId) {
        VetAssessmentResponse resp = service.getVetAssessment(scheduleId);
        return resp != null ? ResponseEntity.ok(resp) : ResponseEntity.noContent().build();
    }

    @GetMapping("/training-evaluations/vet-assessments")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    List<VetAssessmentResponse> listVetAssessments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getVetAssessmentsByDate(date != null ? date : LocalDate.now());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_CLUB_MANAGER")
                || authority.getAuthority().equals("ROLE_HEAD_TRAINER")
                || authority.getAuthority().equals("ROLE_VETERINARIAN"));
    }
}
