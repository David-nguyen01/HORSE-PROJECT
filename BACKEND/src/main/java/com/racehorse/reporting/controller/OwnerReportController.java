package com.racehorse.reporting.controller;

import com.racehorse.reporting.dto.OwnerReportResponse;
import com.racehorse.reporting.service.ReportingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class OwnerReportController {

    private final ReportingService reportingService;

    public OwnerReportController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    // Owner Endpoints
    @GetMapping("/api/owner-reports")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public Page<OwnerReportResponse> getOwnReports(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable) {
        return reportingService.getOwnerReports(UUID.fromString(jwt.getSubject()), startDate, endDate, pageable);
    }

    @GetMapping("/api/owner-reports/{id}")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public OwnerReportResponse getOwnReportDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return reportingService.getOwnerReportDetail(id, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/owner-reports/{id}/export")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ResponseEntity<byte[]> exportOwnReport(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String html = reportingService.exportOwnerReportHtml(id, UUID.fromString(jwt.getSubject()));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentDispositionFormData("attachment", "owner_report_" + id + ".html");
        
        return new ResponseEntity<>(html.getBytes(), headers, HttpStatus.OK);
    }

    // Admin Endpoints
    @GetMapping("/api/admin/owner-reports")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public Page<OwnerReportResponse> getAllReports(
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable) {
        return reportingService.getOwnerReports(ownerId, startDate, endDate, pageable);
    }
    
    @GetMapping("/api/admin/owner-reports/{id}")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    public OwnerReportResponse getReportDetailAdmin(@PathVariable UUID id) {
        return reportingService.getOwnerReportDetail(id, null);
    }
}
