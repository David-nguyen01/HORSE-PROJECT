package com.racehorse.wallet.controller;

import com.racehorse.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller quản lý ví và thanh toán phí dịch vụ chăm sóc ngựa.
 * <p>
 * Luồng thanh toán:
 * 1. Owner nạp tiền vào ví (POST /api/wallet/deposit)
 * 2. Owner trả phí dịch vụ cho ngựa (POST /api/wallet/pay-service/{horseId})
 * 3. Hệ thống tự động cập nhật paymentStatus=PAID, paidUntil=+30 ngày
 * 4. Ngựa chuyển sang PENDING_MEDICAL
 */
@RestController
@RequestMapping("/api/wallet")
@PreAuthorize("hasRole('HORSE_OWNER')")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * Xem số dư ví hiện tại của owner.
     */
    @GetMapping("/balance")
    WalletService.BalanceView balance(@AuthenticationPrincipal Jwt jwt) {
        return walletService.getBalance(userId(jwt));
    }

    /**
     * Nạp tiền vào ví (giả lập - thực tế sẽ tích hợp PayOS webhook).
     * Tạm thời nạp trực tiếp (không qua cổng thanh toán).
     */
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.OK)
    WalletService.TransactionView deposit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DepositRequest request) {
        return walletService.deposit(userId(jwt), request.amount(), request.description());
    }

    /**
     * Thanh toán phí dịch vụ chăm sóc cho ngựa.
     * Trừ tiền từ ví, cập nhật paymentStatus → PAID, paidUntil += 30 ngày.
     * Nếu ngựa đang PENDING_PAYMENT → chuyển sang PENDING_MEDICAL.
     */
    @PostMapping("/pay-service/{horseId}")
    WalletService.PaymentResultView payService(
            @PathVariable UUID horseId,
            @AuthenticationPrincipal Jwt jwt) {
        return walletService.payServiceFee(userId(jwt), horseId);
    }

    /**
     * Xem lịch sử giao dịch ví của owner.
     */
    @GetMapping("/transactions")
    List<WalletService.TransactionView> transactions(@AuthenticationPrincipal Jwt jwt) {
        return walletService.getTransactions(userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    // ─── DTO nội bộ ─────────────────────────────────────────────

    public record DepositRequest(
            @NotNull @DecimalMin("1000") BigDecimal amount,
            String description
    ) {
    }
}
