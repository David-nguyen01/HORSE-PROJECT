package com.racehorse.reporting.controller;

import com.racehorse.reporting.service.OwnerReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner-reports")
@PreAuthorize("hasAuthority('PERM_REPORTING_VIEW')")
public class OwnerReportController {
    private final OwnerReportService service;
    public OwnerReportController(OwnerReportService service){this.service=service;}

    @GetMapping("/owners")
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_REPORTING_VIEW')")
    public List<OwnerReportService.OwnerOption> owners() {
        return service.getOwners();
    }

    @GetMapping
    List<OwnerReportService.ReportSummary> list(@AuthenticationPrincipal Jwt jwt,Authentication auth,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        return service.list(user(jwt),manager(auth),from,to);
    }
    @GetMapping("/{id}")
    OwnerReportService.ReportView get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,Authentication auth){return service.get(id,user(jwt),manager(auth));}

    @GetMapping(value="/{id}/download",produces=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> download(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,Authentication auth){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=owner-report-"+id+".json")
                .contentType(MediaType.APPLICATION_JSON).body(service.jsonDownload(id,user(jwt),manager(auth)));
    }

    @GetMapping(value="/{id}/export-csv",produces="text/csv; charset=UTF-8")
    ResponseEntity<byte[]> exportCsv(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,Authentication auth){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=owner-report-"+id+".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8")).body(service.csvDownload(id,user(jwt),manager(auth)));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_REPORTING_EDIT')")
    OwnerReportService.ReportView generate(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody GenerateRequest r){
        return service.generate(r.ownerId(),r.periodStart(),r.periodEnd(),user(jwt));
    }
    private UUID user(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private boolean manager(Authentication auth){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CLUB_MANAGER"));}
    public record GenerateRequest(@NotNull UUID ownerId,@NotNull LocalDate periodStart,@NotNull LocalDate periodEnd){}
}
