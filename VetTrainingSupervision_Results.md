# BÁO CÁO THỰC THI: VET TRAINING SUPERVISION & DISTRIBUTED CLAIMING

- **Nhánh thực hiện:** `bacsithuy`
- **Kế hoạch đối chiếu:** [`VetTrainingSupervision_Plan.md`](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/VetTrainingSupervision_Plan.md)
- **Thời gian hoàn thành:** 04/10/2026
- **Trạng thái:** ✅ Hoàn thành 100% (Backend + Frontend)

---

## 1. TỔNG QUAN

Tính năng **Training Supervision & Distributed Claiming** (Mô hình phân tán tự nhận ca theo dõi buổi tập cho Bác sĩ Thú y) đã được triển khai hoàn chỉnh:
- Bác sĩ Thú y chủ động đăng ký ca theo dõi các phiên tập qua API `POST /api/training-schedules/{id}/claim`.
- Khung giờ trực kiểm tra lâm sàng sau buổi tập: **10:00 – 11:00** (khung vận hành sân: **05:00 – 11:00**).
- Quy chuẩn chốt ca: Hạn tự nhận trước **23:00 ngày hôm trước (T-1)** theo múi giờ `Asia/Ho_Chi_Minh`. Sau 23:00, cron job `TrainingAutoPickJob` sẽ tự động phân công bác sĩ có số ca ít nhất trong 30 ngày qua (thuật toán cân bằng tải tất định, không random).
- Mỗi Bác sĩ Thú y nhận tối đa **1 ca/ngày**.

---

## 2. CHI TIẾT CÁC HẠNG MỤC TRIỂN KHAI

### 2.1. Cơ sở dữ liệu & Migration
- File migration: [`DATABASE/15_training_operational_window.sql`](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/DATABASE/15_training_operational_window.sql) (thiết kế idempotent an toàn):
  1. Cập nhật giờ vận hành sân tập (`racetracks`): `opens_at = '05:00:00'`, `closes_at = '11:00:00'`, `closes_next_day = 0`.
  2. Bổ sung các cột mới trong bảng `training_schedules`:
     - `assigned_staff_id`: cho phép `NULL` khi mới tạo lịch.
     - `racetrack_id`: liên kết sân tập.
     - `veterinarian_user_id`: Bác sĩ Thú y phụ trách ca.
     - `staff_assigned_by`, `staff_assigned_at`, `vet_assigned_by`, `vet_assigned_at` (audit log: `USER` hoặc `SYSTEM`).
  3. Cấp quyền: gán `PERM_TRAINING_CLAIM` cho 2 vai trò `STABLE_STAFF` và `VETERINARIAN`.
  4. Triggers chống trùng lịch:
     - `tr_training_schedules_no_staff_overlap`: nhân viên chuồng không trùng ca.
     - `tr_training_schedules_no_vet_overlap`: bác sĩ thú y không trùng ca.
     - `tr_training_schedules_ground_exclusivity`: 1 sân chỉ phục vụ 1 ngựa/ngày.

### 2.2. Backend (Spring Boot / Java)
1. **Entity `TrainingSchedule.java`**:
   - Khai báo các trường `@Column` và `@ManyToOne` với `UserAccount veterinarianUser`, `Racetrack racetrack`, `vetAssignedBy`, `vetAssignedAt`, `staffAssignedBy`, `staffAssignedAt`.
2. **DTO `TrainingScheduleResponse.java`**:
   - Thêm các trường DTO phản hồi, bao gồm cờ `canClaim` (backend tính toán để frontend bật/tắt nút claim theo quy tắc 1 ca/ngày).
3. **Repository `TrainingScheduleRepository.java`**:
   - `findOpenForVet()`: Lọc các phiên tập chưa có bác sĩ và trước hạn chót.
   - `findOpenForStaff()`: Lọc các phiên tập chưa có nhân viên chuồng.
   - `findByVetId()`: Danh sách phiên tập bác sĩ đã tiếp nhận.
   - `existsByVetOnDate()`: Kiểm tra giới hạn 1 ca/ngày/bác sĩ.
   - `findByIdWithLock()`: Cơ chế khóa bi quan `LockModeType.PESSIMISTIC_WRITE` chống race-condition khi nhiều bác sĩ đồng thời nhận 1 phiên tập.
   - `findNeedingAssignmentForDate()`: Danh sách các phiên tập còn trống người nhận để chạy auto-pick.
4. **Repository `UserAccountRepository.java`**:
   - `findLeastAutoPickedVet(targetDate)`: Lựa chọn bác sĩ thú y rảnh lịch có ít lượt auto-pick nhất trong 30 ngày qua (sắp xếp tie-break bằng `u.id ASC`).
   - `findLeastAutoPickedStaff(targetDate)`: Tương tự cho nhân viên chuồng.
5. **Service `TrainingScheduleService.java`**:
   - `claim(scheduleId, callerId, isVet)`:
     - Kiểm tra deadline 23:00 T-1 theo múi giờ `Asia/Ho_Chi_Minh`.
     - Kiểm tra điều kiện trống slot và giới hạn 1 ca/ngày.
     - Gán người dùng với `assignedBy = "USER"`.
   - `findOpenSessions(callerId, isVet, from, to)`: Trả về danh sách phiên tập mở kèm cờ `canClaim`.
   - `mine(callerId, isVet, from, to)`: Phân luồng dữ liệu chính xác giữa Bác sĩ Thú y và Nhân viên chuồng.
6. **Controller `TrainingScheduleController.java`**:
   - `GET /api/training-schedules/open`: Bảo vệ bởi `PERM_TRAINING_CLAIM`.
   - `POST /api/training-schedules/{scheduleId}/claim`: Đăng ký nhận ca.
   - `GET /api/training-schedules/mine`: Hỗ trợ cho cả `VETERINARIAN` và `STABLE_STAFF`.
7. **Cron Job `TrainingAutoPickJob.java`**:
   - Chạy định kỳ lúc 23:00 hàng ngày (`@Scheduled(cron = "0 0 23 * * *", zone = "${app.club-time-zone:Asia/Ho_Chi_Minh}")`).
   - Tự động gán Vet/Staff cho các phiên tập ngày T+1 còn trống.
   - Nếu không còn nhân sự trống lịch, tự động gửi thông báo khẩn cấp (`NotificationPriority.HIGH`) đến Ban Quản lý (`findActiveClubManagersForUpdate`).
8. **Unit Tests `TrainingAutoPickJobTest.java` & `TrainingScheduleServiceTest.java`**:
   - Kiểm thử thành công hành vi tự nhận ca trước deadline, từ chối sau deadline, chặn trùng lịch, auto-pick và escalation.

### 2.3. Frontend (React / Vite)
1. **`vetApi.js`**:
   - `getOpenTrainingSessions(from, to)`
   - `claimTrainingSession(scheduleId)`
   - `getMyTrainingSessions(from, to)`
2. **`VetPage.jsx` (`TrainingAssessmentView`)**:
   - Tích hợp 3 sub-tabs hoàn chỉnh:
     * **Phiên tập cần theo dõi (`open`)**:
       - Banner nhắc nhở ca trực y tế: **10:00 – 11:00** kiểm tra lâm sàng thể trạng.
       - Danh sách phiên tập với các badge: `⏳ Chờ nhận`, `✓ Tự nhận`, `🤖 Hệ thống điều phối`.
       - Nút **"Đăng ký theo dõi"** (bật/tắt tự động theo cờ `canClaim`, hộp thoại xác nhận chuyên nghiệp).
     * **Lịch theo dõi của tôi & Đánh giá lâm sàng (`mine`)**:
       - Hiển thị danh sách phiên tập bác sĩ đã nhận.
       - Nút liên kết nhanh sang Sổ khám y tế (`/vet/medical-records?horseId=...`).
       - Nút mở Modal **"Đánh giá lâm sàng"** (thể trạng, nhịp tim hồi phục sau 10p, hô hấp, dáng đi, cơ bắp, kết luận).
     * **Tất cả lịch tập theo ngày (`calendar`)**:
       - Chế độ xem theo ngày bất kỳ cho phép bác sĩ theo dõi tổng thể lịch tập của toàn CLB.
3. **`App.jsx`**:
   - Cập nhật menu sidebar bác sĩ thú y: `/vet/training` ("Theo dõi & Đánh giá buổi tập").
   - Hỗ trợ cả 2 alias đường dẫn: `/vet/training` và `/vet/training-assessment`.

---

## 3. KẾT QUẢ KIỂM THỬ

### 3.1. Backend Tests
- **Số lượng tests:** **128/128** tests passed (`BUILD SUCCESS`).
- **Thời gian chạy:** ~9.7s.
- **Bao phủ:**
  * `TrainingScheduleServiceTest`: 6 tests (bao gồm các test case cho claim, deadline, trùng lịch).
  * `TrainingAutoPickJobTest`: 2 tests (auto-assign và escalation khi hết nhân sự).
  * 120 tests của các module còn lại (Auth, CareTask, Health, Horse, Incident, Inventory, Media, Nutrition, Racing, Stable).

### 3.2. Frontend Build
- **Lệnh thực thi:** `npm run build`
- **Kết quả:** Biên dịch thành công 100% trong 1.09s, zero errors.
- **Bundle output:** `dist/assets/VetPage-BvK45vqs.js` (117.02 kB).
