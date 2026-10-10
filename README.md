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
- Công cụ `sqlcmd` và SQL Server cục bộ tại `localhost:1433`.
- Java 21 trở lên, Maven, Node.js và npm.

Lần đầu mở project:

1. Mở đúng thư mục gốc `HORSE-PROJECT` bằng VS Code.
2. Cài Java 21, Maven và Node.js; cài extension Java/Spring Boot trong VS Code nếu cần.
3. Khởi tạo `racehorse_local` theo [DATABASE/README.md](DATABASE/README.md). Script `DATABASE/setup-local.ps1` chạy các file SQL theo đúng thứ tự; **không dùng `-RecreateDatabase` khi muốn giữ dữ liệu hiện có**.
4. Tạo biến môi trường `JWT_SECRET` theo [BACKEND/README.md](BACKEND/README.md), rồi trong thư mục `BACKEND` chạy `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`.
5. Trong thư mục `FRONTEND`, chạy `npm install` (lần đầu) rồi `npm run dev`.

Backend chạy tại `http://localhost:8080`, frontend tại `http://localhost:5173`. Profile `local` trỏ `racehorse_local`; profile mặc định dành cho môi trường được cấu hình bằng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và `JWT_SECRET`. Không để credential thật trong source.

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
