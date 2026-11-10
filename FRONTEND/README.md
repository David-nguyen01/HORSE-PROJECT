# Racehorse Club Frontend

Giao diện React/Vite. Bản Vercel gọi API trên Render; backend Render dùng chung
PostgreSQL Supabase cho cả nhóm.

## Cấu hình Vercel

Đặt `VITE_API_URL=https://horse-project-4qa4.onrender.com` trong Vercel →
Project → Settings → Environment Variables, rồi redeploy frontend. Biến Vite
được đóng vào JavaScript khi build, nên thay đổi biến mà không redeploy sẽ không
cập nhật trang đang phát hành. Không đưa mật khẩu database, JWT secret hoặc
khóa Cloudinary vào biến `VITE_*`.

Trang Vercel chính cũng có URL Render mặc định nếu chưa đặt `VITE_API_URL`;
giá trị `racehorse.api_url` đã lưu trong localStorage của từng trình duyệt vẫn
được ưu tiên. Khi chuyển từ cấu hình cũ, xóa giá trị tùy chỉnh trong giao diện
hoặc localStorage nếu trình duyệt vẫn gọi `127.0.0.1`.

Khi chạy frontend tại `localhost` mà không đặt `VITE_API_URL`, API mặc định
là `http://127.0.0.1:8080`.

## Chạy dự án

Backend phải chạy tại `http://127.0.0.1:8080`, sau đó:

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
- FR07 nhập trực tiếp chỉ số buổi tập cho HLV trưởng/Quản lý; chủ sở hữu xem trong hồ sơ ngựa.
- FR03 lưới trạng thái, ba biểu đồ, bộ lọc ngày/ngựa/trạng thái và xuất PNG/PDF.

Mỗi điểm biểu đồ FR03 hiển thị mã đánh giá để đối chiếu về bản ghi FR07 gốc. Giao diện không tự tính lại các chỉ số.
