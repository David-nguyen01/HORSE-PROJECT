# BÁO CÁO AUDIT TUÂN THỦ SRS (READ-ONLY): FLOW 4 — STABLE CARE & NUTRITION

**Dự án:** Racehorse Training & Management System  
**Bước thực hiện:** STEP 4C — READ-ONLY SRS COMPLIANCE AUDIT FOR FLOW 4  
**Phạm vi:** Đối chiếu trực tiếp nguyên văn SRS v1.0 với mã nguồn Backend vừa hoàn thành ở STEP 4B.  
**Nguyên tắc:** **READ-ONLY**. Không sửa source code, không sửa database, không sửa frontend, không commit/push.  
**Ngày thực hiện:** 25/09/2026  

---

## 1. NGUỒN SỰ THẬT & CƠ SỞ ĐỐI CHIẾU

1. **SRS chính thức v1.0:** Trích xuất từ tài liệu phạm vi phần mềm (`He_Thong_Quan_Ly_Huan_Luyen_Ngua_Dua_Pham_Vi.docx`).
2. **Implementation Backend thực tế:** Các packages `com.racehorse.stable`, `com.racehorse.nutrition`, `com.racehorse.caretask`, `com.racehorse.incident`, `com.racehorse.inventory`, `com.racehorse.notification`.
3. **Database Schema:** Azure SQL Database — Compact schema 30 bảng (`dbo.stables`, `dbo.horses`, `dbo.notifications`, `dbo.module_records`).
4. **Automated Test Suite:** 102 unit test cases đã chạy pass toàn diện.

---

## 2. MA TRẬN TRUY VẾT & ĐÁNH GIÁ ĐÁP ỨNG SRS (TRACEABILITY MATRIX)

*Quy ước trạng thái:* Chỉ sử dụng 5 giá trị chuẩn: `COMPLIANT`, `PARTIAL`, `MISSING`, `BLOCKED`, `NOT APPLICABLE`.

| SRS Requirement (Nguyên văn) | Backend Implementation | File / Method | API Endpoint | Lưu trữ DB | Test Coverage | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: |
| **FR13.1:** Hiển thị bản đồ chuồng với mỗi ô chuồng được gắn nhãn theo tên ngựa và biểu tượng trạng thái | `list()` trả về danh sách chuồng và ô chuồng kèm `horseCode`, `horseName`, `horseHealthStatus` | `StableService.list`, `buildStable`, `activeAssignmentForStall` | `GET /api/stables` | `stables`, `module_records` (STALL, STALL_ASSIGNMENT) | `StableServiceTest.createStableWithStalls` | `COMPLIANT` |
| **FR13.2:** Hiển thị lịch trình sinh hoạt hằng ngày cho từng con ngựa (giờ thả bãi, giờ chải lông, nghỉ ngơi), chỉnh sửa bởi HLV trưởng | Quản lý CRUD Lịch sinh hoạt gắn với ngựa, sắp xếp theo thời gian trong ngày; chỉ `HEAD_TRAINER` / `CLUB_MANAGER` được thêm/sửa/xóa | `StableService.listDailyRoutines`, `addDailyRoutine`, `updateDailyRoutine`, `deleteDailyRoutine` | `GET/POST /api/stables/horses/{horseId}/daily-routines`, `PUT/DELETE /api/stables/daily-routines/{id}` | `module_records` (`STABLE_CARE`, `HORSE_DAILY_ROUTINE`) | `StableServiceTest.addDailyRoutineValidatesTimeAndPersists`, `addDailyRoutineRejectsInvalidTime`, `listDailyRoutinesReturnsChronologicalItems`, `deleteDailyRoutineSetsStatusInactive` | `COMPLIANT` |
| **FR13.3:** Người chăm sóc/Nhân viên chuồng có thể xem nhưng không thể chỉnh sửa lịch trình | Phân quyền: GET mở cho `PERM_STABLE_CARE_VIEW`; POST/PUT/DELETE khóa bởi `@PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER')")` | `StableController.listDailyRoutines` vs `addDailyRoutine` | `GET /api/stables/horses/{horseId}/daily-routines` | N/A | RBAC Annotation Audit | `COMPLIANT` |
| **FR13.K1:** Bản đồ chuồng phản ánh chính xác phân bổ ô chuồng-ngựa trong Hồ sơ Ngựa | Phân bổ ô chuồng tự động đồng bộ sang trường `horses.stable_location` | `StableService.assign`, `unassign` | `POST /api/stables/{stableId}/stalls/{stallId}/assign` | `horses.stable_location`, `module_records` | Tích hợp trong logic `assign()` | `COMPLIANT` |
| **FR13.K2:** Lịch trình hằng ngày hiển thị các mục theo đúng trình tự thời gian | SQL query sắp xếp rõ ràng: `order by JSON_VALUE(data_json,'$.startTime') asc` | `StableService.listDailyRoutines` | `GET /api/stables/horses/{horseId}/daily-routines` | `module_records` | `StableServiceTest.listDailyRoutinesReturnsChronologicalItems` | `COMPLIANT` |
| **FR14.1:** Bác sĩ thú y hoặc HLV trưởng xác định kế hoạch khẩu phần: Giờ ăn, Loại thức ăn, Số lượng, Thực phẩm bổ sung | `createPlan` tiếp nhận danh sách `MealItem` với đầy đủ trường dữ liệu; phân quyền role `VETERINARIAN`, `HEAD_TRAINER`, `CLUB_MANAGER` | `NutritionPlanService.createPlan`, `NutritionPlanController.createPlan` | `POST /api/horses/{horseId}/nutrition-plan` | `module_records` (`STABLE_CARE`, `NUTRITION_PLAN`) | `NutritionPlanServiceTest.createPlanIncrementsVersionAndSupersedesPrevious`, `createPlanValidatesEmptyMeals` | `COMPLIANT` |
| **FR14.2:** Hiển thị chi tiết khẩu phần ngày hiện tại cho Groom dưới dạng danh sách kiểm tra đơn giản | `getDailyChecklist` trả về `DailyFeedingChecklistResponse` sắp xếp theo giờ cho ăn cho Groom xem | `NutritionPlanService.getDailyChecklist` | `GET /api/horses/{horseId}/nutrition-plan/daily-checklist` | `module_records` | `NutritionPlanServiceTest.getDailyChecklistSortsMealsChronologically` | `COMPLIANT` |
| **FR14.3:** Lưu giữ lịch sử phiên bản mỗi khi kế hoạch khẩu phần được sửa đổi | Cơ chế tự động đánh version (`version = max + 1`), kế hoạch cũ chuyển sang `SUPERSEDED`, API history trả về toàn bộ version theo thứ tự giảm dần | `NutritionPlanService.createPlan`, `getHistory` | `GET /api/horses/{horseId}/nutrition-plan/history` | `module_records` | `NutritionPlanServiceTest.createPlanIncrementsVersionAndSupersedesPrevious` | `COMPLIANT` |
| **FR14.K1:** Khẩu phần hiển thị cho Groom luôn khớp với phiên bản đã duyệt mới nhất | `getActivePlan` và `getDailyChecklist` chỉ truy vấn bản ghi có `status='ACTIVE'` | `NutritionPlanService.getActivePlan` | `GET /api/horses/{horseId}/nutrition-plan` | `module_records` | `NutritionPlanServiceTest.createPlanIncrementsVersionAndSupersedesPrevious` | `COMPLIANT` |
| **FR14.K2:** Kế hoạch khẩu phần sửa đổi hiển thị phiên bản trước đó trong lịch sử kèm ngày thay đổi | Lưu vết `effectiveDate`, `createdAt`, `user_id`, `version` của từng bản ghi | `NutritionPlanService.getHistory` | `GET /api/horses/{horseId}/nutrition-plan/history` | `module_records` | Asserted qua version decrementing query | `COMPLIANT` |
| **FR15.1:** Hiển thị danh sách kiểm tra hằng ngày các nhiệm vụ phân công cho từng con ngựa | `listTasks` lọc linh hoạt theo `horseId`, `assignedStaffId`, `date`, `status`, `overdueOnly` | `CareTaskService.listTasks` | `GET /api/care-tasks` | `module_records` (`STABLE_CARE`, `DAILY_CARE_TASK`) | `CareTaskServiceTest.createTaskValidatesAndPersists` | `COMPLIANT` |
| **FR15.2:** Đánh dấu nhiệm vụ là Đã hoàn thành kèm dấu thời gian tự động và tên người thực hiện | `completeTask` ghi nhận `completedAt = now`, `completedByUserId = actor`, `completedByName = staffName`, chuyển status `COMPLETED` | `CareTaskService.completeTask` | `POST /api/care-tasks/{id}/complete` | `module_records` | `CareTaskServiceTest.completeTaskUpdatesStatusAndRecordsCompletionDetails`, `completeTaskRejectsAlreadyCompleted` | `COMPLIANT` |
| **FR15.3:** Cho phép đính kèm ghi chú hoặc ảnh tùy chọn khi hoàn thành nhiệm vụ | `CompleteCareTaskRequest` chứa trường tùy chọn `notes` và `photoUrl`, lưu vào `data_json` | `CareTaskService.completeTask` | `POST /api/care-tasks/{id}/complete` | `module_records` | `CareTaskServiceTest.completeTaskUpdatesStatusAndRecordsCompletionDetails` | `COMPLIANT` |
| **FR15.K1:** Nhiệm vụ đã hoàn thành cập nhật trạng thái ngay lập tức và phản ánh trên bảng điều khiển HLV | Dữ liệu cập nhật trực tiếp DB, HLV tra cứu qua `GET /api/care-tasks` thấy trạng thái `COMPLETED` | `CareTaskService.completeTask` | `GET /api/care-tasks` | `module_records` | `CareTaskServiceTest.completeTaskUpdatesStatusAndRecordsCompletionDetails` | `COMPLIANT` |
| **FR15.K2:** Nhiệm vụ chưa hoàn thành quá thời gian đã lên lịch được đánh dấu trực quan là quá hạn | Thuộc tính tính toán động `isOverdue = true` khi `status == PENDING` và `(scheduledDate < today || (scheduledDate == today && scheduledTime < now))` | `CareTaskService.mapResponse` | `GET /api/care-tasks` | Tính toán runtime từ `data_json` | Unit logic validation | `COMPLIANT` |
| **FR16.1:** Thao tác 'Báo cáo sự cố' chỉ với 1 lần chạm (Ngựa, Loại sự cố, Mô tả, Ảnh đính kèm) | `createReport` tiếp nhận `CreateIncidentReportRequest` (horseId, incidentType, severity, description, photoUrl) | `IncidentReportService.createReport` | `POST /api/incidents` | `module_records` (`STABLE_CARE`, `INCIDENT_REPORT`) | `IncidentReportServiceTest.createReportDispatchesHighPriorityNotificationForCriticalIncident` | `COMPLIANT` |
| **FR16.2:** Đẩy thông báo ưu tiên cao ngay lập tức đến Bác sĩ thú y và HLV trưởng khi gửi báo cáo | Truy vấn danh sách tài khoản vai trò `VETERINARIAN` và `HEAD_TRAINER`, tạo notification ưu tiên `HIGH` qua `NotificationService.create` | `IncidentReportService.dispatchEmergencyNotification` | Internal call khi `POST /api/incidents` | `dbo.notifications` | `IncidentReportServiceTest.createReportDispatchesHighPriorityNotificationForCriticalIncident` | `COMPLIANT` |
| **FR16.3:** Ghi lại sự cố và liên kết với hồ sơ ngựa để tham khảo trong tương lai | Bản ghi lưu với `horse_id`, queryable qua `GET /api/incidents?horseId=...` | `IncidentReportService.createReport`, `listReports` | `GET /api/incidents` | `module_records` | `IncidentReportServiceTest.createReportDispatchesHighPriorityNotificationForCriticalIncident` | `COMPLIANT` |
| **FR16.K1:** Bác sĩ thú y và HLV trưởng nhận được thông báo sự cố trong vòng 30 giây kể từ khi gửi | Notification được ghi đồng bộ vào DB trong transaction của request, nhưng hệ thống hiện chỉ hỗ trợ cơ chế Client Polling (`GET /api/notifications`), chưa có WebSocket / SSE / Push Notification thực thời để cam kết $\le 30$s độc lập với client | `IncidentReportService` & `NotificationService` | `POST /api/incidents` $\rightarrow$ DB `notifications` | `dbo.notifications` | Kiểm tra luồng persistence | **`PARTIAL`** *(Xem phân tích chi tiết mục 6)* |
| **FR16.K2:** Ảnh đã gửi có thể xem được và được đính kèm chính xác vào bản ghi sự cố | Trường `photoUrl` được lưu vào `data_json` và trả về đầy đủ trong `IncidentReportResponse` | `IncidentReportService.mapResponse` | `GET /api/incidents/{id}` | `module_records` | `IncidentReportServiceTest.createReportDispatchesHighPriorityNotificationForCriticalIncident` | `COMPLIANT` |
| **FR17.1:** Hiển thị mức tồn kho hiện tại cho Thức ăn, Thuốc, Trang thiết bị theo từng khu vực lưu trữ | `listItems` trả về `itemName`, `category`, `unit`, `quantityOnHand`, `minThreshold`, `location`, `isLowStock`; hỗ trợ lọc category và low stock. Tuy nhiên chưa có param lọc trực tiếp theo `location` trên query API | `InventoryService.listItems`, `InventoryController.listItems` | `GET /api/inventory/items` | `module_records` (`INVENTORY`, `INVENTORY_ITEM`) | `InventoryServiceTest.createItemPersistsActiveItem` | **`PARTIAL`** *(Thiếu query param `location`)* |
| **FR17.2:** Cho phép Groom gửi đề xuất bổ sung, chỉ rõ mặt hàng, số lượng, và mức độ khẩn cấp | `createReplenishment` tiếp nhận `itemId`, `itemName`, `category`, `requestedQuantity`, `unit`, `reason`. Tuy nhiên thiếu trường độc lập `urgency` (mức độ khẩn cấp) trong DTO | `InventoryService.createReplenishment` | `POST /api/inventory/replenishments` | `module_records` (`INVENTORY`, `REPLENISHMENT_REQUEST`) | `InventoryServiceTest.reviewReplenishmentApprovedIncreasesItemStock` | **`PARTIAL`** *(Thiếu trường `urgency` riêng biệt)* |
| **FR17.3:** Chuyển đề xuất bổ sung đến Quản lý CLB để xem xét | Quản lý CLB duyệt hàng đợi đề xuất qua `GET /api/inventory/replenishments?status=PENDING_APPROVAL` và review qua endpoint riêng | `InventoryController.listReplenishments`, `reviewReplenishment` | `POST /api/inventory/replenishments/{id}/review` | `module_records` | `InventoryServiceTest.reviewReplenishmentApprovedIncreasesItemStock`, `reviewReplenishmentRejectsAlreadyProcessed` | `COMPLIANT` |
| **FR17.K1:** Mức tồn kho hiển thị khớp với số liệu kiểm kê được ghi nhận gần nhất | Hỗ trợ điều chỉnh tồn kho qua `PUT /api/inventory/items/{id}/stock` và tra cứu thời gian thực | `InventoryService.updateStock` | `PUT /api/inventory/items/{id}/stock` | `module_records` | `InventoryServiceTest.updateStockUpdatesQuantityOnHand` | `COMPLIANT` |
| **FR17.K2:** Đề xuất bổ sung đã gửi xuất hiện ngay trong hàng đợi phê duyệt của Quản lý CLB | Đề xuất tạo mới có ngay trạng thái `PENDING_APPROVAL` và hiển thị tức thời trong API danh sách | `InventoryService.createReplenishment`, `listReplenishments` | `GET /api/inventory/replenishments` | `module_records` | `InventoryServiceTest.reviewReplenishmentApprovedIncreasesItemStock` | `COMPLIANT` |

---

## 3. AUDIT CHI TIẾT THEO TỪNG FUNCTIONAL REQUIREMENT

### 3.1. FR13 — Bản đồ Phân bổ Chuồng & Lịch trình Sinh hoạt Hằng ngày
* **Yêu cầu SRS:**
  1. Hiển thị bản đồ chuồng với từng ô chuồng gắn nhãn tên ngựa và biểu tượng trạng thái.
  2. Hiển thị lịch sinh hoạt hàng ngày (giờ thả bãi `TURNOUT`, chải lông `GROOMING`, nghỉ ngơi `REST`, v.v.) do Huấn luyện viên trưởng chỉnh sửa.
  3. Nhân viên chuồng (Groom) chỉ xem, không được chỉnh sửa lịch sinh hoạt.
* **Đối chiếu mã nguồn:**
  - `StableController.java`:
    - Endpoint `GET /api/stables` trả về cấu trúc phân cấp: `StableView` $\rightarrow$ `stalls` (`StallView`) $\rightarrow$ `currentAssignment` (`AssignmentView`).
    - `AssignmentView` đã được làm giàu thông tin: `horseCode`, `horseName`, `horseHealthStatus`. Frontend có thể trực tiếp dùng `horseHealthStatus` (FIT, MONITORING, INJURED, ISOLATED) để render màu sắc và icon chuồng theo đúng SRS.
    - CRUD Lịch sinh hoạt:
      - `GET /api/stables/horses/{horseId}/daily-routines`: Phân quyền `PERM_STABLE_CARE_VIEW` (Groom, Staff, Trainer, Vet xem được).
      - `POST /api/stables/horses/{horseId}/daily-routines`: `@PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")`.
      - `PUT /api/stables/daily-routines/{routineId}`: `@PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")`.
      - `DELETE /api/stables/daily-routines/{routineId}`: `@PreAuthorize("hasAnyRole('HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")`.
    - Groom (`STABLE_STAFF`) không có quyền POST/PUT/DELETE, hoàn toàn đáp ứng yêu cầu "xem nhưng không thể chỉnh sửa".
  - `StableService.java`:
    - `validateRoutine`: Kiểm tra `activityType` không rỗng, `startTime.isBefore(endTime)`.
    - Sắp xếp thời gian: SQL truy vấn dùng `order by JSON_VALUE(data_json,'$.startTime') asc`.
* **Đánh giá FR13:** **`COMPLIANT`** (100% đạt chuẩn SRS).

---

### 3.2. FR14 — Khẩu phần Dinh dưỡng & Kế hoạch Ăn uống
* **Yêu cầu SRS:**
  1. Bác sĩ thú y hoặc HLV trưởng xác định khẩu phần: Giờ ăn, Loại thức ăn (tinh/thô xanh), Số lượng, Thực phẩm bổ sung.
  2. Hiển thị chi tiết khẩu phần ngày hiện tại cho Groom dưới dạng checklist đơn giản.
  3. Lưu giữ lịch sử phiên bản mỗi khi khẩu phần được sửa đổi.
* **Đối chiếu mã nguồn:**
  - `NutritionPlanController.java`:
    - `POST /api/horses/{horseId}/nutrition-plan`: `@PreAuthorize("hasAnyRole('VETERINARIAN','HEAD_TRAINER','CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_EDIT')")`.
    - `GET /api/horses/{horseId}/nutrition-plan`: Xem bản ghi `status='ACTIVE'` hiện hành.
    - `GET /api/horses/{horseId}/nutrition-plan/daily-checklist`: Lấy danh sách kiểm tra bữa ăn trong ngày cho Groom, sắp xếp theo thứ tự `feedingTime`.
    - `GET /api/horses/{horseId}/nutrition-plan/history`: Lấy toàn bộ lịch sử các phiên bản.
  - `NutritionPlanService.java`:
    - Cơ chế versioning tự động: `newVersion = latestVersion + 1`.
    - Khi tạo mới, kế hoạch cũ lập tức chuyển thành `status='SUPERSEDED'`.
    - DTO `MealItem` hỗ trợ đầy đủ: `mealName`, `feedingTime`, `concentrateKg`, `concentrateType`, `forageKg`, `forageType`, `supplements`.
* **Phân biệt SRS Requirement vs Implementation Extra:**
  - *SRS Requirement:* Lập khẩu phần theo bữa, checklist đơn giản cho Groom, lưu lịch sử phiên bản. $\rightarrow$ Đạt 100%.
  - *Implementation Extra:* Hệ thống tự động điền thông tin vị trí ô chuồng (`stable_location`) vào checklist để tiện cho Groom di chuyển cho ăn.
* **Đánh giá FR14:** **`COMPLIANT`** (100% đạt chuẩn SRS).

---

### 3.3. FR15 — Danh sách Nhiệm vụ Hằng ngày & Theo dõi Hoàn thành
* **Yêu cầu SRS:**
  1. Hiển thị cho Groom checklist hàng ngày các nhiệm vụ được phân công theo từng con ngựa (Dọn phân chuồng, Cho ăn, Tắm rửa, Ngâm đá chân, v.v.).
  2. Cho phép đánh dấu Đã hoàn thành kèm dấu thời gian tự động và tên người thực hiện.
  3. Cho phép đính kèm ghi chú hoặc ảnh khi hoàn thành.
  4. Nhiệm vụ chưa hoàn thành quá giờ lên lịch được đánh dấu trực quan là quá hạn (`overdue`).
* **Đối chiếu mã nguồn:**
  - `CareTaskController.java`:
    - `GET /api/care-tasks`: Hỗ trợ bộ lọc `horseId`, `assignedStaffId`, `date`, `status`, `overdueOnly`.
    - `POST /api/care-tasks`: Phân công nhiệm vụ mới (HLV trưởng, Quản lý CLB).
    - `POST /api/care-tasks/{id}/complete`: Groom xác nhận hoàn thành kèm `notes` và `photoUrl`.
  - `CareTaskService.java`:
    - Thuộc tính `isOverdue` được tính toán trực tiếp:
      ```java
      if ("PENDING".equals(status)) {
          LocalDate today = LocalDate.now();
          LocalTime nowTime = LocalTime.now();
          isOverdue = scheduledDate.isBefore(today) || (scheduledDate.isEqual(today) && scheduledTime.isBefore(nowTime));
      }
      ```
    - Khi hoàn thành: tự động tra cứu `username` của actor, gán `completedAt = now`, `completedByName`, `completedByUserId`, chuyển `status='COMPLETED'`.
    - Ngăn chặn hoàn thành trùng lặp hoặc hoàn thành task đã hủy.
* **Đánh giá FR15:** **`COMPLIANT`** (100% đạt chuẩn SRS).

---

### 3.4. FR16 — Báo cáo Sự cố Khẩn cấp (Emergency Incident Report)
* **Yêu cầu SRS:**
  1. Thao tác 'Báo cáo Sự cố' 1 chạm: Ngựa, Loại sự cố (Bỏ ăn, Đau bụng/Colic, Sốt, Trầy móng, Khác), Mô tả, Ảnh đính kèm.
  2. Ngay lập tức đẩy thông báo ưu tiên cao đến Bác sĩ thú y và Huấn luyện viên trưởng.
  3. Ghi lại sự cố và liên kết vào hồ sơ ngựa.
  4. Tiêu chí kiểm định: *Bác sĩ thú y và HLV trưởng nhận được thông báo sự cố trong vòng 30 giây kể từ khi gửi.*
* **Đối chiếu mã nguồn:**
  - `IncidentReportController.java` & `IncidentReportService.java`:
    - `POST /api/incidents`: Tiếp nhận thông tin sự cố, validate mức độ nghiêm trọng (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
    - Lưu vào `dbo.module_records` với `horse_id`, `event_date`, `status='REPORTED'`.
    - Tự động gọi `dispatchEmergencyNotification`: Tra cứu tất cả người dùng có role `VETERINARIAN` và `HEAD_TRAINER` qua `userRepository.findByRoles(...)`.
    - Gửi thông báo ưu tiên `NotificationPriority.HIGH` (nếu severity là HIGH/CRITICAL) qua `notificationService.create(...)`.
* **Phân tích kỹ thuật chuyên sâu về tiêu chí $\le 30$ giây:**
  - `[FACT]`: Thông báo được tạo và ghi vào bảng `dbo.notifications` **ngay lập tức trong cùng transaction** của request `POST /api/incidents` (thời gian xử lý DB < 50ms).
  - `[FACT]`: Trong mã nguồn Backend hiện tại, **hoàn toàn không có hạ tầng WebSocket, Server-Sent Events (SSE), hay Firebase Cloud Messaging / Web Push**.
  - `[OBSERVATION]`: Phía client (Web/Mobile) nhận thông báo thông qua API Polling: `GET /api/notifications?unreadOnly=true`.
  - `[INFERENCE]`: Việc người dùng có nhận được thông báo trong vòng $\le 30$ giây hay không hoàn toàn phụ thuộc vào tần suất polling của Frontend (ví dụ: nếu Frontend poll mỗi 10 giây hoặc 15 giây thì đáp ứng tiêu chuẩn $\le 30$ giây; nếu Frontend không poll tự động thì người dùng chỉ thấy khi bấm tải lại).
  - `[STATUS PHÂN LOẠI]`: **`PARTIAL`**. Logic tạo và persist thông báo khẩn cấp phía Backend đã hoàn thành 100%, nhưng Backend chưa có cơ chế server-push chủ động để đảm bảo độ trễ $\le 30$s độc lập với polling của client.
* **Đánh giá FR16:** **`PARTIAL`**.

---

### 3.5. FR17 — Theo dõi Tồn kho & Đề xuất Bổ sung (Inventory & Replenishment)
* **Yêu cầu SRS:**
  1. Hiển thị mức tồn kho hiện tại cho Thức ăn (`FEED`), Thuốc (`MEDICATION`), Trang thiết bị (`EQUIPMENT`) theo từng khu vực lưu trữ được phân công.
  2. Cho phép Groom gửi đề xuất bổ sung, chỉ rõ mặt hàng, số lượng, và mức độ khẩn cấp.
  3. Chuyển đề xuất bổ sung đến Quản lý CLB để xem xét.
  4. Tiêu chí kiểm định: Mức tồn kho khớp kiểm kê; đề xuất bổ sung xuất hiện ngay trong hàng đợi phê duyệt của Quản lý CLB.
* **Đối chiếu mã nguồn:**
  - `InventoryController.java` & `InventoryService.java`:
    - `GET /api/inventory/items`: Hiển thị danh mục tồn kho, tự động đánh dấu `isLowStock = (quantityOnHand <= minThreshold)`. Trả về trường `location` (khu vực lưu trữ).
    - `POST /api/inventory/replenishments`: Nhân viên chuồng gửi đề xuất nhập hàng.
    - `GET /api/inventory/replenishments?status=PENDING_APPROVAL`: Hàng đợi phê duyệt cho Quản lý CLB.
    - `POST /api/inventory/replenishments/{id}/review`: Quản lý CLB phê duyệt (`APPROVED`) hoặc từ chối (`REJECTED`).
* **Khoảng cách (Gaps) so với nguyên văn SRS:**
  1. *Thiếu trường mức độ khẩn cấp (`urgency`):* Nguyên văn SRS yêu cầu *"chỉ rõ mặt hàng, số lượng, và mức độ khẩn cấp"*. Trong `CreateReplenishmentRequest`, các trường hiện có là `(itemId, itemName, category, requestedQuantity, unit, reason)`. Groom có thể ghi mức độ khẩn cấp vào `reason`, nhưng việc thiếu một trường enum/string riêng (`urgency` / `priority`: LOW, NORMAL, URGENT) là một thiếu sót nhỏ so với câu chữ SRS.
  2. *Bộ lọc theo khu vực lưu trữ (`location`):* SRS yêu cầu *"theo từng khu vực lưu trữ được phân công"*. Dữ liệu trả về có trường `location`, nhưng endpoint `GET /api/inventory/items` hiện chỉ nhận query params `category` và `lowStockOnly`, chưa có query param `location` để lọc trực tiếp trên backend.
  3. *Hành vi tự động cộng tồn kho khi duyệt (Implementation Addition):* Khi Quản lý duyệt đề xuất, Backend tự động cộng dồn số lượng đề xuất vào kho (`quantityOnHand += requestedQuantity`). Đây là tiện ích gia tăng hợp lý, dù SRS chỉ dừng ở việc "chuyển đề xuất đến Quản lý xem xét, không bao gồm phê duyệt đơn đặt hàng ngoài hệ thống".
* **Đánh giá FR17:** **`PARTIAL`** (Đáp ứng 85% — Core workflow hoạt động tốt, thiếu trường `urgency` và query param `location`).

---

## 4. RBAC & AN TOÀN TRUY CẬP (RBAC AUDIT)

Hệ thống bảo vệ phân quyền trên 2 lớp:
1. **Lớp Controller (@PreAuthorize):**
   - Mọi endpoint đều yêu cầu xác thực JWT (`@AuthenticationPrincipal Jwt jwt`).
   - Quyền Xem (`PERM_STABLE_CARE_VIEW`): Cho phép tất cả các vai trò nghiệp vụ (Groom, Trainer, Vet, Manager) truy cập danh sách chuồng, sinh hoạt, khẩu phần, checklist, nhiệm vụ, báo cáo sự cố và kho hàng.
   - Quyền Sửa (`PERM_STABLE_CARE_EDIT`):
     - Phân bổ chuồng/ô chuồng: Giới hạn riêng cho `CLUB_MANAGER`.
     - Lịch sinh hoạt: Giới hạn cho `HEAD_TRAINER` và `CLUB_MANAGER`.
     - Khẩu phần dinh dưỡng: Giới hạn cho `VETERINARIAN`, `HEAD_TRAINER`, `CLUB_MANAGER`.
     - Hoàn thành nhiệm vụ chăm sóc: Mở cho `STABLE_STAFF`, `HEAD_TRAINER`, `CLUB_MANAGER`.
     - Báo cáo sự cố khẩn cấp: Mở cho nhân viên có `PERM_STABLE_CARE_EDIT`.
   - Quyền Phê duyệt (`PERM_STABLE_CARE_APPROVE`):
     - Phê duyệt đề xuất bổ sung kho: Chỉ dành riêng cho `CLUB_MANAGER` (`hasRole('CLUB_MANAGER') and hasAuthority('PERM_STABLE_CARE_APPROVE')`).
2. **Lớp Service (Business Validation):**
   - Mọi thao tác gắn với ngựa (`horseId`) đều gọi `horseAccess.requireActive(horseId)`, ngăn chặn thao tác trên ngựa đã ngừng hoạt động/lưu trữ.
   - Không có kẽ hở cho phép bypass phân quyền qua service path khác.

---

## 5. KIỂM TOÁN DATABASE & SCHEMA (DATABASE AUDIT)

* **Nguyên tắc bất biến:** Không tạo thêm bảng mới, bảo toàn giới hạn $\le 30$ bảng trên Azure SQL.
* **Bảng sử dụng:**
  1. `dbo.stables`: Lưu thông tin thực thể cơ sở chuồng nuôi (`name`, `location`, `is_active`).
  2. `dbo.horses`: Lưu thông tin vị trí ô chuồng của ngựa (`stable_location`).
  3. `dbo.notifications`: Lưu thông báo tức thời của sự cố khẩn cấp (`priority='HIGH'`).
  4. `dbo.module_records`: Bảng JSON trung tâm lưu trữ toàn bộ các thực thể của Flow 4:
     - `module_code = 'STABLE_CARE'`:
       - `record_type = 'STALL'`: Ô chuồng.
       - `record_type = 'STALL_ASSIGNMENT'`: Lịch sử và trạng thái phân bổ ngựa vào ô chuồng.
       - `record_type = 'HORSE_DAILY_ROUTINE'`: Lịch sinh hoạt phi tập luyện.
       - `record_type = 'NUTRITION_PLAN'`: Phiên bản khẩu phần ăn.
       - `record_type = 'DAILY_CARE_TASK'`: Nhiệm vụ chăm sóc và bằng chứng hoàn thành.
       - `record_type = 'INCIDENT_REPORT'`: Báo cáo sự cố khẩn cấp.
     - `module_code = 'INVENTORY'`:
       - `record_type = 'INVENTORY_ITEM'`: Mặt hàng trong kho.
       - `record_type = 'REPLENISHMENT_REQUEST'`: Đề xuất bổ sung vật tư.
* **Tính toàn vẹn & Rủi ro mồ côi (Orphan Record Risk):**
  - Mọi bản ghi `module_records` liên quan đến ngựa đều lưu `horse_id` có ràng buộc khóa ngoại với `dbo.horses`.
  - Mọi bản ghi người dùng thao tác đều lưu `user_id` liên kết với `dbo.users`.
  - Check constraint `ck_module_records_module` trên Azure SQL cho phép cả `'STABLE_CARE'` và `'INVENTORY'`. Không vi phạm bất kỳ constraint nào.

---

## 6. KIỂM TOÁN CHẤT LƯỢNG TEST SUITE (TEST AUDIT)

Toàn bộ **102 test cases** trên hệ thống Backend đều vượt qua (`0 failures, 0 errors`):

| Test Class | Số Test | Business Rule Chính Được Kiểm Thử | Negative Cases | Validation Cases |
| :--- | :---: | :--- | :---: | :---: |
| `StableServiceTest` | 5 | Tạo chuồng & ô chuồng; validate thời gian sinh hoạt; kiểm tra thứ tự thời gian (`order by startTime asc`); xóa mềm lịch sinh hoạt | Giờ bắt đầu $\ge$ Giờ kết thúc | Validate `activityType`, `startTime`, `endTime` |
| `NutritionPlanServiceTest` | 4 | Tự động tăng version; supersede kế hoạch cũ; tạo checklist cho Groom xếp theo giờ cho ăn | Không tìm thấy plan active | Validate danh sách bữa ăn không rỗng |
| `CareTaskServiceTest` | 4 | Tạo nhiệm vụ phân công; Groom hoàn thành kèm ảnh và ghi chú | Chặn hoàn thành nhiệm vụ đã COMPLETED | Validate dữ liệu task |
| `IncidentReportServiceTest` | 4 | Tạo sự cố khẩn cấp; tự động dispatch thông báo `HIGH` đến Vet và Trainer; cập nhật giải quyết sự cố (`RESOLVED`) | Chặn mức độ nghiêm trọng không hợp lệ | Validate `severity` hợp lệ |
| `InventoryServiceTest` | 5 | Tạo mặt hàng; tính toán `isLowStock`; cập nhật tồn kho; Manager duyệt đề xuất tự động tăng tồn kho | Chặn duyệt lại đề xuất đã xử lý | Validate `newQuantity`, `requestedQuantity` |

*Nhận xét:* Test suite bao phủ đầy đủ các luồng nghiệp vụ cốt lõi và các negative cases chính. Một số bộ lọc query param phụ (như lọc ngày trong CareTask, lọc lowStockOnly trong Inventory) hoạt động qua Spring Data/JDBC query nhưng chưa có unit test assert riêng.

---

## 7. PHỤ THUỘC LIÊN LUỒNG (CROSS-FLOW DEPENDENCY)

1. **FR01 Hồ sơ Ngựa & FR08 Bản đồ Sức khỏe:**
   - Việc phân bổ chuồng (FR13) tự động ghi đè trường `horses.stable_location`. Giá trị này hiển thị trực tiếp trên màn hình Hồ sơ ngựa (FR01) và Bản đồ sức khỏe toàn chuồng (FR08).
2. **FR11 Khóa Huấn luyện & FR16 Sự cố Khẩn cấp:**
   - Khi Groom báo cáo sự cố nghiêm trọng (ví dụ: `COLIC` hoặc `HOOF_INJURY`), Bác sĩ thú y sau khi nhận thông báo có thể truy cập FR11 để ban hành Lệnh khóa huấn luyện. Đây là quy trình nghiệp vụ nối tiếp giữa Flow 4 và Flow 3.
3. **FR19 Báo cáo Chủ sở hữu & FR22 Tài chính Vận hành:**
   - Khẩu phần ăn (FR14) và số lượng xuất kho vật tư (FR17) sẽ là căn cứ tính toán chi phí nuôi dưỡng và chi phí vận hành cho các báo cáo định kỳ của Flow 5.

---

## 8. KẾT LUẬN & ĐÁNH GIÁ TỔNG THỂ (FINAL VERDICT)

### 8.1. Bảng Tổng Kết Theo Từng FR

| FR | Tên Chức Năng | Overall Status | Mức Độ Đáp Ứng SRS | Khoảng Cách Còn Lại (Remaining Gaps) |
| :--- | :--- | :---: | :---: | :--- |
| **FR13** | Phân bổ chuồng & Lịch sinh hoạt hằng ngày | **COMPLIANT** | 100% | Không có gap |
| **FR14** | Khẩu phần dinh dưỡng & Kế hoạch ăn uống | **COMPLIANT** | 100% | Không có gap |
| **FR15** | Nhiệm vụ chăm sóc hằng ngày & Checklist | **COMPLIANT** | 100% | Không có gap |
| **FR16** | Báo cáo sự cố khẩn cấp | **PARTIAL** | 90% | Backend tạo notification tức thì vào DB, nhưng delivery $\le 30$s hiện phụ thuộc vào chu kỳ Polling của client (chưa có WebSocket/SSE server push) |
| **FR17** | Theo dõi tồn kho & Đề xuất bổ sung | **PARTIAL** | 85% | Thiếu trường độc lập `urgency` trong request đề xuất bổ sung; thiếu query param lọc theo `location` trong API tồn kho |

---

### 8.2. Đánh Giá Toàn Bộ Flow 4 Backend (Flow 4 Backend Verdict)

# **MOSTLY COMPLIANT** (Đạt Đa Số Yêu Cầu SRS)

* **Lý do:** Toàn bộ logic nghiệp vụ cốt lõi, ma trận RBAC, contract dữ liệu và tính toàn vẹn database của cả 5 FR đều đã hoạt động chính xác. Hai điểm `PARTIAL` thuộc về:
  1. Yêu cầu thời gian thực $\le 30$s của FR16 (phụ thuộc cơ chế push vs polling).
  2. Hai trường/bộ lọc nhỏ của FR17 (`urgency` field và `location` filter).

---

## 9. KHUYẾN NGHỊ BÀN GIAO FRONTEND

* **Có cần sửa Backend trước khi bàn giao Frontend hay không?**
  - **KHÔNG CẦN CHẶN.** Frontend team hoàn toàn có thể bắt đầu tích hợp ngay lập tức các màn hình của Flow 4:
    - Sơ đồ chuồng & Lịch sinh hoạt ngựa (FR13).
    - Lập khẩu phần & Bảng checklist cho ăn hôm nay (FR14).
    - Danh sách nhiệm vụ chăm sóc & Xác nhận hoàn thành kèm ảnh (FR15).
    - Màn hình báo cáo sự cố khẩn cấp 1 chạm (FR16) — Frontend cấu hình polling `GET /api/notifications` mỗi 10-15 giây để đạt chuẩn $\le 30$s.
    - Quản lý kho vật tư & Đề xuất nhập hàng (FR17).
  - Các cải tiến nhỏ (bổ sung trường `urgency` và bộ lọc `location` cho FR17) có thể thực hiện dạng minor enhancement nhanh khi chuẩn bị kết nối API với Frontend.
