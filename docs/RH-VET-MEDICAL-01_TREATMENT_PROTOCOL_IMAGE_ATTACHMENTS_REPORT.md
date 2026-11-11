# BÁO CÁO KỸ THUẬT: ĐÍNH KÈM HÌNH ẢNH PHÁC ĐỒ ĐIỀU TRỊ (TASK RH-VET-MEDICAL-01)

## 1. Mục tiêu và Phạm vi (Objective & Scope)
Bổ sung tính năng đính kèm nhiều hình ảnh tùy chọn (multiple image attachments) vào mục Phác đồ điều trị ("Phác đồ điều trị & hướng dẫn can thiệp *") trong quy trình khám lâm sàng / tạo hồ sơ bệnh án của Bác sĩ Thú y tại tuyến đường `/vet/medical-records` (Bước 2: Chẩn đoán & Phác đồ).

Các hình ảnh đính kèm phải được lưu trữ an toàn, gắn chính xác vào phiếu khám bệnh, hiển thị trực quan trong danh sách bệnh án, và không làm ảnh hưởng đến cấu trúc cơ sở dữ liệu (Zero Migration) cũng như ma trận phân quyền hiện hữu.

---

## 2. Kết quả Khảo sát Kiến trúc (Architecture Discovery Findings)

### 2.1 Cơ chế lưu trữ và tải file hiện hữu
- **Cloudinary Integration:** Tái sử dụng `CloudStorageClient` và `StoredFileService` (`/api/files?scope=medical` hoặc `scope=medical-treatment`).
- **Xác thực định dạng & bảo mật file:** `ProfileFileValidator` kiểm tra header, magic bytes (PNG, JPEG, GIF, WEBP, BMP, TIFF) và giới hạn kích thước (tối đa 10MB từ client, 50MB Spring multipart limit).
- **Quyền tải file:** Tuyến `POST /api/files` được bảo vệ bằng `@PreAuthorize("hasAnyAuthority('PERM_PROFILE_EDIT','PERM_TRAINING_EDIT','PERM_MEDICAL_EDIT','PERM_STABLE_CARE_EDIT')")`. Bác sĩ Thú y có quyền `PERM_MEDICAL_EDIT`, hoàn toàn đủ thẩm quyền upload file mà không cần cấp thêm quyền mới hay mở rộng phạm vi vai trò.

### 2.2 Cơ chế lưu trữ hồ sơ bệnh án và tính tương thích Zero Schema Changes
- **Module Records Architecture:** Bệnh án được lưu trữ trong bảng `module_records` (`module_code='MEDICAL'`, `record_type='MEDICAL_EXAMINATION'`) với trường `data_json` dạng `NVARCHAR(MAX)` / JSON.
- **Attachment Persistence Design:**
  - Metadata file gốc và Cloudinary asset được quản lý trong bảng `stored_files` (`id`, `uploaded_by_user_id`, `cloudinary_asset_id`, `cloudinary_public_id`, `resource_type`, `file_format`, `original_filename`, `content_type`, `file_size`, `secure_url`).
  - Trong phiếu khám bệnh (`module_records.data_json`), mảng `attachments` được lưu trữ dưới dạng:
    ```json
    "attachments": [
      {
        "fileId": "2c50019d-6e3b-4736-ab3d-b15cdd2cdf39",
        "url": "https://res.cloudinary.com/demo/image/upload/v123456789/xray.jpg",
        "filename": "xray_hock.jpg",
        "fileSize": 102400,
        "contentType": "image/jpeg"
      }
    ]
    ```
  - **Tác động Schema:** **0** bảng mới, **0** cột mới, **0** file migration. Hoàn toàn tái sử dụng schema hiện hữu.

### 2.3 Kiểm tra Chống giả mạo và Bảo mật Sở hữu File (Ownership Security)
- Khi Bác sĩ gửi danh sách `attachmentFileIds`, `MedicalExaminationService.create` thực hiện truy vấn trực tiếp bảng `stored_files`:
  1. Kiểm tra file tồn tại (`ResourceNotFoundException`).
  2. Kiểm tra `uploaded_by_user_id == veterinarianId` (`BusinessException: Không có quyền sử dụng file đính kèm không thuộc sở hữu của bạn`). Ngăn chặn hoàn toàn việc chèn trộm UUID file của người dùng khác hoặc module khác.
  3. Kiểm tra `resource_type == 'image'` hoặc MIME bắt đầu bằng `image/` (`BusinessException: File đính kèm phải là hình ảnh`).

---

## 3. Danh sách File Khảo sát và Chỉnh sửa (Files Inspected & Modified)

### 3.1 Backend
1. **`BACKEND/src/main/java/com/racehorse/health/dto/MedicalAttachmentResponse.java` (Tạo mới):**
   - DTO record đại diện cho thông tin tệp đính kèm: `fileId`, `url`, `filename`, `fileSize`, `contentType`.
2. **`BACKEND/src/main/java/com/racehorse/health/dto/CreateMedicalExaminationRequest.java` (Cập nhật):**
   - Bổ sung `List<UUID> attachmentFileIds`.
   - Cung cấp overloaded constructors để tương thích ngược 100% với các bài test và caller hiện hữu.
3. **`BACKEND/src/main/java/com/racehorse/health/dto/MedicalExaminationResponse.java` (Cập nhật):**
   - Bổ sung `List<MedicalAttachmentResponse> attachments`.
   - Cung cấp overloaded constructors để tương thích ngược.
4. **`BACKEND/src/main/java/com/racehorse/health/dto/MedicalExaminationDetailResponse.java` (Cập nhật):**
   - Bổ sung `List<MedicalAttachmentResponse> attachments`.
   - Cung cấp overloaded constructors để tương thích ngược.
5. **`BACKEND/src/main/java/com/racehorse/health/service/MedicalExaminationService.java` (Cập nhật):**
   - Xác thực file đính kèm trong `create`: kiểm tra quyền sở hữu uploader, loại tài nguyên hình ảnh.
   - Trích xuất mảng `attachments` từ `data_json` trong `getDetail` và `listByHorse`.
6. **`BACKEND/src/test/java/com/racehorse/health/service/MedicalExaminationServiceTest.java` (Cập nhật test):**
   - Thêm 4 bài kiểm thử chuyên biệt:
     - `createWithAttachmentsPersistsAndReturnsAttachments`
     - `createRejectsAttachmentOwnedByAnotherUser`
     - `createRejectsNonExistentAttachment`
     - `createRejectsNonImageAttachment`

### 3.2 Frontend
1. **`FRONTEND/src/vetApi.js` (Cập nhật):**
   - Bổ sung hàm `uploadAttachment(file, scope = 'medical-treatment')` gửi multipart `FormData` đến `POST /api/files`.
2. **`FRONTEND/src/VetPage.jsx` (Cập nhật):**
   - Nhập khẩu biểu tượng `Image as ImageIcon` từ `lucide-react`.
   - Trong `CreateExamModal`:
     - Quản lý trạng thái `attachments`, `attachmentError`, `uploadingCount`.
     - `handleFileSelect`: kiểm tra định dạng MIME (`image/*`), dung lượng (<= 10MB), tải lên qua `vetApi.uploadAttachment`, theo dõi trạng thái tải và preview.
     - `removeAttachment`: cho phép xóa hình ảnh đã chọn trước khi lưu phiếu khám.
     - Kiểm tra chặn chuyển bước hoặc lưu phiếu khi còn ảnh đang trong tiến trình upload (`uploadingCount > 0`).
     - Chặn gửi trùng lặp (`busy`).
     - Gắn trường giao diện upload ảnh đính kèm ngay dưới textarea `"Phác đồ điều trị & hướng dẫn can thiệp *"`.
   - Trong `MedicalRecordsView`:
     - Hiển thị danh sách thumbnail hình ảnh đính kèm ngay dưới mục "Phác đồ điều trị" với liên kết mở tab mới và cơ chế `onError={handleImageFallback}` xử lý ảnh hỏng an toàn.
3. **`FRONTEND/src/medicalExaminationAttachments.test.js` (Tạo mới test):**
   - 6 bộ test tự động kiểm tra contract `vetApi.uploadAttachment`, vị trí DOM trong Bước 2, xác thực định dạng / dung lượng 10MB, payload `attachmentFileIds`, hiển thị thumbnail và mô phỏng vòng đời chọn / xóa / lỗi upload.

---

## 4. Kiểm chứng Phân quyền và Bảo mật (Authorization Verification)

| Vai trò (Role) | Quyền hạn tương ứng | Quyền Upload File đính kèm (`/api/files`) | Quyền Tạo Phiếu Khám (`/api/horses/{id}/medical-examinations`) | Quyền Xem Bệnh án & Đính kèm |
| :--- | :--- | :---: | :---: | :---: |
| **VETERINARIAN** | `ROLE_VETERINARIAN`, `PERM_MEDICAL_EDIT`, `PERM_MEDICAL_VIEW` | **Cho phép** | **Cho phép** (chính chủ sở hữu file) | **Cho phép** |
| **CLUB_MANAGER** | `ROLE_CLUB_MANAGER`, `PERM_MEDICAL_VIEW` | Theo phân quyền chung | **Từ chối** (403) | **Cho phép** |
| **HEAD_TRAINER** | `ROLE_HEAD_TRAINER`, `PERM_MEDICAL_VIEW` | Từ chối | **Từ chối** (403) | **Cho phép** |
| **HORSE_OWNER** | `ROLE_HORSE_OWNER`, `PERM_MEDICAL_VIEW` | Từ chối | **Từ chối** (403) | **Cho phép** (chỉ ngựa sở hữu) |
| **STABLE_STAFF** | `ROLE_STABLE_STAFF` | Từ chối | **Từ chối** (403) | **Từ chối** (403) |

---

## 5. Kết quả Kiểm thử Tự động (Automated Test Results)

### 5.1 Backend Tests
- **`MedicalExaminationServiceTest`:**
  - 23/23 tests PASSED (Bao gồm 19 test hồi quy hiện hữu và 4 test mới cho attachment persistence & ownership security).
- **`BarnHealthServiceTest`:**
  - 2/2 tests PASSED.
- **`Flow3ControllerRbacTest`:**
  - 5/5 tests PASSED (Xác minh toàn vẹn RBAC và method signatures qua Reflection).

### 5.2 Frontend Tests & Build
- **`npm test`:**
  - 66/66 tests PASSED (Bao gồm 60 test hồi quy hiện hữu và 6 test mới trong `medicalExaminationAttachments.test.js`).
- **`npm run build`:**
  - Vite production build hoàn tất thành công trong 888ms mà không phát sinh bất kỳ lỗi cú pháp nào.

---

## 6. Tác động Cơ sở dữ liệu (Database Schema Impact)
- **Database Schema Alterations:** **NONE** (0 migrations, 0 altered tables).
- Cơ chế lưu trữ JSON schema-less trong `module_records.data_json` kết hợp với bảng `stored_files` có sẵn bảo toàn trọn vẹn cơ sở dữ liệu.

---

## 7. Yêu cầu Kích hoạt Runtime (Runtime Activation Requirements)
- **Backend:** Các thay đổi DTO và Service trong Backend cần được nạp khi Backend được khởi động lại trong đợt triển khai kế tiếp (tiến trình Backend hiện hành vẫn đang chạy ổn định).
- **Frontend:** Do Vite dev server hỗ trợ HMR (Hot Module Replacement), các thay đổi trong `vetApi.js` và `VetPage.jsx` có hiệu lực ngay lập tức trong môi trường kiểm thử cục bộ.

---

## 8. Hạn chế Còn lại (Remaining Limitations)
- Tính năng đính kèm hình ảnh được thiết kế chuyên biệt cho hồ sơ bệnh án lâm sàng ban đầu của Bác sĩ Thú y (`CreateExamModal`). Phụ lục tiến triển (`AddMedicalExaminationAddendumRequest`) hiện vẫn duy trì ghi chú văn bản thuần túy theo yêu cầu phân tách phạm vi nhiệm vụ.
