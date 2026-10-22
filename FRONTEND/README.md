# Racehorse Club Frontend

Giao diện React/Vite. Khi deploy lên Vercel, giao diện dùng backend và SQL Server
**trên chính máy đang mở trang**, không dùng tunnel hoặc database của máy khác.

## Dùng trang Vercel với dữ liệu riêng trên từng máy

1. Trên **mỗi máy**: cài SQL Server, Java 21, Maven và `sqlcmd`; chạy
   `DATABASE/setup-local.ps1` theo `DATABASE/README.md` để tạo `racehorse_local`.
   Không dùng `-RecreateDatabase` nếu đã có dữ liệu.
2. Trên **mỗi máy**: đặt `RACEHORSE_LOCAL_DB_PASSWORD` và `JWT_SECRET` theo
   `BACKEND/README.md`, rồi chạy `BACKEND/run-local.ps1`. Giữ tiến trình backend
   hoạt động khi dùng web. Kiểm tra `http://127.0.0.1:8080/actuator/health`
   trả về JSON có `"status":"UP"`.
3. Mở `https://horse-project-phi.vercel.app/login` bằng Chrome hoặc Edge mới.
   Nhấn **Kiểm tra kết nối** và chấp nhận yêu cầu cho trang truy cập mạng cục bộ
   nếu trình duyệt hiển thị. Sau đó đăng nhập bằng tài khoản trong database
   **của máy đó**. Không tắt backend hay SQL Server trong lúc sử dụng.

Frontend production cố định gọi `http://127.0.0.1:8080`; `127.0.0.1` luôn là
máy của người mở trình duyệt, không phải máy của người deploy Vercel. Biến
`VITE_API_URL` chỉ dùng khi phát triển local. Không đưa mật khẩu SQL/JWT vào
`VITE_*`. Trình duyệt không hỗ trợ hoặc từ chối quyền mạng cục bộ có thể không
kết nối được từ Vercel; khi đó chạy frontend tại `http://localhost:5173` như
hướng dẫn dưới đây.

**Lưu ý:** Dữ liệu giữa các máy hoàn toàn độc lập; tạo tài khoản/ngựa trên máy A
không làm máy B có cùng dữ liệu. Không cấu hình Vercel rewrite `/api` sang
`localhost` vì đó sẽ là localhost của máy chủ Vercel, không phải của người dùng.

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
