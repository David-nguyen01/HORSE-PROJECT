# Đặc Tả Vai Trò: Quản Lý Câu Lạc Bộ (CLUB_MANAGER)
## Role Specification & Business Behavior

> **Vai trò:** `CLUB_MANAGER`  
> **Tên hiển thị:** Quản lý câu lạc bộ  
> **Tài khoản mẫu kiểm thử:** `admin@gmail.com` / `Admin123`  
> **Thư mục:** `BUSINESS_DOCS/roles/`  
> **Thời điểm thẩm định:** 12/10/2026

---

## 1. Tổng Quan Vai Trò (Role Overview)

### 1.1. Mục Đích & Trách Nhiệm
`CLUB_MANAGER` là vai trò quản trị điều hành cao nhất trong hệ thống RTMS, chịu trách nhiệm:
- Quản trị toàn bộ hạ tầng cơ sở vật chất: Chuồng trại (`stables`), ô chuồng (`stalls`), và phân bổ vị trí nuôi nhốt cho đàn ngựa.
- Quản trị tài khoản nhân sự (`users`) và ma trận phân quyền động (`role_permissions`).
- Quản trị hồ sơ ngựa gốc (`horses`), phả hệ và bảo lãnh thanh toán phí dịch vụ (`service_override`).
- Quản lý các giải đua ngựa (`race_events`), duyệt xác nhận đăng ký thi đấu chính thức (`confirm`).
- Phê duyệt các đơn đề xuất bổ sung vật tư kho (`replenishments`).
- Giám sát sức khỏe tài chính, sổ cái kế toán (`financial_transactions`), KPI hoạt động và thanh tra nhật ký kiểm toán hệ thống (`audit_logs`).

### 1.2. Quyền Hạn Hệ Thống (Permissions & Authorities)
- **Role Authority:** `ROLE_CLUB_MANAGER`.
- **Dynamic Authorities (mặc định được cấp toàn quyền):**
  - `PERM_PROFILE_VIEW`, `PERM_PROFILE_EDIT`, `PERM_PROFILE_APPROVE`
  - `PERM_TRAINING_VIEW`, `PERM_TRAINING_EDIT`, `PERM_TRAINING_APPROVE`
  - `PERM_MEDICAL_VIEW`, `PERM_MEDICAL_EDIT`, `PERM_MEDICAL_APPROVE`
  - `PERM_STABLE_CARE_VIEW`, `PERM_STABLE_CARE_EDIT`, `PERM_STABLE_CARE_APPROVE`
  - `PERM_RACING_VIEW`, `PERM_RACING_EDIT`, `PERM_RACING_APPROVE`
  - `PERM_REPORTING_VIEW`, `PERM_REPORTING_EDIT`, `PERM_REPORTING_APPROVE`
  - `PERM_SYSTEM_VIEW`, `PERM_SYSTEM_EDIT`, `PERM_SYSTEM_APPROVE`
  - `PERM_INVENTORY_VIEW`, `PERM_INVENTORY_EDIT`, `PERM_INVENTORY_APPROVE`

### 1.3. Điểm Truy Cập Giao Diện & Backend (Entry Points)
- **Frontend Routes (`App.jsx`):**
  - `/`: Dashboard quản trị tổng quan (`AdminDashboard` trong `AdminPages.jsx`)
  - `/horses`, `/horses/:id`: Danh sách & chi tiết hồ sơ ngựa
  - `/races`: Quản lý sự kiện đua và duyệt xác nhận (`TrainingRacePage.jsx`)
  - `/admin/stables`: Quản lý chuồng trại, ô chuồng & phân bổ (`StablesPage`)
  - `/admin/inventory-catalog`: Quản lý danh mục vật tư kho (`InventoryCatalogPage`)
  - `/admin/finance-reports`: Báo cáo tài chính & sổ cái giao dịch (`FinanceReportsPage.jsx`)
  - `/admin/accounts`: Quản lý tài khoản người dùng (`AccountsPage`)
  - `/admin/permissions`: Ma trận phân quyền động RBAC (`PermissionsPage`)
  - `/admin/archived-horses`: Danh sách ngựa đã lưu trữ (`ArchivedHorses`)
  - `/admin/audit-logs`: Nhật ký kiểm toán hệ thống (`AuditLogs`)
- **Backend Controllers:**
  - `AdminManagementController`, `ManagementDashboardController`, `AuditLogController`
  - `StableController`, `HorseController`, `RaceEventController`, `InventoryController`, `FinanceController`

---

## 2. Các Tính Năng Vai Trò Thực Hiện (Features Performed)

### CM-001: Quản Trị Tài Khoản & Người Dùng Hệ Thống
- **Mục đích:** Tạo mới, sửa thông tin, đặt lại mật khẩu và khóa/mở khóa tài khoản nhân sự và chủ ngựa.
- **Tiền điều kiện:** Đăng nhập với quyền `CLUB_MANAGER`.
- **Điểm kích hoạt:** Màn hình **Tài khoản** (`/admin/accounts`).
- **Quy trình thực hiện:**
  1. Xem danh sách tài khoản kèm trạng thái hoạt động và vai trò.
  2. Bấm "Tạo tài khoản": Nhập username, email, mật khẩu ban đầu và vai trò (`CLUB_MANAGER`, `HEAD_TRAINER`, `STABLE_STAFF`, `HORSE_OWNER`, `VETERINARIAN`).
  3. Bật/tắt trạng thái kích hoạt (`enabled = true/false`).
- **Quy tắc nghiệp vụ & Ràng buộc:**
  - Mã nguồn thực thi `AccountActivityGuard.java`: CẤM vô hiệu hóa (`enabled = false`) hoặc thay đổi vai trò nếu người dùng đang là:
    - Huấn luyện viên phụ trách bất kỳ con ngựa nào đang hoạt động (`current_trainer_id`).
    - Nhân viên chăm sóc bất kỳ con ngựa nào (`caretaker_user_id`).
    - Bác sĩ chẩn đoán của bất kỳ con ngựa nào (`diagnosing_vet_user_id`).
    - Chủ sở hữu của bất kỳ con ngựa nào (`horse_ownerships`).
  - Mật khẩu phải được băm qua `BCryptPasswordEncoder`.
- **Dữ liệu tác động:** Bảng `dbo.users`, `dbo.user_roles`.
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`AdminManagementController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/admin/AdminManagementController.java), [`AccountActivityGuard.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/auth/service/AccountActivityGuard.java).
  - Frontend: [`AccountsPage` trong `AdminPages.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/AdminPages.jsx).

### CM-002: Quản Trị Ma Trận Phân Quyền Động (RBAC Dynamic Matrix)
- **Mục đích:** Bật/tắt linh hoạt các quyền Xem (`VIEW`), Sửa (`EDIT`), Duyệt (`APPROVE`) trên 8 phân hệ cho 5 vai trò.
- **Tiền điều kiện:** Vai trò `CLUB_MANAGER`.
- **Điểm kích hoạt:** Màn hình **Ma trận quyền** (`/admin/permissions`).
- **Quy trình thực hiện:**
  1. Hiển thị ma trận 8 module x 5 vai trò.
  2. Quản lý tích chọn/hủy chọn các quyền hạn tương ứng.
  3. Bấm "Lưu thay đổi", gửi request cập nhật danh sách quyền của từng vai trò.
- **Quy tắc nghiệp vụ:** Quyền hạn có hiệu lực ngay trong phiên đăng nhập tiếp theo hoặc khi refresh token.
- **Dữ liệu tác động:** Bảng `dbo.role_permissions`.
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`RolePermissionController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/auth/controller/RolePermissionController.java).
  - Frontend: [`PermissionsPage` trong `AdminPages.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/AdminPages.jsx).

### CM-003: Quản Lý Khu Chuồng & Cấu Trúc Ô Chuồng
- **Mục đích:** Thiết lập danh mục các khu chuồng (`stables`) và khai báo từng ô chuồng (`stalls`) trong khu.
- **Tiền điều kiện:** Vai trò `CLUB_MANAGER`, có quyền `PERM_STABLE_CARE_EDIT`.
- **Điểm kích hoạt:** Màn hình **Chuồng trại** (`/admin/stables`).
- **Quy trình thực hiện:**
  1. Tạo khu chuồng mới: Nhập tên khu (VD: "Chuồng A"), vị trí (VD: "Khu Đông"), và danh sách mã ô chuồng ban đầu.
  2. Bổ sung ô chuồng vào khu đã có: Nhập mã ô chuồng (`code`).
  3. Xóa ô chuồng / Xóa khu chuồng: Chỉ cho phép xóa khi ô chuồng trống, không có phân bổ hoạt động.
- **Quy tắc nghiệp vụ:**
  - **Giới hạn sức chứa cứng:** Mỗi khu chuồng chỉ được chứa tối đa **4 con ngựa** (Trigger `tr_module_records_stall_assignment_unique_active`).
- **Dữ liệu tác động:** Bảng `dbo.stables`, `dbo.stalls`.
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`StableController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java#L39-L68), [`StableService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/stable/service/StableService.java).
  - Frontend: [`StablesPage` trong `AdminPages.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/AdminPages.jsx).

### CM-004: Phân Bổ Ngựa Vào Ô Chuồng & Chuyển Chuồng
- **Mục đích:** Xếp ngựa vào ô chuồng cụ thể hoặc giải phóng ô chuồng khi ngựa xuất chuồng.
- **Tiền điều kiện:** Ngựa đang trong trạng thái chờ xếp chuồng (`PENDING_STALL`) hoặc cần chuyển ô; ô chuồng đích đang trống (`vacant`).
- **Điểm kích hoạt:** Modal `AssignHorseDialog` tại màn hình Chuồng trại (`/admin/stables`).
- **Quy trình thực hiện:**
  1. Chọn ô chuồng còn trống, bấm "Xếp ngựa".
  2. Chọn con ngựa từ danh sách đủ điều kiện.
  3. Hệ thống tạo bản ghi phân bổ active mới, kết thúc phân bổ cũ (nếu có), cập nhật vị trí chuồng vào hồ sơ ngựa.
- **Quy tắc nghiệp vụ:**
  - Trigger `tr_module_records_stall_assignment_unique_active`:
    1. Chặn 2 phân bổ ACTIVE cho cùng 1 ô chuồng (`stallId`).
    2. Chặn 1 con ngựa có 2 phân bổ ACTIVE đồng thời.
    3. Chặn khu chuồng vượt quá sức chứa tối đa 4 con ngựa.
  - Khi phân bổ thành công, cập nhật `horses.intake_status = 'STABLED'`.
  - Khi giải phóng chuồng (`unassign`), cập nhật `horses.intake_status = 'PENDING_STALL'` và reset `horses.current_trainer_id = NULL`.
- **Dữ liệu tác động:** Bảng `dbo.module_records` (loại `STALL_ASSIGNMENT`), bảng `dbo.horses`.
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`StableController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java#L70-L81), [`StableService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/stable/service/StableService.java#L190-L248).
  - Frontend: [`AssignHorseDialog` trong `AdminPages.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/AdminPages.jsx#L1193).

### CM-005: Quản Lý Hồ Sơ Ngựa & Bảo Lãnh Chi Phí
- **Mục đích:** Khởi tạo hồ sơ ngựa đua, gắn phả hệ, quản lý tỷ lệ sở hữu, và cấp bảo lãnh phí (`service_override`) cho ngựa.
- **Tiền điều kiện:** Quyền `CLUB_MANAGER` và `PERM_PROFILE_EDIT`.
- **Điểm kích hoạt:** Màn hình **Hồ sơ ngựa** (`/horses`, `/horses/:id`).
- **Quy trình thực hiện:**
  1. Tạo hồ sơ ngựa: Mã hiệu (`code`), tên (`name`), ngày sinh, giới tính, giống ngựa, vi mạch (`microchipNo`).
  2. Thiết lập phả hệ: Chọn bố mẹ trong hệ thống hoặc nhập tên/mã thủ công.
  3. Cấp bảo lãnh: Bật cờ `service_override = true` kèm lý do bảo lãnh (`override_reason`).
  4. Lưu trữ hồ sơ (`archive`): Khi ngựa giải nghệ hoặc chuyển nhượng, đánh dấu `archived_at = now`.
- **Quy tắc nghiệp vụ:**
  - Khi `service_override = true`, ngựa được phép vượt qua bước kiểm tra thanh toán để tham gia khám y tế và huấn luyện dù chủ chưa đóng tiền.
  - Ràng buộc sở hữu: Một con ngựa chỉ có tối đa 1 bản ghi sở hữu với tỷ lệ 100% (`ux_horse_ownerships_one_owner`).
- **Dữ liệu tác động:** Bảng `dbo.horses`, `dbo.horse_ownerships`.
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`HorseController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/horse/controller/HorseController.java), [`HorseService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/horse/service/HorseService.java).
  - Frontend: `Horses` và `HorseDetail` trong [`App.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/App.jsx).

### CM-006: Quản Lý Giải Đua & Xác Nhận Danh Sách Thi Đấu Chính Thức
- **Mục đích:** Tạo giải đua, cập nhật quy chế, đóng đăng ký, và xác nhận danh sách ngựa thi đấu sau khi chủ đã duyệt.
- **Tiền điều kiện:** Vai trò `CLUB_MANAGER`, có quyền `PERM_RACING_EDIT`.
- **Điểm kích hoạt:** Màn hình **Quản lý giải đua** (`/races`).
- **Quy trình thực hiện:**
  1. Tạo giải đua mới: Tên giải (`race_name`), trường đua (`racecourse`), ngày đua (`race_date`), hạn đăng ký (`registration_deadline`), cự ly (`distance_meters`), hạng đua (`race_class`), yêu cầu tuổi và cân nặng tối thiểu/tối đa.
  2. Xem danh sách đăng ký dự đua của các ngựa.
  3. Với các đơn đã được Chủ sở hữu duyệt (`APPROVE`), bấm **"Xác nhận tham gia"** (`confirm`).
  4. Sau ngày đua, bấm **"Hoàn thành giải đua"** (`complete`).
- **Quy tắc nghiệp vụ:**
  - Trigger `tr_module_records_race_registration_status_guard`:
    - Chỉ đơn ở trạng thái `APPROVE` mới được chuyển sang `CONFIRMED`.
    - Đơn đã `CONFIRMED` không thể rút (`WITHDRAWN`).
    - Không thể thay đổi trạng thái đăng ký sau khi giải đua đã hoàn thành (`completed_at IS NOT NULL`).
  - Không thể bấm hoàn tất giải đua trước ngày thi đấu (`raceDate.isAfter(today)` ném BusinessException).
- **Dữ liệu tác động:** Bảng `dbo.race_events`, `dbo.module_records` (loại `RACE_REGISTRATION`).
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`RaceEventController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/racing/controller/RaceEventController.java), [`RaceEventService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/racing/service/RaceEventService.java).
  - Frontend: [`TrainingRacePage.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/TrainingRacePage.jsx).

### CM-007: Phê Duyệt Đề Xuất Bổ Sung Vật Tư Kho
- **Mục đích:** Xem xét và phê duyệt hoặc từ chối các đề xuất mua sắm, bổ sung vật tư do nhân viên chuồng gửi lên.
- **Tiền điều kiện:** Quyền `CLUB_MANAGER` và `PERM_STABLE_CARE_APPROVE`.
- **Điểm kích hoạt:** Tab **Vật tư & Tồn kho** (`/admin/inventory-catalog` hoặc màn hình kho).
- **Quy trình thực hiện:**
  1. Quản lý xem danh sách đề xuất ở trạng thái `PENDING_APPROVAL`.
  2. Bấm "Duyệt" (`APPROVED`) hoặc "Từ chối" (`REJECTED`) kèm ghi chú phản hồi.
- **Quy tắc nghiệp vụ:** Duy nhất `CLUB_MANAGER` mới có quyền thẩm định đề xuất (`@PreAuthorize("hasRole('CLUB_MANAGER')")`).
- **Dữ liệu tác động:** Bảng `dbo.module_records` (loại `REPLENISHMENT_REQUEST`).
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`InventoryController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/inventory/controller/InventoryController.java#L47), [`InventoryService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/inventory/service/InventoryService.java#L162).
  - Frontend: [`InventoryCatalogPage` trong `AdminPages.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/AdminPages.jsx).

### CM-008: Quản Lý Sổ Cái Tài Chính & Xem Báo Cáo Doanh Thu
- **Mục đích:** Theo dõi dòng tiền thu/chi, doanh thu gói dịch vụ, tiền nạp ví, tiền phí dự đua và xuất báo cáo CSV.
- **Tiền điều kiện:** Quyền `CLUB_MANAGER` và `PERM_FINANCE_VIEW` (hoặc `PERM_REPORTING_VIEW`).
- **Điểm kích hoạt:** Màn hình **Tài chính & Báo cáo** (`/admin/finance-reports`).
- **Quy trình thực hiện:**
  1. Xem tóm tắt tài chính: Tổng thu, tổng chi, số dư ròng.
  2. Lọc giao dịch theo loại danh mục (`WALLET_TOPUP`, `SERVICE_FEE`, `RACE_ENTRY_FEE`, `VET_EXPENSE`, `FEED_EXPENSE`), khoảng thời gian.
  3. Bấm "Xuất file CSV" để tải toàn bộ bảng kê giao dịch.
- **Quy tắc nghiệp vụ:** Bảng `financial_transactions` lưu tiền tệ bằng VND chuẩn; các giao dịch nạp ví được liên kết trực tiếp tới `wallet_owner_user_id`.
- **Dữ liệu tác động:** Đọc từ `dbo.financial_transactions`.
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`FinanceController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/finance/controller/FinanceController.java), [`FinanceService.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/finance/service/FinanceService.java).
  - Frontend: [`FinanceReportsPage.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/FinanceReportsPage.jsx).

### CM-009: Thanh Tra Nhật Ký Kiểm Toán Hệ Thống (Audit Logs)
- **Mục đích:** Giám sát toàn bộ các thay đổi nhạy cảm trong hệ thống nhằm phục vụ thanh tra, kiểm toán bảo mật.
- **Tiền điều kiện:** Chỉ dành riêng cho vai trò `CLUB_MANAGER` (`@PreAuthorize("hasRole('CLUB_MANAGER')")`).
- **Điểm kích hoạt:** Màn hình **Nhật ký hệ thống** (`/admin/audit-logs`).
- **Quy trình thực hiện:**
  1. Xem danh sách kiểm toán sắp xếp giảm dần theo thời gian: Người thực hiện, phân hệ, hành động, thời điểm, IP, thiết bị.
  2. Bấm vào từng dòng để xem chi tiết đối tượng, giá trị trước (`old_values_json`) và sau (`new_values_json`).
  3. Bấm "Xuất CSV" để tải file nhật ký kiểm toán UTF-8 BOM.
- **Quy tắc nghiệp vụ:**
  - Bảng `dbo.audit_logs` có trigger `tr_audit_logs_append_only`: CẤM TUYỆT ĐỐI mọi câu lệnh `UPDATE` hoặc `DELETE` trên bảng này.
- **Dữ liệu tác động:** Đọc từ `dbo.audit_logs`.
- **Trạng thái triển khai:** `IMPLEMENTED`.
- **Bằng chứng:**
  - Backend: [`AuditLogController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/audit/controller/AuditLogController.java).
  - Frontend: [`AuditLogs` trong `AdminPages.jsx`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/FRONTEND/src/AdminPages.jsx).

---

## 3. Yêu Cầu / Sự Kiện Vai Trò Này Gửi Đi (Requests Sent)

| Sự Kiện / Yêu Cầu Gửi Đi | Vai Trò Tiếp Nhận | Mục Đích & Điều Kiện Kích Hoạt | Dữ Liệu Chuyển Giao | Tác Động Trạng Thái |
|:---|:---|:---|:---|:---|
| **Phân bổ ô chuồng cho ngựa** | `STABLE_STAFF`, `HEAD_TRAINER` | Xếp ngựa vào ô chuồng; ngựa đủ điều kiện để HLV tiếp nhận vào lịch tập. | Mã ô chuồng, mã ngựa, ngày phân bổ. | `horses.intake_status = 'STABLED'`; tạo `STALL_ASSIGNMENT`. |
| **Cấp bảo lãnh chi phí (`service_override`)** | Hệ thống, `HEAD_TRAINER` | Cho phép ngựa chưa đóng phí được tập luyện khẩn cấp. | `horseId`, `overrideReason`. | `horses.service_override = 1`. |
| **Xác nhận đăng ký đua (`confirm`)** | `HEAD_TRAINER`, `HORSE_OWNER` | Chốt danh sách đua sau khi Chủ ngựa đã duyệt `APPROVE`. | `registrationId`, thời điểm chốt. | `module_records.status = 'CONFIRMED'`. |
| **Duyệt đề xuất mua vật tư (`APPROVED`)** | `STABLE_STAFF` | Cho phép nhân viên chuồng tiến hành nhập kho vật tư khi hàng về. | `replenishmentId`, phản hồi duyệt. | `module_records.status = 'APPROVED'`. |
| **Khóa / Mở khóa tài khoản** | Người dùng liên quan | Vô hiệu hóa quyền truy cập hệ thống của nhân sự vi phạm. | `userId`, `enabled = false`. | Thu hồi token; chặn đăng nhập. |

---

## 4. Yêu Cầu / Sự Kiện Vai Trò Này Nhận Về (Requests Received)

| Sự Kiện / Nhiệm Vụ Nhận Về | Vai Trò Khởi Tạo | Kênh Phản Ánh / Màn Hình | Hành Động Quản Lý Có Thể Thực Hiện |
|:---|:---|:---|:---|
| **Đề xuất bổ sung vật tư kho (`PENDING_APPROVAL`)** | `STABLE_STAFF` | Màn hình Vật tư / Danh mục kho (`/admin/inventory-catalog`). | Thẩm định nhu cầu, bấm **Phê duyệt** hoặc **Từ chối**. |
| **Đơn đăng ký đua đã được chủ duyệt (`APPROVE`)** | `HORSE_OWNER` | Màn hình Quản lý giải đua (`/races`). | Bấm **Xác nhận** (`confirm`) để chốt danh sách thi đấu. |
| **Thông báo sự cố khẩn cấp nghiêm trọng** | `STABLE_STAFF`, `HEAD_TRAINER` | Chuông thông báo (`NotificationBell`), nhật ký sự cố. | Nắm bắt tình hình thương tật hoặc tai nạn tại hiện trường. |
| **Thông báo cảnh báo quá tải / chấn thương** | `HEAD_TRAINER`, `VETERINARIAN` | Thông báo hệ thống, Dashboard. | Xem xét tình trạng thể lực, giám sát lệnh khóa huấn luyện. |

---

## 5. Quy Tắc Trạng Thái & Vòng Đời (Status & Lifecycle Rules)

1. **Vòng đời Phân bổ Ô chuồng (`STALL_ASSIGNMENT`):**
   - `ACTIVE` (đang ở chuồng) → `ENDED` (kết thúc phân bổ, chuyển ô khác hoặc xuất chuồng).
   - Ràng buộc: Một ô chuồng chỉ có tối đa 1 bản ghi `ACTIVE`.
2. **Vòng đời Giải đua (`race_events`):**
   - Đang mở nhận đăng ký (`is_active = 1`, `completed_at IS NULL`) → Đã hoàn thành (`is_active = 1`, `completed_at IS NOT NULL`) hoặc Vô hiệu hóa (`is_active = 0`).
3. **Vòng đời Đăng ký Đua (`RACE_REGISTRATION`):**
   - `SUBMITTED` → `APPROVE` (chủ duyệt) → `CONFIRMED` (Quản lý xác nhận).
   - Quản lý KHÔNG THỂ xác nhận trực tiếp đơn đang ở trạng thái `SUBMITTED` (Trigger 53001 chặn).
4. **Vòng đời Đề xuất Vật tư (`REPLENISHMENT_REQUEST`):**
   - `PENDING_APPROVAL` → `APPROVED` (Quản lý duyệt) hoặc `REJECTED` (Quản lý từ chối) → `FULFILLED` (Nhân viên nhập hàng).

---

## 6. Ghi Chú Đánh Giá & Các Điểm Cần Rà Soát (Gaps & Review Notes)

1. `[OBSERVATION]` **Thiếu giao diện riêng cho Phòng chờ Tiếp nhận (`HorseIntakeController`):**
   - Backend đã có API tiếp nhận phòng chờ (`POST /api/horse-intake/{id}/waiting-room`), nhưng hiện tại Quản lý CLB đang xếp chuồng trực tiếp thông qua modal `AssignHorseDialog` trong `AdminPages.jsx`.
   - Cần Chủ dự án xác nhận: Có cần dựng thêm màn hình "Tiếp nhận vào trạm chờ" trước khi xếp chuồng hay giữ nguyên quy trình xếp chuồng trực tiếp như hiện tại?
2. `[FACT]` **Xung đột Menu cũ trong tài liệu lịch sử:**
   - Trong `FULL_RBAC_FLOW_AUDIT.md` cũ từng ghi nhận Quản lý CLB bị chặn truy cập `/stable-care`. Trên bản code `develop` hiện tại, Quản lý CLB đã có trang `/admin/stables` và `/admin/inventory-catalog` riêng biệt, không còn xung đột điều hướng.
