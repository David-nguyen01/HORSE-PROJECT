# Racehorse Database

Thư mục này là nơi duy nhất chứa script khởi tạo Microsoft SQL Server. Backend chỉ kết nối và sử dụng schema đã có; Hibernate được cấu hình `ddl-auto=none` nên không tự tạo hoặc sửa bảng.

## Cài đặt bằng file tổng

Mở và chạy `racehorse_database_full.sql` trong SSMS, hoặc chạy task `Database: initialize local` trong VS Code. File tổng tạo database, toàn bộ schema, constraint/index/trigger, 5 role cố định và tài khoản quản lý mặc định. File có thể chạy lại an toàn và là lựa chọn chuẩn cho cài đặt mới.

`build-full-database.ps1` tái tạo file tổng từ các migration nguồn. Kết quả cuối cùng có đúng **30 bảng**. `user_roles` là bảng gán role chính thức; bảng nền trùng lặp `user_role_assignments` không tồn tại trong schema cuối.

## Các migration nguồn

Các file dưới đây được giữ để theo dõi lịch sử thay đổi. Nếu không dùng file tổng, kết nối SQL Server bằng tài khoản `sa`, sau đó chạy đúng thứ tự:

1. `00_create_database.sql` — tạo database `racehorse` nếu chưa tồn tại.
2. `01_create_schema.sql` — tạo bảng, khóa ngoại, ràng buộc và index.
3. `02_seed_data.sql` — tạo tài khoản quản lý câu lạc bộ mặc định.
4. `03_horse_role_features.sql` — bổ sung quyền sở hữu, huấn luyện viên hiện tại, lưu trữ hồ sơ và lịch sử đua.
5. `04_cloudinary_profile_files.sql` — lưu metadata file chính và đề xuất thay file trên Cloudinary.
6. `05_training_evaluations.sql` — lưu chỉ số buổi tập FR07 và tạo index truy vấn dashboard FR03.
7. `06_training_plans_scheduling_monitoring.sql` — kế hoạch/giai đoạn, lịch và lượt chạy thử, notification, telemetry, ngưỡng và cảnh báo cho FR04–FR06.
8. `07_health_medical.sql` — file dùng chung, trạng thái sức khỏe, khám bệnh/phụ lục, chấn thương 3D, khóa huấn luyện và chăm sóc phòng ngừa cho FR08–FR12.
9. `08_stable_care_inventory.sql` — chuồng/ô chuồng, sinh hoạt, dinh dưỡng có phiên bản, nhiệm vụ, sự cố, danh mục và tồn kho cho FR13–FR17/FR20.
10. `09_racing_reporting_rbac_audit.sql` — cuộc đua/đăng ký, sổ giao dịch, ma trận quyền cho 5 role cố định và audit append-only cho FR18–FR23.
11. `10_confirmed_business_rules.sql` — cấu hình đường đua 06:00–24:00, slot 60 phút và lịch sử cân nặng nhập thủ công.
12. `11_confirmed_automation_and_governance.sql` — telemetry nhập tay, chống gửi thông báo trùng, quyết định duyệt vật tư, VND, báo cáo ngày và dữ liệu ma trận quyền mặc định.
13. `12_compact_schema.sql` — gộp schema từ 62 bảng thiết kế chi tiết xuống 30 bảng vận hành mà không thay đổi 5 role hay API hiện có.

Các script đều có kiểm tra dữ liệu hoặc đối tượng đã tồn tại, vì vậy có thể chạy lại mà không tạo trùng.

## Nguyên tắc thiết kế

- Dashboard FR03 đọc trực tiếp `training_evaluations`; database không tự tính chỉ số người dùng đã nhập.
- FR04 chặn giai đoạn trùng thời gian trong cùng một kế hoạch bằng trigger.
- FR05 chặn lịch trùng của cùng nhân viên và chặn đặt trùng làn chạy bằng trigger.
- FR08 lưu trạng thái hiện tại và tự ghi lịch sử trạng thái; FR09 không cho xóa hồ sơ khám, chỉ thêm phụ lục.
- FR11 chỉ cho tồn tại một khóa huấn luyện đang hoạt động trên mỗi ngựa.
- FR14 lưu từng phiên bản khẩu phần; lịch sử cũ không bị ghi đè.
- FR19/FR22 dùng chung `financial_transactions`; danh sách ngựa và giao dịch đóng băng trong báo cáo được lưu bằng JSON ngay trên `owner_reports`.
- FR23 dùng trigger chặn mọi thao tác sửa/xóa trên `audit_logs`. Thời gian lưu giữ nằm trong `audit_settings` và phải được quản lý câu lạc bộ cấu hình, vì tài liệu chưa nêu số ngày mặc định.
- FR21 giữ đúng 5 role trong ràng buộc `user_roles`; ứng dụng không cung cấp API tạo thêm role. Quản lý câu lạc bộ được thay role của tài khoản khác.
- FR06 chỉ nhận chỉ số do Head Trainer nhập thủ công; không tích hợp thiết bị realtime ở giai đoạn này.
- FR12 và báo cáo chủ sở hữu dùng job backend chạy lúc 06:00 theo múi giờ câu lạc bộ. `deduplication_key` ngăn job gửi cùng một thông báo nhiều lần.
- `replenishment_requests.decision_status` có bốn trạng thái rõ ràng: `PENDING`, `APPROVED`, `REJECTED`, `FULFILLED`.
- Tất cả giao dịch hiện dùng `VND`; quy tắc phân bổ chi phí giữa nhiều chủ sở hữu chưa được áp dụng.

Những quyết định chưa có trong tài liệu như vòng đời kế hoạch đầy đủ, phân bổ chi phí đồng sở hữu và số ngày giữ audit không được tự đặt trong schema.

## Schema compact 30 bảng

Các phần được gộp có chủ đích:

- `role_definitions`, `module_permissions`, `role_permission_assignments` → `role_permissions`.
- `preventive_care_completions` → các cột hoàn thành trên `preventive_care_schedules`.
- `owner_report_horses`, `owner_report_transactions` → hai cột JSON trên `owner_reports`.
- Trạng thái sức khỏe hiện tại (quan hệ 1-1) → các cột `health_*` trên `horses`; lịch sử chi tiết chưa có API được lưu trữ trong `module_records`.
- Các bảng chi tiết y tế chưa có API, dinh dưỡng, nhiệm vụ, sự cố và kho → `module_records`, phân biệt bằng `module_code` và `record_type`.
- `stables` vẫn là bảng quan hệ riêng để giữ khóa ngoại từ `financial_transactions.stable_id`; không lưu tham chiếu chuồng dưới dạng JSON.
- Dữ liệu cũ của các bảng được gộp được đóng gói thành JSON trong `module_records` trước khi bảng cũ bị xóa.

Các bảng nghiệp vụ đang được backend truy cập thường xuyên như `horses`, `training_plans`, `training_schedules`, `training_evaluations`, telemetry, cảnh báo, khóa huấn luyện và tài chính vẫn được giữ quan hệ riêng để không ảnh hưởng hiệu năng hoặc logic role.

Tài khoản mặc định sau khi seed:

```text
Email: admin@gmail.com
Password: Admin123
Role: CLUB_MANAGER
```

Mật khẩu trong database được lưu dưới dạng BCrypt, không lưu chuỗi `Admin123` trực tiếp.
