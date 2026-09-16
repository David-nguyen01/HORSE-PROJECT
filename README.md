# HORSE-PROJECT

Repository chứa toàn bộ mã nguồn của dự án HORSE theo mô hình monorepo.

## Cấu trúc

```text
HORSE-PROJECT/
├── BACKEND/     # API và xử lý phía máy chủ
├── FRONTEND/    # Giao diện người dùng
├── DATABASE/    # Script khởi tạo Microsoft SQL Server
├── .gitignore
└── README.md
```

Xem hướng dẫn riêng trong từng thư mục để cài đặt và chạy từng ứng dụng.

## Chạy local bằng VS Code

Yêu cầu trên máy:

- SQL Server chạy tại `localhost:1433`, bật SQL Authentication.
- Database `racehorse` đã được tạo bằng các script trong `DATABASE` theo đúng thứ tự ghi trong `DATABASE/README.md`.
- Java 21 trở lên, Maven, Node.js và npm.

Lần đầu mở project:

1. Mở đúng thư mục gốc `HORSE-PROJECT` bằng VS Code.
2. Cài các extension được VS Code đề xuất cho Java, Spring Boot và Maven.
3. Nếu chưa có database, chọn **Terminal → Run Task → Database: initialize local**. Task này chạy file tổng `DATABASE/racehorse_database_full.sql`.
4. Chọn **Terminal → Run Task → Frontend: install dependencies** một lần.
5. Mở **Run and Debug**, chọn **Full Stack (Local)** rồi nhấn `F5`.

VS Code sẽ chạy backend tại `http://localhost:8080` và frontend tại `http://localhost:5173`. Backend mặc định trỏ tới Azure SQL; mật khẩu phải được cung cấp qua biến môi trường `DB_PASSWORD`. Khi cần chạy SQL Server local, ghi đè `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` trong môi trường local, không commit credential vào repository.

Tài khoản quản lý mặc định sau khi chạy seed database:

```text
Email: admin@gmail.com
Password: Admin123
```

Các task kiểm tra có sẵn trong **Terminal → Run Task**:

- `Backend: test`
- `Database: initialize local`
- `Frontend: install dependencies`
- `Frontend: build`

Những quyết định nghiệp vụ chưa được chốt được lưu tại [BUSINESS_DECISIONS_PENDING.md](BUSINESS_DECISIONS_PENDING.md). Không tự triển khai các quy tắc trong file này khi chưa có xác nhận của chủ dự án.
