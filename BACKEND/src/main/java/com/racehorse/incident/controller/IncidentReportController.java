package com.racehorse.incident.controller;

import com.racehorse.incident.dto.CreateIncidentReportRequest;
import com.racehorse.incident.dto.CreateTrainingEmergencyRequest;
import com.racehorse.incident.dto.IncidentReportResponse;
import com.racehorse.incident.dto.UpdateIncidentStatusRequest;
import com.racehorse.incident.service.IncidentReportService;
import com.racehorse.horse.service.OwnerHorseReadGuard;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class IncidentReportController {

    private final IncidentReportService service;
    private final OwnerHorseReadGuard ownerGuard;

    public IncidentReportController(IncidentReportService service, OwnerHorseReadGuard ownerGuard) {
        this.service = service;
        this.ownerGuard = ownerGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT')")
    public IncidentReportResponse createReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateIncidentReportRequest request) {
        return service.createReport(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/training-emergency")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HEAD_TRAINER') and hasAuthority('PERM_TRAINING_VIEW')")
    public IncidentReportResponse createTrainingEmergency(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTrainingEmergencyRequest request) {
        return service.createTrainingEmergency(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/training-emergency/sent")
    @PreAuthorize("hasRole('HEAD_TRAINER') and hasAuthority('PERM_TRAINING_VIEW')")
    public List<IncidentReportResponse> sentTrainingEmergencies(@AuthenticationPrincipal Jwt jwt) {
        return service.sentTrainingEmergencies(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/training-emergency/horses/{horseId}")
    @PreAuthorize("hasRole('HORSE_OWNER') and hasAuthority('PERM_PROFILE_VIEW')")
    public List<IncidentReportResponse> ownerTrainingEmergencies(@PathVariable UUID horseId, @AuthenticationPrincipal Jwt jwt) {
        return service.ownerTrainingEmergencies(horseId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/training-emergency/{id}")
    @PreAuthorize("hasAnyRole('HEAD_TRAINER','HORSE_OWNER')")
    public IncidentReportResponse trainingEmergency(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            org.springframework.security.core.Authentication authentication) {
        boolean owner = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_HORSE_OWNER"));
        return service.trainingEmergency(id, UUID.fromString(jwt.getSubject()), owner);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') or hasAuthority('PERM_MEDICAL_VIEW')")
    public List<IncidentReportResponse> listReports(
            @RequestParam(required = false) UUID horseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        ownerGuard.requireHorse(horseId, jwt, authentication);
        return service.listReports(horseId, status, severity, fromDate, toDate);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') or hasAuthority('PERM_MEDICAL_VIEW')")
    public IncidentReportResponse getReport(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        IncidentReportResponse report = service.getReport(id);
        ownerGuard.requireHorse(report.horseId(), jwt, authentication);
        return report;
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('CLUB_MANAGER') or (hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')) or (hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT'))")
    public IncidentReportResponse updateStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody UpdateIncidentStatusRequest request) {
        return service.updateStatus(id, UUID.fromString(jwt.getSubject()), request, managerOrVet(authentication));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('CLUB_MANAGER') or (hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')) or (hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT'))")
    public IncidentReportResponse acknowledge(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String notes = body != null ? body.getOrDefault("notes", "") : "";
        return service.updateStatus(id, UUID.fromString(jwt.getSubject()),
                new UpdateIncidentStatusRequest("ACKNOWLEDGED", notes), managerOrVet(authentication));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('CLUB_MANAGER') or (hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')) or (hasRole('STABLE_STAFF') and hasAuthority('PERM_STABLE_CARE_EDIT'))")
    public IncidentReportResponse dismiss(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String notes = body != null ? body.getOrDefault("notes", "") : "";
        return service.updateStatus(id, UUID.fromString(jwt.getSubject()),
                new UpdateIncidentStatusRequest("DISMISSED", notes), managerOrVet(authentication));
    }

    private boolean managerOrVet(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_CLUB_MANAGER".equals(authority.getAuthority())
                        || "ROLE_VETERINARIAN".equals(authority.getAuthority()));
    }
}
