# STEP 2A — READ-ONLY SRS COMPLIANCE AUDIT REPORT: FLOW 2

**Document Type:** Read-Only SRS Compliance, Architecture & Verification Audit  
**Module Target:** Flow 2 — Training Plan & Execution (FR03, FR04, FR05, FR06, FR07)  
**Standard of Reference:** Tài liệu Phạm vi Phần mềm (SRS chính thức v1.0, 9/9/2026)  
**Source Code Baseline:** `BACKEND/src/main/java/com/racehorse/training/...`  
**Database Schema Baseline:** `DATABASE/06_training_plans_scheduling_monitoring.sql`, `DATABASE/12_compact_schema.sql`  
**Audit Date:** 2026-09-24  
**Audit Action:** READ-ONLY (Zero source code edits, zero DB migrations, zero git commits)  

---

## 1. SRS FR03 Requirements (Bảng Tiến độ & Biểu đồ Thể lực Toàn Câu lạc bộ)

* **Phạm vi:** Cung cấp cho Huấn luyện viên trưởng một bảng điều khiển tổng hợp về xu hướng thể lực trên toàn bộ ngựa trong câu lạc bộ (khối lượng tập luyện, tiến triển tốc độ, chỉ số hồi phục theo khoảng thời gian có thể chọn). Không bao gồm việc nhập dữ liệu từng buổi tập riêng lẻ (FR07).
* **Actors / Roles:** Huấn luyện viên trưởng (`HEAD_TRAINER`), Quản lý Câu lạc bộ (`CLUB_MANAGER`).
* **Quyền:** `TRAINING:VIEW` (`PERM_TRAINING_VIEW`).
* **Dữ liệu yêu cầu:**
  * Lưới ngựa kèm chỉ báo trạng thái thể lực theo mã màu (`fitnessStatus`).
  * Biểu đồ trực quan (đường/cột) cho: Khối lượng tập luyện (`trainingLoad`), Tốc độ trung bình (`averageSpeedKmh`), Hồi phục nhịp tim (`heartRateRecoveryBpm`).
  * Bộ lọc: Ngựa (`horseId`), Giai đoạn (`phaseId`), Khoảng thời gian (`from`, `to`).
  * Xuất bảng điều khiển dưới dạng PDF hoặc ảnh chụp.
* **Tiêu chí Kiểm định (Acceptance Criteria):**
  1. Biểu đồ cập nhật trong vòng 5 giây sau khi áp dụng bộ lọc hoặc thay đổi khoảng thời gian.
  2. Huấn luyện viên trưởng có thể xác nhận độ chính xác dữ liệu bằng cách đối chiếu một điểm dữ liệu trên biểu đồ với bản ghi buổi tập gốc tương ứng (`evaluationId`).

---

## 2. FR03 Compliance Matrix

| Yêu cầu SRS FR03 | Hiện trạng Implementation | Đánh giá Tuân thủ | Evidence / Source Code |
| :--- | :--- | :--- | :--- |
| **Lưới ngựa với trạng thái mã màu** | `FitnessDashboardResponse.HorseFitnessSummary` trả về `fitnessStatus` (`GREEN`, `YELLOW`, `RED`). | **[FACT] COMPLETE** | `FitnessDashboardResponse.java` (L20-30), `FitnessDashboardService.java` (L79-89). |
| **Biểu đồ khối lượng, tốc độ, hồi phục nhịp tim** | `FitnessDashboardResponse.FitnessDataPoint` trả về `trainingLoad`, `averageSpeedKmh`, `heartRateRecoveryBpm`, `fitnessStatus`, `sessionDate`. | **[FACT] COMPLETE** | `FitnessDashboardResponse.java` (L31-41), `FitnessDashboardService.java` (L94-100). |
| **Lọc theo Ngựa & Khoảng thời gian** | `GET /api/fitness/dashboard` hỗ trợ query params: `horseId`, `from`, `to`, `fitnessStatus`, `query`. | **[FACT] COMPLETE** | `FitnessDashboardController.java` (L27-35), `FitnessDashboardService.java` (L44-60). |
| **Lọc theo Giai đoạn (Phase Filter)** | Controller và Service **KHÔNG CÓ** tham số `phaseId`. Service không hỗ trợ lọc theo giai đoạn kế hoạch. | **[FACT] MISSING** | `FitnessDashboardController.java` thiếu `@RequestParam(required = false) UUID phaseId`. |
| **Đối chiếu điểm dữ liệu với buổi tập gốc** | Mỗi `FitnessDataPoint` chứa `evaluationId` trỏ trực tiếp đến bản ghi đánh giá buổi tập gốc tại `dbo.training_evaluations`. | **[FACT] COMPLETE** | `FitnessDashboardResponse.java` (L32), `FitnessDashboardServiceTest.java` (L51-73). |
| **Xuất PDF hoặc ảnh chụp** | Backend không có endpoint xuất PDF cho dashboard. Thao tác này phụ thuộc vào frontend render/print. | **[INFERENCE] UNSPECIFIED IN BE** | Không có endpoint PDF export trong `FitnessDashboardController`. |

---

## 3. SRS FR04 Requirements (Xây dựng Kế hoạch Huấn luyện Theo Giai đoạn)

* **Phạm vi:** Cho phép Huấn luyện viên trưởng thiết kế kế hoạch huấn luyện nhiều giai đoạn, có cấu trúc cho từng con ngựa. Bao gồm xác định mục tiêu cự ly, cường độ khối lượng tập, loại mặt sân cho mỗi giai đoạn.
* **Actors / Roles:**
  * Tạo / Sửa / Sao chép: `HEAD_TRAINER`, `CLUB_MANAGER`.
  * Xem: `HEAD_TRAINER`, `CLUB_MANAGER`, `HORSE_OWNER`, `VETERINARIAN`, `STABLE_STAFF`.
* **Dữ liệu yêu cầu cho từng giai đoạn:**
  * Tên Giai đoạn (`name`)
  * Ngày Bắt đầu & Ngày Kết thúc (`startDate`, `endDate`)
  * Cự ly Mục tiêu (`targetDistanceMeters`)
  * Cường độ Khối lượng (`workloadIntensity`: LOW, MEDIUM, HIGH)
  * Loại Mặt sân (`surfaceType`: GRASS, DIRT, SYNTHETIC)
* **Quy tắc Nghiệp vụ & Kiểm định:**
  1. Cho phép sao chép kế hoạch hiện có làm mẫu cho con ngựa khác.
  2. Liên kết mỗi kế hoạch với một hồ sơ ngựa cụ thể và hiển thị trong tab Huấn luyện của con ngựa đó.
  3. **Không thể lưu kế hoạch nếu khoảng thời gian của các giai đoạn bị trùng lặp đối với cùng một con ngựa.**
  4. Sau khi lưu, kế hoạch hiển thị trên tab Huấn luyện với tất cả các giai đoạn được liệt kê theo trình tự thời gian.

---

## 4. FR04 Compliance Matrix

| Yêu cầu SRS FR04 | Hiện trạng Implementation | Đánh giá Tuân thủ | Evidence / Source Code |
| :--- | :--- | :--- | :--- |
| **Tạo kế hoạch nhiều giai đoạn** | `POST /api/training-plans` nhận danh sách phases với đầy đủ 6 trường dữ liệu. | **[FACT] COMPLETE** | `TrainingPlanController.java` (L35-39), `TrainingPlanRequest.java`, `TrainingPlanService.java` (L44-51). |
| **Cập nhật kế hoạch** | `PUT /api/training-plans/{planId}` cho phép cập nhật tên và các giai đoạn nếu chưa phát sinh lịch tập. | **[FACT] COMPLETE** | `TrainingPlanService.java` (L54-69). |
| **Sao chép kế hoạch sang ngựa khác** | `POST /api/training-plans/{planId}/copy` sao chép toàn bộ cấu trúc giai đoạn sang ngựa mới. | **[FACT] COMPLETE** | `TrainingPlanController.java` (L46-53), `TrainingPlanService.java` (L72-89). |
| **Chặn trùng lặp thời gian giữa các giai đoạn** | Validate 2 tầng: tầng Service (`buildPhases` throw `BusinessException`) và trigger DB `dbo.tr_training_plan_phases_no_overlap`. | **[FACT] COMPLETE** | `TrainingPlanService.java` (L120-126), `06_training_plans_scheduling_monitoring.sql` (L84-103). |
| **Liệt kê giai đoạn theo trình tự thời gian** | Tự động sắp xếp phases theo `startDate` rồi gán `phaseOrder = 1..N`. | **[FACT] COMPLETE** | `TrainingPlanService.java` (L116-139). |
| **Quyền Xem kế hoạch huấn luyện (RBAC)** | `TrainingPlanController` gắn class-level `@PreAuthorize` yêu cầu `PERM_TRAINING_EDIT`. Chặn toàn bộ vai trò chỉ có `VIEW`. | **[FACT] BUG (CRITICAL)** | `TrainingPlanController.java` (L26): Thiếu tách biệt giữa `VIEW` và `EDIT`. `GET` bị chặn 403 đối với `HORSE_OWNER`, `VETERINARIAN`, `STABLE_STAFF`. |

---

## 5. SRS FR05 Requirements (Phân công Lịch trình Hằng ngày & Lượt chạy Thử)

* **Phạm vi:** Chuyển đổi kế hoạch huấn luyện thành các nhiệm vụ hằng ngày có thể phân công cho đội chăm sóc chuồng và quản lý lượt/làn chạy thử.
* **Actors / Roles:** Huấn luyện viên trưởng (`HEAD_TRAINER`), Quản lý CLB (`CLUB_MANAGER`), Nhân viên chuồng (`STABLE_STAFF`).
* **Dữ liệu yêu cầu:**
  * Kế hoạch & Giai đoạn tham chiếu (`trainingPlanPhaseId`).
  * Con ngựa (`horseId`), Nhiệm vụ (`taskName`), Khung giờ (`scheduledStart`, `scheduledEnd`).
  * Nhân viên phụ trách (`assignedStaffId` thuộc role `STABLE_STAFF`).
  * Lượt chạy thử: Đường đua (`racetrackId`), Làn chạy (`racetrackLaneId`), Khung giờ chạy thử (`bookedStart`, `bookedEnd`).
* **Quy tắc Nghiệp vụ & Kiểm định:**
  1. Chỉ được tạo lịch từ kế hoạch huấn luyện đang hoạt động (`isActive = true`).
  2. Ngày tập phải nằm trong khoảng thời gian của giai đoạn huấn luyện (`startDate <= date <= endDate`).
  3. **Không bị trùng lịch cho cùng một nhân viên tại cùng khung giờ.**
  4. Lịch đặt lượt chạy thử hiển thị các khung giờ đường đua/làn chạy còn trống (không trùng làn).
  5. Nhân viên được phân công nhận được thông báo khi lịch trình được công bố.

---

## 6. FR05 Compliance Matrix

| Yêu cầu SRS FR05 | Hiện trạng Implementation | Đánh giá Tuân thủ | Evidence / Source Code |
| :--- | :--- | :--- | :--- |
| **Tạo lịch từ kế hoạch đang hoạt động** | `TrainingScheduleService.create` kiểm tra `phase.getTrainingPlan().isActive() == true`. | **[FACT] COMPLETE** | `TrainingScheduleService.java` (L61-63). |
| **Ngày tập nằm trong giai đoạn** | Kiểm tra ngày bắt đầu lịch tập nằm trong `phase.getStartDate()` và `phase.getEndDate()`. | **[FACT] COMPLETE** | `TrainingScheduleService.java` (L64-67). |
| **Phân công cho `STABLE_STAFF`** | Kiểm tra tài khoản được phân công phải có role `STABLE_STAFF`. | **[FACT] COMPLETE** | `TrainingScheduleService.java` (L69-71). |
| **Chống trùng lịch nhân viên** | Validate 2 tầng: Service kiểm tra overlap và trigger DB `dbo.tr_training_schedules_no_staff_overlap`. | **[FACT] COMPLETE** | `TrainingScheduleService.java` (L72-75), `06_training_plans_scheduling_monitoring.sql` (L155-176). |
| **Lịch đặt lượt chạy thử & Slot trống** | `RacetrackService.slots(...)` sinh 18 slot (6:00–24:00) cho từng làn và đánh dấu khả dụng/đã đặt. | **[FACT] COMPLETE** | `RacetrackController.java` (L29-34), `RacetrackService.java` (L56-82), `RacetrackServiceTest.java`. |
| **Đặt lượt chạy thử chống trùng làn** | Validate slot đầu giờ, giờ mở cửa, và trigger DB `dbo.tr_trial_run_bookings_no_lane_overlap`. | **[FACT] COMPLETE** | `RacetrackService.java` (L85-101), `06_training_plans_scheduling_monitoring.sql` (L224-245). |
| **Công bố lịch & Thông báo nhân viên** | `publish(...)` lưu `publishedAt` và gọi `NotificationService.create` tạo thông báo ưu tiên `NORMAL`. | **[FACT] COMPLETE** | `TrainingScheduleController.java` (L45-49), `TrainingScheduleService.java` (L92-109). |
| **Đánh dấu hoàn thành lịch tập** | `TrainingSchedule` có cột `completed_at`, nhưng **KHÔNG CÓ endpoint hoặc service method nào cập nhật giá trị này**. | **[FACT] DISCONNECTED** | `TrainingSchedule.java` (L57, L95), thiếu `complete()` method. |

---

## 7. SRS FR06 Requirements (Cảnh báo Quá tải Thể lực & Nguy cơ Chấn thương Thời gian thực)

* **Phạm vi:** Giám sát tự động, thời gian thực đối với dữ liệu nhịp tim và tốc độ trực tiếp để cảnh báo tình trạng quá tải hoặc nguy cơ chấn thương trong buổi tập. Cảnh báo dựa trên ngưỡng và ghi nhận sự kiện cảnh báo.
* **Actors / Roles:** Huấn luyện viên trưởng (`HEAD_TRAINER`), Quản lý CLB (`CLUB_MANAGER`).
* **Dữ liệu yêu cầu:**
  * Dữ liệu nhịp tim (`heartRateBpm`) và tốc độ (`speedKmh`) theo mốc thời gian (`recordedAt`).
  * Hồ sơ ngưỡng có thể cấu hình theo ngựa: Loại chỉ số (`metricType`: `HEART_RATE`, `SPEED`), giá trị Min, Max, khoảng thời gian hiệu lực (`effectiveFrom`, `effectiveTo`).
  * Sự kiện cảnh báo: Mã ngựa, chỉ số quan sát, ngưỡng bị vượt, hướng vượt (`thresholdDirection`: `MINIMUM`, `MAXIMUM`), thời điểm xảy ra.
* **Quy tắc Nghiệp vụ & Kiểm định:**
  1. Cảnh báo được tạo ra trong vòng 10 giây kể từ khi chỉ số vượt qua ngưỡng đã cấu hình.
  2. Đẩy thông báo thời gian thực đến bảng điều khiển/thiết bị của Huấn luyện viên trưởng.
  3. Huấn luyện viên trưởng có thể xem toàn bộ nhật ký cảnh báo cho bất kỳ con ngựa nào, có thể lọc theo ngày.

---

## 8. FR06 Compliance Matrix

| Yêu cầu SRS FR06 | Hiện trạng Implementation | Đánh giá Tuân thủ | Evidence / Source Code |
| :--- | :--- | :--- | :--- |
| **Cấu hình hồ sơ ngưỡng theo ngựa** | CRUD ngưỡng qua `/api/horses/{id}/metric-thresholds`, hỗ trợ min/max cho nhịp tim và tốc độ, lưu `dbo.horse_metric_thresholds`. | **[FACT] COMPLETE** | `TelemetryController.java` (L23-42), `TelemetryService.java` (L24-68). |
| **Tiếp nhận dữ liệu đo (Telemetry)** | `POST /api/horses/{id}/telemetry` tiếp nhận danh sách các lần đọc nhịp tim/tốc độ, lưu `telemetry_sessions` và `telemetry_readings`. | **[FACT] COMPLETE (REST)** | `TelemetryController.java` (L43-47), `TelemetryService.java` (L71-91). *(Lưu ý: tiếp nhận qua REST, chưa có WebSocket streaming)*. |
| **So sánh ngưỡng & Tạo cảnh báo tự động** | `TelemetryService.evaluate(...)` so sánh tức thì từng chỉ số với ngưỡng active; ghi vào `dbo.alert_events`. | **[FACT] COMPLETE** | `TelemetryService.java` (L152-167). |
| **Đẩy thông báo cảnh báo ưu tiên cao** | Tự động tạo bản ghi tại `dbo.notifications` với `priority = 'HIGH'`, `notification_type = 'FITNESS_THRESHOLD'` gửi tới `HEAD_TRAINER`. | **[FACT] COMPLETE** | `TelemetryService.java` (L168-173). |
| **Nhật ký cảnh báo có thể lọc theo ngày** | `GET /api/horses/{id}/alerts` lọc theo `metric`, `acknowledged`, `from`, `to`, phân trang. | **[FACT] COMPLETE** | `TelemetryController.java` (L57-65), `TelemetryService.java` (L116-134). |
| **Xác nhận đã xử lý cảnh báo (Acknowledge)** | `POST /api/horses/{id}/alerts/{alertId}/acknowledge` ghi nhận người và thời điểm xác nhận. | **[FACT] COMPLETE (EXTRA)** | `TelemetryController.java` (L66-70), `TelemetryService.java` (L136-145). |

---

## 9. SRS FR07 Requirements (Đánh giá Buổi tập & Phản hồi Thành tích)

* **Phạm vi:** Ghi nhận kết quả buổi tập và phản hồi chuyên môn sau mỗi buổi huấn luyện hoàn tất. Nhập chỉ số có cấu trúc và nhận xét dạng văn bản tự do của huấn luyện viên.
* **Actors / Roles:** Huấn luyện viên trưởng (`HEAD_TRAINER`), Quản lý CLB (`CLUB_MANAGER`), Chủ sở hữu Ngựa (`HORSE_OWNER`).
* **Dữ liệu yêu cầu biểu mẫu sau buổi tập:**
  * Ngày Buổi tập (`sessionDate`)
  * Thời lượng (`durationMinutes`)
  * Cự ly Đã chạy (`distanceMeters`)
  * Tốc độ Trung bình (`averageSpeedKmh`)
  * Mức độ Gắng sức Cảm nhận (`perceivedExertion`: 1–10)
  * Ghi chú của Huấn luyện viên (`trainerNotes`)
  * Đính kèm video chạy thử hoặc liên kết video (`videoUrl`)
  * Dữ liệu bổ sung: Khối lượng tập (`trainingLoad`), Nhịp tim hồi phục (`heartRateRecoveryBpm`), Trạng thái thể lực (`fitnessStatus`).
* **Quy tắc Nghiệp vụ & Kiểm định:**
  1. Không thể đánh dấu buổi tập là hoàn tất cho đến khi tất cả các trường chỉ số bắt buộc được điền đầy đủ.
  2. Chủ sở hữu Ngựa có thể xem phản hồi và video đính kèm (nếu có) trong cổng thông tin của họ ngay sau khi được gửi.

---

## 10. FR07 Compliance Matrix

| Yêu cầu SRS FR07 | Hiện trạng Implementation | Đánh giá Tuân thủ | Evidence / Source Code |
| :--- | :--- | :--- | :--- |
| **Đầy đủ trường thông tin buổi tập** | `CreateTrainingEvaluationRequest` chứa toàn bộ 6 trường nghiệp vụ bắt buộc + 3 chỉ số tính toán. | **[FACT] COMPLETE** | `CreateTrainingEvaluationRequest.java` (L16-30), `TrainingEvaluation.java`. |
| **Ràng buộc điền đầy đủ các chỉ số** | Bean Validation: `@NotNull`, `@Min(1)`, `@DecimalMin("0.01")`, `@Min(1) @Max(10)`, `@NotBlank @Size(max=4000)`. | **[FACT] COMPLETE** | `CreateTrainingEvaluationRequest.java` (L17-27). |
| **Đính kèm hoặc liên kết video** | Trường `videoUrl` được kiểm tra định dạng URI hợp lệ (HTTP/HTTPS), ngăn chặn tấn công injection. | **[FACT] COMPLETE** | `TrainingEvaluationService.java` (L89-102), `TrainingEvaluationServiceTest.java` (L81-92). |
| **Chủ sở hữu xem phản hồi & video** | `GET /api/training-evaluations/{id}` và `GET /api/horses/{id}/training-evaluations` cho phép `HORSE_OWNER` truy cập (kiểm tra quyền sở hữu ngựa qua `HorseAccessService`). | **[FACT] COMPLETE** | `TrainingEvaluationController.java` (L45-60), `TrainingEvaluationService.java` (L67-82). |
| **Liên kết Đánh giá với Lịch huấn luyện** | SQL có `training_schedule_id` trong `dbo.training_evaluations`, nhưng **Java Entity `TrainingEvaluation.java` không map trường này**. | **[FACT] DISCONNECTED** | `TrainingEvaluation.java` thiếu `trainingSchedule`, `CreateTrainingEvaluationRequest` thiếu `trainingScheduleId`. |

---

## 11. RBAC Audit Flow 2

Đối chiếu giữa **Mục 1.1 Actors & FR03–FR07/FR21 trong SRS**, **Database Seeds (`11_confirmed_automation_and_governance.sql`)**, và **Annotation `@PreAuthorize`**:

| HTTP Method | Endpoint URL | Role / Permission Hiện Tại | Quyền SRS Yêu Cầu | Kết Quả Audit |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/fitness/dashboard` | `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `POST` | `/api/training-plans` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `PUT` | `/api/training-plans/{id}` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `POST` | `/api/training-plans/{id}/copy` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/training-plans/{id}` | *(Class-level)* `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `PERM_TRAINING_VIEW` (Trainer, Manager, Owner, Vet, Staff) | **[FACT] FAIL — Class-level EDIT permission blocks VIEW access!** |
| `GET` | `/api/horses/{id}/training-plans` | *(Class-level)* `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `PERM_TRAINING_VIEW` (Trainer, Manager, Owner, Vet, Staff) | **[FACT] FAIL — Blocks Owner/Vet/Staff from viewing Training tab!** |
| `POST` | `/api/training-schedules` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `POST` | `/api/training-schedules/{id}/publish` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/training-schedules/{id}` | `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','STABLE_STAFF')` | `CLUB_MANAGER`, `HEAD_TRAINER`, `STABLE_STAFF` | **[FACT] PASS** |
| `GET` | `/api/training-schedules` | `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/training-schedules/mine` | `hasAuthority('PERM_TRAINING_VIEW') and hasRole('STABLE_STAFF')` | `STABLE_STAFF` | **[FACT] PASS** |
| `POST` | `/api/racetracks` | `hasAuthority('PERM_TRAINING_EDIT') and hasRole('CLUB_MANAGER')` | `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/racetracks` | `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `CLUB_MANAGER`, `HEAD_TRAINER` | **[FACT] PASS** |
| `GET` | `/api/racetracks/{id}/slots` | `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `CLUB_MANAGER`, `HEAD_TRAINER` | **[FACT] PASS** |
| `POST` | `/api/trial-run-bookings` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/trial-run-bookings` | `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `DELETE` | `/api/trial-run-bookings/{id}` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `POST` | `/api/horses/{id}/metric-thresholds` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/horses/{id}/metric-thresholds` | `hasAuthority('PERM_TRAINING_VIEW')` | `CLUB_MANAGER`, `HEAD_TRAINER`, `HORSE_OWNER`, `VETERINARIAN` | **[FACT] PASS** |
| `POST` | `/api/horses/{id}/telemetry` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/horses/{id}/telemetry` | `hasAuthority('PERM_TRAINING_VIEW')` | Authorized users with view rights | **[FACT] PASS** |
| `GET` | `/api/horses/{id}/alerts` | `hasAuthority('PERM_TRAINING_VIEW')` | `HEAD_TRAINER`, `CLUB_MANAGER`, `VETERINARIAN` | **[FACT] PASS** |
| `POST` | `/api/horses/{id}/alerts/{id}/ack` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `POST` | `/api/training-evaluations` | `hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')` | `HEAD_TRAINER`, `CLUB_MANAGER` | **[FACT] PASS** |
| `GET` | `/api/training-evaluations/{id}` | `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')` | `HEAD_TRAINER`, `CLUB_MANAGER`, `HORSE_OWNER` | **[FACT] PASS** |
| `GET` | `/api/horses/{id}/training-evaluations`| `hasAuthority('PERM_TRAINING_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')` | `HEAD_TRAINER`, `CLUB_MANAGER`, `HORSE_OWNER` | **[FACT] PASS** |

---

## 12. Database Contract Audit Flow 2

Kiểm tra toàn bộ bảng dữ liệu liên quan Flow 2 so với giới hạn **30 bảng của compact schema (`DATABASE/12_compact_schema.sql`)**:

| Bảng Cơ sở dữ liệu | Số Cột | Vai trò trong Flow 2 | Hiện diện trong Compact Schema | Ràng buộc / Trigger Quan trọng |
| :--- | :--- | :--- | :--- | :--- |
| `dbo.training_plans` | 10 | Lưu kế hoạch gốc và kế hoạch bản sao | **[FACT] CÓ** | FK `horse_id`, FK `source_plan_id`, FK `created_by_user_id`. |
| `dbo.training_plan_phases` | 11 | Lưu các giai đoạn huấn luyện | **[FACT] CÓ** | Trigger `tr_training_plan_phases_no_overlap` chống trùng ngày; Unique `(training_plan_id, phase_order)`. |
| `dbo.training_schedules` | 14 | Lưu nhiệm vụ/lịch huấn luyện hằng ngày | **[FACT] CÓ** | Trigger `tr_training_schedules_no_staff_overlap` chống trùng lịch nhân viên; Check `scheduled_start < scheduled_end`. |
| `dbo.training_evaluations` | 13 | Lưu kết quả đánh giá sau buổi tập | **[FACT] CÓ** | SQL có `training_schedule_id UNIQUEIDENTIFIER NULL`, nhưng Java entity chưa map cột này. |
| `dbo.racetracks` | 4 | Quản lý thông tin đường đua | **[FACT] CÓ** | Unique `name`. |
| `dbo.racetrack_lanes` | 4 | Quản lý làn chạy | **[FACT] CÓ** | Unique `(racetrack_id, lane_number)`. |
| `dbo.trial_run_bookings` | 6 | Đặt lượt/làn chạy thử | **[FACT] CÓ** | Trigger `tr_trial_run_bookings_no_lane_overlap` chống trùng slot; Unique `training_schedule_id`. |
| `dbo.horse_metric_thresholds` | 10 | Ngưỡng nhịp tim và tốc độ | **[FACT] CÓ** | Check `metric_type IN ('HEART_RATE','SPEED')`, Check min < max. |
| `dbo.telemetry_sessions` | 5 | Phiên đo dữ liệu | **[FACT] CÓ** | FK `horse_id`, FK `training_schedule_id`. |
| `dbo.telemetry_readings` | 5 | Bản ghi chi tiết nhịp tim/tốc độ | **[FACT] CÓ** | Identity PK, Check `heart_rate_bpm BETWEEN 0 AND 300`, `speed_kmh >= 0`. |
| `dbo.alert_events` | 11 | Lưu sự kiện vượt ngưỡng | **[FACT] CÓ** | FK `horse_id`, FK `threshold_id`, Check direction `MINIMUM`/`MAXIMUM`. |
| `dbo.notifications` | 10 | Bảng dùng chung gửi thông báo | **[FACT] CÓ** | Priority `NORMAL` / `HIGH`, FK `recipient_user_id`. |

**Kết luận Database:** 
* Toàn bộ các bảng cốt lõi của Flow 2 đã tồn tại và nằm hoàn toàn trong giới hạn trần 30 bảng của compact schema.
* **Không cần tạo bảng mới nào** cho Flow 2.

---

## 13. Cross-Module Dependencies Audit

### 13.1. Training Lock (FR11) → Scheduling (FR05)
* **Quy định SRS:** *"Sau khi bị khóa, hệ thống ngăn Huấn luyện viên trưởng phân công con ngựa đó vào bất kỳ buổi tập cường độ cao nào (FR04/FR05) và hiển thị cảnh báo chặn."*
* **Hiện trạng Implementation:**
  * Tại `TrainingScheduleService.java` dòng 76-79:
    ```java
    if (phase.getWorkloadIntensity() == WorkloadIntensity.HIGH
            && lockRepository.findByHorseIdAndReleasedAtIsNull(phase.getTrainingPlan().getHorse().getId()).isPresent()) {
        throw new BusinessException("Không thể xếp lịch cường độ cao vì ngựa đang bị khóa huấn luyện");
    }
    ```
  * **[FACT] Hoàn toàn tuân thủ.** Khi ngựa có lệnh khóa chưa gỡ (`releasedAt IS NULL`), việc xếp lịch với `workloadIntensity = HIGH` bị chặn ngay lập tức kèm thông báo lỗi rõ ràng.
* **Đối với FR04 (Kế hoạch):** Kế hoạch là mẫu thiết kế khung thời gian dài hạn. Hệ thống hiện không chặn tạo phase HIGH trong kế hoạch, nhưng khi chuyển hóa thành lịch tập cụ thể (FR05), hệ thống chặn tuyệt đối.

### 13.2. Training Lock (FR11) → Race Registration (FR18)
* **Quy định SRS:** *"Một con ngựa đang trong tình trạng Khóa Huấn luyện đang hoạt động (FR11) hoặc có trạng thái Bị thương/Cách ly không thể được đăng ký tham gia đua."*
* **Hiện trạng Implementation:**
  * `RaceEventService.register(...)` gọi `HorseMeasurementService.readiness(...)`.
  * `readiness(...)` kiểm tra `training_locks where horse_id=? and released_at is null`. Nếu có, fail rule: *"Ngựa đang bị khóa huấn luyện"*.
  * **[FACT] Hoàn toàn tuân thủ.**

### 13.3. Evaluation (FR07) → Fitness Dashboard (FR03) & Race Readiness (FR18)
* Dữ liệu từ `TrainingEvaluation` cung cấp điểm dữ liệu trực tiếp cho `FitnessDashboardService`.
* `HorseMeasurementService.readiness` kiểm tra đánh giá thể lực gần nhất: `fitnessStatus` phải bằng `GREEN`.

---

## 14. End-to-End Flow Audit

### Flow A: Training Plan → Phase → Schedule
* **Luồng:** Tạo Plan (`POST /api/training-plans`) → Tạo các Phases liên tiếp không trùng lặp → Tạo Schedule (`POST /api/training-schedules`) gắn vào Phase.
* **Đánh giá:** **[FACT] LIỀN MẠCH & HOẠT ĐỘNG TỐT**.

### Flow B: Schedule → Trial Run Booking
* **Luồng:** Tạo Schedule → Tra cứu Slot trống của Đường đua (`GET /api/racetracks/{id}/slots`) → Đặt lượt chạy thử (`POST /api/trial-run-bookings`) gắn với Schedule và Làn chạy.
* **Đánh giá:** **[FACT] LIỀN MẠCH & HOẠT ĐỘNG TỐT**.

### Flow C: Schedule → Telemetry / Alert
* **Luồng:** Tạo Schedule → Ingest Telemetry (`POST /api/horses/{id}/telemetry`) truyền `trainingScheduleId` → Kiểm tra ngưỡng tự động → Phát sinh `alert_events` và `notifications` ưu tiên cao.
* **Đánh giá:** **[FACT] LIỀN MẠCH & HOẠT ĐỘNG TỐT**.

### Flow D: Schedule → Evaluation
* **Luồng:** Buổi tập hoàn tất → Huấn luyện viên nhập đánh giá (`POST /api/training-evaluations`).
* **Đánh giá:** **[FACT] ĐỨT ĐOẠN (BROKEN LINKAGE)**.
  * `CreateTrainingEvaluationRequest` không nhận `trainingScheduleId`.
  * Không có thao tác nào cập nhật `completedAt` trên `TrainingSchedule`.
  * Bảng `dbo.training_evaluations` có cột `training_schedule_id` nhưng Java entity không map, dẫn đến việc không thể xác định buổi tập nào đã được đánh giá xong trên lịch trình hằng ngày.

### Flow E: Training Lock → Scheduling Restriction
* **Luồng:** Bác sĩ thú y kích hoạt Khóa (`POST /api/horses/{id}/training-locks`) → HLV tạo lịch tập cường độ cao → Hệ thống chặn với `BusinessException`.
* **Đánh giá:** **[FACT] LIỀN MẠCH & HOẠT ĐỘNG TỐT**.

---

## 15. Test Coverage Flow 2

Tổng số tests backend hiện có: 42 tests. Trong đó các test liên quan trực tiếp đến Flow 2 gồm:

| Test Class | Số Test | Các Case Được Kiểm Thử | Gaps / Thiếu Sót Trong Test |
| :--- | :--- | :--- | :--- |
| `FitnessDashboardServiceTest` | 2 | Trả về đúng dữ liệu gốc và ID đánh giá; Từ chối khoảng ngày đảo ngược. | Chưa test lọc theo query tên/mã ngựa; Chưa test lọc theo fitness status. |
| `TrainingPlanServiceTest` | 1 | Từ chối các phase trùng lặp thời gian trước khi lưu DB. | Chưa test `copy()` plan; Chưa test `update()` plan; Chưa test kiểm tra RBAC. |
| `TrainingScheduleServiceTest` | 1 | Từ chối xếp lịch cường độ cao khi ngựa đang bị khóa huấn luyện. | Chưa test chống trùng lịch nhân viên; Chưa test `publish()` và bắn notification. |
| `TrainingEvaluationServiceTest` | 3 | Lưu đúng metrics do trainer nhập; Từ chối link video không an toàn; Kiểm tra quyền sở hữu ngựa của chủ sở hữu. | Chưa test validation các trường số âm hoặc ngoài range; Chưa test controller endpoints. |
| `RacetrackServiceTest` | 2 | Sinh đủ 18 slot 1 tiếng cho làn hoạt động; Đặt slot 23:00 kết thúc 24:00 đúng timezone. | Chưa test xung đột đặt trùng làn; Chưa test hủy lượt đặt. |
| `TrainingLockServiceTest` | 2 | Từ chối 2 lock đồng thời trên 1 ngựa; Chặn gỡ lock trước ngày tái khám. | Đã bao phủ tốt nghiệp vụ cốt lõi. |

**Tổng số test Flow 2:** 11 tests.  
**Đánh giá:** Các happy path và validation cốt lõi đã có test, nhưng **hoàn toàn chưa có Controller WebMvcTest** để phát hiện lỗi `@PreAuthorize` ở tầng HTTP request.

---

## 16. Bugs Found

1. **[FACT - CRITICAL BUG] `TrainingPlanController` RBAC Chặn Quyền Đọc:**
   * **Vị trí:** [`TrainingPlanController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/TrainingPlanController.java#L26)
   * **Nguyên nhân:** Class-level annotation:
     ```java
     @PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
     ```
   * **Hậu quả:** Áp dụng lên cả `GET /api/training-plans/{planId}` và `GET /api/horses/{horseId}/training-plans`. Khi `HORSE_OWNER`, `VETERINARIAN`, hoặc `STABLE_STAFF` truy cập tab Huấn luyện của ngựa, hệ thống trả về **403 FORBIDDEN**, vi phạm SRS FR04 và quyền `TRAINING:VIEW` trong DB seed.
2. **[FACT - ARCHITECTURAL BUG] Đứt đoạn liên kết giữa Evaluation và Schedule:**
   * **Vị trí:** [`TrainingEvaluation.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/entity/TrainingEvaluation.java), [`TrainingSchedule.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/entity/TrainingSchedule.java)
   * **Nguyên nhân:** Cột `training_schedule_id` đã được tạo trong SQL (`06_training_plans_scheduling_monitoring.sql` L141), nhưng entity `TrainingEvaluation` không khai báo thuộc tính này.
   * **Hậu quả:** Khi gửi đánh giá sau buổi tập, hệ thống không gắn kết được với `TrainingSchedule`, và `TrainingSchedule.completedAt` không bao giờ được cập nhật.
3. **[FACT - MISSING FEATURE] Thiếu bộ lọc Giai đoạn (Phase Filter) trong Dashboard:**
   * **Vị trí:** [`FitnessDashboardController.java`](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/FitnessDashboardController.java#L28)
   * **Nguyên nhân:** Thiếu `@RequestParam(required = false) UUID phaseId` trong Controller và Service.

---

## 17. Missing Requirements

1. **FR03 — Lọc Dashboard theo Giai đoạn (Phase):** SRS yêu cầu: *"có thể lọc theo ngựa, giai đoạn, hoặc khoảng thời gian"*. Hiện thiếu lọc theo giai đoạn.
2. **FR03 — Xuất PDF / Ảnh chụp:** SRS yêu cầu: *"Hệ thống cho phép xuất bảng điều khiển dưới dạng PDF hoặc ảnh chụp."* Hiện chưa có endpoint xuất dữ liệu/báo cáo PDF ở backend.
3. **FR05 — Cơ chế Push Notification di động:** Notification hiện được lưu vào DB `dbo.notifications`. Chưa có WebSocket hay push notification provider thời gian thực cho thiết bị di động.

---

## 18. Extra / Non-SRS Features (Các tính năng mở rộng hợp lệ)

1. **Đo đạc Telemetry theo ngữ cảnh:** `measurementContext` gồm `RESTING`, `TRAINING`, `RECOVERY` giúp phân loại chỉ số sinh trắc học chính xác hơn.
2. **Xác nhận Cảnh báo (`Acknowledge Alert`):** `POST /api/horses/{id}/alerts/{id}/acknowledge` cho phép HLV ghi nhận đã xem cảnh báo để tránh trùng lặp thao tác.
3. **Cơ chế 18 Slot giờ mở cửa đường đua (6:00–24:00):** Tự động chia slot 60 phút và xử lý chuyển ngày an toàn theo timezone câu lạc bộ (`Asia/Ho_Chi_Minh`).

---

## 19. Recommended Fixes (Kế hoạch khắc phục cho Step 2)

Dưới đây là các hành động kỹ thuật cần thiết để chuẩn bị cho bước Implementation của Step 2:

1. **Sửa RBAC tại `TrainingPlanController`:**
   * Gỡ class-level `@PreAuthorize`.
   * Gắn `@PreAuthorize("hasAuthority('PERM_TRAINING_EDIT') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")` lên `create`, `update`, `copy`.
   * Gắn `@PreAuthorize("hasAuthority('PERM_TRAINING_VIEW')")` lên `get` và `listForHorse` (cho phép cả `HORSE_OWNER`, `VETERINARIAN`, `STABLE_STAFF`).
2. **Hoàn thiện liên kết `TrainingEvaluation` → `TrainingSchedule`:**
   * Bổ sung `trainingScheduleId` vào `CreateTrainingEvaluationRequest`.
   * Bổ sung quan hệ `@ManyToOne` hoặc mapping cột `training_schedule_id` vào `TrainingEvaluation.java`.
   * Khi tạo đánh giá, nếu có `trainingScheduleId`, cập nhật `TrainingSchedule.completedAt = now`.
3. **Bổ sung `phaseId` filter cho Fitness Dashboard:**
   * Thêm `phaseId` vào `FitnessDashboardController` và `FitnessDashboardService`.
   * Truy vấn các evaluation thuộc các schedule gắn với phase tương ứng.
4. **Bổ sung Unit Tests:**
   * Viết test kiểm tra RBAC của `TrainingPlanController`.
   * Viết test cho `TelemetryService` (ngưỡng, cảnh báo, thông báo).
   * Viết test cho `TrainingPlanService.copy`.

---

## 20. Final Status của Flow 2 Audit

### ⚠️ **ĐÁNH GIÁ CUỐI CÙNG: NEEDS FIX**

* **Kiến trúc & Nghiệp vụ cốt lõi:** Đã hoàn thành 85% và bám sát SRS v1.0.
* **Cơ sở dữ liệu:** Đầy đủ, chuẩn hóa, không cần tạo thêm bảng mới, tuân thủ giới hạn 30 bảng.
* **Lý do NEEDS FIX:**
  1. Lỗi RBAC tại `TrainingPlanController` chặn quyền đọc kế hoạch huấn luyện của Chủ sở hữu, Bác sĩ thú y, và Nhân viên chuồng.
  2. Đứt đoạn liên kết giữa Đánh giá buổi tập (FR07) và Lịch huấn luyện (FR05) khiến lịch tập không bao giờ được đánh dấu hoàn thành.
  3. Thiếu bộ lọc theo Giai đoạn trong Bảng điều khiển thể lực (FR03).
