# Racehorse Database

Thư mục này là nơi duy nhất chứa script khởi tạo Microsoft SQL Server. Backend chỉ kết nối và sử dụng schema đã có; Hibernate được cấu hình `ddl-auto=none` nên không tự tạo hoặc sửa bảng.

## Chạy lần đầu bằng SSMS

Kết nối SQL Server bằng tài khoản `sa`, sau đó chạy đúng thứ tự:

1. `00_create_database.sql` — tạo database `racehorse` nếu chưa tồn tại.
2. `01_create_schema.sql` — tạo bảng, khóa ngoại, ràng buộc và index.
3. `02_seed_data.sql` — tạo tài khoản quản lý câu lạc bộ mặc định.
4. `03_horse_role_features.sql` — bổ sung quyền sở hữu, huấn luyện viên hiện tại, lưu trữ hồ sơ và lịch sử đua.
5. `04_cloudinary_profile_files.sql` — lưu metadata file chính và đề xuất thay file trên Cloudinary.
6. `05_training_evaluations.sql` — lưu chỉ số buổi tập FR07 và tạo index truy vấn dashboard FR03.
7. `06_training_plans_scheduling_monitoring.sql` — kế hoạch/giai đoạn, lịch và lượt chạy thử, notification, telemetry, ngưỡng và cảnh báo cho FR04–FR06.
8. `07_health_medical.sql` — file dùng chung, trạng thái sức khỏe, khám bệnh/phụ lục, chấn thương 3D, khóa huấn luyện và chăm sóc phòng ngừa cho FR08–FR12.
9. `08_stable_care_inventory.sql` — chuồng/ô chuồng, sinh hoạt, dinh dưỡng có phiên bản, nhiệm vụ, sự cố, danh mục và tồn kho cho FR13–FR17/FR20.
10. `09_racing_reporting_rbac_audit.sql` — cuộc đua/đăng ký, sổ giao dịch, báo cáo chủ sở hữu, nền tảng RBAC động và audit append-only cho FR18–FR23.

Các script đều có kiểm tra dữ liệu hoặc đối tượng đã tồn tại, vì vậy có thể chạy lại mà không tạo trùng.

## Nguyên tắc thiết kế

- Dashboard FR03 đọc trực tiếp `training_evaluations`; database không tự tính chỉ số người dùng đã nhập.
- FR04 chặn giai đoạn trùng thời gian trong cùng một kế hoạch bằng trigger.
- FR05 chặn lịch trùng của cùng nhân viên và chặn đặt trùng làn chạy bằng trigger.
- FR08 lưu trạng thái hiện tại và tự ghi lịch sử trạng thái; FR09 không cho xóa hồ sơ khám, chỉ thêm phụ lục.
- FR11 chỉ cho tồn tại một khóa huấn luyện đang hoạt động trên mỗi ngựa.
- FR14 lưu từng phiên bản khẩu phần; lịch sử cũ không bị ghi đè.
- FR19/FR22 dùng chung `financial_transactions` và bảng nối báo cáo–giao dịch để mọi tổng số truy ngược được về dữ liệu gốc.
- FR23 dùng trigger chặn mọi thao tác sửa/xóa trên `audit_logs`. Thời gian lưu giữ nằm trong `audit_settings` và phải được quản lý câu lạc bộ cấu hình, vì tài liệu chưa nêu số ngày mặc định.

Các bảng mới là nền tảng database. Chúng không đồng nghĩa API/backend cho các FR tương ứng đã được triển khai. Những quyết định chưa có trong tài liệu như vòng đời kế hoạch đầy đủ, giao thức telemetry, quy trình duyệt bổ sung tồn kho và số ngày giữ audit không được tự đặt trong schema.

Tài khoản mặc định sau khi seed:

```text
Email: admin@gmail.com
Password: Admin123
Role: CLUB_MANAGER
```

Mật khẩu trong database được lưu dưới dạng BCrypt, không lưu chuỗi `Admin123` trực tiếp.
