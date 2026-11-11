# BÁO CÁO ĐỒNG BỘ SOURCE MIRROR VÀ KHẢO SÁT KIẾN TRÚC QUẢN LÝ NHIỆM VỤ CHUỒNG
## (TASK RH-STABLE-TASK-MANAGEMENT-01)

---

## 1. Định danh và Kiểm chứng Tính Toàn vẹn Snapshot (Source Snapshot Identity & Integrity)

Quá trình đồng bộ một chiều (one-way synchronization) từ kho mã nguồn chuẩn của dự án (`HORSE-PROJECT`) sang kho lưu trữ cá nhân phục vụ đánh giá (`clone-horse/source-mirror`) đã được thực thi thành công thông qua công cụ tự động hóa đã được kiểm định an toàn (`sync-source-mirror.ps1`).

### Thông số Snapshot:
- **Snapshot ID:** `SNAPSHOT-20261111-030936-DEVELOP-6516562`
- **Thời điểm đồng bộ (Timestamp):** `2026-11-11T03:09:36+07:00`
- **Kho nguồn (Source Repository):** `C:\DEV\RACE HORSE\HORSE-PROJECT`
- **Nhánh nguồn (Source Branch):** `develop`
- **Source HEAD Commit:** `65165624859051913e940f53dee8253e02040eb7`
- **Thư mục đích (Mirror Destination):** `C:\DEV\clone-horse\source-mirror`
- **Tổng số tệp được đồng bộ:** **453** files
- **Tổng dung lượng mã nguồn:** **3.99 MB** (4,188,194 bytes)
- **Tình trạng xác thực (Verification Status):** `VERIFIED_COMPLETE` (100% SHA-256 Match across all 453 files)
- **Manifest:** Đã cập nhật `snapshot-manifest.json` và `snapshot-manifest.md`.
- **Bộ kiểm thử công cụ (`test-mirror-tooling.ps1`):** **10/10** test cases PASSED (xác nhận kiểm tra traversal, reparse point, bảo vệ file hạ tầng, loại trừ file bí mật và đối chiếu mã băm).

---

## 2. Bằng chứng Bảo toàn Kho Mã nguồn Sản phẩm (Product Repository Preservation Evidence)

Kho mã nguồn sản phẩm (`C:\DEV\RACE HORSE\HORSE-PROJECT`) tuân thủ nghiêm ngặt ranh giới an toàn:
- **Zero Commit / Zero Push:** Không thực hiện bất kỳ lệnh commit hoặc push nào trong `HORSE-PROJECT`.
- **Zero File Mutation / Cleanup:** Không reset, checkout, stash, clean hoặc xóa bất kỳ file nào.
- **Bảo toàn 100% các tệp đang sửa đổi và chưa theo dõi (Working Tree):**
  - **31 files modified:** Bao gồm các tệp thuộc các tác vụ trước đó (`BarnHealthMapResponse.java`, `BarnHealthService.java`, `CreateMedicalExaminationRequest.java`, `MedicalExaminationDetailResponse.java`, `MedicalExaminationResponse.java`, `MedicalExaminationService.java`, `HorseResponse.java`, `HorseMapper.java`, `HorsePhotoController.java`, `HorseProfileFileController.java`, `HorseProfileFileService.java`, `StableController.java`, `StableService.java`, `TrainingScheduleController.java`, `TrainingScheduleRepository.java`, `TrainingScheduleService.java`, 6 unit tests Backend, `AdminPages.jsx`, `App.jsx`, `StableCarePage.jsx`, `TrainingSchedulePage.jsx`, `VetPage.jsx`, `api.js`, `styles.css`, `trainingRules.js`, `vetApi.js`).
  - **10 untracked files/directories:** `MedicalAttachmentResponse.java`, `dailyRoutine.test.js`, `imageFallback.js`, `imageFallback.test.js`, `medicalExaminationAttachments.test.js`, `stallRefreshAndTrainerEligibility.test.js`, `trainingPreparation.test.js`, các thư mục controller test và `docs/`.
- **Zero Database Writes / Migrations:** Không thực thi bất kỳ câu lệnh ghi CSDL hoặc migration nào.
- **Zero Process Restarts:** Không khởi động lại Backend hay Frontend.

---

## 3. Kiến trúc Hiện hữu của Hệ thống Nhiệm vụ Chăm sóc Chuồng (Current Stable Task Architecture)

Qua khảo sát toàn diện mã nguồn Backend, Frontend và Cơ sở dữ liệu:

### 3.1 Sự phân tách giữa Định nghĩa Nhiệm vụ (Definitions) và Phiên bản Thực thi (Instances)
Hệ thống **ĐÃ CÓ SẴN** sự phân tách giữa:
1. **Bảng mẫu định nghĩa nhiệm vụ (`dbo.care_routine_definitions`):** Lưu trữ các quy trình chăm sóc tiêu chuẩn/thường nhật được lặp lại hàng ngày.
2. **Bảng phiên bản nhiệm vụ trong ca (`dbo.care_tasks`):** Lưu trữ từng nhiệm vụ cụ thể của một ngày xác định (`scheduled_date`), ghi nhận trạng thái (`PENDING`, `COMPLETED`, `CANCELLED`), người thực hiện (`assigned_staff_id`, `completed_by_user_id`), ghi chú và ảnh minh chứng.

### 3.2 Vị trí Định nghĩa Nhiệm vụ trong Mã nguồn
- **Database Table:** `dbo.care_routine_definitions` được tạo trong `DATABASE/05_health_and_care.sql`.
- **Dữ liệu hạt nhân (Seed Data):** 5 nhiệm vụ thường nhật được seed tại `DATABASE/09_seed_reference_data.sql` (dòng 178-207).
- **Frontend Labels:** Danh mục loại nhiệm vụ được định nghĩa tại `FRONTEND/src/StableCarePage.jsx` (`TASK_TYPE_LABELS`: `MUCKING_OUT`, `FEEDING`, `BATHING`, `ICE_BOOTS`, `GROOMING`, `WALKING`, `OTHER`).
- **Khoảng trống hiện tại (Architecture Gap):** Backend hiện **chưa có** bất kỳ Controller hoặc API nào để Quản lý Câu lạc bộ (Club Manager) xem, thêm, sửa, đổi thứ tự hay bật/tắt (Active/Reserve) các bản ghi trong `dbo.care_routine_definitions`. Toàn bộ dữ liệu hiện tại chỉ được nạp qua SQL seed.

### 3.3 Cơ chế Sinh Nhiệm vụ Tự động (Catch-up Generation)
- Khi nhân viên chuồng truy cập màn hình `/stable-care/tasks`, Frontend gọi `GET /api/care-tasks?date=YYYY-MM-DD`.
- Tại `CareTaskService.java:33` (`ensureRoutineInstancesForDate(date)`), Backend tự động thực thi:
  ```sql
  INSERT INTO dbo.care_tasks (
      id, routine_definition_id, task_category, scope, horse_id,
      title, description, task_type, scheduled_date, scheduled_time, status,
      created_at, updated_at
  )
  SELECT
      NEWID(), d.id, 'ROUTINE', d.scope, NULL,
      d.title, d.description, d.task_type, ?, d.scheduled_time, 'PENDING',
      SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET()
  FROM dbo.care_routine_definitions d
  WHERE d.is_active = 1
    AND NOT EXISTS (
        SELECT 1 FROM dbo.care_tasks t
        WHERE t.routine_definition_id = d.id
          AND t.scheduled_date = ?
          AND t.horse_id IS NULL
    );
  ```
- **Tính chất quan trọng:**
  - Chỉ các định nghĩa có `is_active = 1` mới được sinh thành nhiệm vụ trong ngày.
  - Ràng buộc duy nhất `uq_care_tasks_facility_routine_instance` ngăn ngừa hoàn toàn việc sinh trùng lặp trong cùng một ngày.
  - Các nhiệm vụ thường nhật này ban đầu có `assigned_staff_id = NULL` (việc chung của ca trực), nhân viên chuồng nào hoàn thành đầu tiên sẽ được hệ thống tự động ghi nhận là người thực hiện.

---

## 4. Danh mục 5 Định nghĩa Nhiệm vụ Hiện hữu (Existing Task Definitions)

Trích xuất trực tiếp từ `DATABASE/09_seed_reference_data.sql`:

| STT | Mã (Code) | Tiêu đề Nhiệm vụ (Title) | Mô tả Chi tiết (Description) | Loại (Task Type) | Giờ thực hiện | Phạm vi (Scope) | Thứ tự | Trạng thái |
| :---: | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| 1 | `ROUTINE_MORNING_STABLE_CHECK` | Kiểm tra an toàn khu vực chuồng & thông gió | Kiểm tra tổng quan khu vực chuồng nuôi, chốt khóa an toàn, độ thông thoáng và đèn chiếu sáng đầu ca sáng. | `OTHER` | 07:00:00 | `FACILITY` | 10 | Active (`is_active=1`) |
| 2 | `ROUTINE_WATER_TROUGH_CHECK` | Kiểm tra & vệ sinh hệ thống máng nước tự động | Kiểm tra van phao tự động, áp lực nước và cọ rửa máng uống đảm bảo nguồn nước sạch cho toàn bộ chuồng trại. | `FEEDING` | 08:00:00 | `FACILITY` | 20 | Active (`is_active=1`) |
| 3 | `ROUTINE_MORNING_MUCKING` | Dọn phân chuồng & thay chất độn chuồng ca sáng | Thu gom phân, thay lớp rơm/mùn cưa ẩm ướt và rải vôi bột khử khuẩn lối đi hành lang chuồng. | `MUCKING` | 08:30:00 | `FACILITY` | 30 | Active (`is_active=1`) |
| 4 | `ROUTINE_MIDDAY_FORAGE` | Bổ sung cỏ khô & kiểm tra máng ăn trưa | Cấp thêm cỏ khô Alfalfa/Timothy cho từng ô chuồng và theo dõi lượng ăn của các ngựa trong chuồng. | `FEEDING` | 11:30:00 | `FACILITY` | 40 | Active (`is_active=1`) |
| 5 | `ROUTINE_EVENING_BEDDING_CHECK` | Kiểm tra đệm lót & khóa chốt ca tối | San phẳng lớp đệm lót cuối ngày, bổ sung rơm sạch và chốt khóa an toàn toàn bộ cửa chuồng trước khi hết ca. | `OTHER` | 16:30:00 | `FACILITY` | 50 | Active (`is_active=1`) |

---

## 5. Đề xuất 4 Nhiệm vụ Thư viện Dự phòng (Proposed Four Reserve Tasks)

Căn cứ theo đúng tài liệu đặc tả trách nhiệm của Nhân viên chuồng (`BUSINESS_DOCS/roles/ROLE_STABLE_STAFF.md`) và các mã phân loại chuẩn trong `TASK_TYPE_LABELS`:

### 1. Phun khử trùng tiêu độc chuồng trại & rải vôi lối đi (Biosecurity Disinfection)
- **Mã đề xuất:** `ROUTINE_FACILITY_DISINFECTION`
- **Tiêu đề:** `Phun khử trùng tiêu độc chuồng trại & rải vôi lối đi`
- **Mô tả:** `Tiến hành phun sương dung dịch khử trùng chuồng nuôi, rải vôi bột sát khuẩn hành lang và bãi xuất nhập ngựa định kỳ nhằm phòng ngừa dịch bệnh lây nhiễm.`
- **Loại nhiệm vụ:** `MUCKING_OUT` (hoặc `OTHER`)
- **Giờ thực hiện:** `14:00:00`
- **Phạm vi (Scope):** `FACILITY`
- **Thứ tự (Sort order):** 60
- **Trạng thái khởi tạo:** Dự phòng (`is_active = 0`)

### 2. Chải lông, làm sạch bờm & cạy rửa móng ca chiều (Grooming & Hoof Care)
- **Mã đề xuất:** `ROUTINE_AFTERNOON_GROOMING`
- **Tiêu đề:** `Chải lông, làm sạch bờm & cạy rửa móng ca chiều`
- **Mô tả:** `Dùng bàn chải chuyên dụng chải sạch bụi bẩn trên thân ngựa, gỡ rối bờm đuôi, dùng móc móng cạy sạch đất đá và kiểm tra tình trạng móng chân toàn đàn.`
- **Loại nhiệm vụ:** `GROOMING`
- **Giờ thực hiện:** `15:00:00`
- **Phạm vi (Scope):** `FACILITY`
- **Thứ tự (Sort order):** 70
- **Trạng thái khởi tạo:** Dự phòng (`is_active = 0`)

### 3. Ngâm chân nước đá / chườm lạnh chống viêm gân (Tendon Ice Boots)
- **Mã đề xuất:** `ROUTINE_TENDON_COOLING_ICEBOOTS`
- **Tiêu đề:** `Ngâm chân nước đá / chườm lạnh chống viêm gân`
- **Mô tả:** `Chuẩn bị ủng đá lạnh (Ice boots) hoặc xịt vòi nước lạnh áp lực nhẹ vào 4 cổ chân và gân gót của ngựa vừa kết thúc vận động để hạ nhiệt và giảm nguy cơ sưng viêm.`
- **Loại nhiệm vụ:** `ICE_BOOTS`
- **Giờ thực hiện:** `10:30:00`
- **Phạm vi (Scope):** `FACILITY`
- **Thứ tự (Sort order):** 80
- **Trạng thái khởi tạo:** Dự phòng (`is_active = 0`)

### 4. Dắt ngựa đi dạo hạ nhiệt & thư giãn cơ bắp (Cool-down Walking)
- **Mã đề xuất:** `ROUTINE_COOLDOWN_WALKING`
- **Tiêu đề:** `Dắt ngựa đi dạo hạ nhiệt & thư giãn cơ bắp`
- **Mô tả:** `Dắt ngựa đi dạo quanh khuôn viên bãi cỏ nhẹ nhàng trong 15-20 phút giúp ổn định nhịp tim, nhịp thở và thả lỏng hệ cơ bắp sau khi tập luyện hoặc lưu chuồng kéo dài.`
- **Loại nhiệm vụ:** `WALKING`
- **Giờ thực hiện:** `16:00:00`
- **Phạm vi (Scope):** `FACILITY`
- **Thứ tự (Sort order):** 90
- **Trạng thái khởi tạo:** Dự phòng (`is_active = 0`)

---

## 6. Phân tích Sở hữu Câu lạc bộ & Chuồng trại (Club / Stable Ownership Findings)

1. **Phạm vi Tổ chức (Organization Scope):**
   - Hệ thống được thiết kế phục vụ một Câu lạc bộ đua ngựa duy nhất ("Racehorse Club"). Không tồn tại bảng `clubs` đa người thuê (multi-tenant).
   - Bảng `dbo.stables` quản lý các khu chuồng vật lý thuộc câu lạc bộ (ví dụ: Khu chuồng A, Khu chuồng B, Khu chuồng C).
2. **Quan hệ giữa Club và Stables:**
   - Một Câu lạc bộ quản lý nhiều khu chuồng (`stables`). Quản lý Câu lạc bộ (`CLUB_MANAGER`) có toàn quyền tạo, sửa, đổi trạng thái các khu chuồng.
3. **Phạm vi Áp dụng của Danh mục Nhiệm vụ (Task Catalog Scope):**
   - Trong bảng `dbo.care_routine_definitions`, trường `scope` hiện mang giá trị `'FACILITY'` hoặc `'HORSE'`.
   - Bảng **không có** cột `stable_id`. Điều này có nghĩa là Danh mục nhiệm vụ chăm sóc thường nhật hiện tại áp dụng ở **cấp độ Toàn cơ sở / Toàn câu lạc bộ (Club-wide / Facility-level)** cho toàn bộ ca trực chăm sóc, không bị chia cắt theo từng khu chuồng đơn lẻ.

---

## 7. Phân tích Tính Toàn vẹn Dữ liệu Lịch sử (Historical Task Integrity Analysis)

| Câu hỏi Phân tích | Thực tế Triển khai (Verified Fact) | Hệ quả & Biện pháp Đảm bảo |
| :--- | :--- | :--- |
| **Bản ghi nhiệm vụ lưu snapshot hay tham chiếu động?** | `dbo.care_tasks` lưu trữ **bản sao snapshot vật lý** gồm `title`, `description`, `task_type`, `scheduled_time`, đồng thời lưu `routine_definition_id` làm khóa ngoại liên kết. | Khi định nghĩa mẫu trong `care_routine_definitions` bị chỉnh sửa, các nhiệm vụ lịch sử trong quá khứ **hoàn toàn không bị biến đổi**, đảm bảo tính toàn vẹn báo cáo kiểm toán. |
| **Điều gì xảy ra khi xóa một định nghĩa nhiệm vụ?** | Bảng `dbo.care_tasks` có ràng buộc khóa ngoại: `CONSTRAINT fk_care_tasks_routine FOREIGN KEY (routine_definition_id) REFERENCES dbo.care_routine_definitions(id)` **không có CASCADE**. | **Hard Delete là NGUY HIỂM và BỊ CẤM:** Nếu xóa cứng một định nghĩa đã từng sinh ra nhiệm vụ trong `care_tasks`, SQL Server sẽ chặn lại với lỗi vi phạm ràng buộc khóa ngoại (FK violation). |
| **Chiến lược xóa an toàn là gì?** | Bảng `care_routine_definitions` đã có sẵn cột `is_active BIT NOT NULL`. | **Chiến lược xóa an toàn tuyệt đối là Soft Delete / Archive:** Đổi `is_active = 0` (chuyển sang Thư viện dự phòng hoặc Lưu trữ). Định nghĩa không bị xóa khỏi CSDL nên khóa ngoại lịch sử được giữ nguyên. |
| **Chuyển đổi giữa Active và Reserve có làm trùng lặp nhiệm vụ không?** | CSDL có chỉ mục duy nhất lọc: `uq_care_tasks_facility_routine_instance ON dbo.care_tasks (routine_definition_id, scheduled_date) WHERE routine_definition_id IS NOT NULL AND horse_id IS NULL`. | Kể cả khi Club Manager bật/tắt một nhiệm vụ nhiều lần trong ngày, chỉ mục `UNIQUE` sẽ bảo vệ hệ thống không bao giờ sinh ra 2 bản ghi nhiệm vụ cho cùng một định nghĩa trong cùng một ngày. |
| **API hiện tại đã đủ để cung cấp danh sách định nghĩa chưa?** | Hiện chỉ có API `/api/care-tasks` để lấy các phiên bản thực thi trong ngày. Chưa có API nào trả về danh sách template `care_routine_definitions`. | Cần phát triển thêm API riêng cho danh mục template (ví dụ: `GET /api/care-routine-definitions`). |

---

## 8. Ma trận Phân quyền & Ranh giới Thẩm quyền (Authorization Boundaries)

Hệ thống duy trì nghiêm ngặt 5 vai trò hiện hữu, không sinh thêm role mới:

| Vai trò (Role) | Thẩm quyền Hiện hữu | Quyền Quản lý Danh mục Mẫu (`care_routine_definitions`) | Quyền Xem Danh mục Mẫu Ban hành | Quyền Thực thi Nhiệm vụ Hàng ngày (`care_tasks`) |
| :--- | :--- | :---: | :---: | :---: |
| **CLUB_MANAGER** | `ROLE_CLUB_MANAGER`, `PERM_STABLE_CARE_EDIT`, `PERM_STABLE_CARE_VIEW`, `PERM_SYSTEM_EDIT` | **Toàn quyền (CRUD, Active/Reserve, Reorder)** | **Cho phép** | **Cho phép (thực hiện thay khi cần)** |
| **STABLE_STAFF** | `ROLE_STABLE_STAFF`, `PERM_STABLE_CARE_VIEW`, `PERM_STABLE_CARE_EDIT` | **Không có quyền (Chỉ đọc)** | **Cho phép (Chỉ đọc bản Active)** | **Toàn quyền thực thi, nhận việc & hoàn thành** |
| **HEAD_TRAINER** | `ROLE_HEAD_TRAINER`, `PERM_STABLE_CARE_EDIT` | Không có quyền | Không cấp phép | Chỉ tạo nhiệm vụ đặc biệt / Hủy |
| **VETERINARIAN** | `ROLE_VETERINARIAN`, `PERM_MEDICAL_*` | Không có quyền | Không cấp phép | Không có quyền |
| **HORSE_OWNER** | `ROLE_HORSE_OWNER` | Không có quyền | Không cấp phép | Chỉ xem nhiệm vụ của ngựa mình sở hữu |

### Ranh giới Thẩm quyền Đề xuất:
- **Quản lý danh mục (Mutations - Add/Edit/Toggle/Reorder):**
  `@PreAuthorize("hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")`
- **Đọc danh mục ban hành (Read Active Definitions):**
  `@PreAuthorize("hasAnyAuthority('PERM_STABLE_CARE_VIEW')")`

---

## 9. Đánh giá Tác động Cơ sở Dữ liệu & Di chuyển (Database & Migration Impact)

- **Thay đổi Schema CSDL:** **HOÀN TOÀN KHÔNG CẦN THIẾT (ZERO SCHEMA MIGRATIONS).**
  - Bảng `dbo.care_routine_definitions` đã sở hữu đầy đủ tất cả các trường phục vụ nghiệp vụ yêu cầu:
    - `id` (UUID): Khóa chính.
    - `code` (VARCHAR): Mã định danh duy nhất.
    - `title` (NVARCHAR): Tiêu đề công việc.
    - `description` (NVARCHAR): Hướng dẫn thực hiện chi tiết.
    - `task_type` (VARCHAR): Phân loại nghiệp vụ (`MUCKING_OUT`, `FEEDING`, `BATHING`, `ICE_BOOTS`, `GROOMING`, `WALKING`, `OTHER`).
    - `scheduled_time` (TIME): Giờ lên lịch thực hiện trong ca.
    - `scope` (VARCHAR): Mặc định `'FACILITY'`.
    - `is_active` (BIT): Đại diện trực tiếp cho **Active List** (`is_active = 1`) và **Reserve Library** (`is_active = 0`).
    - `sort_order` (INT): Đại diện trực tiếp cho tính năng **Sắp xếp thứ tự ưu tiên (Reorder active tasks)**.
    - `created_at`, `updated_at`: Theo dõi thời gian cập nhật.
- **Dữ liệu hạt nhân (Data Seeding):**
  - 4 nhiệm vụ dự phòng mới có thể được bổ sung qua một script `INSERT ... WHERE NOT EXISTS` đơn giản với `is_active = 0` khi triển khai chính thức.

---

## 10. Kế hoạch Triển khai Khuyến nghị (Recommended Phased Implementation Plan)

### Giai đoạn 1: Backend Domain Model & APIs
- **Nội dung:**
  - Tạo package `com.racehorse.caretask.definition` (hoặc mở rộng `com.racehorse.caretask`):
    - DTO: `CareRoutineDefinitionResponse`, `CreateCareRoutineRequest`, `UpdateCareRoutineRequest`, `ReorderCareRoutinesRequest`.
    - Service: `CareRoutineDefinitionService` (truy vấn danh sách theo `isActive`, thêm mới, cập nhật, chuyển đổi Active/Reserve, sắp xếp lại `sort_order`).
    - Controller: `CareRoutineDefinitionController` với các endpoint:
      - `GET /api/care-routine-definitions` (Hỗ trợ lọc `?activeOnly=true|false`).
      - `POST /api/care-routine-definitions` (Tạo định nghĩa mới).
      - `PUT /api/care-routine-definitions/{id}` (Chỉnh sửa nội dung, giờ giấc, loại).
      - `PATCH /api/care-routine-definitions/{id}/toggle-status` (Chuyển đổi Active ↔ Reserve).
      - `PUT /api/care-routine-definitions/reorder` (Cập nhật `sort_order` hàng loạt).
      - `DELETE /api/care-routine-definitions/{id}` (Soft delete: đặt `is_active = 0`).
- **Tác động:** Không thay đổi CSDL; cần restart backend khi triển khai.

### Giai đoạn 2: Nạp 4 Nhiệm vụ Dự phòng vào Thư viện
- **Nội dung:**
  - Bổ sung 4 bản ghi dự phòng vào `09_seed_reference_data.sql` (với `is_active = 0`).
- **Tác động:** Không thay đổi cấu trúc bảng; chỉ nạp dữ liệu tham chiếu (Data Migration).

### Giai đoạn 3: Giao diện Quản lý Danh mục của Club Manager
- **Nội dung:**
  - Thêm trang `AdminPages.jsx` → `StableTaskCatalogPage` (tuyến đường `/admin/stable-tasks`).
  - Thiết kế 2 tab rõ ràng:
    - **Tab 1: Danh sách Đang ban hành (Active List):** Hiển thị các công việc đang tự động sinh hàng ngày cho Stable Staff, hỗ trợ đổi thứ tự ưu tiên (Reorder lên/xuống), chỉnh sửa, hoặc bấm "Chuyển sang Thư viện dự phòng".
    - **Tab 2: Thư viện Nhiệm vụ Dự phòng (Reserve Library):** Hiển thị các định nghĩa đang lưu kho, hỗ trợ bấm "Kích hoạt vào ca trực (Publish/Activate)", chỉnh sửa, hoặc tạo định nghĩa mới.
  - Tích hợp vào Menu điều hướng của Quản lý Câu lạc bộ trong `App.jsx`.
- **Tác động:** Chỉ mã nguồn Frontend.

### Giai đoạn 4: Tích hợp Giao diện Stable Staff & Hiển thị Thông báo Nguồn
- **Nội dung:**
  - Trong `CareTasksView` (`FRONTEND/src/StableCarePage.jsx`):
    - Bổ sung thanh thông báo trang nhã: *"Danh mục công việc thường nhật được ban hành và quản lý bởi Quản lý Câu lạc bộ."*
    - Hiển thị thông tin mô tả chi tiết của định nghĩa nhiệm vụ khi nhân viên cần tham khảo hướng dẫn thực hiện.
- **Tác động:** Chỉ mã nguồn Frontend.

### Giai đoạn 5: Kiểm thử Hồi quy & Tương thích Dữ liệu Lịch sử
- **Nội dung:**
  - Kiểm thử tự động (Unit & Integration tests) đảm bảo:
    1. Hoàn thành nhiệm vụ cũ không bị ảnh hưởng khi template thay đổi.
    2. Chuyển đổi qua lại giữa Active và Reserve không làm phát sinh bản ghi trùng lặp trong ngày (`uq_care_tasks_facility_routine_instance`).
    3. Phân quyền RBAC chặt chẽ: chỉ `CLUB_MANAGER` mới có quyền can thiệp vào thư viện nhiệm vụ.
- **Tác động:** Không rủi ro hồi quy dữ liệu.

---

## 11. Các Quyết định Sản phẩm Cần Lưu ý (Product Considerations)

1. **Phạm vi tác động của việc sửa đổi Giờ thực hiện (`scheduled_time`):**
   - *Phát hiện:* Việc sửa giờ trong định nghĩa chỉ tác động đến các ngày tương lai khi nhiệm vụ được sinh mới. Nếu sửa giờ vào giữa ngày hôm nay, các nhiệm vụ đã sinh lúc sáng sẽ giữ nguyên giờ cũ trừ khi người dùng chọn áp dụng ngay.
   - *Khuyến nghị:* Mặc định chỉ áp dụng cho các phiên bản sinh trong tương lai; có thể hỗ trợ tùy chọn *"Cập nhật cho các nhiệm vụ đang chờ thực hiện hôm nay"*.
2. **Quy tắc tạo mã định danh duy nhất (`code`):**
   - *Phát hiện:* Bảng `care_routine_definitions` yêu cầu `code VARCHAR(50) UNIQUE NOT NULL`.
   - *Khuyến nghị:* Giao diện người dùng cho phép Club Manager nhập tiêu đề tiếng Việt thân thiện, hệ thống tự động sinh `code` chuẩn hóa không dấu dạng `ROUTINE_<SLUG>_<RANDOM>` để đảm bảo tính duy nhất và không gây lỗi cú pháp SQL.
