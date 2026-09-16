# Các quyết định nghiệp vụ chưa chốt

Cập nhật ngày 16/09/2026. Các mục dưới đây chưa được tự suy đoán hoặc triển khai thành quy tắc nghiệp vụ cho đến khi chủ dự án xác nhận.

1. Quy tắc phân bổ chi phí khi một ngựa có nhiều chủ sở hữu.
2. Giao thức nhận dữ liệu realtime từ thiết bị trong tương lai. Hiện tại Head Trainer nhập chỉ số thủ công.
3. Định dạng mô hình 3D và hệ tọa độ dùng để đánh dấu vị trí chấn thương.
4. Thời gian lưu giữ Audit Log.
5. Mẫu, bố cục và nội dung báo cáo PDF/ảnh xuất ra.
6. Vòng đời đầy đủ của giáo án: nháp, duyệt, đang áp dụng, hoàn thành và hủy.
7. Quyền cập nhật trạng thái sức khỏe `FIT`, `MONITOR`, `INJURED`, `QUARANTINED`. Đề xuất hiện tại: Veterinarian cập nhật; Club Manager chỉ quản trị.
8. Thông tin Cloudinary thật cần được cung cấp qua biến môi trường, không ghi trực tiếp vào source code:
   - `CLOUDINARY_CLOUD_NAME`
   - `CLOUDINARY_API_KEY`
   - `CLOUDINARY_API_SECRET`

## Trạng thái kỹ thuật hiện tại

- Database chính có 30 bảng và đã vượt kiểm tra toàn vẹn.
- Backend và database đã được kiểm tra local.
- Các thay đổi hiện tại chưa được commit hoặc push lên GitHub.
