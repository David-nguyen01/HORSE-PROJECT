package com.racehorse.nutrition.controller;

import com.racehorse.nutrition.service.NutritionPlanService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
}
