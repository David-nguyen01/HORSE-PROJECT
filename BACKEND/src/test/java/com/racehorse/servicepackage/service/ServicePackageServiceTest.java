package com.racehorse.servicepackage.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.servicepackage.dto.*;
import com.racehorse.servicepackage.entity.ServicePackageEntity;
import com.racehorse.servicepackage.repository.ServicePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServicePackageServiceTest {

    private ServicePackageRepository repository;
    private JdbcTemplate jdbc;
    private ObjectMapper json;
    private ServicePackageService service;

    @BeforeEach
    void setUp() {
        repository = mock(ServicePackageRepository.class);
        jdbc = mock(JdbcTemplate.class);
        json = new ObjectMapper();
        service = new ServicePackageService(repository, jdbc, json);
    }

    @Test
    void listActiveReturnsOnlyActivePackagesOrdered() {
        ServicePackageEntity p1 = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        ServicePackageEntity p2 = sampleEntity("P90", "Gói 3 tháng", new BigDecimal("15000000"), 90, true, 20);

        when(repository.findByIsActiveOrderByDisplayOrderAscCreatedAtAsc(true)).thenReturn(List.of(p1, p2));

        List<ServicePackageResponse> list = service.listActive();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).code()).isEqualTo("P30");
        assertThat(list.get(1).code()).isEqualTo("P90");
        assertThat(list.get(0).isActive()).isTrue();
    }

    @Test
    void listReserveReturnsOnlyReservePackages() {
        ServicePackageEntity p = sampleEntity("P365_OLD", "Gói cũ", new BigDecimal("55000000"), 365, false, 30);
        when(repository.findByIsActiveOrderByDisplayOrderAscCreatedAtAsc(false)).thenReturn(List.of(p));

        List<ServicePackageResponse> list = service.listReserve();
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().code()).isEqualTo("P365_OLD");
        assertThat(list.getFirst().isActive()).isFalse();
    }

    @Test
    void getByIdReturnsPackageWhenFound() {
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        ServicePackageResponse response = service.get(id);
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.code()).isEqualTo("P30");
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getByCodeReturnsPackageWhenFound() {
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        when(repository.findByCode("P30")).thenReturn(Optional.of(entity));

        ServicePackageResponse response = service.getByCode("P30");
        assertThat(response.code()).isEqualTo("P30");
    }

    @Test
    void createPackageSucceedsWithUppercaseCodeAndDefaults() {
        UUID actorId = UUID.randomUUID();
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "p180", "Gói 6 tháng", "Mô tả 6 tháng",
                new BigDecimal("28000000"), 180, true, null);

        when(repository.existsByCode("P180")).thenReturn(false);
        when(jdbc.queryForObject(contains("MAX(display_order)"), eq(Integer.class))).thenReturn(30);

        when(repository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> {
            ServicePackageEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        ServicePackageResponse response = service.create(actorId, req);
        assertThat(response.code()).isEqualTo("P180");
        assertThat(response.name()).isEqualTo("Gói 6 tháng");
        assertThat(response.displayOrder()).isEqualTo(40);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createPackageThrowsWhenCodeAlreadyExists() {
        UUID actorId = UUID.randomUUID();
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "P30", "Gói trùng", "Mô tả",
                new BigDecimal("5000000"), 30, true, 10);

        when(repository.existsByCode("P30")).thenReturn(true);

        assertThatThrownBy(() -> service.create(actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("P30");
    }

    @Test
    void updatePackageModifiesAttributesWhilePreservingCode() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Tên cũ", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);
        entity.setVersion(1L);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateServicePackageRequest req = new UpdateServicePackageRequest(
                "Tên mới", "Mô tả mới", new BigDecimal("6000000"), 45, 15, 1L);

        ServicePackageResponse updated = service.update(id, actorId, req);
        assertThat(updated.code()).isEqualTo("P30"); // Code strictly immutable
        assertThat(updated.name()).isEqualTo("Tên mới");
        assertThat(updated.priceVnd()).isEqualTo(new BigDecimal("6000000"));
        assertThat(updated.durationDays()).isEqualTo(45);
        assertThat(updated.displayOrder()).isEqualTo(15);
    }

    @Test
    void updatePackageThrowsOnOptimisticLockVersionMismatch() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Tên", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);
        entity.setVersion(2L); // Current version is 2

        when(repository.findById(id)).thenReturn(Optional.of(entity));

        UpdateServicePackageRequest req = new UpdateServicePackageRequest(
                "Tên mới", "Mô tả", new BigDecimal("6000000"), 30, 10, 1L); // Stale version 1

        assertThatThrownBy(() -> service.update(id, actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("phiên làm việc khác");
    }

    @Test
    void transferChangesActiveState() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ServicePackageResponse response = service.transfer(id, actorId, false);
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void reorderUpdatesDisplayOrderSequentially() {
        UUID actorId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ServicePackageEntity p1 = sampleEntity("P30", "Gói 1", new BigDecimal("5000000"), 30, true, 10);
        p1.setId(id1);
        ServicePackageEntity p2 = sampleEntity("P90", "Gói 2", new BigDecimal("15000000"), 90, true, 20);
        p2.setId(id2);

        when(repository.findById(id1)).thenReturn(Optional.of(p1));
        when(repository.findById(id2)).thenReturn(Optional.of(p2));
        when(repository.findByIsActiveOrderByDisplayOrderAscCreatedAtAsc(true)).thenReturn(List.of(p2, p1));

        List<ServicePackageResponse> result = service.reorder(actorId, List.of(id2, id1));
        assertThat(result).hasSize(2);
        assertThat(p2.getDisplayOrder()).isEqualTo(10);
        assertThat(p1.getDisplayOrder()).isEqualTo(20);
    }

    @Test
    void deleteAllowedWhenNoReferencesExist() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("UNUSED_PLAN", "Gói nháp", new BigDecimal("1000000"), 15, false, 50);
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(jdbc.queryForObject(contains("FROM dbo.horses"), eq(Integer.class), eq("UNUSED_PLAN"), eq("UNUSED_PLAN")))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FROM dbo.users"), eq(Integer.class), eq("UNUSED_PLAN")))
                .thenReturn(0);
        when(jdbc.query(contains("FROM dbo.module_records"), any(RowMapper.class)))
                .thenReturn(List.of());

        service.delete(id, actorId);
        verify(repository).delete(entity);
    }

    @Test
    void deleteBlockedWhenReferencedByHorseActivePlan() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(jdbc.queryForObject(contains("FROM dbo.horses"), eq(Integer.class), eq("P30"), eq("P30")))
                .thenReturn(2);

        assertThatThrownBy(() -> service.delete(id, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang được sử dụng bởi 2 ngựa");
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteBlockedWhenReferencedByUserAccount() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(jdbc.queryForObject(contains("FROM dbo.horses"), eq(Integer.class), eq("P30"), eq("P30")))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FROM dbo.users"), eq(Integer.class), eq("P30")))
                .thenReturn(1);

        assertThatThrownBy(() -> service.delete(id, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gán cho tài khoản người dùng");
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteBlockedWhenReferencedByHistoricalLegacyModuleRecord() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(jdbc.queryForObject(contains("FROM dbo.horses"), eq(Integer.class), eq("P30"), eq("P30")))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FROM dbo.users"), eq(Integer.class), eq("P30")))
                .thenReturn(0);
        when(jdbc.query(contains("FROM dbo.module_records"), any(RowMapper.class)))
                .thenReturn(List.of("{\"plan\":\"P30\",\"previousDueDate\":\"2026-11-01\"}"));

        assertThatThrownBy(() -> service.delete(id, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lịch sử đăng ký hoặc thanh toán");
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteBlockedWhenReferencedByHistoricalModernPackageCodeRecord() {
        UUID actorId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(jdbc.queryForObject(contains("FROM dbo.horses"), eq(Integer.class), eq("P30"), eq("P30")))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("FROM dbo.users"), eq(Integer.class), eq("P30")))
                .thenReturn(0);
        when(jdbc.query(contains("FROM dbo.module_records"), any(RowMapper.class)))
                .thenReturn(List.of("{\"packageCode\":\"P30\",\"totalChargedVnd\":5000000}"));

        assertThatThrownBy(() -> service.delete(id, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lịch sử đăng ký hoặc thanh toán");
        verify(repository, never()).delete(any());
    }

    @Test
    void sanitizeMojibakeRestoresLegacyMojibakeStrings() {
        String mojibakeName = "GÃ³i 1 thÃ¡ng TiÃªu chuáº©n";
        String mojibakeDesc = "ChÄƒm sÃ³c dinh dÆ°á»¡ng tiÃªu chuáº©n, theo dÃµi sá»©c khá» e vÃ  huáº¥n luyá»‡n cÆ¡ báº£n trong 30 ngÃ y.";

        String cleanName = ServicePackageService.sanitizeMojibake(mojibakeName);
        String cleanDesc = ServicePackageService.sanitizeMojibake(mojibakeDesc);

        assertThat(cleanName).isEqualTo("Gói 1 tháng Tiêu chuẩn");
        assertThat(cleanDesc).isEqualTo("Chăm sóc dinh dưỡng tiêu chuẩn, theo dõi sức khỏe và huấn luyện cơ bản trong 30 ngày.");

        String p90MojibakeName = "GÃ³i 3 thÃ¡ng NÃ¢ng cao";
        assertThat(ServicePackageService.sanitizeMojibake(p90MojibakeName)).isEqualTo("Gói 3 tháng Nâng cao");

        String p365MojibakeName = "GÃ³i 12 thÃ¡ng ToÃ n diá»‡n";
        assertThat(ServicePackageService.sanitizeMojibake(p365MojibakeName)).isEqualTo("Gói 12 tháng Toàn diện");
    }

    @Test
    void sanitizeMojibakePreservesNormalAndCleanVietnameseText() {
        String clean = "Gói 1 tháng Tiêu chuẩn và Huấn luyện chuyên nghiệp";
        assertThat(ServicePackageService.sanitizeMojibake(clean)).isEqualTo(clean);
        assertThat(ServicePackageService.sanitizeMojibake(null)).isNull();
        assertThat(ServicePackageService.sanitizeMojibake("Standard Package 30 Days")).isEqualTo("Standard Package 30 Days");
    }

    @Test
    void transferToReserveHandlesSqlServerStringUuidsWithoutClassCastException() {
        UUID actorId = UUID.randomUUID();
        UUID pkgId = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, true, 10);
        entity.setId(pkgId);

        when(repository.findByIdForUpdate(pkgId)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any())).thenReturn(entity);

        // SQL Server JDBC Driver returns String for uniqueidentifier in queryForList Map
        Map<String, Object> horseRow = new HashMap<>();
        horseRow.put("horse_id", UUID.randomUUID().toString()); // String representation!
        horseRow.put("owner_id", UUID.randomUUID().toString()); // String representation!
        horseRow.put("paid_until", java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(20)));
        horseRow.put("service_plan_started_on", java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(10)));
        horseRow.put("service_next_due_date", java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(20)));

        when(jdbc.queryForList(contains("from dbo.horses h"), eq("P30"), any(java.time.LocalDate.class)))
                .thenReturn(List.of(horseRow));

        // When moving to reserve, should not throw ClassCastException
        ServicePackageResponse response = service.transfer(pkgId, actorId, false);
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void transferToActiveHandlesSqlServerStringUuidsInAlertResolution() {
        UUID actorId = UUID.randomUUID();
        UUID pkgId = UUID.randomUUID();
        ServicePackageEntity entity = sampleEntity("P30", "Gói 1 tháng", new BigDecimal("5000000"), 30, false, 10);
        entity.setId(pkgId);

        when(repository.findByIdForUpdate(pkgId)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any())).thenReturn(entity);

        // SQL Server returns String for id in queryForList
        Map<String, Object> alertRow = new HashMap<>();
        alertRow.put("id", UUID.randomUUID().toString()); // String representation!
        alertRow.put("data_json", "{\"packageId\":\"" + pkgId + "\",\"paidUntil\":\"" + java.time.LocalDate.now().plusDays(10) + "\"}");

        when(jdbc.queryForList(contains("DISCONTINUED_HORSE_EXPIRY_ALERT")))
                .thenReturn(List.of(alertRow));

        ServicePackageResponse response = service.transfer(pkgId, actorId, true);
        assertThat(response.isActive()).isTrue();
    }

    private ServicePackageEntity sampleEntity(String code, String name, BigDecimal price, int days, boolean active, int order) {
        ServicePackageEntity entity = new ServicePackageEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription("Mô tả");
        entity.setPriceVnd(price);
        entity.setDurationDays(days);
        entity.setActive(active);
        entity.setDisplayOrder(order);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setVersion(0L);
        return entity;
    }
}
