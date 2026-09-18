package com.racehorse.health.controller;

import com.racehorse.health.dto.*;
import com.racehorse.health.service.HorseMeasurementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}")
public class HorseMeasurementController {
    private final HorseMeasurementService service;
    public HorseMeasurementController(HorseMeasurementService service) { this.service=service; }

    @PostMapping("/weights") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    HorseWeightResponse add(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,
                            @Valid @RequestBody CreateHorseWeightRequest request) {
        return service.addWeight(horseId,userId(jwt),request);
    }
    @GetMapping("/weights")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')")
    List<HorseWeightResponse> list(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth) {
        return service.weights(horseId,userId(jwt),privileged(auth));
    }
    @GetMapping("/race-readiness")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')")
    RaceReadinessResponse readiness(@PathVariable UUID horseId,@RequestParam(required=false) UUID raceEventId,
                                    @AuthenticationPrincipal Jwt jwt,Authentication auth) {
        return service.readiness(horseId,raceEventId,userId(jwt),privileged(auth));
    }
    @PutMapping("/health-status")
    @PreAuthorize("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')")
    HorseMeasurementService.HealthStatusView updateHealth(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateHorseHealthStatusRequest request){
        return service.updateHealthStatus(horseId,userId(jwt),request);
    }
    @GetMapping("/health-status")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW')")
    HorseMeasurementService.HealthStatusView health(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth){
        return service.healthStatus(horseId,userId(jwt),privileged(auth));
    }
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private boolean privileged(Authentication a){return a.getAuthorities().stream().anyMatch(x->!x.getAuthority().equals("ROLE_HORSE_OWNER")&&x.getAuthority().startsWith("ROLE_"));}
}
