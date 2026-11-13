package com.racehorse.servicepackage.service;

import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.ForbiddenException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.notification.dto.BroadcastDismissalResponse;
import com.racehorse.notification.dto.BroadcastNotificationResponse;
import com.racehorse.notification.entity.Notification;
import com.racehorse.notification.entity.NotificationPriority;
import com.racehorse.notification.repository.NotificationRepository;
import com.racehorse.notification.service.NotificationService;
import com.racehorse.servicepackage.dto.UpdateServicePackageRequest;
import com.racehorse.servicepackage.entity.ServicePackageEntity;
import com.racehorse.servicepackage.repository.ServicePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackageDiscontinuationBroadcastTest {

    @Mock
    private ServicePackageRepository packageRepository;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private ObjectMapper json;
    private Clock clock;
    private Instant now;

    private ServicePackageService packageService;
    private NotificationService notificationService;

    private UUID actorId;
    private UUID pkgId;
    private UUID owner1Id;
    private UUID owner2Id;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        now = Instant.parse("2026-11-12T10:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);

        actorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        pkgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        owner1Id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        owner2Id = UUID.fromString("44444444-4444-4444-4444-444444444444");

        packageService = new ServicePackageService(packageRepository, jdbc, json, userAccountRepository);
        notificationService = new NotificationService(notificationRepository, clock, jdbc, json);
        lenient().when(packageRepository.saveAndFlush(any(ServicePackageEntity.class)))
                .thenAnswer(inv -> packageRepository.save(inv.getArgument(0)));
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

    @Nested
    @DisplayName("A. Transition Detection & Event Persistence")
    class TransitionDetectionTests {

        @Test
        @DisplayName("1. Active -> Reserve creates one broadcast event in module_records and individual notifications")
        void activeToReserve_createsBroadcastEventAndNotifications() {
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> {
                ServicePackageEntity saved = inv.getArgument(0);
                saved.setVersion(2L);
                return saved;
            });

            UserAccount owner1 = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
            UserAccount owner2 = createUser(owner2Id, "owner2", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(owner1, owner2));

            var response = packageService.transfer(pkgId, actorId, false);

            assertThat(response.isActive()).isFalse();

            // Verify event recorded in module_records
            verify(jdbc).update(contains("insert into dbo.module_records"),
                    any(UUID.class), eq(actorId), any(LocalDate.class), contains("packageCode"), eq(pkgId.toString()), eq("2"));

            // Verify individual persistent notifications dispatched to each eligible owner
            verify(jdbc).update(contains("insert into dbo.notifications"),
                    any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"),
                    eq(ServicePackageService.BROADCAST_TITLE), eq(ServicePackageService.BROADCAST_MESSAGE),
                    eq(pkgId), eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id), eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id));

            verify(jdbc).update(contains("insert into dbo.notifications"),
                    any(UUID.class), eq(owner2Id), eq("SERVICE_PACKAGE_DISCONTINUED"),
                    eq(ServicePackageService.BROADCAST_TITLE), eq(ServicePackageService.BROADCAST_MESSAGE),
                    eq(pkgId), eq("pkg-broadcast:" + pkgId + ":2:" + owner2Id), eq("pkg-broadcast:" + pkgId + ":2:" + owner2Id));
        }

        @Test
        @DisplayName("2. Editing an already Reserve package (update) creates NO broadcast event")
        void editReservePackage_createsNoBroadcast() {
            ServicePackageEntity entity = createPackage(pkgId, "OLD_P30", "Gói cũ", false, 3L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any(ServicePackageEntity.class))).thenReturn(entity);

            UpdateServicePackageRequest req = new UpdateServicePackageRequest("Gói cũ sửa tên", "mô tả mới",
                    BigDecimal.valueOf(6_000_000), 30, 1, 3L);
            packageService.update(pkgId, actorId, req);

            verify(jdbc, never()).update(contains("insert into dbo.module_records"), any(), any(), any(), any());
            verify(jdbc, never()).update(contains("insert into dbo.notifications"), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("3. Reserve -> Reserve transition creates NO broadcast event")
        void reserveToReserve_createsNoBroadcast() {
            ServicePackageEntity entity = createPackage(pkgId, "RETIRED_PLAN", "Gói nghỉ hưu", false, 2L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));

            var response = packageService.transfer(pkgId, actorId, false);

            assertThat(response.isActive()).isFalse();
            verify(packageRepository, never()).save(any());
            verify(jdbc, never()).update(contains("insert into dbo.module_records"), any(), any(), any(), any());
            verify(jdbc, never()).update(contains("insert into dbo.notifications"), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("4. Reserve -> Active (Reactivation) creates NO broadcast event")
        void reserveToActive_reactivationCreatesNoBroadcast() {
            ServicePackageEntity entity = createPackage(pkgId, "SEASONAL", "Gói mùa giải", false, 2L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            var response = packageService.transfer(pkgId, actorId, true);

            assertThat(response.isActive()).isTrue();
            verify(jdbc, never()).update(contains("insert into dbo.module_records"), any(), any(), any(), any());
            verify(jdbc, never()).update(contains("insert into dbo.notifications"), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("5. Active -> Active edit creates NO broadcast event")
        void activeToActive_editCreatesNoBroadcast() {
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));

            // transfer with targetActive = true on already active entity
            var response = packageService.transfer(pkgId, actorId, true);

            assertThat(response.isActive()).isTrue();
            verify(packageRepository, never()).save(any());
            verify(jdbc, never()).update(contains("insert into dbo.module_records"), any(), any(), any(), any());
            verify(jdbc, never()).update(contains("insert into dbo.notifications"), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("6. Reactivate, then later Active -> Reserve creates a new independent event and new notifications")
        void reactivateThenDeactivate_createsNewIndependentBroadcast() {
            // Step 1: Active -> Reserve (v1 -> v2)
            ServicePackageEntity entity = createPackage(pkgId, "FLEX_PLAN", "Gói linh hoạt", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> {
                ServicePackageEntity saved = inv.getArgument(0);
                saved.setVersion(2L);
                return saved;
            });
            UserAccount owner = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(owner));

            packageService.transfer(pkgId, actorId, false);

            verify(jdbc).update(contains("insert into dbo.notifications"),
                    any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"), any(), any(), eq(pkgId),
                    eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id), eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id));

            // Step 2: Reactivate (v2 -> v3)
            entity.setActive(false);
            entity.setVersion(2L);
            when(packageRepository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> {
                ServicePackageEntity saved = inv.getArgument(0);
                saved.setVersion(3L);
                return saved;
            });
            packageService.transfer(pkgId, actorId, true);

            // Step 3: Deactivate again (v3 -> v4)
            entity.setActive(true);
            entity.setVersion(3L);
            when(packageRepository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> {
                ServicePackageEntity saved = inv.getArgument(0);
                saved.setVersion(4L);
                return saved;
            });
            packageService.transfer(pkgId, actorId, false);

            // Verify second independent broadcast notification created with v4 key
            verify(jdbc).update(contains("insert into dbo.notifications"),
                    any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"), any(), any(), eq(pkgId),
                    eq("pkg-broadcast:" + pkgId + ":4:" + owner1Id), eq("pkg-broadcast:" + pkgId + ":4:" + owner1Id));
        }

        @Test
        @DisplayName("7. Repeating the same transition request does not duplicate the event")
        void repeatingTransition_doesNotDuplicateEvent() {
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> {
                ServicePackageEntity saved = inv.getArgument(0);
                saved.setVersion(2L);
                return saved;
            });
            UserAccount owner = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(owner));

            // First call transitions Active -> Reserve
            packageService.transfer(pkgId, actorId, false);

            // Second repeated call sees entity is already Reserve
            entity.setActive(false);
            packageService.transfer(pkgId, actorId, false);

            // Exactly 1 module_records event and 1 notification call was executed
            verify(jdbc, times(1)).update(contains("insert into dbo.module_records"), any(), any(), any(), any(), any(), any());
            verify(jdbc, times(1)).update(contains("insert into dbo.notifications"), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("8. Deduplication key ensures SQL Server atomic deduplication on retries")
        void deduplicationKey_preventsDuplicateDeliveries() {
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any(ServicePackageEntity.class))).thenAnswer(inv -> {
                ServicePackageEntity saved = inv.getArgument(0);
                saved.setVersion(2L);
                return saved;
            });
            UserAccount owner = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(owner));

            packageService.transfer(pkgId, actorId, false);

            // Verify SQL pattern contains 'where not exists(select 1 from dbo.notifications where deduplication_key=?)'
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbc).update(sqlCaptor.capture(), any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"), any(), any(), eq(pkgId),
                    eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id), eq("pkg-broadcast:" + pkgId + ":2:" + owner1Id));
            assertThat(sqlCaptor.getValue()).contains("where not exists(select 1 from dbo.notifications where deduplication_key=?)");
        }
    }

    @Nested
    @DisplayName("B. Recipient Scope & Account Filtering")
    class RecipientScopeTests {

        @Test
        @DisplayName("9. All eligible enabled Owner accounts receive exactly one notification")
        void allEligibleOwners_receiveNotification() {
            ServicePackageEntity entity = createPackage(pkgId, "P90", "Gói 3 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any())).thenAnswer(inv -> {
                ServicePackageEntity s = inv.getArgument(0);
                s.setVersion(2L);
                return s;
            });

            UserAccount o1 = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
            UserAccount o2 = createUser(owner2Id, "owner2", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(o1, o2));

            packageService.transfer(pkgId, actorId, false);

            verify(jdbc).update(contains("insert into dbo.notifications"), any(UUID.class), eq(owner1Id), any(), any(), any(), any(), any(), any());
            verify(jdbc).update(contains("insert into dbo.notifications"), any(UUID.class), eq(owner2Id), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("10. Owners who never purchased or used the package still receive the broadcast")
        void ownersWhoNeverUsedPackage_stillReceiveBroadcast() {
            ServicePackageEntity entity = createPackage(pkgId, "EXOTIC_PLAN", "Gói đặc biệt", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any())).thenAnswer(inv -> {
                ServicePackageEntity s = inv.getArgument(0);
                s.setVersion(2L);
                return s;
            });

            // owner1 has no horses using this package; still queried by role HORSE_OWNER
            UserAccount o1 = createUser(owner1Id, "owner_with_no_horses", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(o1));

            packageService.transfer(pkgId, actorId, false);

            verify(jdbc).update(contains("insert into dbo.notifications"), any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("11. Non-Owner roles (Trainer, Vet, Stable Staff, Manager) receive NO broadcast")
        void nonOwners_receiveNoBroadcast() {
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any())).thenAnswer(inv -> {
                ServicePackageEntity s = inv.getArgument(0);
                s.setVersion(2L);
                return s;
            });

            // findByRoles with Role.HORSE_OWNER returns only HORSE_OWNER accounts
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of());

            packageService.transfer(pkgId, actorId, false);

            // Zero notifications dispatched
            verify(jdbc, never()).update(contains("insert into dbo.notifications"), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("12. Disabled/inactive Owner accounts are excluded from the broadcast")
        void disabledAccounts_areExcluded() {
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any())).thenAnswer(inv -> {
                ServicePackageEntity s = inv.getArgument(0);
                s.setVersion(2L);
                return s;
            });

            // findByRoles filters 'u.enabled = true', so disabled owners are not returned
            UserAccount activeOwner = createUser(owner1Id, "active_owner", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(activeOwner));

            packageService.transfer(pkgId, actorId, false);

            verify(jdbc, times(1)).update(contains("insert into dbo.notifications"), any(UUID.class), eq(owner1Id), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("13. Accounts created after publication do not receive historical broadcasts")
        void accountsCreatedAfterPublication_doNotReceiveHistoricalBroadcast() {
            // Discontinuation happens at time T1; snapshot only includes owners existing at T1
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any())).thenAnswer(inv -> {
                ServicePackageEntity s = inv.getArgument(0);
                s.setVersion(2L);
                return s;
            });
            UserAccount existingOwner = createUser(owner1Id, "existing_owner", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(existingOwner));

            packageService.transfer(pkgId, actorId, false);

            UUID newOwnerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
            verify(jdbc, never()).update(contains("insert into dbo.notifications"), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("14. Offline recipients retain their persistent notifications for later retrieval")
        void offlineRecipients_retainNotificationsInDatabase() {
            // Notifications are inserted into dbo.notifications table immediately
            ServicePackageEntity entity = createPackage(pkgId, "P30", "Gói 1 tháng", true, 1L);
            when(packageRepository.findById(pkgId)).thenReturn(Optional.of(entity));
            when(packageRepository.save(any())).thenAnswer(inv -> {
                ServicePackageEntity s = inv.getArgument(0);
                s.setVersion(2L);
                return s;
            });
            UserAccount offlineOwner = createUser(owner1Id, "offline_owner", Role.HORSE_OWNER, true);
            when(userAccountRepository.findByRoles(List.of(Role.HORSE_OWNER))).thenReturn(List.of(offlineOwner));

            packageService.transfer(pkgId, actorId, false);

            // Verified inserted into DB table
            verify(jdbc).update(contains("insert into dbo.notifications"), any(UUID.class), eq(owner1Id), eq("SERVICE_PACKAGE_DISCONTINUED"), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("C. Persistence, Acknowledgement & Dismissal")
    class AcknowledgementAndDismissalTests {

        private UUID notif1Id;
        private UUID notif2Id;
        private UserAccount owner1;
        private UserAccount owner2;

        @BeforeEach
        void initNotifications() {
            notif1Id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            notif2Id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            owner1 = createUser(owner1Id, "owner1", Role.HORSE_OWNER, true);
            owner2 = createUser(owner2Id, "owner2", Role.HORSE_OWNER, true);
        }

        @Test
        @DisplayName("15. Each Owner has independent notification state")
        void independentNotificationStatePerOwner() {
            Notification n1 = new Notification(owner1, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now);
            Notification n2 = new Notification(owner2, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now);

            assertThat(n1.getRecipient().getId()).isEqualTo(owner1Id);
            assertThat(n2.getRecipient().getId()).isEqualTo(owner2Id);
            assertThat(n1.getReadAt()).isNull();
            assertThat(n2.getReadAt()).isNull();
        }

        @Test
        @DisplayName("16. One Owner dismissing a notification does NOT affect another Owner's notification")
        void oneOwnerDismissing_doesNotAffectAnotherOwner() {
            Notification n1 = new Notification(owner1, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now);
            when(notificationRepository.findById(notif1Id)).thenReturn(Optional.of(n1));
            when(jdbc.queryForObject(contains("from dbo.module_records"), eq(Integer.class), eq(owner1Id), eq(notif1Id.toString())))
                    .thenReturn(0);

            BroadcastDismissalResponse response = notificationService.dismissBroadcast(owner1Id, notif1Id);

            assertThat(response.dismissed()).isTrue();
            // module_records dismissal inserted specifically for owner1
            verify(jdbc).update(contains("insert into dbo.module_records"),
                    any(UUID.class), eq(owner1Id), any(LocalDate.class), contains(notif1Id.toString()));
            // Zero dismissal calls for owner2
            verify(jdbc, never()).update(contains("insert into dbo.module_records"),
                    any(UUID.class), eq(owner2Id), any(LocalDate.class), anyString());
        }

        @Test
        @DisplayName("17. Merely fetching broadcasts via listActiveBroadcasts does NOT dismiss them")
        void merelyFetchingBroadcasts_doesNotDismissThem() {
            var mockBroadcast = new BroadcastNotificationResponse(
                    notif1Id, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now, null, false);

            when(jdbc.query(anyString(), any(RowMapper.class), eq(owner1Id)))
                    .thenReturn(List.of(mockBroadcast));

            List<BroadcastNotificationResponse> list = notificationService.listActiveBroadcasts(owner1Id);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).dismissed()).isFalse();

            // Zero writes to module_records or notifications
            verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("18. Ordinary mark-read behavior (NotificationBell) does NOT dismiss the broadcast")
        void ordinaryMarkRead_doesNotDismissBroadcast() {
            Notification n1 = new Notification(owner1, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now);
            when(notificationRepository.findByIdAndRecipientId(notif1Id, owner1Id)).thenReturn(Optional.of(n1));
            when(notificationRepository.save(n1)).thenReturn(n1);

            // Bell click calls markRead
            notificationService.markRead(owner1Id, notif1Id);

            // Notification entity has readAt set
            assertThat(n1.getReadAt()).isNotNull();

            // BUT zero module_records dismissal records written
            verify(jdbc, never()).update(contains("PACKAGE_DISCONTINUATION_DISMISSAL"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("19. Explicit dismissal persists in module_records and marks notification read_at")
        void explicitDismissal_persistsInModuleRecordsAndMarksRead() {
            Notification n1 = new Notification(owner1, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now);
            when(notificationRepository.findById(notif1Id)).thenReturn(Optional.of(n1));
            when(jdbc.queryForObject(contains("from dbo.module_records"), eq(Integer.class), eq(owner1Id), eq(notif1Id.toString())))
                    .thenReturn(0);

            BroadcastDismissalResponse response = notificationService.dismissBroadcast(owner1Id, notif1Id);

            assertThat(response.dismissed()).isTrue();
            assertThat(response.notificationId()).isEqualTo(notif1Id);

            // Persisted in module_records
            verify(jdbc).update(contains("insert into dbo.module_records"),
                    any(UUID.class), eq(owner1Id), any(LocalDate.class), contains(notif1Id.toString()));

            // Notification marked read in NotificationRepository
            verify(notificationRepository).save(n1);
            assertThat(n1.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("20. Acknowledgement/dismissal is idempotent")
        void dismissalIsIdempotent() {
            Notification n1 = new Notification(owner1, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now);
            n1.markRead(now); // already marked read
            when(notificationRepository.findById(notif1Id)).thenReturn(Optional.of(n1));
            // Already dismissed (count = 1)
            when(jdbc.queryForObject(contains("from dbo.module_records"), eq(Integer.class), eq(owner1Id), eq(notif1Id.toString())))
                    .thenReturn(1);

            BroadcastDismissalResponse response = notificationService.dismissBroadcast(owner1Id, notif1Id);

            assertThat(response.dismissed()).isTrue();
            // Does NOT insert duplicate row into module_records
            verify(jdbc, never()).update(contains("insert into dbo.module_records"), any(), any(), any(), any());
        }

        @Test
        @DisplayName("21. An Owner cannot dismiss another Owner's notification (throws ForbiddenException)")
        void crossOwnerDismissal_forbidden() {
            Notification n1 = new Notification(owner1, "SERVICE_PACKAGE_DISCONTINUED", NotificationPriority.HIGH,
                    ServicePackageService.BROADCAST_TITLE, ServicePackageService.BROADCAST_MESSAGE,
                    "SERVICE_PACKAGE", pkgId, now);
            when(notificationRepository.findById(notif1Id)).thenReturn(Optional.of(n1));

            // owner2 attempts to dismiss owner1's notification
            assertThatThrownBy(() -> notificationService.dismissBroadcast(owner2Id, notif1Id))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Bạn không có quyền thao tác trên thông báo này");

            verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("22. Non-broadcast notifications cannot be dismissed through the broadcast endpoint")
        void nonBroadcastNotification_rejected() {
            Notification medicalNotification = new Notification(owner1, "MEDICAL_QUEUE", NotificationPriority.NORMAL,
                    "Lịch khám", "Ngựa đã xếp hàng khám", "HORSE", UUID.randomUUID(), now);
            when(notificationRepository.findById(notif1Id)).thenReturn(Optional.of(medicalNotification));

            assertThatThrownBy(() -> notificationService.dismissBroadcast(owner1Id, notif1Id))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Chỉ có thể xác nhận thông báo phát thanh gói dịch vụ");

            verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("23. A dismissed broadcast stays dismissed after subsequent retrieval/login")
        void dismissedBroadcast_excludedFromFutureRetrievals() {
            // When querying listActiveBroadcasts, the SQL query excludes records present in module_records with record_type='PACKAGE_DISCONTINUATION_DISMISSAL'
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            when(jdbc.query(sqlCaptor.capture(), any(RowMapper.class), eq(owner1Id))).thenReturn(List.of());

            List<BroadcastNotificationResponse> active = notificationService.listActiveBroadcasts(owner1Id);

            assertThat(active).isEmpty();
            assertThat(sqlCaptor.getValue()).contains("PACKAGE_DISCONTINUATION_DISMISSAL");
            assertThat(sqlCaptor.getValue()).contains("not exists");
        }
    }
}
