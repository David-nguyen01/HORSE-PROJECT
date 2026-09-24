# BÁO CÁO HOÀN THIỆN BACKEND FLOW 4: STABLE CARE & NUTRITION (FR13 – FR17)

**Dự án:** Racehorse Training & Management System  
**Bước thực hiện:** STEP 4B — BACKEND IMPLEMENTATION FLOW 4  
**Phạm vi:** Chỉ Backend (`BACKEND/`). Tuyệt đối không can thiệp Frontend, không chạy Git mutation.  
**Ngày thực hiện:** 25/09/2026  
**Trạng thái:** **IMPLEMENTATION COMPLETE / AUTOMATED VERIFICATION PASS** (102/102 tests PASS)

---

## 1. TỔNG QUAN KẾT QUẢ TRIỂN KHAI

Toàn bộ **Flow 4 — Stable Care & Nutrition** theo đúng đặc tả SRS chính thức v1.0 đã được hiện thực hóa đầy đủ trên tầng Backend, đáp ứng chặt chẽ các yêu cầu nghiệp vụ, ma trận phân quyền RBAC và kiến trúc lưu trữ compact schema (`dbo.module_records` không tạo bảng mới, đảm bảo tổng số bảng $\le 30$).

| Functional Requirement | Tên Chức Năng | Trạng thái triển khai | Số Unit Test |
| :--- | :--- | :--- | :---: |
| **FR13** | Phân bổ chuồng & Lịch sinh hoạt hằng ngày (Stall Allocation & Daily Routine) | **HOÀN THÀNH 100%** | 5 |
| **FR14** | Khẩu phần dinh dưỡng & Kế hoạch ăn uống (Nutrition Plan & Daily Feeding Checklist) | **HOÀN THÀNH 100%** | 4 |
| **FR15** | Nhiệm vụ chăm sóc chuồng hằng ngày & Checklist (Daily Care Tasks & Overdue Tracking) | **HOÀN THÀNH 100%** | 4 |
| **FR16** | Báo cáo sự cố khẩn cấp & Tự động thông báo tức thời (Emergency Incident Report & Notification) | **HOÀN THÀNH 100%** | 4 |
| **FR17** | Quản lý tồn kho & Đề xuất bổ sung vật tư (Inventory Tracking & Replenishment Approval) | **HOÀN THÀNH 100%** | 5 |
| **Tổng cộng Flow 4** | | **5/5 FRs ĐẠT CHUẨN SRS** | **22 Tests mới** |

---

## 2. CHI TIẾT HIỆN THỰC THEO TỪNG FUNCTIONAL REQUIREMENT

### 2.1. FR13 — Phân bổ chuồng & Lịch sinh hoạt hằng ngày (Stall Allocation & Daily Routine)
* **Hiện trạng trước đó:** Chỉ có phân bổ chuồng cơ bản (assign horse vào stall). Thiếu hoàn toàn thông tin chi tiết ngựa (code, name, healthStatus) và toàn bộ tính năng **Lịch sinh hoạt phi tập luyện (Daily Non-Training Routine)** gồm: giờ ra sân cỏ (turnout), chải chuốt (grooming), nghỉ ngơi (rest).
* **Giải pháp & Code đã triển khai:**
  1. `StableService.java`: Bổ sung `horseCode`, `horseName`, `horseHealthStatus` vào view `AssignmentView`.
  2. Xây dựng CRUD cho Lịch sinh hoạt hàng ngày gắn với từng ngựa:
     - `getDailyRoutines(horseId)`: Sắp xếp theo trình tự thời gian trong ngày (`startTime asc`).
     - `createDailyRoutine(horseId, actor, request)`: Validate thời gian, lưu trữ vào `dbo.module_records` (`module_code='STABLE_CARE'`, `record_type='HORSE_DAILY_ROUTINE'`).
     - `updateDailyRoutine(routineId, request)`
     - `deleteDailyRoutine(routineId)`
* **File liên quan:**
  - `com.racehorse.stable.service.StableService`
  - `com.racehorse.stable.controller.StableController`
  - `com.racehorse.stable.service.StableServiceTest` (5 test cases PASS).

---

### 2.2. FR14 — Khẩu phần dinh dưỡng & Kế hoạch ăn uống (Nutrition Plan & Feeding Checklist)
* **Đặc tả SRS:**
  - Lập khẩu phần dinh dưỡng chi tiết theo từng bữa (sáng, trưa, tối, đêm), bao gồm: loại thức ăn (cỏ khô alfalfa, cám viên oats, chất bổ sung vitamin/khoáng, điện giải), khối lượng/định lượng (kg, g, lít), hướng dẫn pha trộn/lưu ý đặc biệt.
  - Hỗ trợ đánh số phiên bản kế hoạch (`version = 1, 2, ...`). Khi kế hoạch mới được phê duyệt, kế hoạch cũ chuyển trạng thái `SUPERSEDED`.
  - Cung cấp checklist cho nhân viên chuồng (groom) để tra cứu bữa ăn hàng ngày theo giờ.
* **Code đã triển khai:**
  - DTOs:
    - `MealItem(mealName, mealTime, feedType, quantity, unit, instructions)`
    - `CreateNutritionPlanRequest(horseId, dailyCaloriesTarget, notes, meals)`
    - `NutritionPlanResponse`
    - `DailyFeedingChecklistResponse(horseId, horseCode, horseName, targetDate, meals)`
  - Service & Controller:
    - `NutritionPlanService`: Quản lý versioning tự động, kích hoạt kế hoạch mới nhất, tra cứu lịch sử dinh dưỡng, tổng hợp checklist bữa ăn hôm nay.
    - `NutritionPlanController`: Các REST APIs được bảo vệ bởi RBAC.
* **File liên quan:**
  - `com.racehorse.nutrition.dto.*`
  - `com.racehorse.nutrition.service.NutritionPlanService`
  - `com.racehorse.nutrition.controller.NutritionPlanController`
  - `com.racehorse.nutrition.service.NutritionPlanServiceTest` (4 test cases PASS).

---

### 2.3. FR15 — Nhiệm vụ chăm sóc chuồng hằng ngày & Checklist (Daily Care Tasks)
* **Đặc tả SRS:**
  - Phân công nhiệm vụ định kỳ cho nhân viên chuồng: dọn phân / thay rơm (`MUCKING_OUT`), cho ăn (`FEEDING`), tắm ngựa (`BATHING`), quấn băng / chườm lạnh chân (`ICE_BOOTS`), chải chuốt (`GROOMING`), dắt đi dạo nhẹ (`WALKING`).
  - Hệ thống tự động phát hiện trễ hạn (`isOverdue = true`) khi nhiệm vụ ở trạng thái `PENDING` và giờ hẹn đã qua so với giờ thực tế.
  - Hoàn thành nhiệm vụ (`completeTask`): Cho phép nhân viên chuồng đính kèm ghi chú thực tế và link hình ảnh (`photoUrl`) xác nhận kết quả.
* **Code đã triển khai:**
  - DTOs:
    - `CreateCareTaskRequest(horseId, taskType, title, scheduledDate, scheduledTime, assignedStaffId)`
    - `CompleteCareTaskRequest(notes, photoUrl)`
    - `CareTaskResponse(id, horseId, horseCode, horseName, taskType, title, scheduledDate, scheduledTime, isOverdue, ...)`
  - Service & Controller:
    - `CareTaskService`: Quản lý vòng đời task (`PENDING` $\rightarrow$ `COMPLETED` / `CANCELLED`), chống hoàn thành trùng lặp, lọc theo ngày/nhân viên/ngựa/quá hạn.
    - `CareTaskController`: Phân quyền RBAC chuẩn xác (`PERM_STABLE_CARE_VIEW`, `PERM_STABLE_CARE_EDIT`).
* **File liên quan:**
  - `com.racehorse.caretask.dto.*`
  - `com.racehorse.caretask.service.CareTaskService`
  - `com.racehorse.caretask.controller.CareTaskController`
  - `com.racehorse.caretask.service.CareTaskServiceTest` (4 test cases PASS).

---

### 2.4. FR16 — Báo cáo sự cố khẩn cấp (Emergency Incident Report)
* **Đặc tả SRS:**
  - Cơ chế báo cáo sự cố 1 chạm (1-touch incident report) cho nhân viên chuồng khi phát hiện ngựa có dấu hiệu bất thường: bỏ ăn (`OFF_FEED`), đau bụng cấp (`COLIC`), sốt cao (`FEVER`), chấn thương móng (`HOOF_INJURY`), bất an/kích động (`RESTLESSNESS`), khác (`OTHER`).
  - Mức độ nghiêm trọng: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
  - Tự động phát thông báo khẩn cấp: Ngay khi sự cố được tạo, hệ thống tự động tìm kiếm tất cả người dùng có vai trò Bác sĩ thú y (`VETERINARIAN`) và Huấn luyện viên trưởng (`HEAD_TRAINER`) để gửi Notification với mức ưu tiên `HIGH` qua `NotificationService`.
* **Code đã triển khai:**
  - DTOs:
    - `CreateIncidentReportRequest(horseId, incidentType, severity, description, photoUrl)`
    - `UpdateIncidentStatusRequest(status, resolutionNotes)`
    - `IncidentReportResponse`
  - Service & Controller:
    - `IncidentReportService`: Kiểm tra tính hợp lệ của ngựa, lưu báo cáo vào `module_records`, kích hoạt quy trình dispatch thông báo tức thời. Xử lý cập nhật trạng thái (`UNDER_REVIEW`, `RESOLVED`).
    - `IncidentReportController`: Các REST APIs được bảo vệ theo chuẩn an ninh.
* **File liên quan:**
  - `com.racehorse.incident.dto.*`
  - `com.racehorse.incident.service.IncidentReportService`
  - `com.racehorse.incident.controller.IncidentReportController`
  - `com.racehorse.incident.service.IncidentReportServiceTest` (4 test cases PASS).

---

### 2.5. FR17 — Theo dõi tồn kho & Đề xuất bổ sung (Inventory & Replenishment)
* **Đặc tả SRS:**
  - Quản lý định mức vật tư chuồng nuôi: Thức ăn (`FEED`), Thuốc thú y (`MEDICATION`), Thực phẩm chức năng (`SUPPLEMENT`), Dụng cụ (`EQUIPMENT`).
  - Tự động cảnh báo tồn kho thấp (`isLowStock = true` khi `quantityOnHand <= minThreshold`).
  - Quy trình Đề xuất bổ sung (Replenishment Proposal):
    - Nhân viên chuồng gửi đề xuất (`PENDING_APPROVAL`).
    - Quản lý CLB (`CLUB_MANAGER` với quyền `PERM_STABLE_CARE_APPROVE`) phê duyệt (`APPROVED`) hoặc từ chối (`REJECTED`).
    - **Tự động cộng tồn kho:** Khi đề xuất được phê duyệt (`APPROVED`), hệ thống tự động tăng `quantityOnHand += requestedQuantity` vào mặt hàng tương ứng.
* **Code đã triển khai:**
  - DTOs:
    - `CreateInventoryItemRequest`, `UpdateStockRequest`, `InventoryItemResponse`
    - `CreateReplenishmentRequest`, `ReviewReplenishmentRequest`, `ReplenishmentResponse`
  - Service & Controller:
    - `InventoryService`: Quản lý kho hàng và quy trình phê duyệt đề xuất bổ sung tự động cập nhật số dư tồn kho.
    - `InventoryController`: Các REST APIs với đầy đủ ma trận phân quyền.
* **File liên quan:**
  - `com.racehorse.inventory.dto.*`
  - `com.racehorse.inventory.service.InventoryService`
  - `com.racehorse.inventory.controller.InventoryController`
  - `com.racehorse.inventory.service.InventoryServiceTest` (5 test cases PASS).

---

## 3. DANH MỤC API ENDPOINTS TOÀN DIỆN (FLOW 4)

| Endpoint | HTTP | Phân quyền RBAC | Mô tả chức năng |
| :--- | :---: | :--- | :--- |
| **FR13 — STABLE & ROUTINE** | | | |
| `/api/stables/horses/{horseId}/daily-routines` | `GET` | `PERM_STABLE_CARE_VIEW` | Lấy lịch sinh hoạt phi tập luyện theo thứ tự thời gian |
| `/api/stables/horses/{horseId}/daily-routines` | `POST` | `HEAD_TRAINER, CLUB_MANAGER` + `PERM_STABLE_CARE_EDIT` | Thêm hoạt động sinh hoạt mới (turnout, grooming, rest...) |
| `/api/stables/daily-routines/{routineId}` | `PUT` | `HEAD_TRAINER, CLUB_MANAGER` + `PERM_STABLE_CARE_EDIT` | Cập nhật hoạt động sinh hoạt |
| `/api/stables/daily-routines/{routineId}` | `DELETE` | `HEAD_TRAINER, CLUB_MANAGER` + `PERM_STABLE_CARE_EDIT` | Xóa hoạt động sinh hoạt |
| **FR14 — NUTRITION PLAN** | | | |
| `/api/horses/{horseId}/nutrition-plan` | `GET` | `PERM_STABLE_CARE_VIEW` | Xem khẩu phần dinh dưỡng hiện hành (phiên bản mới nhất) |
| `/api/horses/{horseId}/nutrition-plan/history` | `GET` | `PERM_STABLE_CARE_VIEW` | Xem lịch sử các phiên bản khẩu phần dinh dưỡng |
| `/api/horses/{horseId}/nutrition-plan/daily-checklist` | `GET` | `PERM_STABLE_CARE_VIEW` | Lấy checklist bữa ăn hôm nay cho nhân viên chuồng |
| `/api/horses/{horseId}/nutrition-plan` | `POST` | `HEAD_TRAINER, VETERINARIAN, CLUB_MANAGER` + `PERM_STABLE_CARE_EDIT` | Lập khẩu phần mới (tự động supersede phiên bản cũ) |
| **FR15 — DAILY CARE TASKS** | | | |
| `/api/care-tasks` | `GET` | `PERM_STABLE_CARE_VIEW` | Danh sách nhiệm vụ chăm sóc (lọc date, horse, staff, overdue) |
| `/api/care-tasks/{id}` | `GET` | `PERM_STABLE_CARE_VIEW` | Xem chi tiết một nhiệm vụ chăm sóc |
| `/api/care-tasks` | `POST` | `HEAD_TRAINER, CLUB_MANAGER` + `PERM_STABLE_CARE_EDIT` | Phân công nhiệm vụ mới cho nhân viên chuồng |
| `/api/care-tasks/{id}/complete` | `POST` | `STABLE_STAFF, HEAD_TRAINER, CLUB_MANAGER` + `PERM_STABLE_CARE_EDIT` | Nhân viên chuồng xác nhận hoàn thành (kèm ảnh & ghi chú) |
| `/api/care-tasks/{id}/cancel` | `POST` | `HEAD_TRAINER, CLUB_MANAGER` + `PERM_STABLE_CARE_EDIT` | Hủy nhiệm vụ chăm sóc |
| **FR16 — EMERGENCY INCIDENTS** | | | |
| `/api/incidents` | `POST` | `PERM_STABLE_CARE_EDIT` (Grooms, Trainers, Vets) | Báo cáo sự cố khẩn cấp (1-touch, tự động gửi notification) |
| `/api/incidents` | `GET` | `PERM_STABLE_CARE_VIEW` | Tra cứu danh sách sự cố (lọc horseId, status, severity, date) |
| `/api/incidents/{id}` | `GET` | `PERM_STABLE_CARE_VIEW` | Xem chi tiết sự cố |
| `/api/incidents/{id}/status` | `PUT` | `PERM_STABLE_CARE_EDIT` | Cập nhật tiến độ xử lý sự cố (`UNDER_REVIEW`, `RESOLVED`) |
| **FR17 — INVENTORY & REPLENISHMENT** | | | |
| `/api/inventory/items` | `GET` | `PERM_STABLE_CARE_VIEW` | Danh mục tồn kho (hỗ trợ lọc tồn thấp `lowStockOnly=true`) |
| `/api/inventory/items/{id}` | `GET` | `PERM_STABLE_CARE_VIEW` | Xem thông tin một mặt hàng trong kho |
| `/api/inventory/items` | `POST` | `CLUB_MANAGER, HEAD_TRAINER` + `PERM_STABLE_CARE_EDIT` | Khởi tạo/đăng ký mặt hàng mới vào kho |
| `/api/inventory/items/{id}/stock`| `PUT` | `PERM_STABLE_CARE_EDIT` | Điều chỉnh số lượng tồn kho thực tế (kiểm kê, nhập hàng) |
| `/api/inventory/replenishments` | `GET` | `PERM_STABLE_CARE_VIEW` | Tra cứu danh sách đề xuất bổ sung vật tư |
| `/api/inventory/replenishments/{id}` | `GET` | `PERM_STABLE_CARE_VIEW` | Chi tiết một đề xuất bổ sung |
| `/api/inventory/replenishments` | `POST` | `PERM_STABLE_CARE_EDIT` (Groom, Staff) | Gửi đề xuất nhập thêm vật tư |
| `/api/inventory/replenishments/{id}/review` | `POST` | `CLUB_MANAGER` + `PERM_STABLE_CARE_APPROVE` | Quản lý CLB phê duyệt/từ chối (tự động cộng tồn kho khi duyệt) |

---

## 4. BẢO TOÀN KIẾN TRÚC & QUY TẮC DỰ ÁN

1. **Tuân thủ giới hạn $\le 30$ bảng (Zero Schema Alteration):**
   - Hoàn toàn **không tạo thêm bất kỳ bảng nào** trên Azure SQL Database.
   - Toàn bộ dữ liệu của Flow 4 được lưu trữ linh hoạt và an toàn trên bảng `dbo.module_records` bằng cách tận dụng trường `data_json` và các trường định danh có sẵn:
     - `module_code = 'STABLE_CARE'`: dành cho FR13 (Routine), FR14 (Nutrition), FR15 (Care Tasks), FR16 (Incidents).
     - `module_code = 'INVENTORY'`: dành cho FR17 (Inventory Items, Replenishment Requests).
     - Cả hai `module_code` này đều nằm trong tập giá trị hợp lệ của check constraint `ck_module_records_module` trên Azure SQL.
2. **Tuân thủ phân định Frontend - Backend:**
   - Hoàn toàn **không sửa bất kỳ file nào trong thư mục `FRONTEND/`**.
   - Frontend team có thể tích hợp trực tiếp dựa trên contract API và DTO rõ ràng đã xây dựng.
3. **Tuân thủ quy tắc Git:**
   - Không thực hiện bất kỳ lệnh `git commit`, `git push`, hay `git checkout`. Toàn bộ working tree được bảo toàn an toàn cho người dùng tự quản lý.

---

## 5. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG (AUTOMATED TEST VERIFICATION)

Sau khi hoàn thành hiện thực toàn bộ các service và controller của Flow 4, test suite của toàn bộ backend đã được thực thi thông qua lệnh `mvn test`:

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.racehorse.caretask.service.CareTaskServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.incident.service.IncidentReportServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.inventory.service.InventoryServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.nutrition.service.NutritionPlanServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.racehorse.stable.service.StableServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
... (toàn bộ 24 test classes khác của Flow 1, 2, 3 và Auth)
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.470 s
```

- **Tổng số tests chạy:** **102 tests**
- **Thất bại (Failures):** **0**
- **Lỗi (Errors):** **0**
- **Bỏ qua (Skipped):** **0**

---

## 6. KẾT LUẬN & BÀN GIAO

Backend của **Flow 4 — Stable Care & Nutrition (FR13 – FR17)** đã hoàn chỉnh 100%, sẵn sàng cho Frontend kết nối và chuyển sang giai đoạn kế tiếp (Flow 5 — Racing & Reporting).
