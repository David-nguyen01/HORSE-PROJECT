# Mô Hình Dữ Liệu & Ràng Buộc Kỹ Thuật (Data Model & Schema Catalog)
## Database Architecture, Entities, JSON Records & Trigger Guards

> **Thư mục:** `BUSINESS_DOCS/technical/`  
> **Thời điểm thẩm định:** 12/10/2026  
> **Hệ quản trị cơ sở dữ liệu:** Microsoft SQL Server 2022 / Azure SQL Database  
> **Mô hình lưu trữ:** Lai (Hybrid) — Bảng quan hệ chuẩn (RDBMS Relational Core) kết hợp Bảng bản ghi mở rộng JSON (`dbo.module_records`).

---

## 1. Kiến Trúc Lưu Trữ Dữ Liệu Tổng Thể

Hệ thống RTMS áp dụng mô hình lưu trữ 2 lớp:
1. **Lớp Quan Hệ Cốt Lõi (Relational Core Tables):** Dành cho các thực thể đòi hỏi truy vấn cao, quan hệ khóa ngoại chặt chẽ và tính toàn vẹn ACID:
   - Quản trị & Phân quyền: `users`, `user_roles`, `role_permissions`.
   - Cơ sở hạ tầng: `stables`, `stalls`, `racetracks`, `racetrack_lanes`.
   - Ngựa & Sở hữu: `horses`, `horse_ownerships`.
   - Huấn luyện: `training_plans`, `training_plan_phases`, `training_schedules`, `trial_run_bookings`, `training_evaluations`.
   - An toàn y tế: `training_locks`, `preventive_care_schedules`.
   - Chăm sóc chuồng & Vật tư: `care_routine_definitions`, `care_tasks`, `inventory_items`, `inventory_usages`, `inventory_replenishments`.
   - Tài chính & Thông báo: `wallet_top_up_orders`, `financial_transactions`, `owner_reports`, `notifications`.
2. **Lớp Bản Ghi Mô-đun Mở Rộng (`dbo.module_records`):** Bảng đa năng lưu trữ các sự kiện nghiệp vụ phi cấu trúc hoặc cấu trúc động thông qua trường `data_json`:
   - Phân bổ ô chuồng (`record_type = 'STALL_ASSIGNMENT'`).
   - Khám bệnh y khoa (`record_type = 'MEDICAL_EXAMINATION'`, `MEDICAL_EXAM_ADDENDUM`).
   - Đánh dấu chấn thương 3D (`record_type = 'INJURY_RECORD'`, `INJURY_RECOVERY_UPDATE`).
   - Báo cáo sự cố (`module_code = 'INCIDENT'`).
   - Đăng ký tham gia đua ngựa (`record_type = 'RACE_REGISTRATION'`).
   - Kế hoạch dinh dưỡng & Cho ăn (`record_type = 'NUTRITION_PLAN'`, `FEEDING_LOG`).
   - Đánh giá lâm sàng thú y sau buổi tập (`record_type = 'VET_TRAINING_ASSESSMENT'`).

---

## 2. Chi Tiết Các Bảng Quan Hệ Cốt Lõi (Core Tables)

### 2.1. Nhóm Quản Trị & Người Dùng (`Auth & Users`)

#### Bảng `dbo.users`
| Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa Nghiệp Vụ |
|---|---|---|---|
| `id` | `UNIQUEIDENTIFIER` | `PRIMARY KEY, DEFAULT NEWID()` | Định danh người dùng |
| `username` | `VARCHAR(100)` | `NOT NULL, UNIQUE` | Tên đăng nhập |
| `email` | `VARCHAR(255)` | `NOT NULL, UNIQUE` | Email liên hệ & nhận thông báo |
| `password_hash` | `VARCHAR(255)` | `NOT NULL` | Mật khẩu băm BCrypt |
| `full_name` | `NVARCHAR(200)` | `NULL` | Họ và tên hiển thị |
| `phone_number` | `VARCHAR(50)` | `NULL` | Số điện thoại |
| `enabled` | `BIT` | `NOT NULL, DEFAULT 1` | Trạng thái kích hoạt tài khoản |
| `wallet_balance_vnd` | `BIGINT` | `NOT NULL, DEFAULT 0` | Số dư ví tiền điện tử của chủ ngựa (VND) |
| `service_next_due_date` | `DATE` | `NULL` | Ngày đến hạn gói dịch vụ gần nhất của chủ ngựa |
| `created_at` / `updated_at` | `DATETIMEOFFSET` | `NOT NULL, DEFAULT SYSDATETIMEOFFSET()` | Dấu vết thời gian |
| `version` | `BIGINT` | `NOT NULL, DEFAULT 0` | Khóa lạc quan (Optimistic Lock) |

#### Bảng `dbo.user_roles`
| Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa Nghiệp Vụ |
|---|---|---|---|
| `user_id` | `UNIQUEIDENTIFIER` | `NOT NULL, FK -> users(id)` | Khóa ngoại người dùng |
| `role` | `VARCHAR(50)` | `NOT NULL` | Vai trò: `CLUB_MANAGER`, `HEAD_TRAINER`, `STABLE_STAFF`, `HORSE_OWNER`, `VETERINARIAN` |

#### Bảng `dbo.role_permissions`
| Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa Nghiệp Vụ |
|---|---|---|---|
| `role` | `VARCHAR(50)` | `NOT NULL` | Vai trò hệ thống |
| `module_code` | `VARCHAR(50)` | `NOT NULL` | Mã module: `PROFILE`, `TRAINING`, `MEDICAL`, `STABLE_CARE`, `RACING`, `REPORTING`, `SYSTEM`, `INVENTORY` |
| `permission_type` | `VARCHAR(50)` | `NOT NULL` | Quyền hạn: `VIEW`, `EDIT`, `APPROVE` |
| `created_at` | `DATETIMEOFFSET` | `NOT NULL, DEFAULT SYSDATETIMEOFFSET()` | Thời gian cấp quyền |

---

### 2.2. Nhóm Hồ Sơ Ngựa & Chuồng Trại (`Horses & Stables`)

#### Bảng `dbo.horses`
| Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa Nghiệp Vụ |
|---|---|---|---|
| `id` | `UNIQUEIDENTIFIER` | `PRIMARY KEY, DEFAULT NEWID()` | Định danh ngựa |
| `code` | `VARCHAR(50)` | `NOT NULL, UNIQUE` | Mã định danh ngựa (VD: `EQ-001`) |
| `name` | `NVARCHAR(200)` | `NOT NULL` | Tên gọi của ngựa |
| `microchip_passport_number` | `VARCHAR(100)` | `NULL` | Số chíp sinh học hoặc số hộ chiếu |
| `birth_date` | `DATE` | `NULL` | Ngày sinh |
| `breed` | `NVARCHAR(100)` | `NULL` | Giống ngựa (Thoroughbred, Arabian,...) |
| `gender` | `VARCHAR(20)` | `NOT NULL` | Giới tính: `STALLION`, `MARE`, `GELDING` |
| `color_markings` | `NVARCHAR(500)` | `NULL` | Dấu vết màu lông, nhận dạng |
| `status` | `VARCHAR(50)` | `NOT NULL, DEFAULT 'ACTIVE'` | Trạng thái tổng quát: `ACTIVE`, `INJURED`, `RESTING`, `RETIRED` |
| `health_status` | `VARCHAR(50)` | `NOT NULL, DEFAULT 'FIT'` | Trạng thái thú y: `FIT`, `MONITORING`, `INJURED`, `QUARANTINED`, `RECOVERING` |
| `health_source_type` | `VARCHAR(50)` | `NULL` | Nguồn cập nhật y tế gần nhất (`MEDICAL_EXAMINATION`, `TRAINING_EVALUATION`) |
| `health_source_id` | `UNIQUEIDENTIFIER` | `NULL` | ID bản ghi nguồn gây thay đổi sức khỏe |
| `health_changed_by_user_id`| `UNIQUEIDENTIFIER` | `NULL, FK -> users(id)` | Bác sĩ hoặc HLV làm thay đổi sức khỏe |
| `health_changed_at` | `DATETIMEOFFSET` | `NULL` | Thời điểm cập nhật sức khỏe gần nhất |
| `resting_heart_rate_bpm` | `INT` | `NULL` | Nhịp tim lúc nghỉ (BPM) |
| `weight_kg` | `DECIMAL(8,2)` | `NULL` | Trọng lượng cơ thể (kg) |
| `service_override` | `BIT` | `NOT NULL, DEFAULT 0` | Cờ bảo lãnh thanh toán của Quản lý CLB |
| `override_reason` | `NVARCHAR(500)` | `NULL` | Lý do bảo lãnh thanh toán |
| `override_by_user_id` | `UNIQUEIDENTIFIER` | `NULL, FK -> users(id)` | Quản lý ký duyệt bảo lãnh |
| `override_at` | `DATETIMEOFFSET` | `NULL` | Thời điểm duyệt bảo lãnh |
| `payment_status` | `VARCHAR(50)` | `NOT NULL, DEFAULT 'UNPAID'`| Trạng thái thanh toán: `UNPAID`, `PAID`, `OVERDUE` |
| `paid_until` | `DATE` | `NULL` | Ngày hết hạn gói dịch vụ |
| `service_plan` | `VARCHAR(50)` | `NULL` | Gói dịch vụ đang áp dụng: `P30`, `P90`, `P365` |
| `intake_status` | `VARCHAR(50)` | `NOT NULL, DEFAULT 'PENDING_WAITING_ROOM'` | Tiến trình nhập chuồng: `PENDING_WAITING_ROOM`, `WAITING_FOR_VET`, `IN_MEDICAL`, `PENDING_STALL`, `STABLED` |
| `current_trainer_id` | `UNIQUEIDENTIFIER` | `NULL, FK -> users(id)` | Huấn luyện viên trưởng phụ trách |
| `caretaker_user_id` | `UNIQUEIDENTIFIER` | `NULL, FK -> users(id)` | Nhân viên chăm sóc chuồng phụ trách |
| `diagnosing_vet_user_id` | `UNIQUEIDENTIFIER` | `NULL, FK -> users(id)` | Bác sĩ thú y chẩn đoán phụ trách |
| `archived_at` | `DATETIMEOFFSET` | `NULL` | Đánh dấu lưu trữ hồ sơ (Soft Delete) |

#### Bảng `dbo.stables` & `dbo.stalls`
- `stables`: Quản lý khu chuồng (`name`, `location`, `active`). Sức chứa tối đa: **4 ngựa/chuồng** (ràng buộc Trigger).
- `stalls`: Quản lý từng ô chuồng độc lập trong khu (`stable_id`, `code`, `active`).

---

### 2.3. Nhóm Huấn Luyện & Thể Lực (`Training Modules`)

#### Bảng `dbo.training_plans` & `dbo.training_plan_phases`
- `training_plans`: Kế hoạch dài hạn của một con ngựa (`name`, `target_metric`, `active`, `start_date`, `end_date`). Mỗi ngựa chỉ có tối đa 1 kế hoạch `active = 1`.
- `training_plan_phases`: Giai đoạn phát triển cụ thể trong kế hoạch (`phase_order`, `name`, `start_date`, `end_date`, `intensity`, `rest_days_csv`).

#### Bảng `dbo.training_schedules`
| Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa Nghiệp Vụ |
|---|---|---|---|
| `id` | `UNIQUEIDENTIFIER` | `PRIMARY KEY, DEFAULT NEWID()` | Định danh buổi tập |
| `horse_id` | `UNIQUEIDENTIFIER` | `NOT NULL, FK -> horses(id)` | Ngựa tham gia buổi tập |
| `phase_id` | `UNIQUEIDENTIFIER` | `NOT NULL, FK -> training_plan_phases(id)` | Giai đoạn huấn luyện |
| `racetrack_id` | `UNIQUEIDENTIFIER` | `NOT NULL, FK -> racetracks(id)` | Sân đua tổ chức tập |
| `task_name` | `NVARCHAR(200)` | `NOT NULL` | Tên bài tập huấn luyện |
| `session_status` | `VARCHAR(50)` | `NOT NULL, DEFAULT 'PREPARING'` | Vòng đời trạng thái: `PREPARING`, `READY`, `IN_PROGRESS`, `COMPLETED` |
| `scheduled_start` / `scheduled_end` | `DATETIMEOFFSET` | `NOT NULL` | Khung giờ tập (giờ tròn, 60 phút, từ 06:00 đến 23:00) |
| `started_at` / `completed_at` | `DATETIMEOFFSET` | `NULL` | Thời điểm thực tế bắt đầu / kết thúc |
| `ready_by_user_id` / `ready_at` | `DATETIMEOFFSET` | `NULL` | Nhân viên chuồng xác nhận sẵn sàng |
| `published_at` / `cancelled_at` | `DATETIMEOFFSET` | `NULL` | Thời điểm HLV công bố / hủy buổi tập |

#### Bảng `dbo.training_evaluations` (FR07)
Lưu kết quả chấm điểm thể lực chi tiết của HLV: `duration_minutes`, `distance_meters`, `track_completion_seconds`, `average_speed_kmh`, `max_speed_kmh`, `stop_bpm`, `one_minute_bpm`, `recovery_bpm`, `perceived_exertion`, `fitness_status` (`GREEN`/`YELLOW`/`RED`), `trainer_notes`, `video_url`, `injured` (`BIT`), `injury_location`, `injury_notes`.

---

### 2.4. Nhóm An Toàn Y Tế & Chăm Sóc Chuồng (`Health & Care`)

#### Bảng `dbo.training_locks`
| Cột | Kiểu Dữ Liệu | Ràng Buộc | Ý Nghĩa Nghiệp Vụ |
|---|---|---|---|
| `id` | `UNIQUEIDENTIFIER` | `PRIMARY KEY, DEFAULT NEWID()` | Định danh khóa tập y tế |
| `horse_id` | `UNIQUEIDENTIFIER` | `NOT NULL, FK -> horses(id)` | Ngựa bị áp đặt khóa |
| `reason` | `NVARCHAR(500)` | `NOT NULL` | Lý do áp đặt khóa (chấn thương, bệnh lý) |
| `expected_release_date` | `DATE` | `NULL` | Ngày dự kiến bình phục |
| `locked_by_user_id` | `UNIQUEIDENTIFIER` | `NOT NULL, FK -> users(id)` | Bác sĩ thú y ban hành lệnh khóa |
| `locked_at` | `DATETIMEOFFSET` | `NOT NULL, DEFAULT SYSDATETIMEOFFSET()` | Thời điểm bắt đầu khóa |
| `release_notes` | `NVARCHAR(500)` | `NULL` | Kết luận bình phục khi mở khóa |
| `released_by_user_id` | `UNIQUEIDENTIFIER` | `NULL, FK -> users(id)` | Bác sĩ thú y giải phóng khóa |
| `released_at` | `DATETIMEOFFSET` | `NULL` | Thời điểm giải phóng khóa (nếu `NULL` -> Khóa đang hiệu lực!) |

#### Bảng `dbo.care_tasks`
Lưu các nhiệm vụ vệ sinh, cho ăn, chườm đá, hồi phục cho nhân viên chuồng: `routine_definition_id`, `task_category` (`ROUTINE`/`SPECIAL`), `task_type` (`MUCKING_OUT`, `FEEDING`, `BATHING`, `ICE_BOOTS`, `GROOMING`, `WALKING`, `HEALTH_CHECK`), `scheduled_date`, `assigned_staff_id`, `status` (`PENDING`/`COMPLETED`/`CANCELLED`), `completed_at`, `photo_bytes` (ảnh minh chứng lưu dạng byte trong DB), `photo_content_type`.

---

## 3. Bản Ghi Mô-Đun Mở Rộng (`dbo.module_records`)

Bảng `dbo.module_records` đóng vai trò là kho lưu trữ JSON linh hoạt cho các nghiệp vụ đặc thù:

```sql
CREATE TABLE dbo.module_records (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    horse_id UNIQUEIDENTIFIER NULL REFERENCES dbo.horses(id),
    user_id UNIQUEIDENTIFIER NOT NULL REFERENCES dbo.users(id),
    module_code VARCHAR(50) NOT NULL,
    record_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    event_date DATE NOT NULL,
    data_json NVARCHAR(MAX) NOT NULL,
    created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
```

### Các Cấu Trúc JSON Chuẩn Hóa Theo Phân Hệ:

#### 1. Đăng Ký Đua Ngựa (`module_code = 'RACING'`, `record_type = 'RACE_REGISTRATION'`)
- `status`: `SUBMITTED` -> `APPROVE` / `REJECT` -> `CONFIRMED` / `WITHDRAWN`.
- `data_json`:
  ```json
  {
    "eventId": "UUID",
    "registeredWeightKg": 485.50,
    "trainerId": "UUID",
    "ownerDecisionReason": "Đủ thể lực tham gia",
    "confirmedByManagerId": "UUID"
  }
  ```

#### 2. Phân Bổ Ô Chuồng (`module_code = 'STABLE_CARE'`, `record_type = 'STALL_ASSIGNMENT'`)
- `status`: `ACTIVE`, `ENDED`.
- `data_json`:
  ```json
  {
    "stableId": "UUID",
    "stallId": "UUID",
    "stallCode": "A-01",
    "startDate": "2026-10-12",
    "endDate": null
  }
  ```

#### 3. Bệnh Án Lâm Sàng (`module_code = 'MEDICAL'`, `record_type = 'MEDICAL_EXAMINATION'`)
- `status`: `COMPLETED`.
- `data_json`:
  ```json
  {
    "examinationDate": "2026-10-12",
    "reasonForExam": "Khám định kỳ nhập chuồng",
    "symptoms": "Không sốt, mắt sáng, niêm mạc hồng",
    "diagnosis": "Thể trạng tốt, không có bệnh truyền nhiễm",
    "treatmentPlan": "Nuôi dưỡng chế độ bình thường",
    "prescriptions": [
      { "drugName": "Multivitamin", "dosage": "50ml", "route": "Oral", "frequency": "1 lần/ngày" }
    ],
    "healthStatus": "FIT"
  }
  ```

#### 4. Đánh Dấu Chấn Thương 3D (`module_code = 'MEDICAL'`, `record_type = 'INJURY_RECORD'`)
- `status`: `ACUTE`, `RECOVERING`, `HEALED`.
- `data_json`:
  ```json
  {
    "anatomicalRegion": "FL_FETLOCK_LEFT",
    "severity": "HIGH",
    "description": "Sưng nề cổ chân trước trái sau bài tập nước rút",
    "initialExamId": "UUID",
    "recoveryUpdates": [
      {
        "date": "2026-10-15",
        "notes": "Vết sưng đã giảm 70%, hết nhiệt nóng",
        "status": "RECOVERING"
      }
    ]
  }
  ```

#### 5. Đánh Giá Lâm Sàng Sau Tập (`module_code = 'TRAINING'`, `record_type = 'VET_TRAINING_ASSESSMENT'`)
- `status`: `COMPLETED`.
- `data_json`:
  ```json
  {
    "scheduleId": "UUID",
    "overallCondition": "Khá",
    "heartRateRecoveryBpm": 78,
    "respiratoryStatus": "Thở êm sau 5 phút nghỉ",
    "gaitStatus": "Dáng đi đều, không khập khiễng",
    "muscleCondition": "Cơ bắp vai mềm, không co cứng",
    "clinicalConclusion": "Thể lực đáp ứng tốt bài tập",
    "requiresMedicalIntervention": false
  }
  ```

---

## 4. Các Ràng Buộc Kỹ Thuật & Triggers Bảo Vệ Toàn Vẹn (Trigger Guards)

Hệ thống cài đặt 4 Trigger SQL Server cấp cơ sở dữ liệu để bảo vệ bất biến các quy tắc an toàn cốt lõi:

### 1. `tr_enforce_training_window` (Ràng Buộc Khung Giờ Huấn Luyện)
- **Bảng áp dụng:** `dbo.training_schedules`.
- **Hành vi kiểm tra:**
  - Bắt buộc `scheduled_start` phải rơi vào **phút thứ 0** (`DATEPART(minute, scheduled_start) = 0`).
  - Khung giờ hợp lệ: Từ **06:00 đến 23:00** trong ngày.
  - Thời lượng buổi tập chuẩn: Đúng **60 phút** (`DATEDIFF(minute, scheduled_start, scheduled_end) = 60`).
  - Ngăn chặn mọi hành vi chèn hoặc dời lịch tập vào khung giờ đêm (00:00 - 05:00) hoặc khung giờ lẻ phút (như 06:15, 07:30).

### 2. `tr_module_records_stall_assignment_unique_active` (Ràng Buộc Sức Chứa Ô Chuồng)
- **Bảng áp dụng:** `dbo.module_records`.
- **Hành vi kiểm tra:**
  - Một con ngựa tại một thời điểm chỉ có tối đa **1 phân bổ ô chuồng hoạt động** (`status = 'ACTIVE'`).
  - Trong cùng một khu chuồng (`stables`), tổng số ngựa đang có phân bổ hoạt động **không được vượt quá 4**.

### 3. `tr_module_records_race_registration_status_guard` (Bảo Vệ Bộ Ba Đồng Thuận Đua)
- **Bảng áp dụng:** `dbo.module_records`.
- **Hành vi kiểm tra:**
  - Ngăn chặn việc Quản lý câu lạc bộ chuyển trạng thái đăng ký đua thành `CONFIRMED` nếu trạng thái hiện tại của bản ghi **chưa được Chủ ngựa duyệt thành `APPROVE`**.
  - Ngăn chặn hành vi nhảy cóc quy trình bỏ qua sự đồng thuận của chủ sở hữu ngựa.

### 4. `tr_enforce_single_active_nutrition_plan` (Duy Nhất Một Kế Hoạch Dinh Dưỡng Hoạt Động)
- **Bảng áp dụng:** `dbo.module_records`.
- **Hành vi kiểm tra:**
  - Một con ngựa tại một thời điểm chỉ được phép có tối đa **1 kế hoạch dinh dưỡng có trạng thái `ACTIVE`**.
  - Khi tạo hoặc kích hoạt kế hoạch dinh dưỡng mới, kế hoạch cũ phải được lưu trữ hoặc chấm dứt hiệu lực.
