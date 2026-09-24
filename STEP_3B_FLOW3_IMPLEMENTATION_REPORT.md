# Báo cáo Thực hiện: STEP 3B — IMPLEMENT FLOW 3 BACKEND (Medical & Injury: FR08–FR12)

---

## A. Changed Files (Danh sách File đã Sửa và Tạo mới)

### 1. Created Files (Tạo mới: 18 files)
#### DTOs:
* [`BarnHealthMapResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/BarnHealthMapResponse.java)
* [`CreateMedicalExaminationRequest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/CreateMedicalExaminationRequest.java)
* [`AddMedicalExaminationAddendumRequest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/AddMedicalExaminationAddendumRequest.java)
* [`MedicalExaminationResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/MedicalExaminationResponse.java)
* [`MedicalExaminationDetailResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/MedicalExaminationDetailResponse.java)
* [`MedicalExaminationAddendumResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/MedicalExaminationAddendumResponse.java)
* [`CreateInjuryMarkerRequest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/CreateInjuryMarkerRequest.java)
* [`AddInjuryRecoveryUpdateRequest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/AddInjuryRecoveryUpdateRequest.java)
* [`InjuryResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/InjuryResponse.java)
* [`InjuryDetailResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/InjuryDetailResponse.java)
* [`InjuryRecoveryUpdateResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/InjuryRecoveryUpdateResponse.java)
* [`DuePreventiveCareResponse.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/dto/DuePreventiveCareResponse.java)

#### Services & Controllers:
* [`BarnHealthService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/BarnHealthService.java)
* [`BarnHealthController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/BarnHealthController.java)
* [`MedicalExaminationService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/MedicalExaminationService.java)
* [`MedicalExaminationController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/MedicalExaminationController.java)
* [`InjuryService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/InjuryService.java)
* [`InjuryController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/InjuryController.java)

#### Unit Tests (Tạo mới: 6 test suites):
* [`BarnHealthServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/BarnHealthServiceTest.java)
* [`MedicalExaminationServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/MedicalExaminationServiceTest.java)
* [`InjuryServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/InjuryServiceTest.java)
* [`PreventiveCareServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/PreventiveCareServiceTest.java)
* [`Flow3ControllerRbacTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/controller/Flow3ControllerRbacTest.java)
* [`DailyAutomationJobTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/automation/DailyAutomationJobTest.java)

### 2. Modified Files (Sửa đổi: 6 files)
* [`TrainingLockController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/TrainingLockController.java) (Thêm quyền `HORSE_OWNER` và kiểm tra `requireReadable`).
* [`TrainingLockService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/TrainingLockService.java) (Áp dụng `horseAccessService.requireReadable` cho `current` và `history`).
* [`TrainingLockServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/TrainingLockServiceTest.java) (Bổ sung test scoping và quyền xem của chủ ngựa).
* [`PreventiveCareController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/PreventiveCareController.java) (Thêm endpoint `GET /api/preventive-care/due`).
* [`PreventiveCareService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/PreventiveCareService.java) (Thêm phương thức `listDue` tính `isOverdue` và `daysUntilDue`).
* [`DailyAutomationJob.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/automation/DailyAutomationJob.java) (Gửi thông báo nhắc lịch cho cả `VETERINARIAN` và `HEAD_TRAINER`).

---

## B. FR08 — Bản đồ Trạng thái Sức khỏe Toàn Chuồng (Barn Health Map)

* **Requirement `[SRS]`**:
  * Hiển thị lưới/bản đồ toàn bộ chuồng trại, phân loại mã màu sức khỏe từng con ngựa (`FIT`, `MONITOR`, `INJURED`, `QUARANTINED`).
  * Cho phép Bác sĩ thú y lọc bản đồ theo danh mục trạng thái sức khỏe.
  * Hiển thị trạng thái Khóa huấn luyện (Training Lock) trực tiếp trên bản đồ.
* **Implementation `[FACT]`**:
  * Xây dựng [`BarnHealthService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/BarnHealthService.java) và [`BarnHealthController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/BarnHealthController.java).
  * Tích hợp dữ liệu thực từ DB: Chuồng (`dbo.stables`) $\rightarrow$ Ô chuồng (`module_records` với `record_type='STALL'`) $\rightarrow$ Phân bổ ô chuồng (`record_type='STALL_ASSIGNMENT'`) $\rightarrow$ Ngựa (`dbo.horses`) $\rightarrow$ Lệnh khóa huấn luyện hoạt động (`dbo.training_locks`).
  * Tính toán chỉ số tổng hợp: `totalHorses`, `fitCount`, `monitorCount`, `injuredCount`, `quarantinedCount`, `lockedCount`.
* **API `[FACT]`**:
  * `GET /api/health/barn-map`
  * Query parameters:
    * `status` (optional): `FIT`, `MONITOR`, `INJURED`, `QUARANTINED`
    * `stableId` (optional): UUID chuồng cụ thể
* **RBAC `[FACT]`**:
  * `@PreAuthorize("hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','STABLE_STAFF')")`
* **DB Impact `[FACT]`**:
  * Sử dụng bảng hiện có (`dbo.stables`, `dbo.horses`, `dbo.training_locks`, `dbo.module_records`). Không thay đổi schema.
* **Tests `[FACT]`**:
  * Đạt 100% trong [`BarnHealthServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/BarnHealthServiceTest.java) và [`Flow3ControllerRbacTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/controller/Flow3ControllerRbacTest.java).

---

## C. FR09 — Khám bệnh, Chẩn đoán & Hồ sơ Điều trị (Medical Examination)

* **Requirement `[SRS]`**:
  * Biểu mẫu khám bệnh gồm: Ngày khám, Bác sĩ thú y, Triệu chứng, Chẩn đoán, Phác đồ điều trị, Thuốc/Liều lượng, Ngày tái khám.
  * Nhật ký lịch sử y tế theo trình tự thời gian, chỉ đọc (read-only) sau khi lưu.
  * Không thể bị xóa, không bị ghi đè: chỉ bổ sung/chỉnh sửa qua Phụ lục (Addenda).
  * Bác sĩ thú y có thể cập nhật trạng thái sức khỏe của ngựa trực tiếp từ màn hình khám bệnh, phản ánh ngay lập tức trên Bản đồ sức khỏe (FR08).
* **Implementation `[FACT]`**:
  * Xây dựng [`MedicalExaminationService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/MedicalExaminationService.java) và [`MedicalExaminationController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/MedicalExaminationController.java).
  * Lưu trữ vào `dbo.module_records` với `module_code = 'MEDICAL'`, `record_type = 'MEDICAL_EXAMINATION'` và `record_type = 'MEDICAL_EXAMINATION_ADDENDUM'`.
  * Ràng buộc logic nghiệp vụ: `examinationDate <= today`, `followUpDate >= examinationDate`.
  * Tính bất biến (Immutability): Không cung cấp API `DELETE` hoặc `PUT` ghi đè bản ghi khám bệnh. Mọi cập nhật được ghi nhận qua API `POST .../addenda`.
  * Đồng bộ trạng thái sức khỏe: Khi tạo lần khám hoặc bổ sung phụ lục có trường `healthStatusAfter`, hệ thống cập nhật đồng bộ cột `health_status` trong `dbo.horses` với `health_source_type = 'MEDICAL_EXAM'`.
* **API `[FACT]`**:
  * `POST /api/horses/{horseId}/medical-examinations` (Tạo hồ sơ khám)
  * `POST /api/medical-examinations/{examId}/addenda` (Thêm phụ lục bất biến)
  * `GET /api/medical-examinations/{examId}` (Xem chi tiết lần khám kèm danh sách phụ lục)
  * `GET /api/horses/{horseId}/medical-examinations` (Danh sách các lần khám theo thời gian)
* **RBAC `[FACT]`**:
  * Create / Add Addendum: `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')`
  * View / List: `hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')` kết hợp `horseAccessService.requireReadable(horseId, userId, privileged)`.
* **DB Impact `[FACT]`**:
  * Hoàn toàn sử dụng bảng đa năng `dbo.module_records` và cập nhật trường `health_status` của `dbo.horses`. Giữ vững trần 30 bảng.
* **Tests `[FACT]`**:
  * Đạt 100% trong [`MedicalExaminationServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/MedicalExaminationServiceTest.java) và [`Flow3ControllerRbacTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/controller/Flow3ControllerRbacTest.java).

---

## D. FR10 — Đánh dấu Chấn thương 3D & Theo dõi Hồi phục (Injury & 3D Tracking)

* **SRS-Supported Requirements `[SRS]`**:
  * Cho phép Bác sĩ thú y đặt điểm đánh dấu chỉ vị trí và mức độ nghiêm trọng của chấn thương (`MILD`, `MODERATE`, `SEVERE`).
  * Cho phép đính kèm ghi chú, ngày tháng và tệp hình ảnh (X-quang/siêu âm).
  * Duy trì dòng thời gian hiển thị trạng thái của chấn thương thay đổi qua các lần khám liên tiếp (`ACUTE` $\rightarrow$ `RECOVERING` $\rightarrow$ `HEALED`).
* **Implemented in Backend `[FACT]`**:
  * Xây dựng [`InjuryService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/InjuryService.java) và [`InjuryController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/InjuryController.java).
  * Lưu trữ điểm đánh dấu với tọa độ không gian 3 chiều Cartesian (`x`, `y`, `z`), vị trí cơ thể (`bodyLocation`), mức độ nghiêm trọng, trạng thái hiện tại, và mảng ID tệp đính kèm (`attachmentFileIds`).
  * Tự động sinh mục cập nhật đầu tiên trong timeline khi tạo điểm chấn thương.
  * Hỗ trợ API thêm cập nhật hồi phục (`POST /api/injuries/{injuryId}/recovery-updates`), tự động cập nhật `currentStatus` của điểm chấn thương và thêm vào dòng thời gian.
  * Sử dụng `dbo.module_records` với `record_type = 'INJURY_MARKER'` và `record_type = 'INJURY_RECOVERY_UPDATE'`.
* **Assumptions `[ASSUMPTION]`**:
  * Backend hỗ trợ lưu trữ tọa độ dấu thực số (`Double x, y, z`). Tọa độ này là giá trị không gian chuẩn để client 3D map lên mô hình mesh ngựa.
  * Trạng thái hồi phục chuẩn hóa thành 3 nấc: `ACUTE` (Cấp tính), `RECOVERING` (Đang hồi phục), `HEALED` (Đã khỏi).
* **Blocked / Underspecified by SRS v1.0 `[BLOCKED BY SRS SPECIFICATION]`**:
  * SRS v1.0 **không đặc tả** định dạng file 3D (`.glb`, `.gltf`, `.obj`), hệ trục tọa độ 3D (normalized $[0,1]$ hay local/world space), và danh mục giải phẫu chuẩn.
  * **Trách nhiệm hiển thị và tương tác mô hình 3D (3D rendering engine như Three.js / Babylon.js) hoàn toàn thuộc về Frontend**. Backend đã cung cấp đầy đủ hợp đồng dữ liệu chuẩn xác cho Frontend tiêu thụ.
* **API `[FACT]`**:
  * `POST /api/horses/{horseId}/injuries` (Tạo điểm đánh dấu chấn thương 3D)
  * `POST /api/injuries/{injuryId}/recovery-updates` (Cập nhật tiến trình hồi phục)
  * `GET /api/injuries/{injuryId}` (Xem chi tiết chấn thương kèm toàn bộ dòng thời gian)
  * `GET /api/horses/{horseId}/injuries` (Danh sách chấn thương của ngựa, hỗ trợ lọc theo `status`)
* **RBAC `[FACT]`**:
  * Create / Update: `hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')`
  * View / List: `hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')` kết hợp `horseAccessService.requireReadable`.
* **DB Impact `[FACT]`**:
  * Sử dụng `dbo.module_records`. Không thay đổi schema.
* **Tests `[FACT]`**:
  * Đạt 100% trong [`InjuryServiceTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/service/InjuryServiceTest.java) và [`Flow3ControllerRbacTest.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/test/java/com/racehorse/health/controller/Flow3ControllerRbacTest.java).

---

## E. FR11 — Lệnh Khóa Huấn luyện Khẩn cấp (Training Lock Fixes)

* **RBAC & Scoping Fix `[FACT]`**:
  * Trong [`TrainingLockController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/TrainingLockController.java): Bổ sung `HORSE_OWNER` vào `@PreAuthorize` của `GET /horses/{horseId}/training-locks/current` và `GET /horses/{horseId}/training-locks`.
  * Truyền danh tính người dùng và cờ `privileged` vào service.
  * Trong [`TrainingLockService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/TrainingLockService.java): Áp dụng `horseAccessService.requireReadable(horseId, userId, privileged)`. Chủ ngựa chỉ xem được lệnh khóa của ngựa mình sở hữu; người lạ bị từ chối truy cập (`ResourceNotFoundException`).
* **Lock Behavior & Cross-Flow Enforcement `[FACT]`**:
  * Lệnh khóa hoạt động tiếp tục được thực thi chặn các buổi tập cường độ `HIGH` trong Flow 2 (`TrainingScheduleService`) và chặn đăng ký thi đấu trong Flow 5 (`RaceEventService`).
  * Kiểm tra ngày tái khám (`reviewDate`): Bác sĩ thú y không thể gỡ khóa trước ngày tái khám đã định.
* **Health Status Relationship `[SRS]` & `[ASSUMPTION]`**:
  * SRS quy định: Lệnh khóa huấn luyện hiển thị nổi bật trên Bản đồ Sức khỏe (FR08) và hồ sơ ngựa ngay khi được kích hoạt.
  * SRS **không quy định** việc kích hoạt khóa huấn luyện phải tự động ghi đè dịch tễ `health_status` của ngựa thành `INJURED`. Việc giữ `health_status` và `trainingLock` độc lập nhưng hiển thị song song trên Bản đồ Sức khỏe tránh được lỗi mất trạng thái dịch tễ (như ngựa bị `QUARANTINED` không bị biến thành `FIT` khi gỡ khóa).

---

## F. FR12 — Lên lịch Chăm sóc Phòng ngừa (Preventive Care Fixes)

* **Dual-Role Automation Notification Fix `[FACT]`**:
  * Trong [`DailyAutomationJob.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/automation/DailyAutomationJob.java): Sửa truy vấn nhắc lịch gửi thông báo cho cả `VETERINARIAN` và `HEAD_TRAINER` khi tài khoản đang hoạt động (`u.enabled = 1`).
  * Deduplication key bao gồm `ur.user_id`, đảm bảo không trùng lặp và không xung đột giữa hai vai trò.
* **Due / Overdue Aggregation API `[FACT]`**:
  * Bổ sung endpoint `GET /api/preventive-care/due` trong [`PreventiveCareController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/PreventiveCareController.java).
  * Trong [`PreventiveCareService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/PreventiveCareService.java): Triển khai phương thức `listDue(asOf, careType, overdueOnly)`:
    * Trả về danh sách nhiệm vụ chưa hoàn thành/chưa hủy trên toàn trại.
    * Tự động tính cờ `isOverdue` và số ngày còn lại/quá hạn (`daysUntilDue`).
    * Sắp xếp theo thứ tự ưu tiên hạn chót tăng dần (`due_date ASC`).
* **Existing CRUD Stability `[FACT]`**:
  * Đảm bảo chức năng tạo, hoàn thành tự động dời chu kỳ lặp (`next_schedule_id`), cập nhật và hủy lịch hoạt động ổn định, không hồi quy.

---

## G. Database Impact (Tác động Cơ sở Dữ liệu)

* **Schema Modification**: **`No DB schema change`** (Không thay đổi bất kỳ migration hay file SQL nào).
* **Table Count Constraint**: Giữ nguyên toàn bộ cấu trúc compact schema với số lượng bảng dưới 30 bảng (`THROW 51030` tuân thủ 100%).
* **Table Utilization**: Toàn bộ dữ liệu hồ sơ khám bệnh (FR09) và đánh dấu chấn thương 3D / timeline hồi phục (FR10) được lưu trữ chuẩn hóa thông qua bảng đa năng `dbo.module_records` với `module_code = 'MEDICAL'`.

---

## H. Tests (Kết quả Kiểm thử Tự động)

### Command thực thi:
```powershell
mvn test
```

### Kết quả Surefire Report:
```text
Total:   79
Passed:  79
Failed:  0
Skipped: 0
Errors:  0
```

### Các Test Suites bao phủ:
1. `RegisterRequestValidationTest` (3 tests) - Pass
2. `LoginSessionServiceTest` (1 test) - Pass
3. `UserManagementServiceTest` (5 tests) - Pass
4. `DailyAutomationJobTest` (1 test) - Pass *(New: Xác minh thông báo đồng thời Bác sĩ thú y + Huấn luyện viên trưởng)*
5. `SecurityConfigurationTest` (1 test) - Pass
6. `Flow3ControllerRbacTest` (5 tests) - Pass *(New: Xác minh phân quyền RBAC toàn bộ Controller Flow 3)*
7. `BarnHealthServiceTest` (2 tests) - Pass *(New: Xác minh bản đồ chuồng, tổng hợp chỉ số, lọc theo trạng thái)*
8. `InjuryServiceTest` (5 tests) - Pass *(New: Xác minh chấn thương 3D, tọa độ x,y,z, mức độ, cập nhật hồi phục)*
9. `MedicalExaminationServiceTest` (6 tests) - Pass *(New: Xác minh hồ sơ khám, tính bất biến addenda, đồng bộ sức khỏe)*
10. `PreventiveCareServiceTest` (4 tests) - Pass *(New: Xác minh lịch định kỳ, dời chu kỳ và danh sách due/overdue)*
11. `TrainingLockServiceTest` (5 tests) - Pass *(Cập nhật: Xác minh quyền đọc của HORSE_OWNER và chặn người lạ)*
12. `HorseServiceTest` (5 tests) - Pass (Regression test Flow 1)
13. `PedigreeServiceTest` (1 test) - Pass (Regression test Flow 1)
14. `HorsePhotoServiceTest` (5 tests) - Pass (Regression test Flow 1)
15. `HorseProfileFileServiceTest` (5 tests) - Pass (Regression test Flow 1)
16. `ProfileFileValidatorTest` (3 tests) - Pass (Regression test Flow 1)
17. `RaceResultServiceTest` (2 tests) - Pass (Regression test Flow 1)
18. `TrainingPlanControllerTest` (3 tests) - Pass (Regression test Flow 2)
19. `FitnessDashboardServiceTest` (4 tests) - Pass (Regression test Flow 2)
20. `RacetrackServiceTest` (2 tests) - Pass (Regression test Flow 2)
21. `TrainingEvaluationServiceTest` (7 tests) - Pass (Regression test Flow 2)
22. `TrainingPlanServiceTest` (3 tests) - Pass (Regression test Flow 2)
23. `TrainingScheduleServiceTest` (1 test) - Pass (Regression test Flow 2)

---

## I. Remaining Limitations (Các Giới hạn còn tồn tại)

1. **FR10 3D Rendering Model Dependency**:
   * SRS v1.0 không đặc tả file mô hình 3D (định dạng, file 3D mesh cụ thể của ngựa) và hệ tọa độ 3D cục bộ.
   * Backend đã hiện thực hoàn chỉnh việc lưu trữ và truy xuất tọa độ ($x, y, z$), vị trí mô tả, mức độ và lịch sử hồi phục. Việc tải file 3D (ví dụ `.glb` qua Three.js) và render chốt đánh dấu trên mô hình thuộc về phần việc của Frontend khi nhóm Frontend tiến hành tích hợp.
2. **Real-time Push Notification (WebSocket / SSE)**:
   * Hiện tại thông báo hệ thống được lưu vào `dbo.notifications` và có thể truy vấn qua API. SRS đề cập việc phản ánh bản đồ theo thời gian thực (real-time); khi Frontend kết nối, client có thể sử dụng cơ chế polling hoặc tích hợp SSE/WebSocket ở các giai đoạn sau nếu cần thiết.

---

## J. Final Status (Đánh giá Trạng thái Từng FR)

| FR | Tên chức năng | Trạng thái | Đánh giá Tuân thủ SRS |
| :--- | :--- | :--- | :--- |
| **FR08** | Bản đồ Trạng thái Sức khỏe Toàn Chuồng | **`COMPLETE`** | Đã có API `GET /api/health/barn-map` hiển thị ma trận chuồng, ô chuồng, ngựa, mã màu sức khỏe, training lock và bộ lọc trạng thái. |
| **FR09** | Khám bệnh, Chẩn đoán & Hồ sơ Điều trị | **`COMPLETE`** | Đã triển khai đầy đủ nghiệp vụ khám lâm sàng, tính bất biến phụ lục (addenda), lưu trữ chuẩn `module_records`, đồng bộ trạng thái sức khỏe của ngựa. |
| **FR10** | Đánh dấu Chấn thương 3D & Theo dõi Hồi phục | **`COMPLETE`** (Backend) | Đã cung cấp đầy đủ API lưu trữ tọa độ 3D ($x, y, z$), mức độ chấn thương, ảnh đính kèm và timeline hồi phục. (Phần 3D Canvas Rendering thuộc phạm vi Frontend). |
| **FR11** | Lệnh Khóa Huấn luyện Khẩn cấp | **`COMPLETE`** | Đã khắc phục lỗi RBAC cho phép `HORSE_OWNER` xem lệnh khóa ngựa mình, áp dụng `requireReadable`, duy trì chặn workout HIGH và kiểm tra ngày tái khám. |
| **FR12** | Lên lịch Chăm sóc Phòng ngừa | **`COMPLETE`** | Đã sửa job nhắc lịch thông báo đồng thời cho `VETERINARIAN` và `HEAD_TRAINER`; bổ sung API `GET /api/preventive-care/due` tổng hợp danh sách quá hạn/đến hạn. |
