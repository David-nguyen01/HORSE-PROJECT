# KẾ HOẠCH BỔ SUNG: Vai trò Bác sĩ thú y — Training Supervision & Distributed Claiming

> **Phiên bản tài liệu:** 1.0 (Kế hoạch triển khai)
> **Ngày:** 2026-10-03
> **Trạng thái:** SẴN SÀNG TRIỂN KHAI — Dựa trên REQUIREMENTS-new-2.md v3.0
> **Phạm vi:** Bổ sung tính năng cho branch `bacsithuy` — KHÔNG chỉnh sửa branch nào khác
> **Branch mục tiêu:** `bacsithuy`

---

## 1. Tóm tắt & Phạm vi bổ sung

Tài liệu `REQUIREMENTS-new-2.md` v3.0 định nghĩa mô hình mới:
- Bác sĩ thú y **tự vào nhận** phiên theo dõi (thay vì bị chỉ định) qua `POST /api/training-schedules/{id}/claim`.
- Yêu cầu có mặt tại sân: **10:00–11:00** (final hour) để kiểm tra lâm sàng sau buổi tập.
- Cột `veterinarian_user_id`, `vet_assigned_by`, `vet_assigned_at` **chưa tồn tại** trong DB → cần migration.
- Quyền `PERM_TRAINING_CLAIM` **chưa được cấp** cho `VETERINARIAN` → cần insert vào `role_permissions`.

### Những gì đã có sẵn (không cần làm lại)

| Thành phần | Trạng thái |
|---|---|
| VetPage.jsx (6 tab) | ✅ Hoàn chỉnh |
| vetApi.js (FR08–FR12) | ✅ Hoàn chỉnh |
| Backend health/medical controllers | ✅ Hoàn chỉnh |
| `GET /api/training-schedules/mine` | ✅ Có sẵn nhưng chỉ cho `STABLE_STAFF` — cần mở cho `VETERINARIAN` |
| `POST /api/training-schedules` | ✅ Có sẵn cho HEAD_TRAINER/CLUB_MANAGER |

### Những gì cần bổ sung mới

1. **[DB]** Migration SQL `15_training_operational_window.sql`
2. **[Backend]** Mở rộng `TrainingScheduleController` + `TrainingScheduleService` — claim API + mine API cho VETERINARIAN
3. **[Backend]** Cron Job `TrainingAutoPickJob` — bổ sung logic chọn Vet
4. **[Frontend]** Tab mới "Theo dõi buổi tập" trong VetPage
5. **[Frontend]** Bổ sung 3 API call vào `vetApi.js`

---

## 2. Database Migration

### File cần tạo: `DATABASE/15_training_operational_window.sql`

> **Lưu ý:** File migration này **idempotent** — có thể chạy nhiều lần an toàn nhờ các guard `IF COL_LENGTH(...) IS NULL` và `IF NOT EXISTS`.

```sql
-- =========================================================================
-- 15_training_operational_window.sql
-- Branch: bacsithuy | Idempotent Migration
-- Chạy trên Azure SQL (Portal Query Editor hoặc Azure Data Studio)
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1. RACETRACKS: cập nhật giờ vận hành thành 05:00–11:00
-- -------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_racetracks_operating_hours')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT ck_racetracks_operating_hours;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_racetracks_slot_minutes')
    ALTER TABLE dbo.racetracks DROP CONSTRAINT ck_racetracks_slot_minutes;

ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_opens_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_at;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_closes_next_day;
ALTER TABLE dbo.racetracks DROP CONSTRAINT IF EXISTS df_racetracks_slot_minutes;

ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_opens_at      DEFAULT '05:00:00' FOR opens_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_at     DEFAULT '11:00:00' FOR closes_at;
ALTER TABLE dbo.racetracks ADD CONSTRAINT df_racetracks_closes_next_day DEFAULT 0        FOR closes_next_day;
ALTER TABLE dbo.racetracks ADD CONSTRAINT ck_racetracks_operating_hours
    CHECK (opens_at = '05:00:00' AND closes_at = '11:00:00' AND closes_next_day = 0);

UPDATE dbo.racetracks SET opens_at = '05:00:00', closes_at = '11:00:00', closes_next_day = 0;

-- -------------------------------------------------------------------------
-- 2. TRAINING_SCHEDULES: thêm các cột mới (idempotent)
-- -------------------------------------------------------------------------

-- assigned_staff_id thành NULLABLE (staff chưa được gán khi mới tạo lịch)
ALTER TABLE dbo.training_schedules ALTER COLUMN assigned_staff_id UNIQUEIDENTIFIER NULL;

-- Cột racetrack_id
IF COL_LENGTH(N'dbo.training_schedules', N'racetrack_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD racetrack_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_racetrack
        FOREIGN KEY (racetrack_id) REFERENCES dbo.racetracks(id);
END;

-- Cột veterinarian_user_id
IF COL_LENGTH(N'dbo.training_schedules', N'veterinarian_user_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD veterinarian_user_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE dbo.training_schedules ADD CONSTRAINT fk_training_schedules_vet
        FOREIGN KEY (veterinarian_user_id) REFERENCES dbo.users(id);
END;

-- Cột audit claim
IF COL_LENGTH(N'dbo.training_schedules', N'staff_assigned_by') IS NULL
BEGIN
    ALTER TABLE dbo.training_schedules ADD staff_assigned_by  VARCHAR(20) NULL;  -- 'USER' | 'SYSTEM'
    ALTER TABLE dbo.training_schedules ADD staff_assigned_at  DATETIMEOFFSET NULL;
    ALTER TABLE dbo.training_schedules ADD vet_assigned_by    VARCHAR(20) NULL;  -- 'USER' | 'SYSTEM'
    ALTER TABLE dbo.training_schedules ADD vet_assigned_at    DATETIMEOFFSET NULL;
END;

-- -------------------------------------------------------------------------
-- 3. TRIGGERS chống trùng lịch
-- -------------------------------------------------------------------------

-- Trigger 1: nhân viên chuồng không trùng ca
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_staff_overlap
ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM dbo.training_schedules s
        JOIN inserted i ON s.assigned_staff_id = i.assigned_staff_id
                       AND s.id <> i.id
                       AND s.cancelled_at IS NULL
                       AND i.cancelled_at IS NULL
                       AND i.assigned_staff_id IS NOT NULL
        WHERE s.scheduled_start < i.scheduled_end
          AND s.scheduled_end   > i.scheduled_start
    )
        THROW 50061, N'Nhân viên chuồng đã nhận lịch huấn luyện khác trong khung giờ này.', 1;
END;
GO

-- Trigger 2: bác sĩ thú y không trùng ca
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_no_vet_overlap
ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM dbo.training_schedules s
        JOIN inserted i ON s.veterinarian_user_id = i.veterinarian_user_id
                       AND s.id <> i.id
                       AND s.cancelled_at IS NULL
                       AND i.cancelled_at IS NULL
                       AND i.veterinarian_user_id IS NOT NULL
        WHERE s.scheduled_start < i.scheduled_end
          AND s.scheduled_end   > i.scheduled_start
    )
        THROW 50062, N'Bác sĩ thú y đã nhận lịch theo dõi buổi tập khác trong khung giờ này.', 1;
END;
GO

-- Trigger 3: 1 sân chỉ phục vụ 1 ngựa/ngày
CREATE OR ALTER TRIGGER dbo.tr_training_schedules_ground_exclusivity
ON dbo.training_schedules AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM dbo.training_schedules s
        JOIN inserted i ON s.racetrack_id = i.racetrack_id
                       AND s.id <> i.id
                       AND s.cancelled_at IS NULL
                       AND i.cancelled_at IS NULL
                       AND i.racetrack_id IS NOT NULL
        WHERE s.scheduled_start < i.scheduled_end
          AND s.scheduled_end   > i.scheduled_start
    )
        THROW 50063, N'Sân tập đã được đăng ký cho ngựa khác trong ngày này (1 sân / 1 ngựa / ngày).', 1;
END;
GO

-- -------------------------------------------------------------------------
-- 4. QUYỀN: cấp PERM_TRAINING_CLAIM cho STABLE_STAFF và VETERINARIAN
-- -------------------------------------------------------------------------
ALTER TABLE dbo.role_permissions DROP CONSTRAINT IF EXISTS ck_role_permissions_action;
ALTER TABLE dbo.role_permissions ADD CONSTRAINT ck_role_permissions_action
    CHECK (action_code IN ('VIEW', 'EDIT', 'APPROVE', 'CLAIM'));

IF NOT EXISTS (SELECT 1 FROM dbo.role_permissions
               WHERE role_code='STABLE_STAFF' AND module_code='TRAINING' AND action_code='CLAIM')
    INSERT INTO dbo.role_permissions (role_code, module_code, action_code)
    VALUES ('STABLE_STAFF', 'TRAINING', 'CLAIM');

IF NOT EXISTS (SELECT 1 FROM dbo.role_permissions
               WHERE role_code='VETERINARIAN' AND module_code='TRAINING' AND action_code='CLAIM')
    INSERT INTO dbo.role_permissions (role_code, module_code, action_code)
    VALUES ('VETERINARIAN', 'TRAINING', 'CLAIM');
GO

-- -------------------------------------------------------------------------
-- 5. DỮ LIỆU MẪU: tạo 1 phiên tập ngày mai để test claim
-- -------------------------------------------------------------------------
-- Chạy khi cần test, comment lại sau khi đã có dữ liệu thật
/*
INSERT INTO training_schedules (
    id, horse_id, training_plan_phase_id,
    scheduled_start, scheduled_end,
    task_name, status,
    assigned_staff_id, veterinarian_user_id,
    created_by_user_id, created_at
)
SELECT
    NEWID(),
    (SELECT TOP 1 id FROM horses WHERE is_active = 1),
    (SELECT TOP 1 id FROM training_plan_phases),
    -- Ngày mai 05:00 ICT = 22:00 UTC hôm nay
    DATEADD(hour, -7, CAST(CAST(DATEADD(day,1,GETUTCDATE()) AS DATE) AS DATETIME) + ' 05:00:00'),
    DATEADD(hour, -7, CAST(CAST(DATEADD(day,1,GETUTCDATE()) AS DATE) AS DATETIME) + ' 11:00:00'),
    N'Phiên tập test - cần bác sĩ theo dõi',
    'PUBLISHED',
    NULL, NULL,
    (SELECT TOP 1 u.id FROM users u JOIN user_roles ur ON ur.user_id = u.id WHERE ur.role = 'HEAD_TRAINER'),
    SYSDATETIMEOFFSET();
*/
```

**Thực thi:** Chạy toàn bộ file trên **Azure SQL** qua Azure Portal → Query Editor hoặc Azure Data Studio.

**Verify sau khi chạy:**
```sql
-- Kiểm tra cột tồn tại
SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'training_schedules'
  AND COLUMN_NAME IN ('veterinarian_user_id','vet_assigned_by','vet_assigned_at','racetrack_id');

-- Kiểm tra quyền
SELECT * FROM role_permissions WHERE module_code='TRAINING' AND action_code='CLAIM';
```

---

## 3. Backend — API & Service

### 3.1 Danh sách file cần thay đổi

| File | Loại | Thay đổi |
|---|---|---|
| `training/entity/TrainingSchedule.java` | Sửa | +6 fields mới |
| `training/dto/TrainingScheduleResponse.java` | Sửa | +6 fields + `canClaim` flag |
| `training/repository/TrainingScheduleRepository.java` | Sửa | +5 native queries |
| `training/service/TrainingScheduleService.java` | Sửa | +3 methods |
| `training/controller/TrainingScheduleController.java` | Sửa | +2 endpoint, sửa 1 PreAuthorize |
| `training/job/TrainingAutoPickJob.java` | Tạo mới / sửa | +logic `pickVet()` |

> **Lưu ý:** `SecurityConfiguration.java` **không cần sửa** — `PERM_TRAINING_CLAIM` load tự động từ bảng `role_permissions` qua cơ chế RBAC hiện có.

---

### 3.2 `TrainingSchedule.java` — Fields cần thêm vào entity

```java
// Thêm vào class TrainingSchedule, sau field assignedStaffId

@Column(name = "staff_assigned_by")
private String staffAssignedBy;   // "USER" | "SYSTEM"

@Column(name = "staff_assigned_at")
private OffsetDateTime staffAssignedAt;

@Column(name = "racetrack_id")
private UUID racetrackId;

@Column(name = "veterinarian_user_id")
private UUID veterinarianUserId;

@Column(name = "vet_assigned_by")
private String vetAssignedBy;     // "USER" | "SYSTEM"

@Column(name = "vet_assigned_at")
private OffsetDateTime vetAssignedAt;

// Getter/Setter hoặc dùng @Data (Lombok)
```

---

### 3.3 `TrainingScheduleResponse.java` — DTO cần thêm

```java
// Thêm vào DTO record / class

UUID racetrackId;
String racetrackName;        // join từ racetracks.name

UUID veterinarianUserId;
String veterinarianName;     // join từ users.full_name
String vetAssignedBy;        // "USER" | "SYSTEM"
OffsetDateTime vetAssignedAt;

String staffAssignedBy;      // "USER" | "SYSTEM"
OffsetDateTime staffAssignedAt;

boolean canClaim;            // backend tính sẵn, frontend dùng để disable/enable nút
```

---

### 3.4 `TrainingScheduleRepository.java` — Native queries cần thêm

```java
// 1. Phiên mở cho Vet (vet slot trống, còn trước deadline)
@Query(value = """
    SELECT * FROM training_schedules
    WHERE veterinarian_user_id IS NULL
      AND cancelled_at IS NULL
      AND scheduled_start BETWEEN :from AND :to
      AND scheduled_start > :deadline
    ORDER BY scheduled_start
    """, nativeQuery = true)
List<TrainingSchedule> findOpenForVet(
    @Param("from") Instant from,
    @Param("to") Instant to,
    @Param("deadline") Instant deadline);

// 2. Phiên mở cho Staff (staff slot trống)
@Query(value = """
    SELECT * FROM training_schedules
    WHERE assigned_staff_id IS NULL
      AND cancelled_at IS NULL
      AND scheduled_start BETWEEN :from AND :to
      AND scheduled_start > :deadline
    ORDER BY scheduled_start
    """, nativeQuery = true)
List<TrainingSchedule> findOpenForStaff(
    @Param("from") Instant from,
    @Param("to") Instant to,
    @Param("deadline") Instant deadline);

// 3. Phiên đã nhận của Vet (cho /mine)
@Query(value = """
    SELECT * FROM training_schedules
    WHERE veterinarian_user_id = :vetId
      AND scheduled_start BETWEEN :from AND :to
    ORDER BY scheduled_start
    """, nativeQuery = true)
List<TrainingSchedule> findByVetId(
    @Param("vetId") UUID vetId,
    @Param("from") Instant from,
    @Param("to") Instant to);

// 4. Kiểm tra Vet đã có ca trong ngày (giới hạn 1 ca/ngày)
@Query(value = """
    SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
    FROM training_schedules
    WHERE veterinarian_user_id = :vetId
      AND cancelled_at IS NULL
      AND CAST(scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS DATE) = :date
    """, nativeQuery = true)
boolean existsByVetOnDate(
    @Param("vetId") UUID vetId,
    @Param("date") LocalDate date);

// 5. Kiểm tra Staff đã có ca trong ngày (giới hạn 1 ca/ngày)
@Query(value = """
    SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
    FROM training_schedules
    WHERE assigned_staff_id = :staffId
      AND cancelled_at IS NULL
      AND CAST(scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS DATE) = :date
    """, nativeQuery = true)
boolean existsByStaffOnDate(
    @Param("staffId") UUID staffId,
    @Param("date") LocalDate date);

// 6. Load với pessimistic lock (chống concurrent claim)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM TrainingSchedule s WHERE s.id = :id")
Optional<TrainingSchedule> findByIdWithLock(@Param("id") UUID id);

// 7. Danh sách session cần auto-pick cho ngày target
@Query(value = """
    SELECT * FROM training_schedules
    WHERE cancelled_at IS NULL
      AND (assigned_staff_id IS NULL OR veterinarian_user_id IS NULL)
      AND CAST(scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS DATE) = :targetDate
    """, nativeQuery = true)
List<TrainingSchedule> findNeedingAssignmentForDate(@Param("targetDate") LocalDate targetDate);
```

---

### 3.5 `TrainingScheduleService.java` — Methods cần thêm/sửa

```java
private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

// -------------------------------------------------------------------------
// CLAIM — nhận phiên tập (STABLE_STAFF hoặc VETERINARIAN)
// -------------------------------------------------------------------------
@Transactional
public TrainingScheduleResponse claim(UUID scheduleId, UUID callerId, boolean isVet) {

    // 1. Load schedule với PESSIMISTIC_WRITE lock
    TrainingSchedule s = scheduleRepository.findByIdWithLock(scheduleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch tập."));

    // 2. Kiểm tra chưa bị hủy
    if (s.getCancelledAt() != null)
        throw new ResponseStatusException(HttpStatus.GONE, "Phiên tập đã bị hủy.");

    // 3. Kiểm tra claim deadline (23:00 T-1 ICT)
    LocalDate trainingDate = s.getScheduledStart().atZone(CLUB_ZONE).toLocalDate();
    Instant deadline = trainingDate.minusDays(1).atTime(23, 0).atZone(CLUB_ZONE).toInstant();
    if (Instant.now().isAfter(deadline))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Thời hạn tự nhận phiên tập đã kết thúc vào 23:00 ngày hôm trước (T-1). " +
            "Phiên tập đã chuyển sang chế độ hệ thống điều phối tự động.");

    if (isVet) {
        // 4a. Kiểm tra slot vet trống
        if (s.getVeterinarianUserId() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Phiên tập đã có bác sĩ thú y đăng ký theo dõi.");
        // 4b. Kiểm tra 1 ca/ngày
        if (scheduleRepository.existsByVetOnDate(callerId, trainingDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bạn đã có lịch theo dõi tại phiên tập khác trong ngày này (tối đa 1 phiên/ngày).");
        // 4c. Gán
        s.setVeterinarianUserId(callerId);
        s.setVetAssignedBy("USER");
        s.setVetAssignedAt(OffsetDateTime.now());

    } else {
        // 5a. Kiểm tra slot staff trống
        if (s.getAssignedStaffId() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Phiên tập đã được nhân viên chuồng khác tiếp nhận.");
        // 5b. Kiểm tra 1 ca/ngày
        if (scheduleRepository.existsByStaffOnDate(callerId, trainingDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bạn đã có lịch làm việc tại phiên tập khác trong ngày này (tối đa 1 phiên/ngày).");
        // 5c. Gán
        s.setAssignedStaffId(callerId);
        s.setStaffAssignedBy("USER");
        s.setStaffAssignedAt(OffsetDateTime.now());
        // 5d. Tạo RECOVERY care task (idempotent)
        careTaskService.createRecoveryTask(s, callerId);
    }

    scheduleRepository.save(s);
    return toResponse(s);
}

// -------------------------------------------------------------------------
// FIND OPEN — danh sách phiên mở để claim
// -------------------------------------------------------------------------
public List<TrainingScheduleResponse> findOpenSessions(
        UUID callerId, boolean isVet, LocalDate from, LocalDate to) {
    LocalDate f = from != null ? from : LocalDate.now(CLUB_ZONE);
    LocalDate t = to   != null ? to   : f.plusDays(14);
    Instant fromI    = f.atTime(5, 0).atZone(CLUB_ZONE).toInstant();
    Instant toI      = t.atTime(23, 0).atZone(CLUB_ZONE).toInstant();
    // deadline = 23:00 hôm nay ICT (chỉ trả về session còn manual-claimable)
    Instant deadline = LocalDate.now(CLUB_ZONE).atTime(23, 0).atZone(CLUB_ZONE).toInstant();

    List<TrainingSchedule> list = isVet
            ? scheduleRepository.findOpenForVet(fromI, toI, deadline)
            : scheduleRepository.findOpenForStaff(fromI, toI, deadline);

    return list.stream()
            .map(s -> toResponseWithCanClaim(s, callerId, isVet))
            .toList();
}

// -------------------------------------------------------------------------
// MINE — mở rộng để hỗ trợ cả VETERINARIAN
// -------------------------------------------------------------------------
public List<TrainingScheduleResponse> mine(
        UUID callerId, boolean isVet, LocalDate from, LocalDate to) {
    Instant f = (from != null ? from : LocalDate.now(CLUB_ZONE).minusDays(30))
            .atStartOfDay(CLUB_ZONE).toInstant();
    Instant t = (to != null ? to : LocalDate.now(CLUB_ZONE).plusDays(30))
            .atTime(23, 59).atZone(CLUB_ZONE).toInstant();

    return (isVet
            ? scheduleRepository.findByVetId(callerId, f, t)
            : scheduleRepository.findByStaffId(callerId, f, t)
    ).stream().map(this::toResponse).toList();
}

// Helper: tính canClaim flag
private TrainingScheduleResponse toResponseWithCanClaim(
        TrainingSchedule s, UUID callerId, boolean isVet) {
    TrainingScheduleResponse r = toResponse(s);
    LocalDate trainingDate = s.getScheduledStart().atZone(CLUB_ZONE).toLocalDate();
    boolean notBusy = isVet
            ? !scheduleRepository.existsByVetOnDate(callerId, trainingDate)
            : !scheduleRepository.existsByStaffOnDate(callerId, trainingDate);
    r.setCanClaim(notBusy);
    return r;
}
```

---

### 3.6 `TrainingScheduleController.java` — Endpoints cần thêm/sửa

```java
// -------------------------------------------------------------------------
// THÊM MỚI: GET /api/training-schedules/open
// -------------------------------------------------------------------------
@GetMapping("/open")
@PreAuthorize("hasAuthority('PERM_TRAINING_CLAIM') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")
List<TrainingScheduleResponse> openSessions(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.findOpenSessions(userId(jwt), isVet(authentication), from, to);
}

// -------------------------------------------------------------------------
// THÊM MỚI: POST /api/training-schedules/{scheduleId}/claim
// -------------------------------------------------------------------------
@PostMapping("/{scheduleId}/claim")
@PreAuthorize("hasAuthority('PERM_TRAINING_CLAIM') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")
TrainingScheduleResponse claim(
        @PathVariable UUID scheduleId,
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication) {
    return service.claim(scheduleId, userId(jwt), isVet(authentication));
}

// -------------------------------------------------------------------------
// SỬA: GET /api/training-schedules/mine
// Thay @PreAuthorize cũ (chỉ STABLE_STAFF) → thêm VETERINARIAN
// -------------------------------------------------------------------------
@GetMapping("/mine")
@PreAuthorize("hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('STABLE_STAFF','VETERINARIAN')")
List<TrainingScheduleResponse> mine(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.mine(userId(jwt), isVet(authentication), from, to);
}

// -------------------------------------------------------------------------
// THÊM helper vào cuối class
// -------------------------------------------------------------------------
private boolean isVet(Authentication authentication) {
    return authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_VETERINARIAN"));
}
```

---

### 3.7 `TrainingAutoPickJob.java` — Tạo mới / bổ sung logic Vet

```java
@Component
public class TrainingAutoPickJob {

    private static final ZoneId CLUB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TrainingScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CareTaskService careTaskService;

    // Constructor injection...

    /**
     * Chạy lúc 23:00 ICT mỗi ngày.
     * Gán staff/vet cho các phiên tập ngày hôm sau chưa có người nhận.
     */
    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void run() {
        LocalDate targetDate = LocalDate.now(CLUB_ZONE).plusDays(1);
        List<TrainingSchedule> openSessions =
                scheduleRepository.findNeedingAssignmentForDate(targetDate);

        for (TrainingSchedule s : openSessions) {
            if (s.getAssignedStaffId() == null)    pickStaff(s, targetDate);
            if (s.getVeterinarianUserId() == null)  pickVet(s, targetDate);
        }
    }

    private void pickVet(TrainingSchedule s, LocalDate targetDate) {
        userRepository.findLeastAutoPickedVet(targetDate).ifPresentOrElse(
            vet -> {
                s.setVeterinarianUserId(vet.getId());
                s.setVetAssignedBy("SYSTEM");
                s.setVetAssignedAt(OffsetDateTime.now());
                scheduleRepository.save(s);
            },
            () -> notificationService.sendEscalation(
                "TRAINING_UNASSIGNED_VET_" + s.getId(),
                "Không còn bác sĩ thú y trống lịch cho buổi tập ngày "
                    + targetDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ". Yêu cầu Quản lý điều phối khẩn cấp."
            )
        );
    }

    private void pickStaff(TrainingSchedule s, LocalDate targetDate) {
        // Logic tương tự pickVet nhưng dùng findLeastAutoPickedStaff
        userRepository.findLeastAutoPickedStaff(targetDate).ifPresentOrElse(
            staff -> {
                s.setAssignedStaffId(staff.getId());
                s.setStaffAssignedBy("SYSTEM");
                s.setStaffAssignedAt(OffsetDateTime.now());
                scheduleRepository.save(s);
                careTaskService.createRecoveryTask(s, staff.getId());
            },
            () -> notificationService.sendEscalation(
                "TRAINING_UNASSIGNED_STAFF_" + s.getId(),
                "Không còn nhân viên chuồng trống lịch cho buổi tập ngày "
                    + targetDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ". Yêu cầu Quản lý điều phối khẩn cấp."
            )
        );
    }
}
```

**Native SQL cho `findLeastAutoPickedVet` (thêm vào UserRepository):**
```sql
SELECT TOP 1 u.* FROM users u
JOIN user_roles ur ON ur.user_id = u.id AND ur.role = 'VETERINARIAN'
WHERE u.is_enabled = 1
  AND u.id NOT IN (
      SELECT veterinarian_user_id FROM training_schedules
      WHERE cancelled_at IS NULL
        AND CAST(scheduled_start AT TIME ZONE 'SE Asia Standard Time' AS DATE) = :targetDate
        AND veterinarian_user_id IS NOT NULL
  )
ORDER BY (
    SELECT COUNT(*) FROM training_schedules
    WHERE veterinarian_user_id = u.id
      AND vet_assigned_by = 'SYSTEM'
      AND scheduled_start >= DATEADD(day, -30, SYSDATETIMEOFFSET())
) ASC,
u.id ASC   -- tie-break tất định, KHÔNG random
```

---

## 4. Frontend

### 4.1 `vetApi.js` — Thêm 3 methods

```js
// Training Supervision — theo REQUIREMENTS-new-2.md §5.3
// Thêm vào object vetApi, sau phần FR12 Preventive Care

getOpenTrainingSessions: (from, to) => {
  const q = new URLSearchParams()
  if (from) q.set('from', from)
  if (to)   q.set('to', to)
  return api(`/api/training-schedules/open?${q.toString()}`)
},

claimTrainingSession: (scheduleId) =>
  api(`/api/training-schedules/${scheduleId}/claim`, { method: 'POST' }),

getMyTrainingSessions: (from, to) => {
  const q = new URLSearchParams()
  if (from) q.set('from', from)
  if (to)   q.set('to', to)
  return api(`/api/training-schedules/mine?${q.toString()}`)
},
```

---

### 4.2 `VetPage.jsx` — Component `TrainingSupervisionView`

**Thêm tab navigation** (vào mảng sidebar items trong `App.jsx`, sau "Dinh dưỡng"):
```
{ path: '/vet/training', label: '📋 Theo dõi buổi tập' }
```

**Component đầy đủ:**

```jsx
// ============================================================
// TrainingSupervisionView — Tab "Theo dõi buổi tập"
// ============================================================
function TrainingSupervisionView() {
  const [subTab, setSubTab]             = useState('open');
  const [openSessions, setOpenSessions] = useState([]);
  const [mySessions, setMySessions]     = useState([]);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState(null);

  // Date range state
  const todayStr  = () => new Date().toISOString().slice(0, 10);
  const futureDt  = (n) => new Date(Date.now() + n*86400000).toISOString().slice(0, 10);
  const [dateFrom, setDateFrom] = useState(todayStr());
  const [dateTo,   setDateTo]   = useState(futureDt(14));

  useEffect(() => {
    if (subTab === 'open') loadOpen();
    else                   loadMine();
  }, [subTab, dateFrom, dateTo]);

  async function loadOpen() {
    setLoading(true); setError(null);
    try {
      const data = await vetApi.getOpenTrainingSessions(dateFrom, dateTo);
      setOpenSessions(Array.isArray(data) ? data : data.content ?? []);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }

  async function loadMine() {
    setLoading(true); setError(null);
    try {
      const data = await vetApi.getMyTrainingSessions(dateFrom, dateTo);
      setMySessions(Array.isArray(data) ? data : data.content ?? []);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }

  async function handleClaim(scheduleId) {
    if (!window.confirm('Xác nhận đăng ký theo dõi phiên tập này?')) return;
    try {
      await vetApi.claimTrainingSession(scheduleId);
      loadOpen(); // refresh
    } catch (e) {
      alert('Lỗi: ' + e.message);
    }
  }

  return (
    <div className="training-supervision-view">
      {/* Sub-tab switcher */}
      <div className="tab-buttons" style={{ display:'flex', gap:8, marginBottom:16 }}>
        <button
          className={`tab-btn ${subTab==='open' ? 'active':''}`}
          onClick={() => setSubTab('open')}
        >
          Phiên tập cần theo dõi
        </button>
        <button
          className={`tab-btn ${subTab==='mine' ? 'active':''}`}
          onClick={() => setSubTab('mine')}
        >
          Lịch theo dõi của tôi
        </button>
      </div>

      {/* Date range filter */}
      <div className="filter-row" style={{ display:'flex', gap:12, alignItems:'center', marginBottom:12 }}>
        <label>Từ ngày:</label>
        <input type="date" value={dateFrom} onChange={e => setDateFrom(e.target.value)} />
        <label>Đến ngày:</label>
        <input type="date" value={dateTo}   onChange={e => setDateTo(e.target.value)}   />
      </div>

      {/* Banner nhắc thời gian trực (chỉ hiện tab open) */}
      {subTab === 'open' && (
        <div className="info-banner" style={{
          background:'#e8f4fd', border:'1px solid #b3d7f5',
          borderRadius:8, padding:'10px 14px', marginBottom:14, fontSize:14
        }}>
          ⏰ Yêu cầu có mặt trực tiếp tại sân:{' '}
          <strong>10:00 – 11:00</strong> để kiểm tra thể trạng và hồi phục sau buổi tập.
        </div>
      )}

      {/* Error */}
      {error && <div className="error-banner">{error}</div>}

      {/* Loading */}
      {loading && <div className="loading-spinner">Đang tải...</div>}

      {/* Bảng phiên tập mở */}
      {!loading && subTab === 'open' && (
        <table className="vet-table">
          <thead>
            <tr>
              <th>Ngày tập</th>
              <th>Ngựa</th>
              <th>Sân tập</th>
              <th>Khung giờ vận hành</th>
              <th>Nhân viên chuồng</th>
              <th>Trạng thái Thú y</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            {openSessions.map(s => (
              <tr key={s.id}>
                <td>{formatDate(s.trainingDate ?? s.scheduledStart)}</td>
                <td>{s.horseName} <span className="horse-code">({s.horseCode})</span></td>
                <td>{s.racetrackName ?? '—'}</td>
                <td>
                  <span className="time-window-badge">05:00 – 11:00</span>
                </td>
                <td>{staffBadge(s)}</td>
                <td>{vetBadge(s)}</td>
                <td>
                  <button
                    className="btn-claim"
                    disabled={!s.canClaim}
                    title={s.canClaim ? 'Đăng ký theo dõi phiên tập này' : 'Bạn đã có lịch theo dõi trong ngày này'}
                    onClick={() => handleClaim(s.id)}
                  >
                    Đăng ký theo dõi
                  </button>
                </td>
              </tr>
            ))}
            {openSessions.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign:'center', color:'#888', padding:24 }}>
                  Không có phiên tập nào cần theo dõi trong khoảng thời gian này
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      {/* Bảng lịch của tôi */}
      {!loading && subTab === 'mine' && (
        <table className="vet-table">
          <thead>
            <tr>
              <th>Ngày tập</th>
              <th>Ngựa</th>
              <th>Sân tập</th>
              <th>Khung giờ vận hành</th>
              <th>Nhân viên chuồng</th>
              <th>Trạng thái phân công</th>
              <th>Khám lâm sàng</th>
            </tr>
          </thead>
          <tbody>
            {mySessions.map(s => (
              <tr key={s.id}>
                <td>{formatDate(s.trainingDate ?? s.scheduledStart)}</td>
                <td>{s.horseName} <span className="horse-code">({s.horseCode})</span></td>
                <td>{s.racetrackName ?? '—'}</td>
                <td><span className="time-window-badge">05:00 – 11:00</span></td>
                <td>{staffBadge(s)}</td>
                <td>{vetBadge(s)}</td>
                <td>
                  {/* Link sang tab khám bệnh, filter theo horse */}
                  <button
                    className="btn-link"
                    onClick={() => window.dispatchEvent(new CustomEvent('vet-nav', {
                      detail: { tab: 'medical', horseId: s.horseId }
                    }))}
                  >
                    Xem hồ sơ khám
                  </button>
                </td>
              </tr>
            ))}
            {mySessions.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign:'center', color:'#888', padding:24 }}>
                  Chưa có lịch theo dõi nào được gán
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}

// ============================================================
// Badge helpers
// ============================================================
function vetBadge(s) {
  if (!s.veterinarianUserId)
    return <span className="badge badge-warning">Chờ nhận</span>;
  if (s.vetAssignedBy === 'USER')
    return <span className="badge badge-success">Tự nhận</span>;
  return <span className="badge badge-secondary">Hệ thống điều phối</span>;
}

function staffBadge(s) {
  if (!s.assignedStaffId)
    return <span className="badge badge-warning">Chờ nhận</span>;
  if (s.staffAssignedBy === 'USER')
    return <span className="badge badge-success">{s.staffName ?? 'Tự nhận'}</span>;
  return <span className="badge badge-secondary">{s.staffName ?? 'Hệ thống điều phối'}</span>;
}

// Helper format ngày
function formatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleDateString('vi-VN', { day:'2-digit', month:'2-digit', year:'numeric' });
}
```

---

## 5. Checklist thực thi (theo thứ tự bắt buộc)

> **QUAN TRỌNG:** Bước DB phải chạy **trước** khi backend compile, vì entity cần các cột mới tồn tại trong DB.

### Bước 1 — Database Migration
- [ ] Tạo file `DATABASE/15_training_operational_window.sql` (nội dung §2)
- [ ] Chạy SQL trên **Azure SQL** (Portal Query Editor hoặc Azure Data Studio)
- [ ] Verify: `SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'training_schedules' AND COLUMN_NAME IN ('veterinarian_user_id','vet_assigned_by','racetrack_id')` → 3 rows
- [ ] Verify: `SELECT * FROM role_permissions WHERE role_code='VETERINARIAN' AND action_code='CLAIM'` → 1 row

### Bước 2 — Backend Entity & DTO
- [ ] Thêm 6 fields vào `TrainingSchedule.java` (§3.2)
- [ ] Thêm fields + `canClaim` vào `TrainingScheduleResponse.java` (§3.3)

### Bước 3 — Backend Repository
- [ ] Thêm 6 native queries vào `TrainingScheduleRepository.java` (§3.4)
- [ ] Thêm `findLeastAutoPickedVet` và `findLeastAutoPickedStaff` vào `UserRepository`

### Bước 4 — Backend Service
- [ ] Implement `claim()` trong `TrainingScheduleService.java` (§3.5)
- [ ] Implement `findOpenSessions()` (§3.5)
- [ ] Mở rộng `mine()` hỗ trợ `isVet=true` (§3.5)

### Bước 5 — Backend Controller
- [ ] Thêm `GET /open` endpoint (§3.6)
- [ ] Thêm `POST /{id}/claim` endpoint (§3.6)
- [ ] Sửa `@PreAuthorize` của `GET /mine` cho phép VETERINARIAN (§3.6)
- [ ] Thêm helper `isVet(Authentication)` method (§3.6)

### Bước 6 — Backend Auto-Pick Job
- [ ] Tạo hoặc mở rộng `TrainingAutoPickJob.java` (§3.7)
- [ ] Verify `@EnableScheduling` có trong main class

### Bước 7 — Backend Build & Test
- [ ] `mvn clean package -DskipTests` → BUILD SUCCESS
- [ ] `POST /api/training-schedules/{id}/claim` (token VETERINARIAN) → `200 OK`
- [ ] `GET /api/training-schedules/open` (token VETERINARIAN) → danh sách + `canClaim` flag
- [ ] `GET /api/training-schedules/mine` (token VETERINARIAN) → lịch đã nhận
- [ ] Test deadline: claim sau 23:00 T-1 → `400 Bad Request`
- [ ] Test conflict: 2 vet claim cùng lúc → 1 thành công, 1 nhận `409 Conflict`

### Bước 8 — Frontend vetApi.js
- [ ] Thêm 3 methods vào `vetApi.js` (§4.1)

### Bước 9 — Frontend VetPage.jsx
- [ ] Tạo component `TrainingSupervisionView` (§4.2)
- [ ] Thêm tab "📋 Theo dõi buổi tập" vào navigation

### Bước 10 — Frontend Build & E2E
- [ ] `npm run build` → 0 lỗi
- [ ] Đăng nhập `bacsithuy@gmail.com` / `12345678`
- [ ] Tab "Theo dõi buổi tập" hiển thị trong sidebar
- [ ] Sub-tab "Phiên tập cần theo dõi": bảng + banner "10:00–11:00"
- [ ] Bấm "Đăng ký theo dõi" → thành công → session biến mất khỏi open list
- [ ] Sub-tab "Lịch theo dõi của tôi" → session vừa claim xuất hiện với badge "Tự nhận"

---

## 6. Lưu ý đặc biệt

| # | Cảnh báo |
|---|---|
| 1 | `PERM_TRAINING_CLAIM` chỉ cấp cho `STABLE_STAFF` và `VETERINARIAN`. **Tuyệt đối không cấp cho `CLUB_MANAGER`**. |
| 2 | Claim deadline check **bắt buộc dùng `Asia/Ho_Chi_Minh` timezone**, không dùng UTC thô. |
| 3 | `findByIdWithLock` phải dùng `PESSIMISTIC_WRITE` — bắt buộc để tránh 2 vet claim đồng thời. |
| 4 | `GET /mine` khi sửa PreAuthorize: phải dùng `isVet` để phân nhánh query, tránh lộ data chéo giữa STABLE_STAFF và VETERINARIAN. |
| 5 | `canClaim` flag tính ở backend, trả về DTO — frontend chỉ dùng để disable/enable nút, không tự tính. |
| 6 | Auto-pick tie-break: `user_id ASC` (tất định), **cấm random**. |
| 7 | RECOVERY care task chỉ tạo khi **STABLE_STAFF được gán**, không tạo khi Vet claim. |

---

## 7. Tổng kết số lượng thay đổi

| Loại | File | Ghi chú |
|---|---|---|
| SQL Migration | `DATABASE/15_training_operational_window.sql` (mới) | 4 cột, 3 triggers, 2 permissions |
| Backend Entity | `training/entity/TrainingSchedule.java` (sửa) | +6 fields |
| Backend DTO | `training/dto/TrainingScheduleResponse.java` (sửa) | +6 fields + canClaim |
| Backend Repository | `training/repository/TrainingScheduleRepository.java` (sửa) | +6 queries |
| Backend Service | `training/service/TrainingScheduleService.java` (sửa) | +3 methods |
| Backend Controller | `training/controller/TrainingScheduleController.java` (sửa) | +2 endpoint, sửa 1 |
| Backend Job | `training/job/TrainingAutoPickJob.java` (tạo mới / sửa) | +pickVet() |
| Frontend API | `FRONTEND/src/vetApi.js` (sửa) | +3 methods |
| Frontend UI | `FRONTEND/src/VetPage.jsx` (sửa) | +TrainingSupervisionView |
| **Tổng** | **9 thay đổi** | **0 bảng DB mới** (giữ ≤ 30 tables) |
