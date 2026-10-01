# Danh sách API chưa được Backend hỗ trợ (Cần Mock)

## 1. Màn Hình HORSE_OWNER
- **Đăng ký / Sửa hồ sơ ngựa (Owner)**: `POST/PUT /api/horses` hiện tại chỉ cho phép `CLUB_MANAGER` và `HEAD_TRAINER`. Owner cần có API riêng (hoặc cấp quyền tạm) để tạo ngựa, có thể tự động gán `paymentStatus = UNPAID`, `status = DRAFT` hoặc `ACTIVE`, `code` tự sinh.
- **Thêm ngựa cha/mẹ bằng tay (Hồ sơ ngoài)**: Khi owner thêm ngựa nhưng ngựa cha/mẹ không thuộc sở hữu của CLB (không có trong DB), cần API hỗ trợ lưu thông tin ngựa cha/mẹ dưới dạng chuỗi (text) thay vì bắt buộc dùng `sireId` / `damId` (khóa ngoại).

## 2. Dữ liệu bổ sung cho Horse Detail (Thiết kế 4 người)
- **Bác sĩ chẩn đoán (Veterinarian)**: API GET `/api/horses/{id}` chưa trả về người bác sĩ khám gần nhất. (Đang dùng mock logic ở hàm `getHorseAssignments`).
- **Người chăm sóc (Care Staff)**: API GET `/api/horses/{id}` chưa trả về nhân viên chăm sóc được gán. (Đang dùng mock logic ở hàm `getHorseAssignments`).
