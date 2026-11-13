package com.racehorse.wallet;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.servicepackage.entity.ServicePackageEntity;
import com.racehorse.servicepackage.repository.ServicePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletDynamicRenewalTest {

    private JdbcTemplate jdbc;
    private ObjectMapper json;
    private PayosGateway gateway;
    private Clock fixedClock;
    private ServicePackageRepository packageRepository;
    private WalletService service;

    private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID horseId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final LocalDate today = LocalDate.of(2026, 11, 12);

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        json = new ObjectMapper();
        gateway = mock(PayosGateway.class);
        packageRepository = mock(ServicePackageRepository.class);
        fixedClock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new WalletService(jdbc, json, gateway, fixedClock, "Asia/Ho_Chi_Minh", packageRepository);

        when(gateway.isVnpay()).thenReturn(true);
    }

    private ServicePackageEntity createPackage(String code, String name, BigDecimal price, int days, boolean active, long version) {
        ServicePackageEntity entity = new ServicePackageEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription("Description for " + code);
        entity.setPriceVnd(price);
        entity.setDurationDays(days);
        entity.setActive(active);
        entity.setDisplayOrder(10);
        entity.setVersion(version);
        return entity;
    }

    private void mockOwnerWallet(BigDecimal balance) {
        Map<String, Object> userRow = new HashMap<>();
        userRow.put("id", ownerId);
        userRow.put("wallet_balance_vnd", balance);
        userRow.put("active_service_plan", null);
        userRow.put("service_plan_started_on", null);
        userRow.put("service_next_due_date", null);
        when(jdbc.queryForList(contains("from users"), eq(ownerId))).thenReturn(List.of(userRow));
    }

    private void mockHorseRow(String activePlan, String nextPlan, LocalDate dueDate, LocalDate paidUntil) {
        Map<String, Object> horseRow = new HashMap<>();
        horseRow.put("id", horseId);
        horseRow.put("active_service_plan", activePlan);
        horseRow.put("next_service_plan", nextPlan);
        horseRow.put("service_next_due_date", dueDate != null ? java.sql.Date.valueOf(dueDate) : null);
        horseRow.put("paid_until", paidUntil != null ? java.sql.Date.valueOf(paidUntil) : null);
        when(jdbc.queryForList(contains("from horses"), eq(horseId), eq(ownerId))).thenReturn(List.of(horseRow));

        // Also mock horses() read query for renewIfDue loop
        doAnswer(invocation -> {
            RowMapper<WalletService.HorsePlanView> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(horseId);
            when(rs.getString("code")).thenReturn("H-001");
            when(rs.getString("name")).thenReturn("Chiến Mã");
            when(rs.getString("active_service_plan")).thenReturn(activePlan);
            when(rs.getString("next_service_plan")).thenReturn(nextPlan);
            when(rs.getDate("service_plan_started_on")).thenReturn(java.sql.Date.valueOf(today.minusDays(30)));
            when(rs.getDate("service_next_due_date")).thenReturn(dueDate != null ? java.sql.Date.valueOf(dueDate) : null);
            when(rs.getDate("paid_until")).thenReturn(paidUntil != null ? java.sql.Date.valueOf(paidUntil) : null);
            when(rs.getString("payment_status")).thenReturn("PAID");
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(contains("from horses h join horse_ownerships"), any(RowMapper.class), eq(ownerId));
    }

    // 1. Existing P30/P90/P365 automatic renewal compatibility
    @Test
    @DisplayName("1. Existing legacy P30 automatic renewal succeeds via catalog resolution")
    void autoRenewal_legacyP30_success() {
        ServicePackageEntity p30 = createPackage("P30", "Gói 1 tháng", BigDecimal.valueOf(5_000_000), 30, true, 1L);
        when(packageRepository.findByCode("P30")).thenReturn(Optional.of(p30));
        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorseRow("P30", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(5_000_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("P30"), eq(today), eq(today.plusDays(30)), eq(today.plusDays(29)), eq(horseId));
    }

    // 2. Dynamic package renewal with a non-enum package code
    @Test
    @DisplayName("2. Dynamic package renewal with non-enum code (e.g. VIP_30) succeeds")
    void autoRenewal_dynamicNonEnumCode_success() {
        ServicePackageEntity vip = createPackage("VIP_30", "Gói VIP 30 ngày", BigDecimal.valueOf(8_500_000), 30, true, 2L);
        when(packageRepository.findByCode("VIP_30")).thenReturn(Optional.of(vip));
        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorseRow("VIP_30", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(1_500_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("VIP_30"), eq(today), eq(today.plusDays(30)), eq(today.plusDays(29)), eq(horseId));
    }

    // 3. Current catalog price and duration are used at renewal execution (Policy A)
    @Test
    @DisplayName("3. Policy A: Current catalog price and duration are resolved at renewal execution time")
    void autoRenewal_policyA_usesLatestCatalogPriceAndDuration() {
        // Price was updated to 7,500,000 VND and duration to 35 days in catalog
        ServicePackageEntity updated = createPackage("P30", "Gói 1 tháng Cập nhật", BigDecimal.valueOf(7_500_000), 35, true, 3L);
        when(packageRepository.findByCode("P30")).thenReturn(Optional.of(updated));
        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorseRow("P30", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        // Deducts latest 7,500,000 VND (leaving 2,500,000 VND), advances next due date by 35 days
        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(2_500_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("P30"), eq(today), eq(today.plusDays(35)), eq(today.plusDays(34)), eq(horseId));
    }

    // 4. Scheduled Active package takes precedence over current package
    @Test
    @DisplayName("4. Scheduled next_service_plan takes precedence over active_service_plan on renewal")
    void autoRenewal_scheduledPlanTakesPrecedence() {
        ServicePackageEntity currentPkg = createPackage("P30", "Gói cũ", BigDecimal.valueOf(5_000_000), 30, true, 1L);
        ServicePackageEntity scheduledPkg = createPackage("VIP_90", "Gói VIP mới", BigDecimal.valueOf(20_000_000), 90, true, 1L);
        when(packageRepository.findByCode("P30")).thenReturn(Optional.of(currentPkg));
        when(packageRepository.findByCode("VIP_90")).thenReturn(Optional.of(scheduledPkg));

        mockOwnerWallet(BigDecimal.valueOf(25_000_000));
        mockHorseRow("P30", "VIP_90", today, today.minusDays(1));

        service.renewOne(ownerId);

        // Deducts scheduled price 20,000,000 VND, sets active plan to VIP_90, clears next_service_plan to null
        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(5_000_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("VIP_90"), eq(today), eq(today.plusDays(90)), eq(today.plusDays(89)), eq(horseId));
    }

    // 5. Current Reserve package with scheduled Active package renews successfully
    @Test
    @DisplayName("5. Current Reserve package with scheduled Active package renews successfully into the Active package")
    void autoRenewal_reserveCurrentWithActiveScheduled_renewsSuccessfully() {
        ServicePackageEntity reserveCurrent = createPackage("OLD_TIER", "Gói cũ dự phòng", BigDecimal.valueOf(5_000_000), 30, false, 2L);
        ServicePackageEntity activeNext = createPackage("ACTIVE_NEW", "Gói mới đang hoạt động", BigDecimal.valueOf(12_000_000), 60, true, 1L);
        when(packageRepository.findByCode("OLD_TIER")).thenReturn(Optional.of(reserveCurrent));
        when(packageRepository.findByCode("ACTIVE_NEW")).thenReturn(Optional.of(activeNext));

        mockOwnerWallet(BigDecimal.valueOf(15_000_000));
        mockHorseRow("OLD_TIER", "ACTIVE_NEW", today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(3_000_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("ACTIVE_NEW"), eq(today), eq(today.plusDays(60)), eq(today.plusDays(59)), eq(horseId));
    }

    // 6. Current Active package with scheduled Reserve package does not renew
    @Test
    @DisplayName("6. Current Active package with scheduled Reserve package does not renew and causes no deduction")
    void autoRenewal_activeCurrentWithReserveScheduled_doesNotRenew() {
        ServicePackageEntity activeCurrent = createPackage("ACTIVE_P30", "Gói hiện tại", BigDecimal.valueOf(5_000_000), 30, true, 1L);
        ServicePackageEntity reserveNext = createPackage("RESERVE_NEXT", "Gói lịch bị hủy", BigDecimal.valueOf(10_000_000), 60, false, 2L);
        when(packageRepository.findByCode("ACTIVE_P30")).thenReturn(Optional.of(activeCurrent));
        when(packageRepository.findByCode("RESERVE_NEXT")).thenReturn(Optional.of(reserveNext));

        mockOwnerWallet(BigDecimal.valueOf(15_000_000));
        mockHorseRow("ACTIVE_P30", "RESERVE_NEXT", today, today.minusDays(1));

        service.renewOne(ownerId);

        // Zero wallet deduction
        verify(jdbc, never()).update(contains("update users set wallet_balance_vnd=?"), any(), any());
        // Horse marked UNPAID
        verify(jdbc).update(contains("update horses set payment_status='UNPAID'"), eq(horseId));
    }

    // 7. Current package transferred to Reserve without a scheduled replacement does not renew
    @Test
    @DisplayName("7. Current package transferred to Reserve without scheduled replacement does not renew")
    void autoRenewal_currentInReserveNoScheduled_doesNotRenew() {
        ServicePackageEntity reservePkg = createPackage("RETIRED_PLAN", "Gói đã nghỉ hưu", BigDecimal.valueOf(5_000_000), 30, false, 1L);
        when(packageRepository.findByCode("RETIRED_PLAN")).thenReturn(Optional.of(reservePkg));

        mockOwnerWallet(BigDecimal.valueOf(20_000_000));
        mockHorseRow("RETIRED_PLAN", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc, never()).update(contains("update users set wallet_balance_vnd=?"), any(), any());
        verify(jdbc).update(contains("update horses set payment_status='UNPAID'"), eq(horseId));
    }

    // 8. Reserve package causes no wallet deduction
    @Test
    @DisplayName("8. Reserve package causes zero wallet deductions under all renewal triggers")
    void autoRenewal_reservePackage_zeroWalletDeduction() {
        ServicePackageEntity reservePkg = createPackage("RESERVE_P30", "Gói dự phòng", BigDecimal.valueOf(5_000_000), 30, false, 1L);
        when(packageRepository.findByCode("RESERVE_P30")).thenReturn(Optional.of(reservePkg));

        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorseRow("RESERVE_P30", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc, never()).update(contains("update users set wallet_balance_vnd"), any(), any());
    }

    // 9. No automatic fallback to another package
    @Test
    @DisplayName("9. No automatic fallback: system does not switch to a different package when target is Reserve")
    void autoRenewal_noAutomaticFallbackWhenReserve() {
        ServicePackageEntity reservePkg = createPackage("RETIRED_30", "Gói nghỉ hưu", BigDecimal.valueOf(5_000_000), 30, false, 1L);
        ServicePackageEntity defaultP30 = createPackage("P30", "Gói mặc định", BigDecimal.valueOf(5_000_000), 30, true, 1L);
        when(packageRepository.findByCode("RETIRED_30")).thenReturn(Optional.of(reservePkg));
        when(packageRepository.findByCode("P30")).thenReturn(Optional.of(defaultP30));

        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorseRow("RETIRED_30", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        // Never updates active_service_plan to P30 or any other package
        verify(jdbc, never()).update(contains("update horses set active_service_plan=?"), eq("P30"), any(), any(), any(), any());
        verify(jdbc).update(contains("update horses set payment_status='UNPAID'"), eq(horseId));
    }

    // 10. Package reactivated before renewal becomes eligible
    @Test
    @DisplayName("10. Package reactivated before renewal execution becomes eligible and renews normally")
    void autoRenewal_packageReactivatedBeforeRenewal_renewsNormally() {
        // Was reserve, now active = true
        ServicePackageEntity reactivated = createPackage("SEASONAL_90", "Gói Mùa Giải", BigDecimal.valueOf(14_000_000), 90, true, 4L);
        when(packageRepository.findByCode("SEASONAL_90")).thenReturn(Optional.of(reactivated));

        mockOwnerWallet(BigDecimal.valueOf(20_000_000));
        mockHorseRow("SEASONAL_90", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(6_000_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("SEASONAL_90"), eq(today), eq(today.plusDays(90)), eq(today.plusDays(89)), eq(horseId));
    }

    // 11. Missing package reference does not cause an enum conversion crash or arbitrary charge
    @Test
    @DisplayName("11. Missing package reference does not crash with IllegalArgumentException and does not charge wallet")
    void autoRenewal_missingPackageReference_safeHandling() {
        when(packageRepository.findByCode("CORRUPT_CODE")).thenReturn(Optional.empty());

        mockOwnerWallet(BigDecimal.valueOf(20_000_000));
        mockHorseRow("CORRUPT_CODE", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc, never()).update(contains("update users set wallet_balance_vnd"), any(), any());
        verify(jdbc).update(contains("update horses set payment_status='UNPAID'"), eq(horseId));
    }

    // 12. Existing late-fee boundaries remain unchanged
    @Test
    @DisplayName("12. Late fee policy boundaries: 0-1 days (0%), 2-3 days (10%), 4+ days (20%)")
    void lateFee_boundariesAndCalculations() {
        BigDecimal base = BigDecimal.valueOf(10_000_000);
        assertThat(WalletService.lateFee(base, 0)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(WalletService.lateFee(base, 1)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(WalletService.lateFee(base, 2)).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(WalletService.lateFee(base, 3)).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        assertThat(WalletService.lateFee(base, 4)).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
        assertThat(WalletService.lateFee(base, 10)).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));

        // Test applied during renewal execution 2 days late: base 10M + late 1M = 11M deducted
        ServicePackageEntity pkg = createPackage("LATE_PLAN", "Gói trễ", base, 30, true, 1L);
        when(packageRepository.findByCode("LATE_PLAN")).thenReturn(Optional.of(pkg));
        mockOwnerWallet(BigDecimal.valueOf(15_000_000));
        LocalDate twoDaysOverdue = today.minusDays(2);
        mockHorseRow("LATE_PLAN", null, twoDaysOverdue, twoDaysOverdue.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(4_000_000)), eq(ownerId));
        // Verify late fee recorded in financial_transactions ledger
        verify(jdbc).update(contains("insert into financial_transactions"),
                any(UUID.class), eq(today), eq("EXPENSE"), eq("LATE_FEE"), eq(BigDecimal.valueOf(1_000_000)),
                contains("Phụ phí quá hạn"), any(UUID.class), eq(ownerId), eq(ownerId));
    }

    // 13. Manual renewal and scheduled automatic renewal use consistent package eligibility rules
    @Test
    @DisplayName("13. Manual renewal (renewHorseNow) rejects Reserve packages and premature renewal consistently")
    void manualRenewal_consistentEligibilityRules() {
        mockOwnerWallet(BigDecimal.valueOf(10_000_000));

        // A. Premature manual renewal before due date is rejected
        mockHorseRow("P30", null, today.plusDays(5), today.plusDays(4));
        assertThatThrownBy(() -> service.renewHorseNow(ownerId, horseId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngựa chưa đến kỳ gia hạn");

        // B. Manual renewal of Reserve package is rejected with explicit business explanation
        ServicePackageEntity reservePkg = createPackage("RESERVE_PLAN", "Gói Dự Phòng", BigDecimal.valueOf(5_000_000), 30, false, 1L);
        when(packageRepository.findByCode("RESERVE_PLAN")).thenReturn(Optional.of(reservePkg));
        mockHorseRow("RESERVE_PLAN", null, today, today.minusDays(1));

        assertThatThrownBy(() -> service.renewHorseNow(ownerId, horseId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Thư viện dự phòng và không thể gia hạn");
    }

    // 14. Successful renewal creates a complete immutable snapshot
    @Test
    @DisplayName("14. Successful renewal creates complete immutable snapshot in module_records")
    void autoRenewal_createsCompleteImmutableSnapshot() throws Exception {
        UUID pkgId = UUID.randomUUID();
        ServicePackageEntity pkg = new ServicePackageEntity();
        pkg.setId(pkgId);
        pkg.setCode("RENEW_VIP");
        pkg.setName("Gói VIP Gia Hạn");
        pkg.setPriceVnd(BigDecimal.valueOf(12_000_000));
        pkg.setDurationDays(30);
        pkg.setActive(true);
        pkg.setVersion(3L);

        when(packageRepository.findByCode("RENEW_VIP")).thenReturn(Optional.of(pkg));
        mockOwnerWallet(BigDecimal.valueOf(20_000_000));
        mockHorseRow("RENEW_VIP", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("insert into module_records"),
                any(UUID.class), eq("SERVICE_PLAN_RENEWAL"), eq(horseId), eq(ownerId),
                eq(today), jsonCaptor.capture());

        String jsonPayload = jsonCaptor.getValue();
        assertThat(jsonPayload).isNotBlank();

        JsonNode node = json.readTree(jsonPayload);
        assertThat(node.path("eventType").asText()).isEqualTo("SERVICE_PLAN_RENEWAL");
        assertThat(node.path("packageId").asText()).isEqualTo(pkgId.toString());
        assertThat(node.path("packageCode").asText()).isEqualTo("RENEW_VIP");
        assertThat(node.path("packageName").asText()).isEqualTo("Gói VIP Gia Hạn");
        assertThat(node.path("priceVnd").asLong()).isEqualTo(12_000_000L);
        assertThat(node.path("durationDays").asInt()).isEqualTo(30);
        assertThat(node.path("packageVersion").asLong()).isEqualTo(3L);
        assertThat(node.path("previousDueDate").asText()).isEqualTo("2026-11-12");
        assertThat(node.path("renewalStartedOn").asText()).isEqualTo("2026-11-12");
        assertThat(node.path("newPaidUntil").asText()).isEqualTo("2026-12-11");
        assertThat(node.path("nextDueDate").asText()).isEqualTo("2026-12-12");
        assertThat(node.path("totalChargedVnd").asLong()).isEqualTo(12_000_000L);
        assertThat(node.path("plan").asText()).isEqualTo("RENEW_VIP");
        assertThat(node.path("termsVersion").asText()).isEqualTo(WalletService.SERVICE_TERMS_VERSION);
        assertThat(node.path("termsAcceptedAt").asText()).isNotBlank();
    }

    // 15. Regression: Insufficient balance during auto-renewal marks horse UNPAID without debiting
    @Test
    @DisplayName("15. Insufficient balance during renewal marks horse UNPAID and does not debit wallet")
    void autoRenewal_insufficientBalance_marksUnpaidNoDebit() {
        ServicePackageEntity pkg = createPackage("EXPENSIVE_30", "Gói 20M", BigDecimal.valueOf(20_000_000), 30, true, 1L);
        when(packageRepository.findByCode("EXPENSIVE_30")).thenReturn(Optional.of(pkg));
        mockOwnerWallet(BigDecimal.valueOf(5_000_000));
        mockHorseRow("EXPENSIVE_30", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        verify(jdbc, never()).update(contains("update users set wallet_balance_vnd"), any(), any());
        verify(jdbc).update(contains("update horses set payment_status='UNPAID'"), eq(horseId));
    }

    // 16. Notification behavior: Reserve package triggers notification to owner with deduplication
    @Test
    @DisplayName("16. Reserve package notification sent to owner with deduplication key")
    void autoRenewal_reservePackage_notifiesOwnerWithDeduplication() {
        ServicePackageEntity reservePkg = createPackage("RESERVE_NOTICE", "Gói thông báo", BigDecimal.valueOf(5_000_000), 30, false, 1L);
        when(packageRepository.findByCode("RESERVE_NOTICE")).thenReturn(Optional.of(reservePkg));
        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorseRow("RESERVE_NOTICE", null, today, today.minusDays(1));

        service.renewOne(ownerId);

        // Verify notification inserted with deduplication key "wallet-reserve:horseId:due"
        verify(jdbc).update(contains("insert into notifications"),
                eq(ownerId), eq("SERVICE_PLAN_RETIRED"),
                contains("Gói dịch vụ đã chuyển vào Thư viện dự phòng"),
                contains("ngừng cung cấp"), eq(horseId),
                eq("wallet-reserve:" + horseId + ":" + today),
                eq("wallet-reserve:" + horseId + ":" + today));
    }
}
