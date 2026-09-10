# HORSE Frontend

Thư mục dành cho giao diện người dùng của HORSE.

Khi thêm frontend, hãy bổ sung hướng dẫn cài đặt, biến môi trường và lệnh khởi chạy tại đây.

## Quy tắc logout và protected route

Frontend phải thực hiện đủ các bước sau khi người dùng logout:

1. Gọi `POST /api/auth/logout` với refresh token.
2. Xóa access token, refresh token và thông tin người dùng khỏi bộ nhớ phía client.
3. Điều hướng tới `/login` bằng chế độ `replace`, không dùng `push`.
4. Mọi protected route phải kiểm tra token và gọi `GET /api/auth/me`; nếu API trả `401` thì tiếp tục xóa trạng thái đăng nhập và `replace` về `/login`.
5. Khi trang được khôi phục từ browser back-forward cache (`pageshow` với `event.persisted`), phải chạy lại bước kiểm tra phiên.

Backend lưu `sessionId` trong JWT và kiểm tra session trong SQL Server ở mỗi request. Vì thế ngay sau logout, access token cũ không thể gọi API, kể cả khi trình duyệt còn giữ bản hiển thị cũ trong lịch sử.
