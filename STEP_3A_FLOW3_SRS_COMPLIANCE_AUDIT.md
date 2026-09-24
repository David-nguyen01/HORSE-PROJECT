# STEP 3A — FLOW 3 SRS COMPLIANCE AUDIT REPORT

**Repository:** SWP Racehorse Training Management System  
**Audit Scope:** Flow 3 (FR08, FR09, FR10, FR11, FR12) — Backend Only  
**Date:** 2026-09-24  
**Auditor:** Antigravity  
**Audit Mode:** READ-ONLY AUDIT (Zero code changes, zero DB changes, zero migrations, zero test executions)  
**Final Status:** **NEEDS FIX**  

---

## 1. SRS FR08 Requirements

### 1.1 Official SRS v1.0 Extraction
* **Module:** Luồng 3: Quản lý Y tế & Xử lý Chấn thương
* **Feature Name:** FR08 – Bản đồ Trạng thái Sức khỏe Toàn Chuồng (Barn-wide Health Status Map)
* **Actor (Vai trò):** Bác sĩ thú y (Veterinarian).
* **Scope (Phạm vi):**
  - Cung cấp cho Bác sĩ thú y một cái nhìn tổng quan trực quan, toàn chuồng về phân loại sức khỏe hiện tại của từng con ngựa.
  - Bao gồm bản đồ hoặc lưới mã màu hiển thị trạng thái:
    - Đủ điều kiện (`FIT`)
    - Cần Theo dõi (`MONITOR`)
    - Bị thương (`INJURED`)
    - Cách ly (`QUARANTINED`)
  - Không bao gồm hồ sơ lâm sàng chi tiết đằng sau mỗi trạng thái (thuộc FR09).
* **Functional Requirements (Yêu cầu chức năng):**
  1. Hệ thống hiển thị bản đồ hoặc lưới chuồng, trong đó vị trí ô chuồng/vị trí của mỗi con ngựa được mã màu theo trạng thái sức khỏe.
  2. Hệ thống cho phép Bác sĩ thú y lọc bản đồ theo danh mục trạng thái.
  3. Các thay đổi trạng thái được thực hiện tại FR09 hoặc FR11 sẽ tự động cập nhật màu sắc trên bản đồ này theo thời gian thực.
* **Verification / Acceptance (Kiểm định):**
  1. Chọn một con ngựa trên bản đồ sẽ mở tóm tắt y tế của nó trong vòng 2 lần nhấp.
  2. Thay đổi trạng thái được ghi nhận ở nơi khác trong hệ thống được phản ánh trên bản đồ mà không cần tải lại trang.

---

## 2. FR08 Compliance Matrix

| Requirement | Implementation Component | DB / Contract | Status | Evidence / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Health Status Enum (`FIT`, `MONITOR`, `INJURED`, `QUARANTINED`)** | `HorseMeasurementService.java` | `dbo.horses.health_status` | **COMPLETE** | `[FACT]` Check constraint `ck_horses_health_status` on `dbo.horses` supports all 4 values. Validated in `HorseMeasurementService.updateHealthStatus`. |
| **Individual Horse Health Status Update** | `PUT /api/horses/{horseId}/health-status` | `dbo.horses.health_status` | **COMPLETE** | `[FACT]` `HorseMeasurementController.updateHealth` allows `VETERINARIAN` with `PERM_MEDICAL_EDIT`. |
| **Individual Horse Health Status View** | `GET /api/horses/{horseId}/health-status` | `dbo.horses.health_status` | **COMPLETE** | `[FACT]` `HorseMeasurementController.health` allows `PERM_MEDICAL_VIEW`, scoped by ownership. |
| **Barn-wide Health Map / Grid (Stall + Horse + Color)** | *None* | `dbo.stables` + `dbo.module_records` (Stall) + `dbo.horses` | **MISSING** | `[FACT]` No endpoint exists returning all stalls, assigned horses, and their health statuses together. `StableController` returns stalls without health status. |
| **Filter Barn Map by Health Category** | *None* | *None* | **MISSING** | `[FACT]` No query or controller parameter exists to filter a barn grid by health category. |
| **Real-time update on status change (FR09/FR11)** | *None* | *None* | **MISSING / BUG** | `[FACT]` No WebSocket/SSE push exists. Furthermore, `TrainingLockService.lock` (FR11) does NOT update `dbo.horses.health_status` at all! |
| **Open Medical Summary on Horse Click** | *None* | *None* | **MISSING** | `[FACT]` No consolidated medical summary endpoint exists (clinical records in FR09 are unimplemented). |

---

## 3. SRS FR09 Requirements

### 3.1 Official SRS v1.0 Extraction
* **Feature Name:** FR09 – Khám bệnh, Chẩn đoán & Hồ sơ Điều trị (Medical Examination, Diagnosis & Treatment Records)
* **Actor (Vai trò):** Bác sĩ thú y (Veterinarian).
* **Scope (Phạm vi):**
  - Bao quát toàn bộ quy trình ghi nhận lâm sàng cho một lần khám ngựa.
  - Bao gồm ghi nhận chẩn đoán, phác đồ điều trị được kê, và lịch sử kê đơn thuốc.
  - Không bao gồm việc lên lịch chăm sóc móng/thợ đóng móng (thuộc FR12).
* **Functional Requirements (Yêu cầu chức năng):**
  1. Hệ thống cung cấp biểu mẫu khám bệnh với các trường:
     - Ngày Khám (`examinationDate`)
     - Bác sĩ thú y (`veterinarian`)
     - Triệu chứng (`symptoms`)
     - Chẩn đoán (`diagnosis`)
     - Phác đồ Điều trị (`treatmentPlan`)
     - Thuốc/Liều lượng (`medicationAndDosage`)
     - Ngày Tái khám (`followUpDate`)
  2. Hệ thống duy trì nhật ký lịch sử y tế theo trình tự thời gian, chỉ đọc, cho từng con ngựa sau khi các mục được lưu.
  3. Hệ thống cho phép Bác sĩ thú y cập nhật trạng thái sức khỏe của ngựa trực tiếp từ màn hình này.
* **Verification / Acceptance (Kiểm định):**
  1. Mọi bản ghi khám bệnh đã gửi đều được đóng dấu thời gian, gán cho Bác sĩ thú y thực hiện khám, và **không thể bị xóa (chỉ có thể bổ sung/chỉnh sửa qua phụ lục — immutable addenda)**.
  2. Trạng thái sức khỏe đã cập nhật được phản ánh trên Bản đồ Trạng thái Sức khỏe (FR08) ngay sau khi lưu.

---

## 4. FR09 Compliance Matrix

| Requirement | Implementation Component | DB / Contract | Status | Evidence / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Examination Form Fields (Date, Vet, Symptoms, Diagnosis, Treatment, Medication, Follow-up)** | *None* | `dbo.module_records(module_code='MEDICAL')` | **MISSING** | `[FACT]` Zero Java classes exist for Medical Examinations (`MedicalExaminationController`, `MedicalExaminationService` do not exist). |
| **Chronological Read-Only Medical History per Horse** | *None* | `dbo.module_records` | **MISSING** | `[FACT]` No GET endpoint exists to list medical history for a horse. |
| **Direct Health Status Update from Examination** | `PUT /api/horses/{id}/health-status` | `dbo.horses.health_status` | **PARTIAL** | `[FACT]` Individual health update exists independently, but is not linked to any examination record creation. |
| **Immutability: No Deletion allowed** | *None* | `12_compact_schema.sql` dropped `tr_medical_examinations_no_delete` | **MISSING** | `[FACT]` Dropped in compact schema; must be enforced by backend service logic when saving to `module_records`. |
| **Append-Only Addenda (Phụ lục)** | *None* | `dbo.module_records` | **MISSING** | `[FACT]` No endpoint or entity for appending addenda to an existing examination record. |
| **Audit & Timestamping** | *None* | `dbo.module_records.created_at` | **MISSING** | `[FACT]` Backend implementation does not exist. |

---

## 5. SRS FR10 Requirements

### 5.1 Official SRS v1.0 Extraction
* **Feature Name:** FR10 – Đánh dấu Chấn thương 3D & Theo dõi Hồi phục (3D Injury Marking & Recovery Tracking)
* **Actor (Vai trò):** Bác sĩ thú y (Veterinarian).
* **Scope (Phạm vi):**
  - Cho phép Bác sĩ thú y đánh dấu và theo dõi trực quan vị trí chấn thương trên mô hình giải phẫu 3D của ngựa.
  - Bao gồm ghi chú tiến độ hồi phục theo thời gian trên cùng một mô hình.
  - Không bao gồm bản thân phác đồ điều trị (thuộc FR09).
* **Functional Requirements (Yêu cầu chức năng):**
  1. Hệ thống hiển thị mô hình cơ/xương 3D tương tác, nơi Bác sĩ thú y có thể đặt điểm đánh dấu chỉ vị trí và mức độ nghiêm trọng của chấn thương.
  2. Hệ thống cho phép đính kèm ghi chú, ngày tháng, và tệp hình ảnh (X-quang/siêu âm) vào mỗi điểm đánh dấu.
  3. Hệ thống duy trì dòng thời gian hiển thị cách trạng thái của một chấn thương đã đánh dấu thay đổi qua các lần khám liên tiếp (ví dụ: Cấp tính → Đang hồi phục → Đã khỏi).
* **Verification / Acceptance (Kiểm định):**
  1. Điểm đánh dấu đã đặt được lưu giữ chính xác trên mô hình 3D và có thể truy xuất lại trong các phiên làm việc sau.
  2. Dòng thời gian hồi phục sắp xếp đúng trình tự thời gian tất cả các cập nhật điểm đánh dấu cho chấn thương được chọn.

---

## 6. FR10 Compliance Matrix

| Requirement | Implementation Component | DB / Contract | Status | Evidence / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **3D Injury Marker Placement (Coordinates, Body part, Severity)** | *None* | `dbo.module_records(module_code='MEDICAL', record_type='INJURY_MARKER')` | **MISSING** | `[FACT]` Zero Java classes exist for Injury Markers. No endpoint exists to record 3D markers. |
| **Attach Notes, Dates & Image Files (X-ray/Ultrasound)** | *None* | `dbo.stored_files` / Cloudinary URL in JSON | **MISSING** | `[FACT]` Image upload exists generically via `StoredFileController`, but no association exists to injury markers. |
| **Recovery Timeline (Progression: Acute → Recovering → Healed)** | *None* | `dbo.module_records(record_type='INJURY_RECOVERY_UPDATE')` | **MISSING** | `[FACT]` No endpoint or data structure exists for recording timeline recovery updates. |
| **3D Anatomical Model & Coordinate System Specification** | *N/A (Client visualization)* | *NOT SPECIFIED BY SRS* | **BLOCKED / SRS UNDERSPECIFIED** | `[FACT]` SRS v1.0 mentions an interactive 3D model, but specifies zero technical details regarding 3D file formats (GLB/OBJ), coordinate system space, or anatomical part taxonomy. |

---

## 7. SRS FR11 Requirements

### 7.1 Official SRS v1.0 Extraction
* **Feature Name:** FR11 – Lệnh Khóa Huấn luyện Khẩn cấp (Emergency Training Lock)
* **Actor (Vai trò):** Bác sĩ thú y (Veterinarian) và Quản lý Câu lạc bộ (Club Manager).
* **Scope (Phạm vi):**
  - Cung cấp cho Bác sĩ thú y quyền ghi đè khẩn cấp để ngay lập tức hạn chế một con ngựa bị thương khỏi việc được lên lịch tập luyện cường độ cao.
  - Bao gồm việc thực thi tự động trên tất cả các màn hình lên lịch huấn luyện.
  - Không bao gồm quy trình gỡ khóa (được bao gồm như một phần của luồng công việc phát hành thuộc cùng tính năng này).
* **Functional Requirements (Yêu cầu chức năng):**
  1. Hệ thống cung cấp thao tác 'Khóa Huấn luyện' chỉ với một lần nhấp trên hồ sơ ngựa, chỉ dành cho vai trò Bác sĩ thú y và Quản lý Câu lạc bộ.
  2. Sau khi bị khóa, hệ thống ngăn Huấn luyện viên trưởng phân công con ngựa đó vào bất kỳ buổi tập cường độ cao nào (FR04/FR05) và hiển thị cảnh báo chặn.
  3. Hệ thống yêu cầu Bác sĩ thú y nhập lý do và có thể yêu cầu ngày tái khám trước khi lệnh khóa được gỡ bỏ.
* **Verification / Acceptance (Kiểm định):**
  1. Việc cố gắng lên lịch cho một con ngựa đang bị khóa vào buổi tập cường độ cao sẽ bị chặn với lời giải thích rõ ràng trên màn hình, tham chiếu đến lệnh khóa.
  2. Trạng thái khóa hiển thị trên Bản đồ Trạng thái Sức khỏe và hồ sơ ngựa ngay sau khi được kích hoạt.
  3. *(From FR18)*: Một con ngựa đang trong tình trạng Khóa Huấn luyện đang hoạt động (FR11) hoặc có trạng thái Bị thương/Cách ly không thể được đăng ký tham gia đua.

---

## 8. FR11 Compliance Matrix

| Requirement | Implementation Component | DB / Contract | Status | Evidence / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **One-Click Lock Action for Vet & Club Manager** | `POST /api/horses/{horseId}/training-locks` | `dbo.training_locks` | **COMPLETE** | `[FACT]` `TrainingLockController.lock` enforces `@PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasAnyRole('VETERINARIAN','CLUB_MANAGER')")`. |
| **Reason & Review Date Input** | `CreateTrainingLockRequest(reason, reviewDate)` | `dbo.training_locks.reason`, `review_date` | **COMPLETE** | `[FACT]` Validated in request DTO and persisted to entity. |
| **Block Release Prior to Review Date** | `TrainingLockService.release` | `dbo.training_locks.released_at` | **COMPLETE** | `[FACT]` Throws `BusinessException("Chưa đến ngày tái khám nên chưa thể gỡ khóa huấn luyện")` if `today.isBefore(trainingLock.getReviewDate())`. |
| **Release Action for Veterinarian** | `POST /api/training-locks/{lockId}/release` | `dbo.training_locks.released_by_user_id` | **COMPLETE** | `[FACT]` Enforces `@PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT') and hasRole('VETERINARIAN')")`. |
| **Block High-Intensity Schedule (FR05)** | `TrainingScheduleService.create` | `dbo.tr_training_schedules_plan_and_lock` | **COMPLETE** | `[FACT]` Java service and SQL Server trigger throw 51013 if `workload_intensity = 'HIGH'` while active lock exists. |
| **Block Race Registration (FR18)** | `RaceEventService.register` | `RaceReadinessResponse` | **COMPLETE** | `[FACT]` `RaceEventService.register` line 84 verifies `readiness.lockCount == 0`. |
| **Auto-Sync Lock to Horse Health Status** | `TrainingLockService.lock` & `release` | `dbo.horses.health_status` | **BUG / MISSING** | `[FACT]` `TrainingLockService.lock` only saves to `training_locks`. It never updates `dbo.horses.health_status` to `INJURED`, violating FR08/FR11 sync requirement. |
| **Horse Owner Lock Visibility** | `TrainingLockController.current` & `history` | *None* | **BUG / NON-COMPLIANT** | `[FACT]` Both endpoints use `hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER')`, completely blocking `HORSE_OWNER` from seeing lock status. |
| **Service Horse Read Access Check** | `TrainingLockService.current` & `history` | *None* | **BUG / MISSING** | `[FACT]` Only calls `requireActive`, misses `requireReadable(horseId, userId, privileged)`. |

---

## 9. SRS FR12 Requirements

### 9.1 Official SRS v1.0 Extraction
* **Feature Name:** FR12 – Lên lịch Chăm sóc Phòng ngừa (Vắc-xin, Tẩy giun, Đóng móng) (Preventive Care Scheduling)
* **Actor (Vai trò):** Bác sĩ thú y (Veterinarian).
* **Scope (Phạm vi):**
  - Bao quát việc lên lịch và hệ thống nhắc nhở tự động cho công tác chăm sóc phòng ngừa định kỳ.
  - Bao gồm theo dõi lịch tiêm vắc-xin, tẩy giun, và đóng móng (chăm sóc móng guốc).
  - Không bao gồm các lần khám thú y khẩn cấp/phát sinh từ sự cố (thuộc FR09).
* **Functional Requirements (Yêu cầu chức năng):**
  1. Hệ thống cho phép Bác sĩ thú y xác định lịch chăm sóc phòng ngừa định kỳ hoặc một lần cho từng con ngựa (loại hình, ngày đến hạn, chu kỳ).
  2. Hệ thống gửi thông báo tự động cho Bác sĩ thú y và Huấn luyện viên trưởng trước ngày đến hạn sắp tới (thời gian báo trước có thể cấu hình).
  3. Hệ thống ghi lại việc hoàn thành mỗi nhiệm vụ chăm sóc phòng ngừa kèm ngày tháng và bên thực hiện.
* **Verification / Acceptance (Kiểm định):**
  1. Một nhiệm vụ chăm sóc phòng ngừa đến hạn hoặc quá hạn được đánh dấu nổi bật trên bảng điều khiển của Bác sĩ thú y.
  2. Đánh dấu một nhiệm vụ là hoàn thành sẽ tự động lên lịch cho lần lặp lại tiếp theo theo chu kỳ đã xác định.

---

## 10. FR12 Compliance Matrix

| Requirement | Implementation Component | DB / Contract | Status | Evidence / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Define Preventive Schedule (`VACCINATION`, `DEWORMING`, `FARRIER`)** | `POST /api/horses/{horseId}/preventive-care` | `dbo.preventive_care_schedules` | **COMPLETE** | `[FACT]` Supports due date, recurrence days, reminder lead days. Restricted to `VETERINARIAN` with `PERM_MEDICAL_EDIT`. |
| **Mark Task Completed with Performer & Date** | `POST /api/preventive-care/{id}/complete` | `dbo.preventive_care_schedules.completed_at` | **COMPLETE** | `[FACT]` Records performing user, completion time, completion notes. |
| **Auto-Schedule Next Recurrence on Completion** | `PreventiveCareService.complete` | `next_schedule_id` | **COMPLETE** | `[FACT]` When `recurrence_days` is present, inserts next schedule with `due_date = dateadd(day, recurrence_days, due_date)`. |
| **Automated Due Reminders via Scheduled Job** | `DailyAutomationJob.remindPreventiveCare` | `dbo.notifications` | **PARTIAL / BUG** | `[FACT]` Cron runs daily at 06:00, generates notifications with deduplication key. BUT: notifies ONLY `VETERINARIAN`, completely omitting `HEAD_TRAINER`! |
| **Vet Dashboard: Due / Overdue Tasks Across All Horses** | *None* | *None* | **MISSING** | `[FACT]` Only single-horse list exists (`GET /api/horses/{horseId}/preventive-care`). No club-wide due/overdue dashboard endpoint exists for Veterinarian. |
| **Automated Test Coverage** | *None* | *None* | **MISSING** | `[FACT]` 0 tests exist for `PreventiveCareService`, `PreventiveCareController`, and `DailyAutomationJob`. |

---

## 11. RBAC Audit

### 11.1 Flow 3 Endpoints RBAC Analysis

| Method | Endpoint | Implemented Permission Check | SRS Requirement | Result |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/horses/{horseId}/weights` | `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')` | Veterinarian | **COMPLIANT** |
| `GET` | `/api/horses/{horseId}/weights` | `hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')` | Staff + Horse Owner | **COMPLIANT** |
| `GET` | `/api/horses/{horseId}/race-readiness` | `hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')` | Staff + Horse Owner | **COMPLIANT** |
| `PUT` | `/api/horses/{horseId}/health-status` | `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')` | Veterinarian | **COMPLIANT** |
| `GET` | `/api/horses/{horseId}/health-status` | `hasAuthority('PERM_MEDICAL_VIEW')` | Staff + Horse Owner | **COMPLIANT** |
| `POST` | `/api/horses/{horseId}/training-locks` | `hasAuthority('PERM_MEDICAL_EDIT') and hasAnyRole('VETERINARIAN','CLUB_MANAGER')` | Veterinarian, Club Manager | **COMPLIANT** |
| `POST` | `/api/training-locks/{lockId}/release` | `hasAuthority('PERM_MEDICAL_EDIT') and hasRole('VETERINARIAN')` | Veterinarian | **COMPLIANT** |
| `GET` | `/api/horses/{horseId}/training-locks/current` | `hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER')` | Staff + Horse Owner | **NON-COMPLIANT** (Owner blocked) |
| `GET` | `/api/horses/{horseId}/training-locks` | `hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER')` | Staff + Horse Owner | **NON-COMPLIANT** (Owner blocked) |
| `POST` | `/api/horses/{horseId}/preventive-care` | `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')` | Veterinarian | **COMPLIANT** |
| `POST` | `/api/preventive-care/{id}/complete` | `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')` | Veterinarian | **COMPLIANT** |
| `GET` | `/api/horses/{horseId}/preventive-care` | `hasAuthority('PERM_MEDICAL_VIEW')` | Staff + Horse Owner | **COMPLIANT** |
| `PUT` | `/api/preventive-care/{id}` | `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')` | Veterinarian | **COMPLIANT** |
| `DELETE`| `/api/preventive-care/{id}` | `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')` | Veterinarian | **COMPLIANT** |
| `GET` | `/api/notifications` | `isAuthenticated()` | Authenticated User | **COMPLIANT** |
| `PATCH`| `/api/notifications/{id}/read` | `isAuthenticated()` | Authenticated User | **COMPLIANT** |
| `GET` | *(FR08 Barn Health Map)* | *Missing* | Veterinarian, Manager, Trainer | **MISSING** |
| `POST` | *(FR09 Medical Examination)* | *Missing* | Veterinarian | **MISSING** |
| `GET` | *(FR09 Medical History)* | *Missing* | Staff + Horse Owner | **MISSING** |
| `POST` | *(FR09 Medical Addendum)* | *Missing* | Veterinarian | **MISSING** |
| `POST` | *(FR10 3D Injury Marker)* | *Missing* | Veterinarian | **MISSING** |
| `GET` | *(FR10 3D Injury Timeline)* | *Missing* | Staff + Horse Owner | **MISSING** |

---

## 12. Database Contract Audit

### 12.1 Schema Ceiling & Compact Schema Architecture [FACT]
The database enforces a strict maximum of **30 tables** via `12_compact_schema.sql` (`THROW 51030`).

### 12.2 Status of Tables Relevant to Flow 3
1. **`dbo.horses` (Relational Table - ACTIVE)**:
   - Holds primary clinical health state: `health_status`, `health_changed_by_user_id`, `health_source_type`, `health_source_id`, `health_changed_at`.
   - Constrained to `('FIT','MONITOR','INJURED','QUARANTINED')`.
2. **`dbo.training_locks` (Relational Table - ACTIVE)**:
   - Preserved as dedicated table.
   - Unique filtered index `uq_training_locks_active_horse` on `(horse_id) WHERE released_at IS NULL`.
3. **`dbo.preventive_care_schedules` (Relational Table - ACTIVE)**:
   - Preserved as dedicated table.
   - Completion columns merged into the schedule record: `completed_by_user_id`, `completed_at`, `completion_notes`, `next_schedule_id`.
4. **`dbo.notifications` (Relational Table - ACTIVE)**:
   - Deduped automated notification repository for reminders.
5. **`dbo.horse_weight_records` (Relational Table - ACTIVE)**:
   - Stores weight records by veterinarian.
6. **`dbo.module_records` (JSON-backed Unified Table - ACTIVE)**:
   - Compacted schema dropped `dbo.medical_examinations`, `dbo.medical_examination_addenda`, `dbo.injury_markers`, `dbo.injury_recovery_updates`, `dbo.injury_update_files`.
   - `dbo.module_records` is designated to hold `module_code='MEDICAL'` records:
     - `record_type='MEDICAL_EXAMINATION'`
     - `record_type='MEDICAL_EXAMINATION_ADDENDUM'`
     - `record_type='INJURY_MARKER'`
     - `record_type='INJURY_RECOVERY_UPDATE'`
   - **Conclusion:** No new database tables are needed or permitted. Flow 3 missing features can be cleanly fulfilled using `dbo.module_records(module_code='MEDICAL')`.

---

## 13. Cross-Flow Dependencies

### 13.1 Flow 1 (Profile) ↔ Flow 3 (Medical) [FACT]
* `HorseAccessService.requireReadable(horseId, userId, privileged)` enforces that `HORSE_OWNER` can only access records for horses they own.
* `HorseMeasurementService` and `PreventiveCareService` properly invoke this check.
* `TrainingLockService` currently misses this check in `current` and `history`.

### 13.2 Flow 3 (Medical / Lock) ↔ Flow 2 (Training) [FACT]
* **Lock blocks High Intensity Schedule**: `TrainingScheduleService.create` checks `lockRepository.findByHorseIdAndReleasedAtIsNull(...)`. If intensity is `HIGH`, creation is blocked.
* **SQL Server Trigger Backup**: `dbo.tr_training_schedules_plan_and_lock` raises error 51013 if high-intensity schedule is created for a locked horse.
* **Missing Sync**: Training lock creation in `TrainingLockService.lock` does not update `horses.health_status`, leaving the horse profile and health overview unsynchronized.

### 13.3 Flow 3 (Medical / Lock) ↔ Flow 5 (Racing) [FACT]
* **Race Registration Gate**: `RaceEventService.register` calls `HorseMeasurementService.readiness(horseId, eventId, actor, true)`.
* It verifies that:
  - `health_status == 'FIT'`
  - `training_locks count == 0`
  - Latest training evaluation `fitness_status == 'GREEN'`
  - Resting heart rate is between 30 and 42 bpm
  - Weight conforms to race event limits.
* This proves active cross-module integration between Flow 3 and Flow 5.

---

## 14. End-to-End Flow Audit

### Flow A — Health (FR08)
```text
Horse → Health Status Update (PUT /api/horses/{id}/health-status) → dbo.horses.health_status
      ↓
[BROKEN]: No Barn-Wide Health Status Map endpoint connects Stables/Stalls with Horse Health Status.
```

### Flow B — Medical Examinations (FR09)
```text
Horse → [BROKEN: 0% IMPLEMENTED]
No Controller, no Service, no DTOs, no API to record examinations, symptoms, diagnosis, treatment, prescriptions, or addenda.
```

### Flow C — Injury Tracking (FR10)
```text
Horse → [BROKEN: 0% IMPLEMENTED]
No Controller, no Service, no DTOs, no API to record 3D markers, severity, image attachments, or chronological recovery timeline.
```

### Flow D — Training Lock (FR11)
```text
POST /api/horses/{id}/training-locks → TrainingLock entity → dbo.training_locks
      ↓
TrainingScheduleService.create (HIGH intensity blocked) [WORKING]
RaceEventService.register (Locked horse blocked) [WORKING]
      ↓
[BROKEN]: horses.health_status is NOT updated to INJURED.
[BROKEN]: Horse Owner is blocked from viewing current lock via API (403 Forbidden).
```

### Flow E — Preventive Care & Reminders (FR12)
```text
POST /api/horses/{id}/preventive-care → dbo.preventive_care_schedules [WORKING]
POST /api/preventive-care/{id}/complete → dbo.preventive_care_schedules (Auto-schedule next) [WORKING]
DailyAutomationJob (06:00) → notifications inserted for VETERINARIAN [WORKING]
      ↓
[BROKEN]: HEAD_TRAINER is omitted from notifications in DailyAutomationJob.
[BROKEN]: No endpoint to view due/overdue tasks across the club.
```

---

## 15. Test Coverage Audit

### 15.1 Existing Tests in Backend
* [`TrainingLockServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/TrainingLockServiceTest.java):
  - `rejectsSecondActiveLockForSameHorse()` [PASS]
  - `cannotReleaseBeforeRequiredReviewDate()` [PASS]
* `TrainingScheduleServiceTest`:
  - `rejectsHighIntensityScheduleWhileHorseIsLocked()` [PASS]

### 15.2 Missing Test Coverage [FACT]
* `HorseMeasurementService`: **0 tests**.
* `HorseMeasurementController`: **0 tests**.
* `PreventiveCareService`: **0 tests**.
* `PreventiveCareController`: **0 tests**.
* `TrainingLockController`: **0 tests**.
* `DailyAutomationJob`: **0 tests**.
* Medical Examination: **0 tests** (no code).
* Injury Tracking: **0 tests** (no code).

---

## 16. Bugs Found

1. **Bug 1 — `DailyAutomationJob` omits `HEAD_TRAINER` notifications**:
   - File: [`DailyAutomationJob.java` line 29](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/automation/DailyAutomationJob.java#L29)
   - Code: `where ur.role='VETERINARIAN'`
   - Root Cause: Head Trainer was omitted from the role query filter.
   - SRS Violation: SRS FR12 requires automatic notifications to **both Veterinarian AND Head Trainer**.

2. **Bug 2 — `TrainingLockService` does not update `horses.health_status`**:
   - File: [`TrainingLockService.java` lines 42-51, 54-63](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/TrainingLockService.java#L42-L63)
   - Code: Locks and releases only update `training_locks` table.
   - Root Cause: Missing mutation on `dbo.horses.health_status`.
   - SRS Violation: SRS FR08 requires that changes in FR11 automatically update health status and reflect on the health map.

3. **Bug 3 — `TrainingLockController` excludes `HORSE_OWNER` from read access**:
   - File: [`TrainingLockController.java` lines 53, 59](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/TrainingLockController.java#L53-L59)
   - Code: `@PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER')")`
   - Root Cause: Hardcoded role filter excludes `ROLE_HORSE_OWNER`.
   - SRS Violation: SRS 1.1 states Horse Owner has rights to view health status of their horses.

4. **Bug 4 — Missing ownership check in `TrainingLockService` read methods**:
   - File: [`TrainingLockService.java` lines 66, 74](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/TrainingLockService.java#L66-L77)
   - Code: Calls `horseAccessService.requireActive(horseId)`, neglecting `requireReadable(horseId, userId, privileged)`.

---

## 17. Missing Requirements

1. **FR08 Barn-Wide Health Status Map Endpoint**:
   - Missing aggregated endpoint (`GET /api/health/barn-map` or `GET /api/stables/health-map`) returning all stalls, assigned horses, operational status, `health_status`, and lock status, with category filtering (`FIT`, `MONITOR`, `INJURED`, `QUARANTINED`).
2. **FR09 Medical Examinations & Addenda API & Service**:
   - Entire clinical examination module is absent in Java backend. Needs `MedicalExaminationController` & `MedicalExaminationService` storing immutable records and append-only addenda in `dbo.module_records`.
3. **FR10 3D Injury Markers & Recovery Timeline API & Service**:
   - Entire injury marker module is absent in Java backend. Needs `InjuryController` & `InjuryService` storing 3D coordinates, severity, status, notes, image attachments, and chronological timeline in `dbo.module_records`.
4. **FR12 Club-Wide Due/Overdue Tasks Overview**:
   - Missing endpoint for veterinarian to view due/overdue preventive care across all horses in the club (`GET /api/preventive-care/due`).

---

## 18. Blocked / Underspecified Requirements

* **FR10 3D Model Format & Coordinate Conventions**:
  - Classification: `BLOCKED / SRS UNDERSPECIFIED`.
  - Evidence: SRS v1.0 specifies that an interactive 3D model allows veterinarians to place markers, but does NOT specify the 3D asset format (GLB, OBJ, STL), spatial coordinate space (0..1 normalized Cartesian vs local mesh vertex/UV coordinates), or anatomical label taxonomy.
  - Technical Resolution: Backend contract will store standardized Cartesian coordinates (`x, y, z`), `modelIdentifier`, `bodyPartLabel`, and `severity`, while client 3D asset integration remains underspecified.

---

## 19. Extra / Non-SRS Features

1. **Horse Weight Management (`horse_weight_records`)**:
   - `POST /api/horses/{horseId}/weights` & `GET /api/horses/{horseId}/weights`.
   - Not explicitly part of FR08 in SRS, but provides valuable physical characteristics (FR01) and race eligibility inputs (FR18).
2. **Race Readiness Evaluation (`GET /api/horses/{horseId}/race-readiness`)**:
   - Composite check combining health status, training lock, training evaluation fitness, resting heart rate, and weight. Bridges Flow 3 with Flow 5 (FR18).

---

## 20. Recommended Fixes for Step 3B

1. **Implement FR08 Barn-Wide Health Map**:
   - Add `GET /api/health/barn-map` in `HorseMeasurementController` (or a dedicated controller) aggregating active stables, stalls, assigned horses, current `health_status`, and lock status, filterable by health category.
2. **Implement FR09 Medical Examinations via `dbo.module_records`**:
   - Create `MedicalExaminationController` and `MedicalExaminationService`.
   - Store clinical examination records with `module_code='MEDICAL'`, `record_type='MEDICAL_EXAMINATION'`.
   - Enforce immutability (no delete, no overwrite).
   - Support append-only addenda (`record_type='MEDICAL_EXAMINATION_ADDENDUM'`).
   - Automatically update `dbo.horses.health_status` when examination specifies post-exam health status.
3. **Implement FR10 Injury Markers via `dbo.module_records`**:
   - Create `InjuryController` and `InjuryService`.
   - Store 3D injury markers (`record_type='INJURY_MARKER'`) with coordinates (`x, y, z`), severity, body part, and status.
   - Store recovery updates (`record_type='INJURY_RECOVERY_UPDATE'`) with status progression, notes, and attachment image URLs.
4. **Fix FR11 Gaps**:
   - In `TrainingLockService.lock`: update `dbo.horses.health_status = 'INJURED'` and set `health_source_type = 'TRAINING_LOCK'`.
   - In `TrainingLockController`: adjust `@PreAuthorize` on GET endpoints to allow `HORSE_OWNER` with `PERM_MEDICAL_VIEW`.
   - Enforce `requireReadable` in `TrainingLockService.current` and `history`.
5. **Fix FR12 Gaps**:
   - In `DailyAutomationJob.remindPreventiveCare`: update SQL role filter to `ur.role IN ('VETERINARIAN','HEAD_TRAINER')`.
   - Add club-wide due/overdue preventive care query (`GET /api/preventive-care/due`).
6. **Implement Test Suite**:
   - Create comprehensive unit tests for all new and modified Flow 3 services and controllers.

---

## 21. Final Status

**NEEDS FIX**

While FR11 (Emergency Training Lock) and parts of FR12 (Preventive Care CRUD & Auto-recurrence) are solidly functional, Flow 3 has major missing components:
* **FR08 Barn Map**: Missing club-wide stall/health aggregation endpoint.
* **FR09 Medical Examinations**: 100% missing in Java backend.
* **FR10 3D Injury Tracking**: 100% missing in Java backend.
* **FR11 & FR12 Bugs**: Health status sync missing, Head Trainer notifications missing, Horse Owner lock read access blocked.
