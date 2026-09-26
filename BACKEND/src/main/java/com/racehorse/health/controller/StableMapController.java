package com.racehorse.health.controller;

import com.racehorse.health.dto.StableMapResponse;
import com.racehorse.health.service.StableMapService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * FR08 – Stable health-status map.
 *
 * GET /api/health/stable-map            → all stalls
 * GET /api/health/stable-map?status=INJURED → filtered by health status
 */
@RestController
@RequestMapping("/api")
public class StableMapController {

    private final StableMapService service;

    public StableMapController(StableMapService service) {
        this.service = service;
    }

    @GetMapping("/health/stable-map")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER')")
    StableMapResponse stableMap(@RequestParam(required = false) String status) {
        return service.stableMap(status);
    }
}
