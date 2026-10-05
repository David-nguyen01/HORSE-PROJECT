package com.racehorse.finance.controller;

import com.racehorse.finance.service.FinanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/financial-transactions")
@PreAuthorize("hasAuthority('PERM_REPORTING_VIEW')")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_REPORTING_EDIT')")
    FinanceService.TransactionView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransactionRequest request) {
        return service.create(user(jwt), request.horseId(), request.stableId(), request.transactionDate(),
                request.transactionType(), request.category(), request.amount(), request.description(),
                request.sourceEntityType(), request.sourceEntityId());
    }

    @GetMapping
    List<FinanceService.TransactionView> list(@AuthenticationPrincipal Jwt jwt, Authentication auth,
            @RequestParam(required=false) UUID horseId, @RequestParam(required=false) UUID stableId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String type, @RequestParam(required=false) String category,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return service.list(user(jwt), manager(auth), horseId, stableId, from, to, type, category, page, size);
    }

    @GetMapping("/count")
    public java.util.Map<String, Long> count(@AuthenticationPrincipal Jwt jwt, Authentication auth,
            @RequestParam(required=false) UUID horseId, @RequestParam(required=false) UUID stableId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String type, @RequestParam(required=false) String category) {
        long total = service.count(user(jwt), manager(auth), horseId, stableId, from, to, type, category);
        return java.util.Map.of("count", total);
    }

    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public org.springframework.http.ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, Authentication auth,
            @RequestParam(required=false) UUID horseId, @RequestParam(required=false) UUID stableId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String type, @RequestParam(required=false) String category) {
        byte[] csv = service.exportCsv(user(jwt), manager(auth), horseId, stableId, from, to, type, category);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=financial-transactions-" + LocalDate.now() + ".csv")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/summary")
    FinanceService.SummaryView summary(@AuthenticationPrincipal Jwt jwt, Authentication auth, @RequestParam(required=false) UUID horseId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(user(jwt), manager(auth), horseId, from, to);
    }

    @GetMapping("/{id}")
    FinanceService.TransactionView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return service.get(id, user(jwt), manager(auth));
    }

    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private boolean manager(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_CLUB_MANAGER")); }

    public record TransactionRequest(UUID horseId, UUID stableId, @NotNull LocalDate transactionDate,
             @NotBlank String transactionType, @NotBlank String category,
             @NotNull @DecimalMin("0.00") BigDecimal amount, @NotBlank @Size(max=2000) String description,
             @Size(max=80) String sourceEntityType, UUID sourceEntityId) {}
}
