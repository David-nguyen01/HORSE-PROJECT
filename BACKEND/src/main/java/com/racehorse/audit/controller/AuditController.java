package com.racehorse.audit.controller;

import com.racehorse.audit.dto.AuditLogResponse;
import com.racehorse.audit.dto.AuditSettingsResponse;
import com.racehorse.audit.dto.UpdateAuditSettingsRequest;
import com.racehorse.audit.service.AuditQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/audit-logs")
    public Page<AuditLogResponse> searchLogs(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) String query,
            Pageable pageable) {
        return auditQueryService.searchLogs(moduleCode, actionCode, startDate, endDate, query, pageable);
    }

    @GetMapping("/audit-settings")
    public AuditSettingsResponse getSettings() {
        return auditQueryService.getSettings();
    }

    @PutMapping("/audit-settings")
    public AuditSettingsResponse updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAuditSettingsRequest request) {
        return auditQueryService.updateSettings(UUID.fromString(jwt.getSubject()), request);
    }
}
