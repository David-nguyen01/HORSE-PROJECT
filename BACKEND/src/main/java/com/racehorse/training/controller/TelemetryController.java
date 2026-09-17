package com.racehorse.training.controller;

import com.racehorse.training.dto.*;
import com.racehorse.training.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/horses/{horseId}")
public class TelemetryController {
    private final TelemetryService service;
    public TelemetryController(TelemetryService service){this.service=service;}
    @PostMapping("/metric-thresholds") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    Map<String,UUID> threshold(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateMetricThresholdRequest request){
        return Map.of("id",service.configureThreshold(horseId,UUID.fromString(jwt.getSubject()),request));
    }
    @GetMapping("/metric-thresholds")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    List<TelemetryService.ThresholdView> thresholds(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(defaultValue="false") boolean includeExpired){
        return service.thresholds(horseId,user(jwt),privileged(auth),includeExpired);
    }
    @PutMapping("/metric-thresholds/{thresholdId}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TelemetryService.ThresholdView updateThreshold(@PathVariable UUID horseId,@PathVariable UUID thresholdId,
            @AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateMetricThresholdRequest request){
        return service.updateThreshold(horseId,thresholdId,user(jwt),request);
    }
    @DeleteMapping("/metric-thresholds/{thresholdId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    void endThreshold(@PathVariable UUID horseId,@PathVariable UUID thresholdId){service.endThreshold(horseId,thresholdId);}
    @PostMapping("/telemetry") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TelemetryResponse telemetry(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateTelemetryRequest request){
        return service.enter(horseId,UUID.fromString(jwt.getSubject()),request);
    }
    @GetMapping("/telemetry")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    List<TelemetryService.SessionView> history(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(required=false) String context,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return service.history(horseId,user(jwt),privileged(auth),context,from,to,page,size);
    }
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    List<TelemetryService.AlertView> alerts(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(required=false) String metric,@RequestParam(required=false) Boolean acknowledged,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return service.alerts(horseId,user(jwt),privileged(auth),metric,acknowledged,from,to,page,size);
    }
    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    TelemetryService.AlertView acknowledge(@PathVariable UUID horseId,@PathVariable UUID alertId,@AuthenticationPrincipal Jwt jwt){
        return service.acknowledge(horseId,alertId,user(jwt));
    }
    private UUID user(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private boolean privileged(Authentication auth){return auth.getAuthorities().stream().anyMatch(a->List.of("ROLE_CLUB_MANAGER","ROLE_HEAD_TRAINER","ROLE_VETERINARIAN").contains(a.getAuthority()));}
}
