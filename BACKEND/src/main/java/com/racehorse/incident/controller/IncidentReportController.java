package com.racehorse.incident.controller;

import com.racehorse.incident.dto.CreateIncidentReportRequest;
import com.racehorse.incident.dto.IncidentReportResponse;
import com.racehorse.incident.dto.UpdateIncidentStatusRequest;
import com.racehorse.incident.service.IncidentReportService;
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
@RequestMapping("/api/incidents")
public class IncidentReportController {

    private final IncidentReportService service;

    public IncidentReportController(IncidentReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT')")
    public IncidentReportResponse createReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateIncidentReportRequest request) {
        return service.createReport(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') or hasAuthority('PERM_MEDICAL_VIEW')")
    public List<IncidentReportResponse> listReports(
            @RequestParam(required = false) UUID horseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.listReports(horseId, status, severity, fromDate, toDate);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_VIEW') or hasAuthority('PERM_MEDICAL_VIEW')")
    public IncidentReportResponse getReport(@PathVariable UUID id) {
        return service.getReport(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_STABLE_CARE_EDIT') or hasAuthority('PERM_MEDICAL_EDIT')")
    public IncidentReportResponse updateStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateIncidentStatusRequest request) {
        return service.updateStatus(id, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') or hasAuthority('PERM_STABLE_CARE_EDIT')")
    public IncidentReportResponse acknowledge(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String notes = body != null ? body.getOrDefault("notes", "") : "";
        return service.updateStatus(id, UUID.fromString(jwt.getSubject()),
                new UpdateIncidentStatusRequest("ACKNOWLEDGED", notes));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') or hasAuthority('PERM_STABLE_CARE_EDIT')")
    public IncidentReportResponse dismiss(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String notes = body != null ? body.getOrDefault("notes", "") : "";
        return service.updateStatus(id, UUID.fromString(jwt.getSubject()),
                new UpdateIncidentStatusRequest("DISMISSED", notes));
    }
}
