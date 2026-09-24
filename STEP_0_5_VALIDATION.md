# BÁO CÁO VALIDATION STEP 0.5: FLOW 1, FLOW 2, FLOW 3 (FR01 – FR12)

---

## 1. Mục Tiêu & Phạm Vi Báo Cáo

Tài liệu này thực hiện đối chiếu, kiểm chứng và phân loại chuẩn xác các phát hiện kỹ thuật từ giai đoạn Reconnaissance (Step 0) đối với 3 flow nghiệp vụ cốt lõi (**Flow 1, Flow 2, Flow 3**) bao gồm các yêu cầu chức năng từ **FR01 đến FR12**.

Báo cáo tuân thủ nghiêm ngặt các nguyên tắc:
- Không sửa source code, không migrate database, không commit/push.
- Đối chiếu trực tiếp với mã nguồn Java, database script, frontend hiện hữu và tài liệu dự án (`README.md`, `BACKEND/README.md`, `DATABASE/README.md`, `BUSINESS_DECISIONS_PENDING.md`).
- Phân định rạch ròi giữa **Confirmed SRS Gap**, **Implementation Bug**, **Enhancement**, **Unspecified** và **Blocked**.

---

## 2. Chi Tiết Kiểm Chứng 8 Vấn Đề Trọng Tâm

### 2.1. TrainingPlan RBAC (FR04)

- **Hiện trạng mã nguồn:**
  Tại [TrainingPlanController.java](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/TrainingPlanController.java#L24-L27):
  ```java
  @RestController
  @RequestMapping("/api")
  @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
  public class TrainingPlanController { ... }
  ```
- **Đối chiếu Database RBAC Matrix:**
  Trong bảng `dbo.role_permissions` (được seed từ migration 11/12):
  - `CLUB_MANAGER`: granted `TRAINING:VIEW`, `TRAINING:EDIT`, `TRAINING:APPROVE`.
  - `HEAD_TRAINER`: granted `TRAINING:VIEW`, `TRAINING:EDIT`, `TRAINING:APPROVE`.
  - `STABLE_STAFF`: granted `TRAINING:VIEW`.
  - `HORSE_OWNER`: granted `TRAINING:VIEW`.
  - `VETERINARIAN`: granted `TRAINING:VIEW`.
- **Đối chiếu các Controller khác:**
  - `TrainingScheduleController.java`: `GET` yêu cầu `PERM_TRAINING_VIEW`, `POST` yêu cầu `PERM_TRAINING_EDIT`.
  - `TelemetryController.java`: `GET` yêu cầu `PERM_TRAINING_VIEW`, `POST`/`PUT` yêu cầu `PERM_TRAINING_EDIT`.
  - `HorseMeasurementController.java`: `GET` yêu cầu `PERM_MEDICAL_VIEW`, `POST`/`PUT` yêu cầu `PERM_MEDICAL_EDIT`.
  - `PreventiveCareController.java`: `GET` yêu cầu `PERM_MEDICAL_VIEW`, `POST`/`PUT`/`DELETE` yêu cầu `PERM_MEDICAL_EDIT`.
- **Kết luận chi tiết từng endpoint:**
  1. `GET /api/training-plans/{planId}`: **BUG (SRS Violation)** — Buộc người đọc phải có quyền `EDIT`, chặn hoàn toàn các role chỉ có quyền `VIEW`.
  2. `GET /api/horses/{horseId}/training-plans`: **BUG (SRS Violation)** — Tương tự, chặn người có quyền `VIEW` đọc danh sách kế hoạch của ngựa.
  3. `POST /api/training-plans`: **Existing design** — Đúng thiết kế (yêu cầu quyền `EDIT` và role `CLUB_MANAGER`/`HEAD_TRAINER`).
  4. `PUT /api/training-plans/{planId}`: **Existing design** — Đúng thiết kế (yêu cầu quyền `EDIT`).
  5. `POST /api/training-plans/{planId}/copy`: **Existing design** — Đúng thiết kế (yêu cầu quyền `EDIT`).

---

### 2.2. Quyền Truy Cập Hồ Sơ Ngựa (GET) Cho VETERINARIAN / STABLE_STAFF (FR01)

- **Đối chiếu Database RBAC Seed:**
  Trong [11_confirmed_automation_and_governance.sql](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/DATABASE/11_confirmed_automation_and_governance.sql#L118-L124):
  ```sql
  WHEN rd.code='VETERINARIAN' AND (
      (mp.module_code='MEDICAL' AND mp.action_code IN ('VIEW','EDIT','APPROVE')) OR
      (mp.module_code IN ('PROFILE','TRAINING','STABLE_CARE','RACING') AND mp.action_code='VIEW')) THEN 1
  WHEN rd.code='STABLE_STAFF' AND (
      (mp.module_code='STABLE_CARE' AND mp.action_code IN ('VIEW','EDIT')) OR
      (mp.module_code IN ('PROFILE','TRAINING') AND mp.action_code='VIEW')) THEN 1
  ```
  [FACT] Database rõ ràng cấp quyền `PERM_PROFILE_VIEW` cho cả `VETERINARIAN` và `STABLE_STAFF`.
- **Đối chiếu Backend Implementation:**
  Tại [HorseController.java](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/horse/controller/HorseController.java#L51-L64):
  ```java
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')")
  HorseResponse getById(@PathVariable UUID id, ...)

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
  Page<HorseResponse> search(...)
  ```
  [FACT] `HorseController` dùng `hasAnyRole` cứng, vô hiệu hóa quyền `PERM_PROFILE_VIEW` đã cấp trong Database cho Bác sĩ thú y và Nhân viên chuồng.
- **Sự cần thiết trong Flow 3 (Medical) và Flow 2 (Training):**
  - Các API y tế (`GET /api/horses/{id}/health-status`, `POST /weights`, `POST /training-locks`) đều chỉ nhận `{horseId}`.
  - Model trả về `HealthStatusView` chỉ chứa: `horseId`, `status`, `changedBy`, `sourceType`, `sourceId`, `changedAt`. Nó **hoàn toàn không có** tên ngựa, mã ngựa, giống, tuổi, vị trí chuồng hay ảnh nhận dạng.
  - Bác sĩ thú y khi mở màn hình bệnh án/y tế bắt buộc phải gọi `GET /api/horses/{id}` để hiển thị thông tin định danh của con ngựa đang được khám.
  - Tương tự, nhân viên chuồng (`STABLE_STAFF`) khi nhận lịch tập (`/training-schedules/mine`) cần tra cứu hồ sơ con ngựa mình chăm sóc.
- **Kết luận:** **Required by existing flow & Database contract**. Sự thiếu vắng của `VETERINARIAN` và `STABLE_STAFF` tại annotation `@PreAuthorize` của `GET /api/horses/{id}` là **BUG (Mâu thuẫn giữa Security Annotation và Database RBAC Contract)**.

---

### 2.3. Mối Quan Hệ Giữa FR07 (Training Evaluation) và FR05 (Daily Schedule)

- **Lịch sử kiến trúc:**
  - `dbo.training_evaluations` (FR07) được tạo tại script [05_training_evaluations.sql](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/DATABASE/05_training_evaluations.sql).
  - `dbo.training_schedules` (FR05) được tạo sau tại script [06_training_plans_scheduling_monitoring.sql](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/DATABASE/06_training_plans_scheduling_monitoring.sql).
- **Yêu cầu SRS:**
  - SRS FR07 quy định: Huấn luyện viên trưởng/Quản lý nhập đánh giá kết quả buổi tập (thời lượng, cự ly, tốc độ, tải lượng, nhịp tim hồi phục, mức gắng sức, trạng thái GREEN/YELLOW/RED, video URL).
  - SRS không có điều khoản bắt buộc: "Mọi bản ghi đánh giá buổi tập bắt buộc phải có một lịch tập trước đó trong hệ thống". Huấn luyện viên có thể thực hiện đánh giá một buổi chạy thử đột xuất hoặc buổi tập tự do.
- **Đánh giá mã nguồn hiện tại:**
  - Entity `TrainingEvaluation` và bảng `dbo.training_evaluations` hoạt động độc lập qua `horse_id` và `session_date`.
  - Việc thiếu `trainingScheduleId` không làm mất bất kỳ requirement bắt buộc nào của SRS FR07.
- **Kết luận:** **Useful enhancement**. (Nếu bổ sung `trainingScheduleId` dạng nullable thì sẽ giúp trace ngược lịch tập tốt hơn, nhưng không bắt buộc theo SRS).

---

### 2.4. Phân Định Giữa `horses.status` và `horses.health_status`

- **Nghiệp vụ của `horses.status` (Flow 1):**
  - Giá trị: `ACTIVE`, `RESTING`, `INJURED`, `RETIRED`.
  - Mục đích: Trạng thái vòng đời/vận hành tổng thể của cá thể ngựa trong câu lạc bộ (ngựa đang hoạt động, đang nghỉ dưỡng theo mùa, bị sự cố dừng hoạt động, hoặc giải nghệ/nghỉ hưu).
  - Thẩm quyền cập nhật: `CLUB_MANAGER` và `HEAD_TRAINER` qua `PUT /api/horses/{id}`.
- **Nghiệp vụ của `horses.health_status` (Flow 3):**
  - Giá trị: `FIT`, `MONITOR`, `INJURED`, `QUARANTINED`.
  - Mục đích: Tình trạng bệnh học / y tế lâm sàng do chuyên môn thú y xác nhận.
  - Thẩm quyền cập nhật: Độc quyền của `VETERINARIAN` qua `PUT /api/horses/{id}/health-status`.
  - Tác động: Dùng trong kiểm tra đủ điều kiện xuất phát dự đua (`RaceReadinessResponse`).
- **Quy tắc đồng bộ trong SRS:**
  - [FACT] Toàn bộ tài liệu SRS và script migration **hoàn toàn không có quy định mapping hay trigger tự động đồng bộ** giữa hai trường này.
  - [FACT] Hai trường phản ánh hai góc nhìn quản trị độc lập: Quản lý vận hành (Manager/Trainer) và Bệnh học thú y (Veterinarian).
- **Kết luận:** **No explicit synchronization rule found / UNSPECIFIED**. Tuyệt đối không tự ý merge hai trường này thành một cột trong database.

---

### 2.5. Ảnh Hưởng Của `health_status` Đến Training Schedule

- **Đối chiếu SRS và Code hiện tại:**
  - Tại [TrainingScheduleService.java](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java#L76-L79):
    ```java
    if (phase.getWorkloadIntensity() == WorkloadIntensity.HIGH
            && lockRepository.findByHorseIdAndReleasedAtIsNull(phase.getTrainingPlan().getHorse().getId()).isPresent()) {
        throw new BusinessException("Không thể xếp lịch cường độ cao vì ngựa đang bị khóa huấn luyện");
    }
    ```
  - Tại Database Trigger `tr_training_schedules_plan_and_lock`:
    ```sql
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN dbo.training_locks training_lock ON training_lock.horse_id = i.horse_id AND training_lock.released_at IS NULL
        WHERE i.workload_intensity = 'HIGH' AND i.cancelled_at IS NULL
    )
    THROW 51013, N'Không thể xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện.', 1;
    ```
- **Phân loại tác động:**
  - **Bắt buộc chặn lịch HIGH:** Khi có **active Training Lock (FR11)**.
  - **Lịch tập LOW / MEDIUM:** Vẫn được phép xếp lịch kể cả khi đang có Training Lock (nhằm phục vụ phục hồi chức năng, đi dạo nhẹ).
  - **Chặn trực tiếp bằng `health_status` (`INJURED`, `QUARANTINED`, `MONITOR`):** **UNSPECIFIED**. SRS không quy định việc cập nhật `health_status` tự động chặn lịch tập mà không thông qua cơ chế Khóa huấn luyện khẩn cấp (Emergency Training Lock - FR11).

---

### 2.6. Ràng Buộc Cơ Sở Dữ Liệu 30 Bảng (Database 30-Table Constraint)

- **Xác minh kỹ thuật:**
  Tại [12_compact_schema.sql](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/DATABASE/12_compact_schema.sql#L265-L268) và [racehorse_database_full.sql](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/DATABASE/racehorse_database_full.sql#L2201-L2204):
  ```sql
  DECLARE @table_count INT=(SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND name<>N'sysdiagrams');
  IF @table_count > 30
      THROW 51030, N'Compact schema vượt quá giới hạn 30 bảng.', 1;
  ```
  [FACT] Cơ chế kiểm tra này thực sự tồn tại và sẽ quăng exception 51030 nếu số lượng bảng nghiệp vụ vượt quá 30.
- **Bản chất của giới hạn:**
  - Giới hạn 30 bảng **không phải là yêu cầu của SRS** (SRS không quy định số lượng bảng DB).
  - Đây là **Architectural Constraint của Implementation hiện tại** (được áp dụng ở migration 12 nhằm tinh gọn cấu trúc database, loại bỏ các bảng detail chưa có API và chuyển dữ liệu sang bảng JSON đa năng `dbo.module_records`).
- **Đánh giá hướng xử lý FR09 / FR10:**
  - Cả hai lựa chọn:
    1. Triển khai FR09 trên `dbo.module_records` (`module_code='MEDICAL'`, `record_type='MEDICAL_EXAMINATIONS'`): Tuân thủ ngay constraint 30 bảng, không cần migration DDL.
    2. Tách lại bảng quan hệ riêng `dbo.medical_examinations`: Cần một **Architectural Decision** chính thức từ chủ dự án để nâng hoặc xóa bỏ constraint 30 bảng trong script migration.

---

### 2.7. Phân Tích Chi Tiết Yêu Cầu FR09 (Medical Examinations)

Đối chiếu từng tiểu mục nghiệp vụ của FR09 với hiện trạng mã nguồn:

| Tiểu mục nghiệp vụ | Hiện trạng mã nguồn Java | Schema Database hiện hữu | Tình trạng | Thiếu sót cụ thể |
|:---|:---|:---|:---:|---|
| **1. Examination (Thăm khám)** | Chưa có Controller/Service | `dbo.module_records` (`module_code='MEDICAL'`, `record_type='MEDICAL_EXAMINATIONS'`) | **MISSING** | Thiếu Entity/DTO/Controller ghi nhận: Ngày khám, bác sĩ khám, triệu chứng lâm sàng. |
| **2. Diagnosis (Chẩn đoán)** | Chưa có Controller/Service | Cột `diagnosis` trong cấu trúc JSON | **MISSING** | Thiếu logic nghiệp vụ lưu và hiển thị kết luận chẩn đoán bệnh. |
| **3. Treatment Plan (Phác đồ)**| Chưa có Controller/Service | Cột `treatment_plan` trong JSON | **MISSING** | Thiếu logic lưu hướng điều trị / thủ thuật thực hiện. |
| **4. Medication & Dosage (Toa thuốc)** | Chưa có Controller/Service | Cột `medication_and_dosage` trong JSON | **MISSING** | Mới chỉ hỗ trợ lưu văn bản mô tả (text). Thiếu danh mục thuốc chuẩn hóa. |
| **5. Follow-up Date (Hẹn tái khám)** | Chưa có Controller/Service | Cột `follow_up_date` trong JSON | **MISSING** | Thiếu logic nhắc lịch tái khám (chưa tích hợp với Notification). |
| **6. Append-only Addenda (Phụ lục)** | Chưa có Controller/Service | Script 07 từng có bảng riêng, script 12 gộp vào JSON | **MISSING** | Thiếu cơ chế ghi thêm phụ lục y tế mà không được sửa/xóa bệnh án gốc. |
| **7. Prescription Catalog (Kho thuốc)** | Không có | Không có | **UNSPECIFIED** | SRS không đặc tả quản lý xuất nhập tồn kho dược phẩm chi tiết. |
| **8. Doping / Withdrawal Period** | Không có | Không có | **UNSPECIFIED** | SRS không cung cấp danh mục hoạt chất cấm hoặc số ngày chờ giải trừ doping. |

---

### 2.8. Phân Tích Chi Tiết Yêu Cầu FR10 (3D Injury Tracking)

- **Yêu cầu bắt buộc theo thiết kế gốc:**
  - Đánh dấu tọa độ tổn thương trên mô hình giải phẫu ngựa (X, Y, Z).
  - Ghi nhận mức độ nghiêm trọng ban đầu (severity).
  - Cập nhật tiến trình hồi phục theo thời gian kèm ảnh minh chứng.
- **Phần đã có trong code/schema:**
  - Cấu trúc dữ liệu đã được quy hoạch trong `dbo.module_records` (`record_type='INJURY_MARKERS'`).
- **Phần chưa có:**
  - Chưa có Backend Controller/Service tiếp nhận tọa độ.
  - Chưa có Component 3D Viewport trên Frontend.
- **Điểm nghẽn (Blocker):**
  - [FACT] `BUSINESS_DECISIONS_PENDING.md` (Mục 3) ghi nhận: *"Định dạng mô hình 3D và hệ tọa độ dùng để đánh dấu vị trí chấn thương chưa được chốt."*
  - Chưa có asset file mô hình 3D (.gltf, .obj), chưa có hệ trục tọa độ quy ước (gốc tọa độ đặt ở tim, đầu hay móng ngựa).
- **Kết luận:** **BLOCKED** — Chưa thể triển khai FR10 cho đến khi có quyết định nghiệp vụ về file mô hình và hệ tọa độ.

---

## 3. Tổng Hợp Phân Loại Phát Hiện (Classification Synthesis)

### A. Confirmed SRS Gaps
1. **FR01:** Thiếu giao diện cập nhật hồ sơ (`PUT /api/horses/{id}`), thay đổi tỷ lệ sở hữu (`PUT /api/horses/{id}/ownership`) và duyệt đề xuất file chính.
2. **FR01:** Thiếu API và UI cho bộ sưu tập 10 ảnh bổ sung (Additional Photos Gallery).
3. **FR02:** Thiếu UI form thêm/sửa kết quả thi đấu lịch sử (`POST`/`PUT /api/horses/{id}/race-results`).
4. **FR04:** Thiếu toàn bộ giao diện Quản lý kế hoạch huấn luyện (CRUD kế hoạch, timeline các phase, copy plan).
5. **FR05:** Thiếu toàn bộ giao diện Lịch tập hàng ngày (Calendar view, phân công nhân viên chuồng) và giao diện đặt chỗ đường đua (18 slot × số làn).
6. **FR06:** Thiếu toàn bộ giao diện Cấu hình ngưỡng cảnh báo, màn hình nhập telemetry buổi tập và trung tâm quản lý cảnh báo (Alert acknowledge).
7. **FR08:** Thiếu giao diện Bản đồ sức khỏe (Health Status Map), modal cập nhật sức khỏe (`health_status`) và form ghi nhận cân nặng định kỳ.
8. **FR09:** Thiếu hoàn toàn mã nguồn (Controller, Service, DTO, Entity, UI) cho nghiệp vụ Khám bệnh, Chẩn đoán và Điều trị.
9. **FR11:** Thiếu giao diện Khóa huấn luyện khẩn cấp (kích hoạt khóa, gỡ khóa sau tái khám, banner cảnh báo khóa).
10. **FR12:** Thiếu giao diện Quản lý chăm sóc phòng ngừa (Lịch tiêm phòng, tẩy giun, móng và xác nhận hoàn tất).

### B. Implementation Bugs
1. **`TrainingPlanController` Class-level `@PreAuthorize`:**
   - Dòng 26 của `TrainingPlanController.java` áp dụng quyền `PERM_TRAINING_EDIT` cho cả class, khiến `GET /api/training-plans/{id}` và `GET /api/horses/{id}/training-plans` từ chối các người dùng chỉ có quyền `PERM_TRAINING_VIEW` (như `STABLE_STAFF`, `HORSE_OWNER`, hoặc Viewer).
2. **`HorseController` Phân Quyền Thiếu Cho Bác Sĩ Thú Y và Nhân Viên Chuồng:**
   - `GET /api/horses/{id}` giới hạn `hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')`, mâu thuẫn với Database RBAC Seed (vốn đã cấp `PERM_PROFILE_VIEW` cho `VETERINARIAN` và `STABLE_STAFF`), khiến Bác sĩ thú y không thể nạp thông tin định danh ngựa để thăm khám.

### C. Enhancements / Design Improvements
1. **FR07 ↔ FR05 Linkage:** Bổ sung trường `trainingScheduleId` (nullable) vào `CreateTrainingEvaluationRequest` để hỗ trợ liên kết buổi đánh giá với buổi lịch cụ thể khi có nhu cầu đối chiếu tiến độ.
2. **Aggregated Health Overview API:** Xây dựng endpoint `GET /api/horses/health-overview` tổng hợp trạng thái sức khỏe toàn đàn phục vụ trực tiếp cho Health Status Map thay vì phải gọi từng con ngựa.

### D. Unspecified / Requires Decision
1. **Quy tắc đồng bộ `status` và `health_status`:** SRS không quy định cơ chế mapping tự động khi một trường thay đổi.
2. **Ảnh hưởng trực tiếp của `health_status` đến Training Schedule:** SRS không quy định tự động cấm xếp lịch tập nếu chỉ đổi `health_status` mà không tạo `Training Lock`.
3. **Kiến trúc lưu trữ FR09 / FR10:** Cần quyết định dùng `dbo.module_records` (tuân thủ giới hạn 30 bảng hiện hữu) hay gỡ bỏ giới hạn 30 bảng để khôi phục bảng quan hệ riêng.
4. **Định dạng 3D và Hệ tọa độ FR10:** Chưa chốt file mô hình và hệ tọa độ giải phẫu ngựa.
5. **Danh mục dược phẩm và thời gian giải trừ doping FR09:** Chưa có đặc tả chi tiết.

---

## 4. FR01–FR12 Final Gap Matrix

| FR | Yêu cầu SRS cốt lõi | Hiện trạng thực tế trong Code / DB | Phân loại (Category) | Hành động khắc phục đề xuất (Action) |
|:---|:---|:---|:---:|:---|
| **FR01** | Quản lý hồ sơ định danh, chủ sở hữu, file chính & 10 ảnh phụ | Đầy đủ API Core. Thiếu UI sửa hồ sơ, đổi chủ sở hữu, duyệt đề xuất. Chưa có API/UI gallery 10 ảnh. `HorseController` chặn Vet đọc hồ sơ. | **SRS GAP** / **BUG** | 1. Sửa quyền `HorseController` cho Vet/Staff.<br>2. Xây dựng UI Modal sửa hồ sơ, đổi chủ sở hữu.<br>3. Quyết định hướng lưu gallery ảnh phụ. |
| **FR02** | Cây phả hệ 3 đời (chống vòng lặp) và lịch sử thi đấu | Backend hoàn chỉnh (DFS chặn vòng lặp, đếm thắng). Frontend hiển thị read-only, thiếu form nhập/sửa thành tích đua. | **SRS GAP** | Xây dựng Modal form thêm/sửa kết quả đua (`POST`/`PUT /api/horses/{id}/race-results`). |
| **FR03** | Dashboard tổng hợp thể lực toàn câu lạc bộ | Hoàn chỉnh cả Backend API, Service, Recharts Frontend và xuất PNG/PDF. | **COMPLETE** | Giữ nguyên hiện trạng. |
| **FR04** | Kế hoạch huấn luyện nhiều giai đoạn (Phased Plan) | Backend CRUD và copy plan hoàn tất (có trigger chống overlap). Bug `@PreAuthorize` chặn GET. Hoàn toàn chưa có UI. | **BUG** / **SRS GAP** | 1. Hạ `@PreAuthorize('PERM_TRAINING_EDIT')` xuống cấp phương thức POST/PUT.<br>2. Xây dựng giao diện Quản lý giáo án. |
| **FR05** | Lịch tập hàng ngày phân công nhân viên & Đặt chỗ đường đua | Backend hoàn tất (trigger chống trùng nhân viên, trùng làn). Đã có notification. Hoàn toàn chưa có UI Calendar & Slot. | **SRS GAP** | Xây dựng giao diện Calendar phân công lịch tập và giao diện chọn 18 slot đường đua. |
| **FR06** | Cấu hình ngưỡng chỉ số & Cảnh báo nguy cơ quá tải/chấn thương | Backend hoàn tất (cấu hình ngưỡng, nhập telemetry thủ công, sinh alert, acknowledge). Hoàn toàn chưa có UI. | **SRS GAP** | Xây dựng giao diện cấu hình ngưỡng, màn hình nhập telemetry và trung tâm tiếp nhận/xác nhận cảnh báo. |
| **FR07** | Đánh giá hoàn tất buổi tập (Validation, Bất biến) | Hoàn chỉnh cả Backend, Validation, Frontend `EvaluationModal`, lịch sử thẻ tại chi tiết ngựa. | **COMPLETE** | Cân nhắc bổ sung `trainingScheduleId` (Enhancement tùy chọn). |
| **FR08** | Bản đồ trạng thái sức khỏe (Health Status Map) & Cân nặng | Backend có API cập nhật sức khỏe cá thể, nhập cân nặng, kiểm tra race-readiness. Thiếu API tổng hợp toàn đàn. Chưa có UI. | **SRS GAP** | Xây dựng Modal cập nhật sức khỏe cho bác sĩ, form nhập cân nặng và màn hình Bản đồ sức khỏe. |
| **FR09** | Bệnh án thăm khám, Chẩn đoán, Phác đồ, Toa thuốc, Phụ lục | Bảng chi tiết cũ bị gộp vào `module_records`. Hoàn toàn chưa có Controller, Service, DTO hay UI. | **SRS GAP** | Hiện thực `MedicalRecordService` và Controller tương tác qua `dbo.module_records` (hoặc xin quyết định mở rộng bảng). |
| **FR10** | Đánh dấu chấn thương 3D và theo dõi phục hồi | Bảng cũ chuyển vào `module_records`. Chưa chốt định dạng mô hình 3D và tọa độ. Chưa có API/UI. | **BLOCKED** | Tạm hoãn triển khai theo `BUSINESS_DECISIONS_PENDING.md` mục 3. |
| **FR11** | Khóa huấn luyện khẩn cấp (Emergency Training Lock) | Backend hoàn chỉnh (chỉ bác sĩ gỡ sau ngày tái khám, trigger chặn lịch HIGH & đua). Chưa có UI. | **SRS GAP** | Xây dựng UI thao tác Khóa khẩn cấp, Modal gỡ khóa và Banner cảnh báo trên hồ sơ ngựa. |
| **FR12** | Lịch chăm sóc phòng ngừa định kỳ & Tự động nhắc nhở | Backend hoàn chỉnh (tự sinh lịch chu kỳ tiếp theo, cron job 06:00 AM gửi notification). Chưa có UI. | **SRS GAP** | Xây dựng giao diện danh sách lịch tiêm phòng/tẩy giun/móng và form xác nhận hoàn thành chăm sóc. |

---

## 5. Thứ Tự Triển Khai An Toàn Được Đề Xuất (Recommended Safe Implementation Order)

```text
  [BƯỚC 1: Sửa các Implementation Bugs nền tảng] (Zero Risk, Zero DB Migration)
    ├── 1.1. TrainingPlanController: Chuyển quyền EDIT xuống POST/PUT/COPY, cấp VIEW cho GET.
    └── 1.2. HorseController: Cho phép VETERINARIAN và STABLE_STAFF thực hiện GET /api/horses/{id}.

  [BƯỚC 2: Hoàn thiện UI cho Flow 1 (Horse Profile & Pedigree)]
    ├── 2.1. Modal chỉnh sửa thông tin hồ sơ ngựa (PUT /api/horses/{id}).
    ├── 2.2. Modal cập nhật tỷ lệ sở hữu xác nhận 100% (PUT /api/horses/{id}/ownership).
    ├── 2.3. Modal thêm/sửa kết quả thi đấu lịch sử (POST/PUT /api/horses/{id}/race-results).
    └── 2.4. Giao diện xem và duyệt đề xuất file chính của chủ sở hữu.

  [BƯỚC 3: Hoàn thiện UI cho Flow 2 (Training Plan & Execution)]
    ├── 3.1. Giao diện Quản lý Kế hoạch huấn luyện FR04 (Danh sách, Phase Timeline, Copy Plan).
    ├── 3.2. Giao diện Lịch tập hàng ngày FR05 (Calendar view, Phân công STABLE_STAFF, Công bố lịch).
    ├── 3.3. Giao diện Đặt chỗ đường đua FR05 (18 slot × làn chạy, kiểm tra xung đột).
    └── 3.4. Giao diện Ngưỡng & Cảnh báo FR06 (Cấu hình ngưỡng, Nhập telemetry, Acknowledge alert).

  [BƯỚC 4: Hoàn thiện UI các module đã có Backend của Flow 3 (Medical)]
    ├── 4.1. Modal cập nhật trạng thái sức khỏe cá thể FIT/MONITOR/INJURED/QUARANTINED (FR08).
    ├── 4.2. Form nhập và danh sách theo dõi lịch sử cân nặng (FR08).
    ├── 4.3. Giao diện kích hoạt và gỡ Khóa huấn luyện khẩn cấp (FR11).
    └── 4.4. Giao diện Quản lý lịch tiêm phòng/tẩy giun/móng định kỳ (FR12).

  [BƯỚC 5: Hiện thực Backend & UI cho FR09 (Khám bệnh & Điều trị)]
    ├── 5.1. Xây dựng DTO, Service và Controller cho Medical Examination dựa trên dbo.module_records.
    └── 5.2. Xây dựng giao diện ghi nhận bệnh án, đơn thuốc và phụ lục y tế bất biến.
```

---

## DO NOT IMPLEMENT YET
