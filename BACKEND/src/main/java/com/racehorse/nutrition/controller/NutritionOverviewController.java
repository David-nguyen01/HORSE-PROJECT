package com.racehorse.nutrition.controller;

import com.racehorse.nutrition.dto.CreateStandardFeedingPlanRequest;
import com.racehorse.nutrition.dto.StallQuickFeedItem;
import com.racehorse.nutrition.dto.UpdateStandardFeedingPlanRequest;
import com.racehorse.nutrition.service.NutritionPlanService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionOverviewController {

    private final NutritionPlanService service;

    public NutritionOverviewController(NutritionPlanService service) {
        this.service = service;
    }

    @GetMapping("/standard-plans")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    public List<Map<String, Object>> getStandardPlans() {
        return service.listStandardPlans();
    }

    @GetMapping("/standard-plans/{planCode}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    public Map<String, Object> getStandardPlan(@PathVariable String planCode) {
        return service.getStandardPlan(planCode);
    }

    @PostMapping("/standard-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINARIAN')")
    public Map<String, Object> createStandardPlan(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody CreateStandardFeedingPlanRequest request) {
        UUID actor = getActorId(jwt, authentication);
        return service.createStandardPlan(actor, request);
    }

    @PutMapping("/standard-plans/{planCode}")
    @PreAuthorize("hasRole('VETERINARIAN')")
    public Map<String, Object> updateStandardPlan(
            @PathVariable String planCode,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody UpdateStandardFeedingPlanRequest request) {
        UUID actor = getActorId(jwt, authentication);
        return service.updateStandardPlan(planCode, actor, request);
    }

    @DeleteMapping("/standard-plans/{planCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VETERINARIAN')")
    public void deleteStandardPlan(
            @PathVariable String planCode,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID actor = getActorId(jwt, authentication);
        service.deleteStandardPlan(planCode, actor);
    }

    @GetMapping("/horses-active")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    public List<Map<String, Object>> getHorsesActive(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.listHorsesActivePlans(date);
    }

    @GetMapping("/stables-workload")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    public Map<String, Object> getStablesWorkload(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getStableWorkload(date);
    }

    @GetMapping("/stables-quick-feed")
    @PreAuthorize("hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_VIEW') and !hasRole('HORSE_OWNER')")
    public List<StallQuickFeedItem> getStablesQuickFeed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getStablesQuickFeed(date);
    }

    private UUID getActorId(Jwt jwt, Authentication authentication) {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {}
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {}
        }
        return UUID.randomUUID();
    }
}
