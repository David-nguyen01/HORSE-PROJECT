package com.racehorse.wallet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService service;

    public WalletController(WalletService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.WalletView wallet(@AuthenticationPrincipal Jwt jwt) {
        return service.wallet(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/top-ups")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.OrderView topUp(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody TopUpRequest request,
                                         HttpServletRequest servletRequest) {
        return service.createTopUp(UUID.fromString(jwt.getSubject()), request.amountVnd(),
                servletRequest.getRemoteAddr());
    }

    @GetMapping("/top-ups/{orderId}")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.OrderView topUpStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return service.order(UUID.fromString(jwt.getSubject()), orderId);
    }

    @PostMapping("/plans/quote")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.PlanQuote quote(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody PlanRequest request) {
        return service.quote(UUID.fromString(jwt.getSubject()), request.horseId(), request.plan());
    }

    @PostMapping("/plans")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.WalletView purchase(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody PlanRequest request) {
        return service.purchase(UUID.fromString(jwt.getSubject()), request.horseId(), request.plan(),
                request.acceptedTerms());
    }

    @PutMapping("/horses/{horseId}/next-plan")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.WalletView scheduleNextPlan(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID horseId, @Valid @RequestBody NextPlanRequest request) {
        return service.scheduleNextPlan(UUID.fromString(jwt.getSubject()), horseId, request.plan(),
                request.acceptedTerms());
    }

    @PostMapping("/horses/{horseId}/renew")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.WalletView renewHorse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID horseId) {
        return service.renewHorseNow(UUID.fromString(jwt.getSubject()), horseId);
    }

    @PostMapping("/top-ups/{orderId}/fake-complete")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fakeComplete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        service.completeFake(UUID.fromString(jwt.getSubject()), orderId);
    }

    @PostMapping("/payos/webhook")
    public Map<String, String> webhook(@RequestBody JsonNode body) {
        service.acceptWebhook(body);
        return Map.of("message", "OK");
    }

    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> parameters) {
        return service.acceptVnpayIpn(parameters);
    }

    public record TopUpRequest(@NotNull @Min(100_000) Long amountVnd) { }
    public record PlanRequest(@NotNull UUID horseId, @NotNull WalletPlan plan, boolean acceptedTerms) { }
    public record NextPlanRequest(@NotNull WalletPlan plan, boolean acceptedTerms) { }
}
