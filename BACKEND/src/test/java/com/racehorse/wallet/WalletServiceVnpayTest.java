package com.racehorse.wallet;

import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceVnpayTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private PayosGateway gateway;

    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private WalletService service;

    @BeforeEach
    void setUp() {
        service = new WalletService(jdbc, json, gateway, clock, "Asia/Ho_Chi_Minh");
    }

    @Test
    void rejectsVnpayIpnWhenGatewayNotVnpay() {
        when(gateway.isVnpay()).thenReturn(false);

        Map<String, String> result = service.acceptVnpayIpn(Map.of("vnp_TxnRef", "123"));
        assertEquals("97", result.get("RspCode"));
        assertEquals("Invalid signature", result.get("Message"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsVnpayIpnWhenSignatureInvalid() {
        when(gateway.isVnpay()).thenReturn(true);
        when(gateway.verifyVnpay(any())).thenReturn(false);

        Map<String, String> result = service.acceptVnpayIpn(Map.of("vnp_TxnRef", "123", "vnp_SecureHash", "invalid"));
        assertEquals("97", result.get("RspCode"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsVnpayIpnWhenOrderNotFound() {
        when(gateway.isVnpay()).thenReturn(true);
        when(gateway.verifyVnpay(any())).thenReturn(true);
        when(jdbc.queryForList(anyString(), eq(999999L))).thenReturn(List.of());

        Map<String, String> params = Map.of(
                "vnp_TxnRef", "999999",
                "vnp_Amount", "10000000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00"
        );

        Map<String, String> result = service.acceptVnpayIpn(params);
        assertEquals("01", result.get("RspCode"));
        assertEquals("Order not found", result.get("Message"));
    }

    @Test
    void handlesDuplicateIpnIdempotentlyWhenOrderAlreadyPaid() {
        when(gateway.isVnpay()).thenReturn(true);
        when(gateway.verifyVnpay(any())).thenReturn(true);

        UUID orderId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(123456L))).thenReturn(List.of(
                Map.of("id", orderId, "status", "PAID", "payos_expected_amount_vnd", BigDecimal.valueOf(100_000))
        ));

        Map<String, String> params = Map.of(
                "vnp_TxnRef", "123456",
                "vnp_Amount", "10000000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00"
        );

        Map<String, String> result = service.acceptVnpayIpn(params);
        assertEquals("02", result.get("RspCode"));
        assertEquals("Order already confirmed", result.get("Message"));
        // Verify no wallet balance update occurred
        verify(jdbc, never()).update(contains("wallet_balance_vnd=wallet_balance_vnd+"), any(), any());
    }

    @Test
    void rejectsVnpayIpnWhenAmountMismatchesExpected() {
        when(gateway.isVnpay()).thenReturn(true);
        when(gateway.verifyVnpay(any())).thenReturn(true);

        UUID orderId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(123456L))).thenReturn(List.of(
                Map.of("id", orderId, "status", "PENDING", "payos_expected_amount_vnd", BigDecimal.valueOf(500_000))
        ));

        Map<String, String> params = Map.of(
                "vnp_TxnRef", "123456",
                "vnp_Amount", "10000000", // 100,000 VND instead of 500,000 VND
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00"
        );

        Map<String, String> result = service.acceptVnpayIpn(params);
        assertEquals("04", result.get("RspCode"));
        assertEquals("Invalid amount", result.get("Message"));
        verify(jdbc, never()).update(contains("wallet_balance_vnd=wallet_balance_vnd+"), any(), any());
    }

    @Test
    void recordsFailedPaymentWhenVnpayResponseNot00() {
        when(gateway.isVnpay()).thenReturn(true);
        when(gateway.verifyVnpay(any())).thenReturn(true);

        UUID orderId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(123456L))).thenReturn(List.of(
                Map.of("id", orderId, "status", "PENDING", "payos_expected_amount_vnd", BigDecimal.valueOf(100_000))
        ));

        Map<String, String> params = Map.of(
                "vnp_TxnRef", "123456",
                "vnp_Amount", "10000000",
                "vnp_ResponseCode", "24", // Customer canceled transaction
                "vnp_TransactionStatus", "02"
        );

        Map<String, String> result = service.acceptVnpayIpn(params);
        assertEquals("00", result.get("RspCode"));
        verify(jdbc).update(contains("set status='FAILED'"), eq(orderId));
        verify(jdbc, never()).update(contains("wallet_balance_vnd=wallet_balance_vnd+"), any(), any());
    }

    @Test
    void creditsWalletExactlyOnceOnSuccessfulVnpayIpn() {
        when(gateway.isVnpay()).thenReturn(true);
        when(gateway.verifyVnpay(any())).thenReturn(true);

        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jdbc.queryForList(anyString(), eq(123456L))).thenReturn(List.of(
                Map.of("id", orderId, "status", "PENDING", "payos_expected_amount_vnd", BigDecimal.valueOf(100_000))
        ));

        // Mock orderRow for complete()
        when(jdbc.query(anyString(), any(RowMapper.class), eq(orderId))).thenReturn(List.of(
                Map.of(
                        "id", orderId,
                        "user_id", userId,
                        "status", "PENDING",
                        "payos_expected_amount_vnd", BigDecimal.valueOf(100_000),
                        "payos_expires_at", Instant.parse("2026-11-11T12:30:00Z"),
                        "data_json", "{}"
                )
        ));

        // Mock owner() row
        when(jdbc.queryForList(contains("from users"), eq(userId))).thenReturn(List.of(
                Map.of("id", userId, "wallet_balance_vnd", BigDecimal.valueOf(200_000))
        ));

        Map<String, String> params = Map.of(
                "vnp_TxnRef", "123456",
                "vnp_Amount", "10000000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00"
        );

        Map<String, String> result = service.acceptVnpayIpn(params);
        assertEquals("00", result.get("RspCode"));
        assertEquals("Confirm Success", result.get("Message"));

        // Verify credit wallet
        verify(jdbc).update(contains("update users set wallet_balance_vnd=wallet_balance_vnd+?"),
                eq(BigDecimal.valueOf(100_000)), eq(userId));
        // Verify ledger recorded
        verify(jdbc).update(contains("insert into financial_transactions"),
                any(), any(), eq("REVENUE"), eq("WALLET_TOPUP"), eq(BigDecimal.valueOf(100_000)),
                eq("Nạp ví qua VNPAY"), eq(orderId), eq(userId), eq(userId));
        // Verify order status set to PAID
        verify(jdbc).update(contains("update module_records set status='PAID'"), eq(orderId));
    }

    @Test
    void createTopUpFailsWhenGatewayNotVnpay() {
        when(gateway.isVnpay()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createTopUp(UUID.randomUUID(), 100_000L, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("VNPAY"));
    }
}
