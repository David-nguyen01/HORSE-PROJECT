package com.racehorse.nutrition.controller;

import com.racehorse.nutrition.dto.*;
import com.racehorse.nutrition.service.NutritionPlanService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}/nutrition-plan")
public class NutritionPlanController {

    private final NutritionPlanService service;

    public NutritionPlanController(NutritionPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public NutritionPlanResponse getActivePlan(@PathVariable UUID horseId) {
        return service.getActivePlan(horseId);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<NutritionPlanResponse> getHistory(@PathVariable UUID horseId) {
        return service.getHistory(horseId);
    }

    @GetMapping("/daily-checklist")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public DailyFeedingChecklistResponse getDailyChecklist(
            @PathVariable UUID horseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getDailyChecklist(horseId, date);
    }

    @GetMapping("/template-options")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW')")
    public List<FeedingTemplateOptionView> getTemplateOptions(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        ownerGuard.requireHorse(horseId, jwt, authentication);
        return service.getTemplateOptionsForHorse(horseId);
    }

    @PostMapping("/apply-template")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STABLE_STAFF','VETERINARIAN','HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public NutritionPlanResponse applyStandardTemplate(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ApplyFeedingTemplateRequest request) {
        return service.applyStandardTemplate(horseId, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VETERINARIAN','HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")
    public NutritionPlanResponse createPlan(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateNutritionPlanRequest request) {
        return service.createPlan(horseId, UUID.fromString(jwt.getSubject()), request);
    }
}
