# Racehorse Database

Thư mục này là nơi duy nhất chứa script khởi tạo Microsoft SQL Server. Backend chỉ kết nối và sử dụng schema đã có; Hibernate được cấu hình `ddl-auto=none` nên không tự tạo hoặc sửa bảng.

## Chạy lần đầu bằng SSMS

Kết nối SQL Server bằng tài khoản `sa`, sau đó chạy đúng thứ tự:

1. `00_create_database.sql` — tạo database `racehorse` nếu chưa tồn tại.
2. `01_create_schema.sql` — tạo bảng, khóa ngoại, ràng buộc và index.
3. `02_seed_data.sql` — tạo tài khoản quản lý câu lạc bộ mặc định.
4. `03_horse_role_features.sql` — bổ sung quyền sở hữu, huấn luyện viên hiện tại, lưu trữ hồ sơ và lịch sử đua.
5. `04_cloudinary_profile_files.sql` — lưu metadata file chính và đề xuất thay file trên Cloudinary.

Các script đều có kiểm tra dữ liệu hoặc đối tượng đã tồn tại, vì vậy có thể chạy lại mà không tạo trùng.

Tài khoản mặc định sau khi seed:

```text
Email: admin@gmail.com
Password: Admin123
Role: CLUB_MANAGER
```

Mật khẩu trong database được lưu dưới dạng BCrypt, không lưu chuỗi `Admin123` trực tiếp.
