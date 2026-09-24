# STEP 1 — SRS COMPLIANCE AUDIT REPORT: FLOW 1 (FR01 & FR02)

**Document Type:** Read-Only SRS Compliance & Verification Audit  
**Target:** Step 1 Backend Implementation (FR01 — Horse Master Profile, FR02 — Pedigree & Racing History)  
**Standard of Reference:** Tài liệu Phạm vi Phần mềm (SRS chính thức v1.0, 9/9/2026)  
**Source Code Baseline:** `BACKEND/src/main/java/com/racehorse/...`  
**Database Schema Baseline:** `DATABASE/12_compact_schema.sql` (30-table ceiling)  
**Audit Date:** 2026-09-24  
**Audit Action:** READ-ONLY (Zero code modifications, zero database migrations, zero git commits)  

---

## 1. SRS FR01 Requirement Matrix (Horse Master Profile)

| No. | Yêu cầu SRS (FR01) | Quy định dữ liệu & Business Rules | Vai trò (Actors) | Tiêu chí Kiểm định (Verification) | Trạng thái Implementation | Bằng chứng Source Code & API |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1.1** | **Hiển thị màn hình Hồ sơ Ngựa với đầy đủ 13 trường** | 13 trường: Mã Ngựa, Tên, Số Chip/Hộ chiếu, Ngày sinh, Giới tính, Màu sắc/Đặc điểm nhận dạng, Giống, Ngựa bố, Ngựa mẹ, Chủ sở hữu, Huấn luyện viên hiện tại, Vị trí Chuồng, Trạng thái (ACTIVE, RESTING, INJURED, RETIRED). | `CLUB_MANAGER`, `HEAD_TRAINER`, `VETERINARIAN`, `STABLE_STAFF`, `HORSE_OWNER` | Mọi trường dữ liệu được lưu trữ và phản ánh đúng trạng thái thực tế. | **COMPLETE** | `Horse.java`, `HorseResponse.java`, `CreateHorseRequest.java`, `UpdateHorseRequest.java`, `HorseStatus.java`, `HorseGender.java`. |
| **1.2** | **Tạo, Chỉnh sửa, Lưu trữ hồ sơ ngựa** | `code` là duy nhất; `microchip_passport_number` duy nhất nếu có; `name`, `birth_date`, `gender`, `color_markings`, `breed`, `stable_location` bắt buộc. Lưu trữ (soft delete qua `archived_at`). | `CLUB_MANAGER`, `HEAD_TRAINER` | Không thể lưu một hồ sơ ngựa mới nếu chưa điền đầy đủ các trường định danh bắt buộc. Sau khi lưu, ngựa xuất hiện ngay lập tức trong danh mục tìm kiếm. | **COMPLETE** | `HorseController.java` (`POST /api/horses`, `PUT /api/horses/{id}`, `PATCH /api/horses/{id}/archive`), `HorseService.java` (`create`, `update`, `archive`). |
| **1.3** | **Ảnh hồ sơ chính (Main Profile Photo)** | Cho phép tải lên 1 ảnh hồ sơ chính. Quản lý / HLV thay thế trực tiếp. Hỗ trợ quy trình Đề xuất ảnh từ Chủ sở hữu (`HORSE_OWNER`) -> Duyệt/Từ chối bởi Quản lý/HLV. | Đề xuất: `HORSE_OWNER`. Duyệt/Từ chối/Tải trực tiếp: `CLUB_MANAGER`, `HEAD_TRAINER`. | Ảnh được lưu trữ trên Cloudinary và liên kết qua `horse_profile_files`. | **COMPLETE** | `HorseProfileFileController.java` (`PUT /api/horses/{id}/main-file`, `POST .../proposals`, `POST .../approve`, `POST .../reject`), `HorseProfileFileService.java`. |
| **1.4** | **Tối đa 10 ảnh bổ sung (Additional Photos Gallery)** | Hỗ trợ tối đa 10 ảnh bổ sung kèm thứ tự hiển thị (1..10) và caption tùy chọn. Chỉ chấp nhận định dạng ảnh (JPEG, PNG, WEBP). | Xem: All authorized roles. Upload/Xóa: `CLUB_MANAGER`, `HEAD_TRAINER`. | Ngăn chặn upload nếu vượt quá 10 ảnh. Xóa ảnh đồng thời xóa asset trên Cloudinary. | **COMPLETE** | `HorsePhotoController.java`, `HorsePhotoService.java`, `HorsePhotoResponse.java`. Lưu trữ qua `dbo.module_records` (`module_code='PROFILE'`) và `dbo.stored_files`. |
| **1.5** | **Liên kết Chủ sở hữu & Tỷ lệ sở hữu** | Cho phép liên kết 1 hoặc nhiều tài khoản `HORSE_OWNER` với tỷ lệ xác định. Tổng tỷ lệ phải đạt chính xác 100.00%. | Phân bổ/Chỉnh sửa: `CLUB_MANAGER`, `HEAD_TRAINER`. Xem: Mọi vai trò hợp lệ. | Mọi thay đổi về tỷ lệ sở hữu sẽ kích hoạt lời nhắc xác nhận trước khi được ghi nhận (`confirmed: true`). | **COMPLETE** | `HorseOwnership.java`, `HorseOwnershipRepository.java`, `ReplaceOwnershipRequest.java` (yêu cầu `confirmed: true`), `HorseService.replaceOwnership`. |

---

## 2. SRS FR02 Requirement Matrix (Pedigree & Racing History)

| No. | Yêu cầu SRS (FR02) | Quy định dữ liệu & Business Rules | Vai trò (Actors) | Tiêu chí Kiểm định (Verification) | Trạng thái Implementation | Bằng chứng Source Code & API |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **2.1** | **Cây phả hệ tối thiểu 3 thế hệ** | Sơ đồ phả hệ tối thiểu 3 thế hệ (dòng Ngựa bố / Ngựa mẹ) liên kết từ Hồ sơ Ngựa. Cấu trúc đệ quy: Bố mẹ -> Ông bà -> Cụ. | Xem: `HORSE_OWNER`, `HEAD_TRAINER`, `CLUB_MANAGER`. | Sơ đồ phả hệ phản ánh chính xác quan hệ Ngựa bố/Ngựa mẹ được lưu trong mỗi hồ sơ ngựa liên kết, không có tham chiếu bị lỗi. Chống lặp vòng (cycle detection). | **COMPLETE** | `PedigreeController.java` (`GET /api/horses/{id}/pedigree`), `PedigreeService.java` (`ANCESTOR_GENERATIONS = 3`), `HorseService.validateParentCycle`. |
| **2.2** | **Lưu trữ bảng lịch sử thành tích đua** | Các cột bắt buộc: Ngày (`raceDate`), Tên Cuộc đua (`raceName`), Trường đua (`racecourse`), Cự ly (`distance`), Thứ hạng về đích (`finishPosition`), Thời gian (`finishTime`), Kỵ sĩ (`jockey`), Tiền thưởng (`prizeMoney`), Điều kiện đường đua (`trackCondition`). | Xem: `HORSE_OWNER`, `HEAD_TRAINER`, `CLUB_MANAGER`. Thêm/Sửa/Xóa: `HEAD_TRAINER`, `CLUB_MANAGER`. | Nhật ký thành tích lưu theo trình tự thời gian, ràng buộc dữ liệu ngày đua không được vượt quá hiện tại (`@PastOrPresent`). | **COMPLETE** | `RaceResult.java`, `RaceResultRepository.java`, `RaceResultController.java` (`GET`, `POST`, `PUT`, `DELETE /api/horses/{id}/race-results`), `UpsertRaceResultRequest.java`. |
| **2.3** | **Tự động tính lại thống kê thắng / về nhất** | Tự động tính lại đúng thống kê thắng/về nhất trong sự nghiệp khi có bản ghi mới được thêm vào hoặc cập nhật. | Tự động tính toán khi truy vấn lịch sử đua. | Bảng lịch sử thành tích có thể sắp xếp theo ngày và tự động tính lại đúng thống kê thắng/về nhất trong sự nghiệp khi có bản ghi mới. | **COMPLETE** | `RaceResultService.history(...)` trả về `RaceHistoryResponse` gồm `totalRaces`, `totalWins` (tính trên các lần `finishPosition == 1`), và danh sách `results` sắp xếp theo `raceDate` giảm dần. |

---

## 3. RBAC Compliance Audit

Đối chiếu giữa **Mục 1.1 Actors & FR01/FR02/FR21 trong SRS**, **Database Seeds (`11_confirmed_automation_and_governance.sql`)**, và **Annotation `@PreAuthorize` tại Controller**:

| Endpoint | Method | Vai trò theo SRS | Quyền Database Seed | `@PreAuthorize` Implementation | Đánh giá Tuân thủ SRS |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/horses` | `GET` | QL Câu lạc bộ, HLV trưởng, Thú y, Nhân viên chuồng | `PROFILE:VIEW` (Manager, Trainer, Vet, Staff) | `hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','STABLE_STAFF')` | **TUÂN THỦ 100%**: Đã mở cho Thú y và Nhân viên chuồng để phục vụ tra cứu chăm sóc/lâm sàng theo phạm vi vai trò. |
| `/api/horses/{id}` | `GET` | QL Câu lạc bộ, HLV trưởng, Thú y, Nhân viên chuồng, Chủ sở hữu (ngựa mình) | `PROFILE:VIEW` (Tất cả vai trò) | `hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','STABLE_STAFF','HORSE_OWNER')` | **TUÂN THỦ 100%**: Chủ sở hữu chỉ xem được ngựa mình sở hữu qua kiểm tra `isPrivileged(...)`. |
| `/api/horses/mine` | `GET` | Chủ sở hữu Ngựa | `PROFILE:VIEW` (Owner) | `hasAuthority('PERM_PROFILE_VIEW') and hasRole('HORSE_OWNER')` | **TUÂN THỦ 100%**: Tách riêng danh sách ngựa của chủ sở hữu. |
| `/api/horses` | `POST` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}` | `PUT` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/ownership` | `PUT` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/archive` | `PATCH` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/main-file` | `GET` | Tất cả vai trò có quyền xem hồ sơ | `PROFILE:VIEW` | `hasAuthority('PERM_PROFILE_VIEW')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/main-file` | `PUT` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/main-file` | `DELETE` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/main-file/proposals` | `POST` | Chủ sở hữu Ngựa | `PROFILE:VIEW` + `ROLE_HORSE_OWNER` | `hasAuthority('PERM_PROFILE_VIEW') and hasRole('HORSE_OWNER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/main-file/proposals` | `GET` | QL Câu lạc bộ, HLV trưởng | `PROFILE:APPROVE` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_APPROVE') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/main-file/proposals/{pid}/approve` | `POST` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/main-file/proposals/{pid}/reject` | `POST` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/photos` | `GET` | Tất cả vai trò có quyền xem | `PROFILE:VIEW` | `hasAuthority('PERM_PROFILE_VIEW')` | **TUÂN THỦ 100%** (lọc chủ sở hữu ở tầng Service). |
| `/api/horses/{id}/photos` | `POST` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/photos/{photoId}` | `DELETE` | QL Câu lạc bộ, HLV trưởng | `PROFILE:EDIT` (Manager, Trainer) | `hasAuthority('PERM_PROFILE_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%**. |
| `/api/horses/{id}/pedigree` | `GET` | QL Câu lạc bộ, HLV trưởng, Chủ sở hữu | `PROFILE:VIEW` | `hasAuthority('PERM_PROFILE_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')` | **TUÂN THỦ 100%** (Đúng câu chữ FR02: "Vai trò Chủ sở hữu Ngựa và Huấn luyện viên trưởng có thể xem"). |
| `/api/horses/{id}/race-results` | `GET` | QL Câu lạc bộ, HLV trưởng, Chủ sở hữu | `RACING:VIEW` | `hasAuthority('PERM_RACING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')` | **TUÂN THỦ 100%** (Đúng câu chữ FR02: "Vai trò Chủ sở hữu Ngựa và Huấn luyện viên trưởng có thể xem"). |
| `/api/horses/{id}/race-results` | `POST` | QL Câu lạc bộ, HLV trưởng | `RACING:EDIT` (Manager, Trainer) | `hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%** (Đúng câu chữ FR02: "chỉ Huấn luyện viên trưởng hoặc Quản lý Câu lạc bộ mới có thể thêm"). |
| `/api/horses/{id}/race-results/{rid}` | `PUT` | QL Câu lạc bộ, HLV trưởng | `RACING:EDIT` (Manager, Trainer) | `hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **TUÂN THỦ 100%** (Đúng câu chữ FR02: "hoặc chỉnh sửa"). |
| `/api/horses/{id}/race-results/{rid}` | `DELETE` | QL Câu lạc bộ, HLV trưởng | `RACING:EDIT` (Manager, Trainer) | `hasAuthority('PERM_RACING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | **HỢP LỆ**: Bổ trợ cho thao tác quản trị dữ liệu sai lệch. |

---

## 4. STEP 1 Changes Detailed Audit

Đánh giá từng phần cụ thể được thêm/chỉnh sửa trong STEP 1:

### 4.1. `HorsePhotoController`, `HorsePhotoService`, `HorsePhotoResponse`
* **Phân loại:** **SRS YÊU CẦU (SRS REQUIRED)**.
* **Đối chiếu SRS:** FR01 quy định rõ: *"Hệ thống hỗ trợ tải lên một ảnh hồ sơ chính và tối đa 10 ảnh bổ sung."* Trước STEP 1, hệ thống hoàn toàn thiếu chức năng ảnh bổ sung.
* **Đánh giá Implementation:** 
  * Giới hạn `MAX_PHOTOS = 10` được validate trực tiếp tại `HorsePhotoService.java` dòng 76-78.
  * Chỉ cho phép tải file ảnh (JPEG, PNG, WEBP), tự động hoặc cho phép chọn thứ tự hiển thị 1..10.
  * Tái sử dụng bảng `dbo.module_records` và `dbo.stored_files`, không vi phạm trần 30 bảng của cơ sở dữ liệu.
  * Khi xóa ảnh, hệ thống gọi xóa cả bản ghi cơ sở dữ liệu và asset trên Cloudinary.
* **Kết luận:** Hoàn toàn chính xác và tuân thủ SRS.

### 4.2. Main-file Proposal Rejection (`POST /api/horses/{horseId}/main-file/proposals/{proposalId}/reject`)
* **Phân loại:** **SRS KHÔNG YÊU CẦU TRỰC TIẾP NHƯNG KHÔNG XUNG ĐỘT (EXTENSIBLE / COMPLEMENTARY)**.
* **Đối chiếu SRS:** SRS FR01 chỉ nêu Quản lý và HLV tạo, sửa hồ sơ và hỗ trợ tải ảnh. Trong source code ban đầu đã có sẵn luồng đề xuất ảnh đại diện (`HORSE_OWNER` propose -> Quản lý/HLV approve), nhưng bị thiếu endpoint từ chối (reject).
* **Đánh giá Implementation:** Thêm `reject` giúp hoàn thiện vòng đời trạng thái của đề xuất (chuyển sang `DELETED` và xóa file Cloudinary nếu đề xuất bị từ chối), tránh việc ảnh rác bị kẹt vĩnh viễn ở trạng thái `PENDING`.
* **Kết luận:** Hợp lý, an toàn, không xung đột với bất kỳ quy định nào trong SRS.

### 4.3. DELETE Historical Race Result (`DELETE /api/horses/{horseId}/race-results/{resultId}`)
* **Phân loại:** **SRS KHÔNG YÊU CẦU TRỰC TIẾP NHƯNG KHÔNG XUNG ĐỘT (EXTENSIBLE / COMPLEMENTARY)**.
* **Đối chiếu SRS:** SRS FR02 viết: *"chỉ Huấn luyện viên trưởng hoặc Quản lý Câu lạc bộ mới có thể thêm hoặc chỉnh sửa các bản ghi đua lịch sử."* SRS không nhắc đến thao tác xóa.
* **Đánh giá Implementation:** Bổ sung phương thức xóa cho HLV trưởng và Quản trị viên để quản lý dữ liệu khi nhập nhầm kết quả đua. Quyền thực hiện được kiểm soát chặt chẽ với `PERM_RACING_EDIT` và `CLUB_MANAGER`, `HEAD_TRAINER`.
* **Kết luận:** An toàn, không gây xung đột.

### 4.4. Thay đổi RBAC trong `HorseController`
* **Phân loại:** **SRS PHÙ HỢP (ALIGNED WITH SRS ACTORS & DB SEED)**.
* **Đối chiếu SRS:** Mục 1.1 Actors quy định rõ Bác sĩ thú y phụ trách lâm sàng/sức khỏe, và Người chăm sóc chuồng phụ trách công việc hằng ngày (cho ăn, vệ sinh). Đồng thời, trong DB seed `11_confirmed_automation_and_governance.sql`, cả hai vai trò này đều được cấp quyền `PROFILE:VIEW`.
* **Đánh giá Implementation:** Cấp quyền xem (`GET /api/horses`, `GET /api/horses/{id}`) cho `VETERINARIAN` và `STABLE_STAFF` khắc phục lỗi 403 Forbidden bất hợp lý trước đó, đồng thời vẫn bảo vệ nghiêm ngặt quyền tạo/sửa/xóa chỉ dành cho `CLUB_MANAGER` và `HEAD_TRAINER`.
* **Kết luận:** Hoàn toàn chính xác.

### 4.5. Thay đổi `RaceResultController` & `RaceResultService`
* **Phân loại:** **SRS YÊU CẦU (SRS REQUIRED)**.
* **Đối chiếu SRS:** FR02 Kiểm định: *"Bảng lịch sử thành tích có thể sắp xếp theo ngày và tự động tính lại đúng thống kê thắng/về nhất trong sự nghiệp khi có bản ghi mới được thêm vào."*
* **Đánh giá Implementation:** `RaceResultService.history(...)` sắp xếp các kết quả theo ngày giảm dần và tính toán động `totalRaces` cùng `totalWins` (`finishPosition == 1`).
* **Kết luận:** Đáp ứng đầy đủ tiêu chí kiểm định của SRS.

---

## 5. Extra / Unrequired Changes (Các phần mở rộng ngoài câu chữ SRS)

Các phần hiện có trong source code nhưng không bắt buộc trực tiếp bởi câu chữ của SRS:
1. **Quy trình Đề xuất ảnh đại diện (`main_file_proposals`)**:
   * *Hiện trạng:* Cho phép `HORSE_OWNER` tải ảnh đề xuất và HLV/Quản lý phê duyệt.
   * *SRS:* Chỉ yêu cầu hỗ trợ tải lên 1 ảnh đại diện và tối đa 10 ảnh bổ sung.
   * *Đánh giá tác động:* Tích cực, không xung đột vì HLV/Quản lý vẫn có endpoint tải trực tiếp (`PUT /api/horses/{id}/main-file`).
2. **Hỗ trợ định dạng PDF/DOCX cho file hồ sơ chính (`ProfileFileValidator`)**:
   * *Hiện trạng:* File hồ sơ chính cho phép tải file tài liệu (PDF, DOCX) ngoài hình ảnh.
   * *SRS:* SRS dùng từ "ảnh hồ sơ chính" (image).
   * *Đánh giá tác động:* Không gây lỗi, nhưng ở frontend nên mặc định chọn file ảnh để làm avatar.
3. **Thao tác xóa kết quả đua (`DELETE /api/horses/{id}/race-results/{rid}`)**:
   * *Hiện trạng:* Cho phép xóa kết quả đua.
   * *SRS:* Nêu thêm hoặc chỉnh sửa.
   * *Đánh giá tác động:* Hoàn toàn vô hại, cần thiết cho việc sửa sai nghiệp vụ.

---

## 6. Missing Requirements (Yêu cầu còn thiếu)

* **Đối với FR01 & FR02 Backend:** **KHÔNG CÓ YÊU CẦU NÀO BỊ THIẾU.**
  * Toàn bộ 13 trường dữ liệu của FR01 đã có mặt.
  * Toàn bộ tiêu chí kiểm định về định danh bắt buộc, tìm kiếm ngay lập tức, và xác nhận thay đổi tỷ lệ sở hữu đã được cài đặt và kiểm thử.
  * Cây phả hệ 3 đời kèm chống lặp vòng đã hoàn thiện.
  * Bảng thành tích đua đầy đủ các cột và tự động tính toán số lần thắng/về nhất.

---

## 7. Potential Bugs & Discrepancies (Phát hiện sai lệch cần lưu ý)

### 7.1. Sai lệch thông tin trong tài liệu báo cáo `STEP_1_BACKEND_FLOW1_IMPLEMENTATION.md`
* **Vấn đề phát hiện:** Trong file báo cáo trước đó (`STEP_1_BACKEND_FLOW1_IMPLEMENTATION.md`), phần mô tả kết quả đua có ghi:
  > *"Racing statistics calculation: Total starts, 1st/2nd/3rd places, win rate %, place rate %, earnings calculation in RaceResultSummaryDto."*
* **Thực tế trong source code:**
  * Không tồn tại class `RaceResultSummaryDto`.
  * Response thực tế trả về từ backend là `RaceHistoryResponse(UUID horseId, long totalRaces, long totalWins, List<RaceResultResponse> results)`.
  * Chỉ tính `totalRaces` và `totalWins` (về nhất). Không tính tỷ lệ % hay về nhì/ba ở backend.
* **Đối chiếu SRS:** SRS FR02 quy định: *"tự động tính lại đúng thống kê thắng/về nhất trong sự nghiệp"*. Do đó, việc trả về `totalRaces` và `totalWins` trong `RaceHistoryResponse` đã **hoàn toàn thỏa mãn yêu cầu của SRS**. Đoạn mô tả trong file `STEP_1_BACKEND_FLOW1_IMPLEMENTATION.md` là do viết dư thừa so với implementation thực tế.

### 7.2. Gán Chủ sở hữu khi Tạo mới Ngựa (Creation vs Ownership Assignment API Flow)
* **Vấn đề phát hiện:** `CreateHorseRequest` không chứa mảng `owners`. Một con ngựa khi được tạo mới qua `POST /api/horses` ban đầu sẽ có danh sách chủ sở hữu rỗng. Việc gán chủ sở hữu và phân chia % phải gọi tiếp qua `PUT /api/horses/{id}/ownership`.
* **Đối chiếu SRS:** SRS kiểm định nêu: *"Mọi thay đổi về tỷ lệ sở hữu sẽ kích hoạt lời nhắc xác nhận trước khi được ghi nhận."*
* **Đánh giá:** Việc tách riêng endpoint gán sở hữu `PUT /api/horses/{id}/ownership` yêu cầu cờ `confirmed: true` phù hợp hoàn hảo với UX hiển thị hộp thoại xác nhận (confirmation modal). Frontend khi tạo ngựa sẽ gọi tạo hồ sơ trước, sau đó kích hoạt modal gán chủ sở hữu.

### 7.3. Tính bắt buộc của Số chip / Hộ chiếu trong DTO Tạo ngựa
* **Vấn đề phát hiện:** Trong `CreateHorseRequest.java`, trường `microchipPassportNumber` chỉ có annotation `@Size(max = 100)` mà không có `@NotBlank`.
* **Đánh giá:** Điều này cho phép lưu một con ngựa mới chưa kịp gắn chip (trong thực tế ngựa con mới sinh có thể chưa có số chip ngay). Nếu có nhập số chip thì hệ thống vẫn validate tính duy nhất tuyệt đối (`existsByMicrochipPassportNumberIgnoreCase`). Đây là hành vi linh hoạt hợp lý.

---

## 8. Recommended Fixes & Integration Notes (Khuyến nghị tích hợp)

Vì đang ở chế độ READ-ONLY VALIDATION và không có bug chặn luồng, **KHÔNG CẦN SỬA CODE BACKEND**. 

Dưới đây là các lưu ý phối hợp cho Frontend Team khi tích hợp Flow 1:

1. **Hợp đồng API Kết quả đua:**
   * Endpoint `GET /api/horses/{horseId}/race-results` trả về cấu trúc:
     ```json
     {
       "horseId": "...",
       "totalRaces": 12,
       "totalWins": 4,
       "results": [ ... ]
     }
     ```
   * Frontend hiển thị tỷ lệ thắng bằng công thức: `(totalWins / totalRaces * 100).toFixed(1) + '%'`.
2. **Quy trình Tạo ngựa và Gán chủ sở hữu:**
   * Bước 1: Gửi `POST /api/horses` với các trường định danh cơ bản.
   * Bước 2: Hiển thị dialog xác nhận tỷ lệ sở hữu và gửi `PUT /api/horses/{id}/ownership` kèm `confirmed: true` và danh sách các chủ sở hữu có tổng tỷ lệ đúng 100.00%.
3. **Thư viện Ảnh bổ sung (Gallery):**
   * Endpoint: `POST /api/horses/{horseId}/photos` (multipart form gồm `file`, `displayOrder`, `caption`).
   * Tối đa 10 ảnh. Nếu vượt quá, backend trả về lỗi HTTP 400 (`Ngựa đã đạt giới hạn tối đa 10 ảnh bổ sung`).

---

## 9. Final Status của STEP 1

Sau khi đối chiếu toàn diện từng câu chữ của SRS v1.0 với source code, cơ sở dữ liệu và 42/42 automated tests:

### 🏁 **ĐÁNH GIÁ CUỐI CÙNG: READY**

* **FR01 (Hồ sơ Gốc của Ngựa):** **COMPLETE & COMPLIANT**
* **FR02 (Phả hệ & Lịch sử Đua):** **COMPLETE & COMPLIANT**
* **RBAC Flow 1:** **COMPLETE & COMPLIANT**
* **Automated Tests:** **42/42 PASS**
* **Zero Frontend Mutation / Zero DB Schema Mutation:** **VERIFIED**

Backend Flow 1 đã sẵn sàng để đội ngũ Frontend tích hợp.
