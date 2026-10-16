# Danh Mục Giao Diện Lập Trình Ứng Dụng (API Catalog)
## Module-by-Module REST Endpoints, RBAC Authorities & UI Callers

> **Thư mục:** `BUSINESS_DOCS/technical/`  
> **Thời điểm thẩm định:** 12/10/2026  
> **Giao thức:** RESTful JSON qua HTTPS  
> **Cơ chế xác thực:** JWT Bearer Token (chuẩn OAuth2 Resource Server)

---

## 1. Phân Hệ Xác Thực & Quản Trị Hệ Thống (`Auth & Administration`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `POST /api/auth/login` | Công khai (`permitAll`) | Đăng nhập hệ thống, cấp phát Access Token | `AuthShell.jsx` (Màn hình đăng nhập) |
| `GET /api/auth/me` | Người dùng đã xác thực | Lấy thông tin tài khoản, vai trò và quyền hạn hiện tại | Mọi trang (`App.jsx`, `AuthProvider`) |
| `GET /api/admin/users` | `hasRole('CLUB_MANAGER')` | Xem danh sách toàn bộ người dùng trong hệ thống | `AdminPages.jsx` (`AccountsPage`) |
| `POST /api/admin/users` | `hasRole('CLUB_MANAGER')` | Tạo tài khoản nhân sự mới kèm vai trò ban đầu | `AdminPages.jsx` (`AccountsPage`) |
| `PUT /api/admin/users/{id}/status` | `hasRole('CLUB_MANAGER')` | Bật/Tắt kích hoạt tài khoản (kiểm tra `AccountActivityGuard`) | `AdminPages.jsx` (`AccountsPage`) |
| `GET /api/admin/role-permissions` | `hasRole('CLUB_MANAGER')` | Tải ma trận phân quyền động 8 module x 5 vai trò | `AdminPages.jsx` (`PermissionsPage`) |
| `PUT /api/admin/role-permissions` | `hasRole('CLUB_MANAGER')` | Cập nhật phân quyền động cho từng vai trò | `AdminPages.jsx` (`PermissionsPage`) |
| `GET /api/admin/audit-logs` | `hasRole('CLUB_MANAGER')` | Tra cứu nhật ký kiểm toán hệ thống | `AdminPages.jsx` (`AuditLogs`) |
| `GET /api/admin/management-dashboard` | `hasRole('CLUB_MANAGER')` | Tải các chỉ số KPI điều hành trang trại tổng quan | `AdminPages.jsx` (`AdminDashboard`) |

---

## 2. Phân Hệ Quản Lý Hồ Sơ Ngựa & Tiếp Nhận (`Horses & Intake`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/horses` | `hasAuthority('PERM_PROFILE_VIEW')` | Tìm kiếm và xem danh sách hồ sơ ngựa | `App.jsx` (`Horses`), `HorseSelect` |
| `GET /api/horses/mine` | `hasRole('HORSE_OWNER')` | Xem danh sách đàn ngựa thuộc quyền sở hữu của tôi | `App.jsx` (`Horses` chế độ chủ ngựa) |
| `GET /api/horses/{id}` | `hasAuthority('PERM_PROFILE_VIEW')` | Xem hồ sơ chi tiết, phả hệ, người phụ trách, tình trạng phí | `App.jsx` (`HorseDetail`) |
| `POST /api/horses` | `hasRole('CLUB_MANAGER') or (hasRole('HORSE_OWNER') and PERM_PROFILE_EDIT)` | Đăng ký hồ sơ ngựa mới vào câu lạc bộ | `App.jsx` (`HorseModal`) |
| `PUT /api/horses/{id}` | `hasRole('CLUB_MANAGER')` | Quản lý cập nhật toàn diện hồ sơ định danh và nhân sự phụ trách | `App.jsx` (`HorseEditModal`) |
| `PUT /api/horses/{id}/mine` | `hasRole('HORSE_OWNER')` | Chủ ngựa cập nhật nhận dạng màu lông và ảnh ngựa | `App.jsx` (`HorseDetail`) |
| `POST /api/horses/{id}/service-override` | `hasRole('CLUB_MANAGER')` | Quản lý duyệt hoặc hủy bảo lãnh thanh toán phí dịch vụ | `App.jsx` (`HorseDetail`) |
| `POST /api/horses/{id}/ownership` | `hasRole('CLUB_MANAGER')` | Chuyển nhượng hoặc gán chủ sở hữu mới cho ngựa | `App.jsx` (`TransferOwnerModal`) |
| `GET /api/paid-horse-assignments/trainer/pending` | `hasRole('HEAD_TRAINER') and PERM_TRAINING_EDIT` | Danh sách ngựa đủ điều kiện chờ HLV trưởng tiếp nhận | `TrainingReceptionPage.jsx` |
| `POST /api/paid-horse-assignments/trainer/{id}` | `hasRole('HEAD_TRAINER') and PERM_TRAINING_EDIT` | HLV trưởng tiếp nhận ngựa vào danh sách huấn luyện | `TrainingReceptionPage.jsx` |

---

## 3. Phân Hệ Cơ Sở Vật Chất, Chuồng Trại & Sân Đua (`Facilities & Stables`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/stables` | `hasAuthority('PERM_STABLE_CARE_VIEW')` | Tải danh mục khu chuồng và các ô chuồng | `AdminPages.jsx`, `StableCarePage.jsx` |
| `POST /api/stables` | `hasRole('CLUB_MANAGER') and PERM_STABLE_CARE_EDIT` | Tạo mới khu chuồng trại | `AdminPages.jsx` (`StablesPage`) |
| `POST /api/stables/{id}/stalls` | `hasRole('CLUB_MANAGER') and PERM_STABLE_CARE_EDIT` | Thêm ô chuồng mới vào khu chuồng | `AdminPages.jsx` (`StablesPage`) |
| `POST /api/stables/{stableId}/stalls/{stallId}/assign` | `hasRole('CLUB_MANAGER') and PERM_STABLE_CARE_EDIT` | Phân bổ ngựa vào ô chuồng (Trigger tối đa 4 ngựa/chuồng) | `AdminPages.jsx` (`StablesPage`) |
| `GET /api/racetracks` | `hasAuthority('PERM_TRAINING_VIEW')` | Tải danh sách sân đua và làn chạy đang hoạt động | `TrainingSchedulePage.jsx` |
| `GET /api/racetracks/{id}/availability` | `hasAuthority('PERM_TRAINING_VIEW')` | Kiểm tra tình trạng trống làn theo ngày và khung giờ tròn | `TrainingSchedulePage.jsx` |

---

## 4. Phân Hệ Kế Hoạch & Lịch Huấn Luyện (`Training Modules`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/horses/{id}/training-plans` | `hasAuthority('PERM_TRAINING_VIEW')` | Xem danh sách kế hoạch huấn luyện của con ngựa | `TrainingSchedulePage.jsx` |
| `POST /api/training-plans` | `hasAnyRole('CLUB_MANAGER','HEAD_TRAINER') and PERM_TRAINING_EDIT` | Tạo mới kế hoạch huấn luyện đa giai đoạn | `TrainingSchedulePage.jsx` |
| `PUT /api/training-plans/{id}` | `hasAnyRole('CLUB_MANAGER','HEAD_TRAINER') and PERM_TRAINING_EDIT` | Cập nhật kế hoạch huấn luyện | `TrainingSchedulePage.jsx` |
| `POST /api/training-plans/{id}/copy` | `hasAnyRole('CLUB_MANAGER','HEAD_TRAINER') and PERM_TRAINING_EDIT` | Sao chép kế hoạch huấn luyện sang ngựa khác | `TrainingSchedulePage.jsx` |
| `GET /api/training-schedules` | `hasAuthority('PERM_TRAINING_VIEW')` | Tra cứu lịch huấn luyện theo khoảng ngày / theo ngựa | `TrainingSchedulePage.jsx` |
| `GET /api/training-schedules/mine` | `hasAnyRole('STABLE_STAFF','VETERINARIAN')` | Tra cứu lịch tập của các con ngựa mà tôi phụ trách | `StableCarePage.jsx`, `VetPage.jsx` |
| `GET /api/training-schedules/{id}/owner-view` | `hasRole('HORSE_OWNER')` | Xem chi tiết buổi tập giao diện tinh giản cho chủ ngựa | `TrainingSchedulePage.jsx` |
| `POST /api/training-schedules` | `hasAnyRole('CLUB_MANAGER','HEAD_TRAINER') and PERM_TRAINING_EDIT` | Đặt lịch tập chi tiết (Trigger khung 06:00-23:00) | `TrainingSchedulePage.jsx` |
| `PATCH /api/training-schedules/{id}/ready` | `hasRole('STABLE_STAFF') or hasRole('CLUB_MANAGER')` | Nhân viên chuồng xác nhận sẵn sàng trước buổi tập | `StableCarePage.jsx` (`TrainingPrep`) |
| `POST /api/training-schedules/{id}/start` | `hasRole('HEAD_TRAINER') and PERM_TRAINING_EDIT` | HLV bấm bắt đầu buổi tập thực địa | `TrainingSchedulePage.jsx` |
| `POST /api/training-schedules/{id}/finish` | `hasRole('HEAD_TRAINER') and PERM_TRAINING_EDIT` | HLV bấm kết thúc buổi tập -> Tự động sinh task hồi phục | `TrainingSchedulePage.jsx` |
| `POST /api/training-evaluations` | `hasRole('HEAD_TRAINER') and PERM_TRAINING_EDIT` | Ghi nhận chấm điểm thể lực FR07 chi tiết sau buổi tập | `TrainingSchedulePage.jsx` |
| `GET /api/horses/{id}/training-evaluations` | `hasAuthority('PERM_TRAINING_VIEW')` | Tra cứu lịch sử đánh giá thể lực của ngựa | `App.jsx`, `TrainingMetricsPage.jsx` |
| `GET /api/horses/{id}/race-readiness` | `hasAuthority('PERM_TRAINING_VIEW')` | Tính toán chỉ số mức độ sẵn sàng thi đấu giải đua | `TrainingMetricsPage.jsx` |

---

## 5. Phân Hệ Y Tế, Bệnh Án Thú Y & Khóa Huấn Luyện (`Medical & Health`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/medical/queue` | `hasRole('VETERINARIAN') and PERM_MEDICAL_VIEW` | Danh sách ngựa chờ khám sàng lọc tại phòng chờ y tế | `VetPage.jsx` (`VetDashboard`) |
| `POST /api/medical/queue/{id}/receive` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Bác sĩ tiếp nhận ngựa vào ca khám y tế | `VetPage.jsx` (`VetDashboard`) |
| `POST /api/medical/queue/{id}/complete` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Hoàn tất khám ban đầu, chuyển ngựa chờ xếp ô chuồng | `VetPage.jsx` (`VetDashboard`) |
| `GET /api/health/barn-map` | `hasAuthority('PERM_MEDICAL_VIEW')` | Tải sơ đồ sức khỏe trực quan toàn bộ các khu chuồng | `VetPage.jsx` (`BarnHealthMapView`) |
| `GET /api/horses/{id}/medical-examinations` | `hasAuthority('PERM_MEDICAL_VIEW')` | Lịch sử bệnh án lâm sàng của ngựa | `VetPage.jsx` (`MedicalRecordsView`) |
| `POST /api/horses/{id}/medical-examinations` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Tạo phiếu khám bệnh bất biến, chẩn đoán, đơn thuốc | `VetPage.jsx` (`MedicalRecordsView`) |
| `POST /api/medical-examinations/{id}/addenda` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Thêm phụ lục bổ sung diễn tiến bệnh án | `VetPage.jsx` (`MedicalRecordsView`) |
| `GET /api/horses/{id}/injuries` | `hasAuthority('PERM_MEDICAL_VIEW')` | Lấy danh sách chấn thương và vị trí 3D | `VetPage.jsx`, `HorseBodyModel3D` |
| `POST /api/horses/{id}/injuries` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Đánh dấu chấn thương trên mô hình 3D (42 vùng giải phẫu) | `VetPage.jsx` (`InjuriesTab`) |
| `POST /api/injuries/{id}/recovery-updates` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Cập nhật tiến trình hồi phục vết thương | `VetPage.jsx` (`InjuriesTab`) |
| `POST /api/horses/{id}/training-locks` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Ban hành khóa huấn luyện y tế đình chỉ tập luyện | `VetPage.jsx` (`TrainingLocksTab`) |
| `POST /api/training-locks/{id}/release` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Bác sĩ xác nhận bình phục và giải phóng khóa tập y tế | `VetPage.jsx` (`TrainingLocksTab`) |
| `GET /api/preventive-care/due` | `hasAuthority('PERM_MEDICAL_VIEW')` | Danh sách lịch tiêm chủng, tẩy giun, móng đến hạn | `VetPage.jsx` (`PreventiveTab`) |
| `POST /api/preventive-care/{id}/complete` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Nghiệm thu thực hiện vắc-xin/tẩy giun/móng định kỳ | `VetPage.jsx` (`PreventiveTab`) |
| `POST /api/training-evaluations/{id}/vet-assessment` | `hasRole('VETERINARIAN') and PERM_MEDICAL_EDIT` | Nộp đánh giá lâm sàng độc lập sau buổi tập | `VetPage.jsx` (`TrainingAssessment`) |

---

## 6. Phân Hệ Chăm Sóc Chuồng, Dinh Dưỡng & Sự Cố (`Stable Care & Incidents`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/care-tasks` | `hasAuthority('PERM_STABLE_CARE_VIEW')` | Tra cứu danh sách nhiệm vụ chăm sóc trong ngày | `StableCarePage.jsx` (`CareTasksView`) |
| `POST /api/care-tasks/{id}/complete` | `hasAuthority('PERM_STABLE_CARE_EDIT')` | Hoàn thành nhiệm vụ chuồng kèm ảnh minh chứng | `StableCarePage.jsx` (`CareTasksView`) |
| `GET /api/horses/{id}/nutrition-plan/daily-checklist` | `hasAuthority('PERM_STABLE_CARE_VIEW')` | Tải checklist các bữa ăn trong ngày theo thực đơn | `StableCarePage.jsx` (`NutritionView`) |
| `POST /api/horses/{id}/nutrition-plan` | `hasAnyRole('VETERINARIAN','CLUB_MANAGER')` | Thiết lập thực đơn và định lượng khẩu phần dinh dưỡng | `VetPage.jsx`, `StableCarePage.jsx` |
| `POST /api/incidents` | `hasAuthority('PERM_STABLE_CARE_EDIT')` | Báo cáo sự cố chuồng trại kèm ảnh (Colic, Sốt, Móng) | `StableCarePage.jsx` (`IncidentsView`) |
| `POST /api/incidents/training-emergency` | `hasRole('HEAD_TRAINER') and PERM_TRAINING_VIEW` | HLV phát báo động khẩn cấp cứu hộ trong buổi tập | `TrainingEmergencyPage.jsx` |
| `GET /api/incidents/training-emergency/horses/{id}` | `hasRole('HORSE_OWNER') and PERM_PROFILE_VIEW` | Chủ ngựa theo dõi sự cố khẩn cấp trên sân của ngựa mình | `App.jsx` (`HorseDetail`) |
| `POST /api/incidents/{id}/acknowledge` | `hasRole('VETERINARIAN') or hasRole('CLUB_MANAGER')` | Tiếp nhận xử lý sự cố chuồng, mở phiếu khám | `VetPage.jsx` (`VetDashboard`) |
| `POST /api/incidents/{id}/dismiss` | `hasRole('VETERINARIAN') or hasRole('CLUB_MANAGER')` | Đóng sự cố nếu đánh giá không cần can thiệp y tế | `VetPage.jsx` (`VetDashboard`) |

---

## 7. Phân Hệ Giải Đua Ngựa & Đồng Thuận (`Racing Events & Registrations`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/races` | `hasAuthority('PERM_RACING_VIEW')` | Danh sách sự kiện giải đua ngựa | `TrainingRacePage.jsx` |
| `POST /api/races` | `hasRole('CLUB_MANAGER') and PERM_RACING_EDIT` | Tạo sự kiện đua ngựa mới | `TrainingRacePage.jsx` |
| `POST /api/races/{eventId}/registrations` | `hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and PERM_RACING_EDIT` | HLV nộp đơn đăng ký ngựa thi đấu (`SUBMITTED`) | `TrainingRacePage.jsx` |
| `GET /api/races/registrations` | `hasAuthority('PERM_RACING_VIEW')` | Tra cứu danh sách đăng ký theo giải hoặc theo ngựa | `TrainingRacePage.jsx` |
| `POST /api/races/registrations/{id}/decision` | `hasRole('HORSE_OWNER') and PERM_RACING_APPROVE` | Chủ ngựa duyệt (`APPROVE`) hoặc từ chối (`REJECT`) thi đấu | `TrainingRacePage.jsx` |
| `POST /api/races/registrations/{id}/confirm` | `hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and PERM_RACING_APPROVE` | Quản lý xác nhận thi đấu chính thức (Trigger chặn nếu chưa duyệt) | `TrainingRacePage.jsx` |
| `POST /api/horses/{id}/race-results` | `hasRole('CLUB_MANAGER') and PERM_RACING_EDIT` | Ghi nhận kết quả, thứ hạng thi đấu sau giải đua | `AdminPages.jsx`, `App.jsx` |

---

## 8. Phân Hệ Vật Tư Kho & Cung Ứng Chuồng (`Inventory & Replenishment`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/inventory/items` | `hasAnyAuthority('PERM_INVENTORY_VIEW','PERM_STABLE_CARE_VIEW')` | Danh mục vật tư, thức ăn, thuốc và tồn kho | `StableCarePage.jsx`, `InventoryCatalog` |
| `POST /api/inventory/items/{id}/usage` | `hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT')` | Ghi nhận xuất sử dụng vật tư tại chuồng (trừ tồn kho) | `StableCarePage.jsx` (`InventoryView`) |
| `GET /api/inventory/replenishments` | `hasAnyAuthority('PERM_INVENTORY_VIEW','PERM_STABLE_CARE_VIEW')` | Danh sách phiếu đề xuất bổ sung vật tư kho | `StableCarePage.jsx`, `AdminPages.jsx` |
| `POST /api/inventory/replenishments` | `hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT')` | Nhân viên chuồng tạo phiếu đề xuất vật tư (`PENDING`) | `StableCarePage.jsx` (`InventoryView`) |
| `POST /api/inventory/replenishments/{id}/review` | `hasRole('CLUB_MANAGER') or PERM_INVENTORY_APPROVE` | Quản lý câu lạc bộ phê duyệt cấp phát vật tư | `AdminPages.jsx`, `StableCarePage.jsx` |
| `POST /api/inventory/replenishments/{id}/receive` | `hasAnyAuthority('PERM_INVENTORY_EDIT','PERM_STABLE_CARE_EDIT')` | Nhân viên xác nhận nhận hàng vào kho (cộng tồn kho) | `StableCarePage.jsx` (`InventoryView`) |

---

## 9. Phân Hệ Tài Chính, Ví Điện Tử & Báo Cáo (`Finance, Wallet & Reports`)

| Phương Thức & Đường Dẫn | Phân Quyền Yêu Cầu (`@PreAuthorize`) | Mục Đích Nghiệp Vụ | Thành Phần Giao Diện Gọi Tới |
|---|---|---|---|
| `GET /api/wallet` | `hasRole('HORSE_OWNER')` | Số dư ví, danh sách gói dịch vụ theo từng ngựa | `WalletPage.jsx` |
| `POST /api/wallet/top-ups` | `hasRole('HORSE_OWNER')` | Tạo lệnh nạp tiền ví, sinh mã QR thanh toán PayOS | `WalletPage.jsx` |
| `GET /api/wallet/top-ups/{id}` | `hasRole('HORSE_OWNER')` | Kiểm tra trạng thái đơn nạp tiền | `WalletPage.jsx` |
| `POST /api/wallet/top-ups/{id}/fake-complete` | `hasRole('HORSE_OWNER')` | Mô phỏng hoàn tất thanh toán (Môi trường kiểm thử) | `WalletPage.jsx` |
| `POST /api/wallet/payos/webhook` | Công khai (Webhook PayOS) | Nhận tín hiệu IPN xác nhận tiền về từ PayOS | Cổng thanh toán PayOS |
| `POST /api/wallet/plans` | `hasRole('HORSE_OWNER')` | Mua gói dịch vụ chăm sóc cho ngựa (P30, P90, P365) | `WalletPage.jsx` |
| `PUT /api/wallet/horses/{id}/next-plan` | `hasRole('HORSE_OWNER')` | Đặt trước gói dịch vụ cho kỳ gia hạn kế tiếp | `WalletPage.jsx` |
| `POST /api/wallet/horses/{id}/renew` | `hasRole('HORSE_OWNER')` | Chủ động gia hạn sớm gói dịch vụ từ số dư ví | `WalletPage.jsx` |
| `GET /api/financial-transactions` | `hasAuthority('PERM_REPORTING_VIEW')` | Sổ cái kế toán, lịch sử giao dịch tài chính | `FinanceReportsPage.jsx` |
| `GET /api/financial-transactions/summary` | `hasAuthority('PERM_REPORTING_VIEW')` | Tổng hợp doanh thu, dòng tiền theo chu kỳ | `FinanceReportsPage.jsx` |
| `GET /api/notifications` | Người dùng đã xác thực | Hộp thư thông báo chuông cá nhân | `NotificationBell.jsx` |
| `PUT /api/notifications/{id}/read` | Người dùng đã xác thực | Đánh dấu đã đọc thông báo | `NotificationBell.jsx` |
