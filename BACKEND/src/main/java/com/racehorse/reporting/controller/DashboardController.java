package com.racehorse.reporting.controller;

import com.racehorse.reporting.dto.DashboardDrilldownResponse;
import com.racehorse.reporting.dto.DashboardResponse;
import com.racehorse.reporting.service.DashboardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID stableId) {
        return dashboardService.getDashboard(startDate, endDate, stableId);
    }

    @GetMapping("/drilldown")
    public DashboardDrilldownResponse getDrilldown(
            @RequestParam String section,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID stableId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DashboardDrilldownResponse.DrilldownRow> rows =
                dashboardService.getDrilldown(section, startDate, endDate, stableId, pageable);
        return new DashboardDrilldownResponse(section.toUpperCase(), rows);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID stableId) {

        String html = dashboardService.exportDashboardHtml(startDate, endDate, stableId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentDispositionFormData("attachment", "dashboard_export.html");

        return new ResponseEntity<>(html.getBytes(), headers, HttpStatus.OK);
    }
}
