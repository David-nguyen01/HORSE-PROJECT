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
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') or hasRole('CLUB_MANAGER')")
    Map<String,UUID> threshold(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,@Valid @RequestBody CreateMetricThresholdRequest request){
        return Map.of("id",service.configureThreshold(horseId,user(jwt),manager(auth),trainer(auth),request));
    }
    @GetMapping("/metric-thresholds")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    List<TelemetryService.ThresholdView> thresholds(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(defaultValue="false") boolean includeExpired){
        return service.thresholds(horseId,user(jwt),manager(auth),trainer(auth),owner(auth),vet(auth),includeExpired);
    }
    @PutMapping("/metric-thresholds/{thresholdId}")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') or hasRole('CLUB_MANAGER')")
    TelemetryService.ThresholdView updateThreshold(@PathVariable UUID horseId,@PathVariable UUID thresholdId,
            @AuthenticationPrincipal Jwt jwt,Authentication auth,@Valid @RequestBody CreateMetricThresholdRequest request){
        return service.updateThreshold(horseId,thresholdId,user(jwt),manager(auth),trainer(auth),request);
    }
    @DeleteMapping("/metric-thresholds/{thresholdId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') or hasRole('CLUB_MANAGER')")
    void endThreshold(@PathVariable UUID horseId,@PathVariable UUID thresholdId,@AuthenticationPrincipal Jwt jwt,Authentication auth){
        service.endThreshold(horseId,thresholdId,user(jwt),manager(auth),trainer(auth));
    }
    @PostMapping("/telemetry") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') or hasRole('CLUB_MANAGER')")
    TelemetryResponse telemetry(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,@Valid @RequestBody CreateTelemetryRequest request){
        return service.enter(horseId,user(jwt),manager(auth),trainer(auth),request);
    }
    @GetMapping("/telemetry")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    List<TelemetryService.SessionView> history(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(required=false) String context,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return service.history(horseId,user(jwt),manager(auth),trainer(auth),owner(auth),vet(auth),context,from,to,page,size);
    }
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")
    List<TelemetryService.AlertView> alerts(@PathVariable UUID horseId,@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(required=false) String metric,@RequestParam(required=false) Boolean acknowledged,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return service.alerts(horseId,user(jwt),manager(auth),trainer(auth),owner(auth),vet(auth),metric,acknowledged,from,to,page,size);
    }
    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') or hasRole('CLUB_MANAGER')")
    TelemetryService.AlertView acknowledge(@PathVariable UUID horseId,@PathVariable UUID alertId,@AuthenticationPrincipal Jwt jwt,Authentication auth){
        return service.acknowledge(horseId,alertId,user(jwt),manager(auth),trainer(auth));
    }
    private UUID user(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private boolean manager(Authentication auth){return hasRole(auth,"CLUB_MANAGER");}
    private boolean trainer(Authentication auth){return hasRole(auth,"HEAD_TRAINER");}
    private boolean owner(Authentication auth){return hasRole(auth,"HORSE_OWNER");}
    private boolean vet(Authentication auth){return hasRole(auth,"VETERINARIAN");}
    private boolean hasRole(Authentication auth,String role){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));}
}
