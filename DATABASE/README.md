# Racehorse Local Database Governance & Setup Guide

Tài liệu này là đặc tả chuẩn quản trị cơ sở dữ liệu cho dự án Racehorse Training Management System.

---

## 1. DATABASE PURPOSE & ARCHITECTURE CONTRACT

- **Tên database chuẩn:** `racehorse_local`
- **Hệ quản trị CSDL:** Microsoft SQL Server 2022 (Express hoặc Developer Edition) chạy trên `localhost:1433`.
- **Collation chuẩn:** `Vietnamese_100_CI_AS_SC` (hỗ trợ lưu trữ, sắp xếp và tìm kiếm tiếng Việt có dấu chuẩn xác).
- **Tài khoản kết nối backend:**
  - Login / User: `racehorse_local_app`
  - Mật khẩu mặc định: `LocalApp@123`
  - Quyền hạn: `db_owner` trên database `racehorse_local`.
- **Quan hệ giữa Backend và Database:**
  - Backend sử dụng Spring Boot JPA / Hibernate với cấu hình `spring.jpa.hibernate.ddl-auto=none`.
  - Database schema không do Hibernate tự sinh mà hoàn toàn do các script SQL trong thư mục `DATABASE/` quản lý.
  - **Nguyên tắc Source-of-Truth:** Mã nguồn Java backend (JPA entities, Spring Data repositories, JdbcTemplate SQL queries) là nguồn chân lý tối cao xác định cấu trúc bảng, cột, kiểu dữ liệu và ràng buộc. Các script trong `DATABASE/` được xây dựng để đáp ứng chính xác hợp đồng nghiệp vụ của backend hiện tại.
  - **Phạm vi môi trường:** Tài liệu và cấu hình này dành cho môi trường máy cục bộ (local development). Mọi tham chiếu tới Azure hoàn toàn nằm ngoài phạm vi hoạt động này.

---

## 2. DATABASE STRUCTURE (MODULAR BASELINE)

Thư mục `DATABASE/` có 11 script baseline (`00`–`10`) và các bản vá idempotent (`32`–`42`) được `setup-local.ps1` áp dụng sau baseline:

```text
DATABASE/
├── 00_create_database.sql          # Khởi tạo DB racehorse_local, collation & login racehorse_local_app
├── 01_core_auth.sql                # Người dùng (users, wallet, plan), user_roles, login_sessions, role_permissions
├── 02_horses_and_profiles.sql      # Hồ sơ ngựa, phả hệ thủ công, cân nặng, gói dịch vụ, quyền sở hữu, file đính kèm
├── 03_facilities.sql               # Chuồng trại và 4 làn/sân; giờ hoạt động được cập nhật ở migration 41
├── 04_training_management.sql      # Kế hoạch/giai đoạn tập, khóa an toàn (training_locks), lịch tập (schedules), đặt làn & đánh giá
├── 05_health_and_care.sql          # Định nghĩa quy trình chăm sóc (definitions), việc chăm sóc (care_tasks), lịch phòng ngừa
├── 06_telemetry_and_alerts.sql     # Ngưỡng sinh trắc, phiên đo dữ liệu (telemetry_sessions), chỉ số (readings), cảnh báo
├── 07_racing_and_finance.sql       # Sự kiện đua (race_events), kết quả, giao dịch tài chính (transactions), báo cáo chủ ngựa, thông báo & audit
├── 08_compact_records.sql          # Kho lưu trữ bán cấu trúc đa năng (module_records) & đơn nạp tiền ví PayOS
├── 09_seed_reference_data.sql      # Dữ liệu danh mục: 5 tài khoản role chuẩn, ma trận 54 quyền RBAC, 2 chuồng, 10 sân tập (40 làn), 5 quy trình
├── 10_seed_demo_data.sql           # Dữ liệu nghiệp vụ mẫu: 3 ngựa (H01, H02, H03), ô chuồng, khẩu phần, care tasks hôm nay, telemetry, tài chính
├── 32_stall_assignment_concurrency_lock.sql # Chống phân ô trùng khi đồng thời
├── 33_race_registration_lifecycle_integrity.sql # Ràng buộc trạng thái đăng ký đua
├── 34_hourly_racetrack_slots.sql   # Nâng cấp DB đã tồn tại sang slot 1 giờ
├── 35_horse_intake_workflow.sql     # Tách trạng thái tiếp nhận khỏi trạng thái thanh toán, giữ phân chuồng cũ
├── 36_align_race_events_schema.sql  # Đồng bộ schema race_events (race_name, racecourse, race_class) với backend
├── 37_owner_report_duplicate_protection.sql # Ràng buộc chống trùng lặp nội dung báo cáo chủ ngựa (BUG-03 / FR19)
├── 38_seed_horse_daily_routines.sql # Khởi tạo lịch sinh hoạt hàng ngày chuẩn (FR13) cho ngựa mẫu (khỏe & dưỡng thương)
├── 39_seed_standard_feeding_plan_and_routine_templates.sql # Khẩu phần dinh dưỡng tiêu chuẩn & mẫu lịch sinh hoạt
├── 40_backfill_stabled_horses_daily_routines.sql # Điền bổ sung lịch sinh hoạt cho ngựa đã vào chuồng
├── 41_fixed_training_window.sql     # Buổi tập mới cố định 05:00–11:00; giữ nguyên buổi cũ
├── 42_owner_race_approval_final.sql # Chủ ngựa duyệt là xác nhận cuối cùng; cập nhật quyền duyệt
├── setup-local.ps1                 # Script PowerShell khởi tạo/tái tạo toàn bộ baseline CSDL cục bộ
├── apply-sync.ps1                  # Script PowerShell áp dụng dữ liệu dùng chung (shared team data)
├── sync/                           # Thư mục lưu trữ các script đồng bộ dữ liệu dùng chung giữa các thành viên
└── README.md                       # Tài liệu quản trị và hướng dẫn này
```


### Chi tiết các script baseline

1. **`00_create_database.sql`**
   - Tạo CSDL `racehorse_local` với collation `Vietnamese_100_CI_AS_SC` nếu chưa có.
   - Tạo server login `racehorse_local_app` (password `LocalApp@123`) và database user gán vai trò `db_owner`.
2. **`01_core_auth.sql`**
   - Bảng `dbo.users`: Bổ sung `wallet_balance_vnd`, `active_service_plan`, `service_plan_started_on`, `service_next_due_date` phục vụ luồng nạp ví và chặn quá hạn chủ ngựa.
   - Bảng `dbo.user_roles`: Ràng buộc 5 vai trò hệ thống (`CLUB_MANAGER`, `HEAD_TRAINER`, `STABLE_STAFF`, `HORSE_OWNER`, `VETERINARIAN`).
   - Bảng `dbo.login_sessions`: Quản lý refresh token phiên đăng nhập.
   - Bảng `dbo.role_permissions`: Ma trận phân quyền động 8 module (`PROFILE`, `TRAINING`, `MEDICAL`, `STABLE_CARE`, `RACING`, `REPORTING`, `SYSTEM`, `INVENTORY`).
3. **`02_horses_and_profiles.sql`**
   - Bảng `dbo.horses`: Chứa đầy đủ phả hệ thực (`sire_id`, `dam_id`) và phả hệ thủ công (`sire_manual_name`, `sire_manual_id`, `dam_manual_name`, `dam_manual_id`), gói dịch vụ của từng ngựa (`active_service_plan`, `next_service_plan`), chỉ số cân nặng hiện tại mới nhất (`weight_kg`, `weight_record_id`, `weight_measured_at`, `weight_measured_by_user_id`, `weight_notes`), liên kết huấn luyện viên, bác sĩ thú y và nhân viên phụ trách (`caretaker_user_id`).
   - Bảng `dbo.horse_ownerships`: Ràng buộc tỷ lệ sở hữu và chủ sở hữu duy nhất (`ux_horse_ownerships_one_owner`).
   - Bảng `dbo.stored_files` & `dbo.horse_profile_files`: Lưu trữ siêu dữ liệu Cloudinary cho ảnh hồ sơ, tài liệu phê duyệt và file báo cáo.
4. **`03_facilities.sql`**
   - Bảng `dbo.stables`: Danh mục khu chuồng nuôi.
   - Bảng `dbo.racetracks`: Sau migration 41, mở `05:00:00`, đóng `11:00:00` cùng ngày; buổi tập mới dùng toàn khung 6 giờ.
   - Bảng `dbo.racetrack_lanes`: Mỗi sân tập có đúng 4 làn chạy được đánh số từ 1 đến 4.
5. **`04_training_management.sql`**
   - Bảng `dbo.training_plans` & `dbo.training_plan_phases`: Kế hoạch và các giai đoạn tập luyện (`rest_days_json` lưu dạng JSON).
   - Bảng `dbo.training_locks`: Quản lý khóa huấn luyện an toàn (cột `reason`, `review_date`, `release_reason`), ngăn xếp lịch bài tập nặng khi ngựa đang có khóa chưa mở.
   - Bảng `dbo.training_schedules`: Lịch tập huấn luyện. Không còn phụ thuộc gán người trực tiếp trên lịch; kiểm soát trạng thái phiên tập theo `ck_training_v3_session_status` hỗ trợ `PREPARING`, `READY`, `IN_PROGRESS`, `COMPLETED`, `INJURED`, `EMERGENCY_STOPPED`.
   - Bảng `dbo.trial_run_bookings`: Ràng buộc đặt làn chạy độc quyền trong toàn bộ khung buổi tập; dữ liệu đặt làn cũ được giữ nguyên.
   - Bảng `dbo.training_evaluations`: Lưu trữ đầy đủ các chỉ số chạy, nhịp tim, hồi phục (không có cột `training_load`), `perceived_exertion` (1..10, nullable).
   - Triggers: Sau migration 41, buổi tập mới bắt buộc 05:00–11:00 (`tr_enforce_training_window`), chống trùng giờ trên làn (`tr_trial_run_bookings_no_lane_overlap`), kiểm soát khớp ngựa và kế hoạch còn hạn (`tr_training_schedules_plan_and_lock`), chặn đánh giá kế hoạch hết hạn (`tr_training_evaluations_plan_expiry`).
6. **`05_health_and_care.sql`**
   - Bảng `dbo.care_routine_definitions`: 5 quy trình chăm sóc tiêu chuẩn hàng ngày.
   - Bảng `dbo.care_tasks`: Các nhiệm vụ chăm sóc thực tế theo ngày dành cho `STABLE_STAFF` (hỗ trợ ghi chú hoàn thành và hình ảnh xác thực với các cột `photo_url`, `photo_bytes`, `photo_content_type`).
   - Bảng `dbo.preventive_care_schedules`: Lịch tiêm phòng, tẩy giun và chăm sóc móng định kỳ.
7. **`06_telemetry_and_alerts.sql`**
   - Bảng `dbo.horse_metric_thresholds`: Thiết lập ngưỡng nhịp tim và tốc độ cho từng ngựa.
   - Bảng `dbo.telemetry_sessions` & `dbo.telemetry_readings`: Dữ liệu sinh trắc đo khi nghỉ (`RESTING`) hoặc tập luyện.
   - Bảng `dbo.alert_events`: Cảnh báo tự động khi chỉ số vượt ngưỡng.
8. **`07_racing_and_finance.sql`**
   - Bảng `dbo.race_events`: Sự kiện đua ngựa (bao gồm cột `completed_at` ghi nhận thời điểm hoàn tất).
   - Bảng `dbo.race_results`: Thành tích đua.
   - Bảng `dbo.financial_transactions`: Sổ cái tài chính thu/chi; bổ sung cột `wallet_owner_user_id` và các danh mục ví mới (`WALLET_TOPUP`, `SERVICE_FEE`, `LATE_FEE`, `PLAN_ADJUSTMENT`).
   - Bảng `dbo.owner_reports`, `dbo.notifications`, `dbo.audit_logs`: Báo cáo định kỳ chủ ngựa, thông báo hệ thống và nhật ký kiểm toán bất biến (append-only trigger `tr_audit_logs_append_only`).
9. **`08_compact_records.sql`**
   - Bảng `dbo.module_records`: Lưu trữ bán cấu trúc JSON cho các nghiệp vụ chuồng (ô chuồng, phân ô), khẩu phần dinh dưỡng ngựa, vật tư kho và luồng nạp ví PayOS (`payos_order_code`, `payos_expected_amount_vnd`, `payos_expires_at`).
10. **`09_seed_reference_data.sql`**
    - Seed 5 tài khoản kiểm thử cho 5 role, ma trận 54 quyền RBAC, Chuồng A, Chuồng B, 10 sân tập (40 làn), 5 quy trình chăm sóc và kho vật tư cơ sở.
11. **`10_seed_demo_data.sql`**
    - Seed 3 ngựa đua (H01 - Thắng, H02 - Ex, H03 - Demo) với thông tin phả hệ, cân nặng chuẩn thú y, quyền sở hữu, ô chuồng, khẩu phần ăn, nhiệm vụ chăm sóc hôm nay cho nhân viên, phiên đo nhịp tim nghỉ ngơi chuẩn phục vụ kiểm tra điều kiện xuất phát (`race-readiness`), kế hoạch huấn luyện và giao dịch tài chính mẫu.
12. **`37_owner_report_duplicate_protection.sql`**
    - Bổ sung cột `content_hash VARCHAR(64) NOT NULL` và Unique Nonclustered Index `ux_owner_reports_period_content` trên `(owner_user_id, period_start, period_end, content_hash)` nhằm ngăn chặn triệt để trùng lặp nội dung báo cáo chủ ngựa khi gọi tuần tự hoặc đồng thời (BUG-03 / FR19).


---

## 3. HƯỚNG DẪN KHỞI TẠO & VẬN HÀNH CSDL LOCAL

### Bước 1: Khởi tạo CSDL từ PowerShell

Chạy lệnh sau từ thư mục gốc của dự án hoặc trong thư mục `DATABASE/`:

```powershell
# Lần đầu khởi tạo hoặc nâng cấp DB hiện có, giữ nguyên dữ liệu:
powershell -ExecutionPolicy Bypass -File DATABASE/setup-local.ps1 -UseWindowsAuth

# Hoặc chạy với tài khoản sa nếu muốn dùng SQL Authentication:
powershell -ExecutionPolicy Bypass -File DATABASE/setup-local.ps1 -SqlServer "localhost,1433" -SqlUsername "sa" -SqlPassword "<mật khẩu SQL Server của bạn>"
```

Chỉ dùng `-RecreateDatabase` khi chủ động muốn **xóa toàn bộ dữ liệu cũ**. Các bản vá `32`–`42` hướng tới `racehorse_local`; không chạy chúng trực tiếp trên Azure. Dữ liệu lịch 05:00–11:00 cũ được giữ nguyên khi nâng cấp; lịch tương lai cần được kiểm tra và chuyển thủ công sang slot mới nếu còn dùng.


### Bước 2: Kiểm tra cấu hình Backend (`application-local.properties`)

File `BACKEND/src/main/resources/application-local.properties` đã được cấu hình trỏ thẳng tới CSDL `racehorse_local`:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=racehorse_local;encrypt=true;trustServerCertificate=true
spring.datasource.username=racehorse_local_app
spring.datasource.password=${RACEHORSE_LOCAL_DB_PASSWORD:LocalApp@123}
app.payos.gateway=${PAYOS_GATEWAY:fake}
app.jwt.secret=${JWT_SECRET}
```

### Bước 3: Khởi chạy Backend với profile `local`

Từ thư mục `BACKEND`:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Hoặc chạy bộ kiểm thử tự động:

```powershell
mvn test
```

---

## 4. TÀI KHOẢN MẪU & DANH SÁCH ROLE

Tất cả 5 tài khoản mẫu được khởi tạo sẵn với mật khẩu chung: **`Admin123`**

| Email | Username | Role chính | ID người dùng | Ghi chú |
|---|---|---|---|---|
| `admin@gmail.com` | `admin` | `CLUB_MANAGER` | `5D036EA7-77FD-4D63-90AC-B90E1E4CAE11` | Toàn quyền quản trị hệ thống & cơ sở vật chất |
| `trainer@gmail.com` | `head_trainer` | `HEAD_TRAINER` | `22222222-2222-2222-2222-222222222222` | Quản lý kế hoạch, lịch tập & đánh giá huấn luyện |
| `staff@gmail.com` | `stable_staff` | `STABLE_STAFF` | `48772A9D-9DA0-44A8-90F2-0459B3ADB768` | Chăm sóc chuồng, thực hiện care task & chuẩn bị ngựa |
| `owner@gmail.com` | `horse_owner` | `HORSE_OWNER` | `44444444-4444-4444-4444-444444444444` | Chủ sở hữu 3 ngựa mẫu; ví có sẵn 50.000.000 VND |
| `vet@gmail.com` | `vet_dr` | `VETERINARIAN` | `55555555-5555-5555-5555-555555555555` | Bác sĩ thú y; khám bệnh, nhập cân nặng & theo dõi y tế |

---

## 5. CƠ CHẾ ĐỒNG BỘ DỮ LIỆU DÙNG CHUNG (SHARED TEAM DATA SYNC)

Khi cần chia sẻ dữ liệu thử nghiệm giữa các thành viên mà không thay đổi baseline cố định:
1. Tạo script `.sql` chứa các câu lệnh `INSERT/UPDATE` idempotent và đặt vào thư mục `DATABASE/sync/` (ví dụ: `20261009_extra_demo_horses.sql`).
2. Commit file đó lên nhánh Git.
3. Đồng nghiệp khi kéo về chạy lệnh:
   ```powershell
   powershell -ExecutionPolicy Bypass -File DATABASE/apply-sync.ps1 -UseWindowsAuth
   ```
   Script sẽ tự động quét và thực thi các file SQL mới trong `DATABASE/sync/` theo thứ tự tên file.

