package com.racehorse.wallet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.List;
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

    @GetMapping("/history")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public List<WalletService.PaymentHistoryItem> paymentHistory(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int offset) {
        if (offset < 0) throw new com.racehorse.common.exception.InvalidInputException("Vị trí lịch sử không hợp lệ");
        return service.paymentHistory(UUID.fromString(jwt.getSubject()), offset, 20);
    }

    @PostMapping("/top-ups")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public WalletService.OrderView topUp(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody TopUpRequest request,
                                         HttpServletRequest servletRequest) {
        return service.createTopUp(UUID.fromString(jwt.getSubject()), request.amountVnd(),
                clientIp(servletRequest));
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

    @PostMapping("/payos/webhook")
    public Map<String, String> webhook(@RequestBody JsonNode body) {
        service.acceptWebhook(body);
        return Map.of("message", "OK");
    }

    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> parameters) {
        return service.acceptVnpayIpn(parameters);
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<String> vnpayReturn(@RequestParam Map<String, String> parameters) {
        Map<String, String> result = service.acceptVnpayIpn(parameters);
        boolean paid = "00".equals(parameters.get("vnp_ResponseCode"))
                && ("00".equals(result.get("RspCode")) || "02".equals(result.get("RspCode")));
        String title = paid ? "Thanh toán Sandbox thành công" : "Thanh toán Sandbox chưa hoàn tất";
        String detail = paid ? "Ví đã được cập nhật. Bạn có thể quay lại ứng dụng để xem số dư."
                : "Giao dịch chưa được xác nhận. Vui lòng quay lại ứng dụng và kiểm tra đơn thanh toán.";
        String html = "<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>VNPAY Sandbox</title></head><body style=\"font-family:system-ui;max-width:480px;"
                + "margin:12vh auto;padding:24px;color:#173d32\"><h1>" + title + "</h1><p>" + detail
                + "</p><small>Đây là giao dịch thử nghiệm trên VNPAY Sandbox.</small></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            String ip = header.split(",")[0].trim();
            if (!ip.isBlank()) return normalizeIp(ip);
        }
        return normalizeIp(request.getRemoteAddr());
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank() || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    public record TopUpRequest(@NotNull @Min(100_000) Long amountVnd) { }
    public record PlanRequest(@NotNull UUID horseId, @NotNull WalletPlan plan, boolean acceptedTerms) { }
    public record NextPlanRequest(@NotNull WalletPlan plan, boolean acceptedTerms) { }
}
