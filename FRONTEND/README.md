# Racehorse Club Frontend

Giao diện React/Vite tạm thời để kiểm thử các API backend đã triển khai.

## Chạy dự án

Backend phải chạy tại `http://localhost:8080`, sau đó:

```bash
cd FRONTEND
npm install
npm run dev
```

Mở `http://localhost:5173`. Vite proxy toàn bộ `/api` và `/actuator` sang backend nên không cần sửa URL khi chạy local.

Tài khoản quản lý mặc định sau khi chạy các script database:

```text
Email: admin@gmail.com
Password: Admin123
```

Có thể tạo file `.env` từ `.env.example` nếu backend dùng URL khác.

## Phạm vi giao diện thử nghiệm

- Đăng nhập, đăng ký với role hợp lệ, làm mới access token và đăng xuất.
- Protected route xác minh phiên bằng `/api/auth/me`; logout dùng `replace` và chặn quay lại trang cũ bằng nút Back.
- Tổng quan theo dữ liệu thật, danh sách và chi tiết hồ sơ ngựa.
- Tạo ngựa, lưu trữ hồ sơ, xem phả hệ và thành tích đua theo quyền.
- Xem/thay/xóa file hồ sơ chính; chủ sở hữu chỉ gửi đề xuất.
- Quản lý tài khoản và thay đổi role dành cho `CLUB_MANAGER`.

FR03 chưa có API nên giao diện không tạo số liệu thể lực giả.
