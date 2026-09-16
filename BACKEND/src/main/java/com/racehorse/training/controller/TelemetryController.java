package com.racehorse.training.controller;

import com.racehorse.training.dto.*;
import com.racehorse.training.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}")
@PreAuthorize("hasRole('HEAD_TRAINER') and hasAuthority('PERM_TRAINING_EDIT')")
public class TelemetryController {
    private final TelemetryService service;
    public TelemetryController(TelemetryService service){this.service=service;}
    @PostMapping("/metric-thresholds") @ResponseStatus(HttpStatus.CREATED)
    Map<String,UUID> threshold(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateMetricThresholdRequest request){
        return Map.of("id",service.configureThreshold(horseId,UUID.fromString(jwt.getSubject()),request));
    }
    @PostMapping("/telemetry") @ResponseStatus(HttpStatus.CREATED)
    TelemetryResponse telemetry(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateTelemetryRequest request){
        return service.enter(horseId,UUID.fromString(jwt.getSubject()),request);
    }
}
