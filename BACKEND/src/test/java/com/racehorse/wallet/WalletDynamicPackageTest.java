package com.racehorse.wallet;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.servicepackage.entity.ServicePackageEntity;
import com.racehorse.servicepackage.repository.ServicePackageRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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

class WalletDynamicPackageTest {

    private JdbcTemplate jdbc;
    private ObjectMapper json;
    private PayosGateway gateway;
    private Clock fixedClock;
    private ServicePackageRepository packageRepository;
    private WalletService service;
    private Validator validator;

    private final UUID ownerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID horseId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        json = new ObjectMapper();
        gateway = mock(PayosGateway.class);
        packageRepository = mock(ServicePackageRepository.class);
        fixedClock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new WalletService(jdbc, json, gateway, fixedClock, "Asia/Ho_Chi_Minh", packageRepository);
        validator = Validation.buildDefaultValidatorFactory().getValidator();

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

    private void mockHorsePlanRow(String activePlan, String nextPlan) {
        Map<String, Object> horseRow = new HashMap<>();
        horseRow.put("id", horseId);
        horseRow.put("active_service_plan", activePlan);
        horseRow.put("next_service_plan", nextPlan);
        horseRow.put("service_next_due_date", null);
        when(jdbc.queryForList(contains("from horses"), eq(horseId), eq(ownerId))).thenReturn(List.of(horseRow));
    }

    // 1. Existing P30/P90/P365 purchase compatibility
    @Test
    @DisplayName("Requirement 1: Existing legacy P30 purchase compatibility works via catalog lookup")
    void purchase_legacyP30_success() {
        ServicePackageEntity p30 = createPackage("P30", "Gói 1 tháng", BigDecimal.valueOf(5_000_000), 30, true, 1L);
        when(packageRepository.findByCode("P30")).thenReturn(Optional.of(p30));
        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorsePlanRow(null, null);

        service.purchase(ownerId, horseId, "P30", true);

        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(5_000_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("P30"), eq(LocalDate.of(2026, 11, 12)), eq(LocalDate.of(2026, 12, 12)), eq(LocalDate.of(2026, 12, 11)), eq(horseId));
    }

    // 2. Dynamic package purchase with a non-enum code
    @Test
    @DisplayName("Requirement 2: Dynamic package purchase with non-enum code (e.g. VIP_30) succeeds")
    void purchase_dynamicNonEnumCode_success() {
        ServicePackageEntity vip = createPackage("VIP_30", "Gói VIP 30 ngày", BigDecimal.valueOf(8_500_000), 30, true, 2L);
        when(packageRepository.findByCode("VIP_30")).thenReturn(Optional.of(vip));
        mockOwnerWallet(BigDecimal.valueOf(10_000_000));
        mockHorsePlanRow(null, null);

        service.purchase(ownerId, horseId, "VIP_30", true);

        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(1_500_000)), eq(ownerId));
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("VIP_30"), eq(LocalDate.of(2026, 11, 12)), eq(LocalDate.of(2026, 12, 12)), eq(LocalDate.of(2026, 12, 11)), eq(horseId));
    }

    // 3. Purchase rejection for nonexistent package
    @Test
    @DisplayName("Requirement 3: Purchase rejection for nonexistent package throws ResourceNotFoundException")
    void purchase_nonexistentPackage_throwsResourceNotFound() {
        when(packageRepository.findByCode("UNKNOWN_CODE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchase(ownerId, horseId, "UNKNOWN_CODE", true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy gói dịch vụ với mã: UNKNOWN_CODE");

        verify(jdbc, never()).update(contains("update users"), any(), any());
    }

    // 4. Purchase rejection for Reserve package
    @Test
    @DisplayName("Requirement 4: Purchase rejection for Reserve package throws BusinessException")
    void purchase_reservePackage_throwsBusinessException() {
        ServicePackageEntity reservePkg = createPackage("OLD_PRO_60", "Gói Cũ 60 ngày", BigDecimal.valueOf(6_000_000), 60, false, 3L);
        when(packageRepository.findByCode("OLD_PRO_60")).thenReturn(Optional.of(reservePkg));

        assertThatThrownBy(() -> service.purchase(ownerId, horseId, "OLD_PRO_60", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Thư viện dự phòng và không còn khả dụng để đăng ký mới");

        verify(jdbc, never()).update(contains("update users"), any(), any());
    }

    // 5. Dynamic catalog price and duration used in purchase
    @Test
    @DisplayName("Requirement 5: Catalog price and duration are strictly applied during purchase")
    void purchase_usesCatalogPriceAndDurationStrictly() {
        ServicePackageEntity custom = createPackage("CUSTOM_45", "Gói 45 ngày", BigDecimal.valueOf(12_345_678), 45, true, 1L);
        when(packageRepository.findByCode("CUSTOM_45")).thenReturn(Optional.of(custom));
        mockOwnerWallet(BigDecimal.valueOf(20_000_000));
        mockHorsePlanRow(null, null);

        service.purchase(ownerId, horseId, "CUSTOM_45", true);

        // Balance 20,000,000 - 12,345,678 = 7,654,322
        verify(jdbc).update(contains("update users set wallet_balance_vnd=?"), eq(BigDecimal.valueOf(7_654_322)), eq(ownerId));
        // Duration 45 days: 2026-11-12 + 45 days = 2026-12-27, paid_until = 2026-12-26
        verify(jdbc).update(contains("update horses set active_service_plan=?"),
                eq("CUSTOM_45"), eq(LocalDate.of(2026, 11, 12)), eq(LocalDate.of(2026, 12, 27)), eq(LocalDate.of(2026, 12, 26)), eq(horseId));
    }

    // 6. Insufficient wallet balance
    @Test
    @DisplayName("Requirement 6: Insufficient wallet balance rejects purchase without deducting funds")
    void purchase_insufficientBalance_throwsBusinessException() {
        ServicePackageEntity pkg = createPackage("EXPENSIVE", "Gói đắt đỏ", BigDecimal.valueOf(25_000_000), 30, true, 1L);
        when(packageRepository.findByCode("EXPENSIVE")).thenReturn(Optional.of(pkg));
        mockOwnerWallet(BigDecimal.valueOf(5_000_000)); // Has 5M, needs 25M
        mockHorsePlanRow(null, null);

        assertThatThrownBy(() -> service.purchase(ownerId, horseId, "EXPENSIVE", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số dư chưa đủ; cần nạp thêm ít nhất 20000000 VND");

        verify(jdbc, never()).update(contains("update users set wallet_balance_vnd=?"), any(), any());
    }

    // 7. Dynamic package scheduling
    @Test
    @DisplayName("Requirement 7: Dynamic package scheduling sets next_service_plan without locking price")
    void scheduleNextPlan_dynamicPackage_success() {
        ServicePackageEntity nextPkg = createPackage("VIP_90", "Gói VIP 90 ngày", BigDecimal.valueOf(20_000_000), 90, true, 1L);
        when(packageRepository.findByCode("VIP_90")).thenReturn(Optional.of(nextPkg));
        mockOwnerWallet(BigDecimal.valueOf(5_000_000));
        mockHorsePlanRow("P30", null);

        service.scheduleNextPlan(ownerId, horseId, "VIP_90", true);

        verify(jdbc).update(contains("update horses set next_service_plan=?"), eq("VIP_90"), eq(horseId));
    }

    // 8. Scheduling rejection for Reserve package
    @Test
    @DisplayName("Requirement 8: Scheduling rejection for Reserve package throws BusinessException")
    void scheduleNextPlan_reservePackage_throwsBusinessException() {
        ServicePackageEntity reservePkg = createPackage("RETIRED_180", "Gói Đã Hủy 180 ngày", BigDecimal.valueOf(10_000_000), 180, false, 4L);
        when(packageRepository.findByCode("RETIRED_180")).thenReturn(Optional.of(reservePkg));
        mockOwnerWallet(BigDecimal.valueOf(15_000_000));
        mockHorsePlanRow("P30", null);

        assertThatThrownBy(() -> service.scheduleNextPlan(ownerId, horseId, "RETIRED_180", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Thư viện dự phòng");

        verify(jdbc, never()).update(contains("update horses set next_service_plan=?"), any(), any());
    }

    // 9. Scheduling does not deduct wallet funds
    @Test
    @DisplayName("Requirement 9: Scheduling does not deduct wallet funds")
    void scheduleNextPlan_doesNotDeductWalletFunds() {
        ServicePackageEntity pkg = createPackage("STANDARD_60", "Gói 60 ngày", BigDecimal.valueOf(8_000_000), 60, true, 1L);
        when(packageRepository.findByCode("STANDARD_60")).thenReturn(Optional.of(pkg));
        mockOwnerWallet(BigDecimal.valueOf(1_000_000)); // Less than package price, should still succeed
        mockHorsePlanRow("P30", null);

        service.scheduleNextPlan(ownerId, horseId, "STANDARD_60", true);

        verify(jdbc, never()).update(contains("update users set wallet_balance_vnd=?"), any(), any());
    }

    // 10. Wallet read API does not crash for dynamic package codes
    @Test
    @DisplayName("Requirement 10: Wallet read API does not throw IllegalArgumentException for dynamic package codes")
    void walletRead_dynamicCode_doesNotCrash() {
        ServicePackageEntity vip = createPackage("VIP_90", "Gói VIP 90 ngày", BigDecimal.valueOf(18_000_000), 90, true, 1L);
        when(packageRepository.findByCode("VIP_90")).thenReturn(Optional.of(vip));
        mockOwnerWallet(BigDecimal.valueOf(50_000_000));

        // Mock horses query returning a horse with dynamic package "VIP_90"
        doAnswer(invocation -> {
            RowMapper<WalletService.HorsePlanView> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(horseId);
            when(rs.getString("code")).thenReturn("H-001");
            when(rs.getString("name")).thenReturn("Thần Mã");
            when(rs.getString("active_service_plan")).thenReturn("VIP_90");
            when(rs.getString("next_service_plan")).thenReturn(null);
            when(rs.getDate("service_plan_started_on")).thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 10, 1)));
            when(rs.getDate("service_next_due_date")).thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 12, 30)));
            when(rs.getDate("paid_until")).thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 12, 29)));
            when(rs.getString("payment_status")).thenReturn("PAID");
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(contains("from horses h join horse_ownerships"), any(RowMapper.class), eq(ownerId));

        WalletService.WalletView view = service.wallet(ownerId);
        assertThat(view).isNotNull();
        assertThat(view.horses()).hasSize(1);
        WalletService.HorsePlanView horseView = view.horses().getFirst();
        assertThat(horseView.activePlan()).isEqualTo("VIP_90");
        assertThat(horseView.renewalAmountVnd()).isEqualByComparingTo(BigDecimal.valueOf(18_000_000));
    }

    // 11. Wallet read API handles unresolved legacy package references safely
    @Test
    @DisplayName("Requirement 11: Wallet read API handles unresolved package references safely without crashing")
    void walletRead_unresolvedPackageReference_handlesSafely() {
        when(packageRepository.findByCode("DELETED_OR_CORRUPT_CODE")).thenReturn(Optional.empty());
        mockOwnerWallet(BigDecimal.valueOf(50_000_000));

        doAnswer(invocation -> {
            RowMapper<WalletService.HorsePlanView> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getObject("id", UUID.class)).thenReturn(horseId);
            when(rs.getString("code")).thenReturn("H-002");
            when(rs.getString("name")).thenReturn("Hắc Báo");
            when(rs.getString("active_service_plan")).thenReturn("DELETED_OR_CORRUPT_CODE");
            when(rs.getString("next_service_plan")).thenReturn(null);
            when(rs.getDate("service_plan_started_on")).thenReturn(null);
            when(rs.getDate("service_next_due_date")).thenReturn(null);
            when(rs.getDate("paid_until")).thenReturn(null);
            when(rs.getString("payment_status")).thenReturn("UNPAID");
            return List.of(mapper.mapRow(rs, 0));
        }).when(jdbc).query(contains("from horses h join horse_ownerships"), any(RowMapper.class), eq(ownerId));

        WalletService.WalletView view = service.wallet(ownerId);
        assertThat(view).isNotNull();
        assertThat(view.horses()).hasSize(1);
        WalletService.HorsePlanView horseView = view.horses().getFirst();
        assertThat(horseView.activePlan()).isEqualTo("DELETED_OR_CORRUPT_CODE");
        assertThat(horseView.renewalAmountVnd()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // 12. Purchase snapshot contains required immutable fields
    @Test
    @DisplayName("Requirement 12: Purchase snapshot in module_records contains all required immutable fields")
    void purchase_createsCompleteImmutableSnapshot() throws Exception {
        UUID pkgId = UUID.randomUUID();
        ServicePackageEntity pkg = new ServicePackageEntity();
        pkg.setId(pkgId);
        pkg.setCode("PREMIUM_30");
        pkg.setName("Gói Cao Cấp 30 ngày");
        pkg.setPriceVnd(BigDecimal.valueOf(15_000_000));
        pkg.setDurationDays(30);
        pkg.setActive(true);
        pkg.setVersion(5L);

        when(packageRepository.findByCode("PREMIUM_30")).thenReturn(Optional.of(pkg));
        mockOwnerWallet(BigDecimal.valueOf(30_000_000));
        mockHorsePlanRow(null, null);

        service.purchase(ownerId, horseId, "PREMIUM_30", true);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("insert into module_records"),
                any(UUID.class), eq("SERVICE_PLAN_PURCHASE"), eq(horseId), eq(ownerId),
                eq("PAID"), eq(LocalDate.of(2026, 11, 12)), jsonCaptor.capture());

        String jsonPayload = jsonCaptor.getValue();
        assertThat(jsonPayload).isNotBlank();

        JsonNode node = json.readTree(jsonPayload);
        assertThat(node.path("eventType").asText()).isEqualTo("SERVICE_PLAN_PURCHASE");
        assertThat(node.path("packageId").asText()).isEqualTo(pkgId.toString());
        assertThat(node.path("packageCode").asText()).isEqualTo("PREMIUM_30");
        assertThat(node.path("packageName").asText()).isEqualTo("Gói Cao Cấp 30 ngày");
        assertThat(node.path("priceVnd").asLong()).isEqualTo(15_000_000L);
        assertThat(node.path("durationDays").asInt()).isEqualTo(30);
        assertThat(node.path("packageVersion").asLong()).isEqualTo(5L);
        assertThat(node.path("startedOn").asText()).isEqualTo("2026-11-12");
        assertThat(node.path("paidUntil").asText()).isEqualTo("2026-12-11");
        assertThat(node.path("nextDueDate").asText()).isEqualTo("2026-12-12");
        assertThat(node.path("totalChargedVnd").asLong()).isEqualTo(15_000_000L);
        assertThat(node.path("plan").asText()).isEqualTo("PREMIUM_30");
        assertThat(node.path("termsVersion").asText()).isEqualTo(WalletService.SERVICE_TERMS_VERSION);
        assertThat(node.path("termsAcceptedAt").asText()).isNotBlank();
    }

    // Additional: Request DTO Validation & Deserialization
    @Test
    @DisplayName("DTO: PlanRequest deserializes with 'plan' alias and passes validation")
    void planRequest_deserializesWithPlanAlias() throws Exception {
        String jsonStr = "{\"horseId\":\"" + horseId + "\",\"plan\":\"VIP_30\",\"acceptedTerms\":true}";
        WalletController.PlanRequest req = json.readValue(jsonStr, WalletController.PlanRequest.class);

        assertThat(req.packageCode()).isEqualTo("VIP_30");
        assertThat(req.plan()).isEqualTo("VIP_30");
        assertThat(req.acceptedTerms()).isTrue();
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("DTO: PlanRequest deserializes with 'packageCode' and passes validation")
    void planRequest_deserializesWithPackageCode() throws Exception {
        String jsonStr = "{\"horseId\":\"" + horseId + "\",\"packageCode\":\"VIP_30\",\"acceptedTerms\":true}";
        WalletController.PlanRequest req = json.readValue(jsonStr, WalletController.PlanRequest.class);

        assertThat(req.packageCode()).isEqualTo("VIP_30");
        assertThat(req.plan()).isEqualTo("VIP_30");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("DTO: PlanRequest rejects empty package code")
    void planRequest_rejectsBlankPackageCode() {
        WalletController.PlanRequest req = new WalletController.PlanRequest(horseId, "   ", true);
        Set<ConstraintViolation<WalletController.PlanRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("DTO: NextPlanRequest deserializes with 'plan' alias and passes validation")
    void nextPlanRequest_deserializesWithPlanAlias() throws Exception {
        String jsonStr = "{\"plan\":\"P90\",\"acceptedTerms\":true}";
        WalletController.NextPlanRequest req = json.readValue(jsonStr, WalletController.NextPlanRequest.class);

        assertThat(req.packageCode()).isEqualTo("P90");
        assertThat(req.plan()).isEqualTo("P90");
        assertThat(req.acceptedTerms()).isTrue();
        assertThat(validator.validate(req)).isEmpty();
    }
}
