package com.racehorse.health.controller;

import com.racehorse.health.dto.BarnHealthMapResponse;
import com.racehorse.health.service.BarnHealthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/health")
public class BarnHealthController {

    private final BarnHealthService barnHealthService;

    public BarnHealthController(BarnHealthService barnHealthService) {
        this.barnHealthService = barnHealthService;
    }

    @GetMapping("/barn-map")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','STABLE_STAFF')")
    public BarnHealthMapResponse getBarnMap(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID stableId) {
        return barnHealthService.getBarnMap(status, stableId);
    }
}
