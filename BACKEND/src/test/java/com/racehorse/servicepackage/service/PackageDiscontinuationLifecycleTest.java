package com.racehorse.servicepackage.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.servicepackage.dto.ServicePackageResponse;
import com.racehorse.servicepackage.entity.ServicePackageEntity;
import com.racehorse.servicepackage.repository.ServicePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackageDiscontinuationLifecycleTest {

    @Mock
    private ServicePackageRepository packageRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private JdbcTemplate jdbc;

    private ObjectMapper json;
    private ServicePackageService service;

    private UUID actorId;
    private UUID pkgId;
    private UUID owner1Id;
    private UUID owner2Id;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        actorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        pkgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        owner1Id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        owner2Id = UUID.fromString("44444444-4444-4444-4444-444444444444");

        service = new ServicePackageService(packageRepository, jdbc, json, userAccountRepository);

        lenient().when(packageRepository.saveAndFlush(any(ServicePackageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ServicePackageEntity createPackage(UUID id, String code, String name, boolean active, long version) {
        ServicePackageEntity entity = new ServicePackageEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(name);
        entity.setPriceVnd(BigDecimal.valueOf(5_000_000));
        entity.setDurationDays(30);
        entity.setActive(active);
        entity.setVersion(version);
        return entity;
    }

    private UserAccount createUser(UUID id, String username, Role role, boolean enabled) {
        UserAccount user = new UserAccount(username, username + "@example.com", "hash", Set.of(role));
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        return user;
    }

    @Test
    @DisplayName("Complete sequence: Active(v1)->Reserve(v1) -> Reserve->Active(v2) -> Active->Reserve(v3) with independent broadcasts")
    void completeLifecycleSequence_withIndependentBroadcasts() {
        // Setup package at version 1 (Active)
        ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
        when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));

        UserAccount owner1 = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
        when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(owner1));

        // -------------------------------------------------------------
        // Sequence 1: Active -> Reserve (Lifecycle 1)
        // -------------------------------------------------------------
        // Initially no prior discontinuation in module_records (maxVer = null)
        when(jdbc.queryForObject(contains("from dbo.module_records"), eq(Long.class), eq(pkgId.toString())))
                .thenReturn(null);

        ServicePackageResponse resp1 = service.transfer(pkgId, actorId, false);
        assertThat(resp1.isActive()).isFalse();

        // Verify broadcast event in module_records with version 1
        verify(jdbc).update(contains("insert into dbo.module_records"),
                any(UUID.class), eq(actorId), any(LocalDate.class), contains("packageCode"),
                eq(pkgId.toString()), eq("1"));

        // Verify notification dispatched with key pkg-broadcast:<pkgId>:1:<ownerId>
        verify(jdbc).update(contains("insert into dbo.notifications"),
                any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"),
                eq(ServicePackageService.BROADCAST_TITLE), eq(ServicePackageService.BROADCAST_MESSAGE),
                eq(pkgId), eq("pkg-broadcast:" + pkgId + ":1:" + owner1Id), eq("pkg-broadcast:" + pkgId + ":1:" + owner1Id));

        reset(jdbc);

        // -------------------------------------------------------------
        // Sequence 2: Repeated Reserve call while already in Reserve (Retry 1)
        // -------------------------------------------------------------
        ServicePackageResponse retryResp = service.transfer(pkgId, actorId, false);
        assertThat(retryResp.isActive()).isFalse();

        // Zero database updates to module_records or notifications
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any());

        // -------------------------------------------------------------
        // Sequence 3: Reserve -> Active (Reactivation)
        // -------------------------------------------------------------
        // Advance version in database to 2
        entity.setVersion(2L);
        ServicePackageResponse reactivateResp = service.transfer(pkgId, actorId, true);
        assertThat(reactivateResp.isActive()).isTrue();

        // Reactivation creates NO broadcast event and NO notifications
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any());

        // -------------------------------------------------------------
        // Sequence 4: Active -> Reserve again (Lifecycle 2)
        // -------------------------------------------------------------
        // Advance version in database to 3
        entity.setVersion(3L);
        // Prior discontinuation exists with version 1
        when(jdbc.queryForObject(contains("from dbo.module_records"), eq(Long.class), eq(pkgId.toString())))
                .thenReturn(1L);

        ServicePackageResponse resp2 = service.transfer(pkgId, actorId, false);
        assertThat(resp2.isActive()).isFalse();

        // Verify second independent broadcast event in module_records with version 3
        verify(jdbc).update(contains("insert into dbo.module_records"),
                any(UUID.class), eq(actorId), any(LocalDate.class), contains("packageCode"),
                eq(pkgId.toString()), eq("3"));

        // Verify second independent notification dispatched with key pkg-broadcast:<pkgId>:3:<ownerId>
        verify(jdbc).update(contains("insert into dbo.notifications"),
                any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"),
                eq(ServicePackageService.BROADCAST_TITLE), eq(ServicePackageService.BROADCAST_MESSAGE),
                eq(pkgId), eq("pkg-broadcast:" + pkgId + ":3:" + owner1Id), eq("pkg-broadcast:" + pkgId + ":3:" + owner1Id));
    }

    @Test
    @DisplayName("Even if entity version in memory fails to increment, maxVer ensures version strictly advances")
    void unincrementedEntityVersion_strictlyAdvancesViaMaxVer() {
        // Setup package with version 1
        ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
        when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));

        UserAccount owner1 = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
        when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(owner1));

        // Prior discontinuation in module_records already recorded at version 1
        when(jdbc.queryForObject(contains("from dbo.module_records"), eq(Long.class), eq(pkgId.toString())))
                .thenReturn(1L);

        // When transfer executes, versionToUse should advance to maxVer + 1 = 2
        service.transfer(pkgId, actorId, false);

        // Event recorded with version 2
        verify(jdbc).update(contains("insert into dbo.module_records"),
                any(UUID.class), eq(actorId), any(LocalDate.class), contains("packageCode"),
                eq(pkgId.toString()), eq("2"));

        // Notification dispatched with version 2
        verify(jdbc).update(contains("insert into dbo.notifications"),
                any(UUID.class), eq(owner1Id), any(), any(), any(), eq(pkgId),
                eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id), eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id));
    }

    @Test
    @DisplayName("Idempotent deduplication query: module_records insertion contains 'where not exists'")
    void moduleRecordsInsertion_containsWhereNotExistsClause() {
        ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
        when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));

        UserAccount owner1 = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
        when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(owner1));

        service.transfer(pkgId, actorId, false);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCaptor.capture(), any(UUID.class), eq(actorId), any(LocalDate.class), contains("packageCode"),
                eq(pkgId.toString()), eq("1"));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("where not exists");
        assertThat(sql).contains("module_code='SYSTEM'");
        assertThat(sql).contains("record_type='SERVICE_PACKAGE_DISCONTINUATION'");
        assertThat(sql).contains("dbo.json_value(data_json, '$.packageId')=?");
        assertThat(sql).contains("dbo.json_value(data_json, '$.packageVersion')=?");
    }
}
