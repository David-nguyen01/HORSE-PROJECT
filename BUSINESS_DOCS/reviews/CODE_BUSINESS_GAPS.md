# Bảng Danh Mục Độ Lệch Mã Nguồn & Yêu Cầu Nghiệp Vụ (Code vs Business Gaps Catalog)
## Discrepancies, Architectural Inconsistencies & Conflict Analysis

> **Thư mục:** `BUSINESS_DOCS/reviews/`  
> **Thời điểm thẩm định:** 12/10/2026  
> **Phương pháp tiếp cận:** Đối chiếu ba nguồn bằng chứng:  
> 1. Mã nguồn Backend thực tế (Java entities, controllers, services, database triggers).  
> 2. Giao diện Frontend thực tế (React components, routes, API client).  
> 3. Tài liệu mô tả ban đầu (SRS trích xuất, README dự án, tài liệu RBAC cũ).

---

## 1. Phân Loại Trạng Thái Độ Lệch (Gap Classification Schema)

Các phát hiện được phân nhóm theo các nhãn tiêu chuẩn:
- `DOC_CODE_CONFLICT`: Tài liệu thiết kế mô tả một đằng nhưng mã nguồn thực tế triển khai một nẻo.
- `BACKEND_ONLY_OR_UNWIRED_API`: Backend đã viết API hoàn chỉnh nhưng Frontend chưa có giao diện gọi tới.
- `DISABLED_CODE_OR_FILTER`: Mã nguồn đã lập trình xong nhưng bị tắt (disable) trong cấu hình chạy thực tế.
- `UI_VALIDATION_MISMATCH`: Ràng buộc nghiệp vụ được chốt cứng ở Database/Backend nhưng Frontend thiếu kiểm tra trước (pre-validation), dẫn đến lỗi ném ra từ tầng dưới.
- `ARCHITECTURAL_INCONSISTENCY`: Sự không đồng nhất về giải pháp kỹ thuật giữa các phân hệ cùng cấp.

---

## 2. Bảng Tổng Hợp Danh Mục Độ Lệch (Gap Catalog)

| Mã Lỗ Hổng | Tên Phát Hiện / Độ Lệch | Phân Loại | Mức Độ Ảnh Hưởng | Trạng Thái Hiện Tại |
|---|---|---|---|---|
| **GAP-01** | Bộ lọc chặn chủ ngựa quá hạn nợ phí (`OwnerOverdueFilter`) bị tắt trong Spring Security | `DISABLED_CODE_OR_FILTER` | **CAO (High)** | Lập trình đầy đủ nhưng cấu hình tắt |
| **GAP-02** | Phân hệ nhập chuồng `HorseIntakeController` chưa có giao diện độc lập | `BACKEND_ONLY_OR_UNWIRED_API` | **TRUNG BÌNH (Medium)** | Thay thế bằng thao tác trực tiếp tại Admin & Vet |
| **GAP-03** | Tách rời trạng thái tiếp nhận (`intake_status`) khỏi thanh toán (`payment_status`) | `DOC_CODE_CONFLICT` | **TRUNG BÌNH (Medium)** | Code đã tách sạch, tài liệu cũ còn nhầm lẫn |
| **GAP-04** | Giới hạn 4 ngựa/chuồng chỉ chặn ở Trigger DB, thiếu chặn ở UI | `UI_VALIDATION_MISMATCH` | **THẤP (Low)** | DB Trigger bảo vệ an toàn, UI chỉ bắt lỗi sau khi bấm |
| **GAP-05** | Tách biệt vai trò Bác sĩ thú y khỏi luồng tập thông thường | `DOC_CODE_CONFLICT` | **CAO (High)** | Đã giải quyết triệt để trên code sau đợt review TV2 |
| **GAP-06** | Phân công nhân viên chăm sóc (`caretaker_user_id`) chưa có tính năng tự nhận ca | `DOC_CODE_CONFLICT` | **THẤP (Low)** | Quản lý gán trực tiếp, đơn giản hóa quy trình |
| **GAP-07** | Không nhất quán giải pháp lưu trữ ảnh minh chứng (`CareTask` vs `Incident`) | `ARCHITECTURAL_INCONSISTENCY` | **THẤP (Low)** | CareTask lưu byte trong DB, Incident lưu Cloudinary URL |
| **GAP-08** | Phiếu đánh giá lâm sàng thú y sau buổi tập chưa có màn hình xem chi tiết cho HLV | `PARTIAL_UI_PRESENTATION` | **TRUNG BÌNH (Medium)** | Backend có API & Notify, UI mới hiển thị thông báo |

---

## 3. Phân Tích Chi Tiết Từng Phát Hiện

### GAP-01: Bộ lọc chặn chủ ngựa nợ phí (`OwnerOverdueFilter`) bị vô hiệu hóa
- **Thực trạng quan sát (`[FACT]`):**
  - File `BACKEND/src/main/java/com/racehorse/wallet/OwnerOverdueFilter.java` được lập trình rất chi tiết: Nếu một chủ ngựa (`HORSE_OWNER`) có `service_next_due_date` quá hạn `>= 2 ngày`, filter sẽ chặn toàn bộ các request ngoại trừ các API nạp ví (`/api/wallet/*`) và xem hồ sơ ngựa cơ bản.
  - Tuy nhiên, trong `SecurityConfiguration.java`:
    ```java
    FilterRegistrationBean<OwnerOverdueFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false); // ĐANG BỊ TẮT HOÀN TOÀN
    ```
- **Hệ quả kinh doanh:** Chủ ngựa dù đã quá hạn nợ tiền nhiều ngày vẫn có thể đăng nhập, duyệt đua, xem báo cáo và tương tác với hệ thống mà không bị phong tỏa quyền hạn như tài liệu nghiệp vụ mô tả.
- **Khuyến nghị PO:** Cần quyết định kích hoạt lại filter này (`enabled = true`) hay chuyển cơ chế nhắc nợ sang thông báo mềm không chặn cứng.

---

### GAP-02: Phân hệ tiếp nhận chuồng `HorseIntakeController` không có giao diện độc lập
- **Thực trạng quan sát (`[FACT]`):**
  - Backend cung cấp một controller riêng biệt: `HorseIntakeController.java` (`/api/horse-intake/...`) gồm các endpoint kiểm tra phòng chờ y tế, đưa vào hàng chờ, tìm chuồng trống.
  - Tuy nhiên, trên Frontend không tồn tại route `/horse-intake` hay file `HorseIntakePage.jsx`.
  - Trên thực tế, luồng nghiệp vụ này được tách ra làm 2 nơi: Bác sĩ thú y tiếp nhận qua `VetPage.jsx` (`/api/medical/queue`), còn Quản lý CLB gán ô chuồng trực tiếp tại `AdminPages.jsx` (`/admin/stables`).
- **Hệ quả kinh doanh:** Vận hành thực tế vẫn trơn tru nhưng controller `HorseIntakeController` trở thành mã nguồn dư thừa hoặc chỉ phục vụ tích hợp bên ngoài.
- **Khuyến nghị PO:** Chốt tài liệu SRS chính thức theo luồng giao diện thực tế (Vet Queue + Admin Stables), loại bỏ hoặc gom gọn `HorseIntakeController`.

---

### GAP-03: Xung đột khái niệm trạng thái tiếp nhận vs trạng thái thanh toán
- **Thực trạng quan sát (`[FACT]`):**
  - Trong tài liệu yêu cầu ban đầu (SRS draft), hệ thống dùng một trường duy nhất `paymentStatus` để mô tả vừa là tình trạng tiền nong, vừa là vị trí vật lý (ví dụ: `paymentStatus = 'IN_MEDICAL'`, `paymentStatus = 'TRAINING'`).
  - Trong mã nguồn hiện tại, Backend đã tái cấu trúc tách rời hoàn toàn:
    - `horses.payment_status`: Chỉ phản ánh tài chính (`UNPAID`, `PAID`, `OVERDUE`).
    - `horses.intake_status`: Phản ánh luồng nhập chuồng vật lý (`PENDING_WAITING_ROOM`, `WAITING_FOR_VET`, `IN_MEDICAL`, `PENDING_STALL`, `STABLED`).
- **Hệ quả kinh doanh:** Thiết kế hiện tại của mã nguồn khoa học và đúng đắn hơn nhiều so với tài liệu SRS cũ, giúp một con ngựa có thể "đã thanh toán" nhưng vẫn đang "chờ xếp ô chuồng" mà không bị ghi đè trạng thái.
- **Khuyến nghị PO:** Cập nhật dứt khoát tài liệu SRS theo chuẩn mới của mã nguồn (`intake_status` độc lập với `payment_status`).

---

### GAP-04: Giới hạn 4 ngựa/chuồng chỉ kiểm soát ở Database Trigger
- **Thực trạng quan sát (`[FACT]`):**
  - Database có Trigger cứng `tr_module_records_stall_assignment_unique_active` kiểm tra: Trong cùng một khu chuồng (`stables`), tổng số ô chuồng có phân bổ hoạt động (`status = 'ACTIVE'`) không được vượt quá 4.
  - Trên Frontend (`AdminPages.jsx`), Quản lý vẫn có thể thêm 6 hay 8 ô chuồng vào một khu chuồng. Khi bấm gán ngựa vào ô thứ 5, Backend mới quăng ngoại lệ từ SQL Server về.
- **Hệ quả kinh doanh:** Người dùng có thể bỡ ngỡ khi thấy chuồng còn ô trống nhưng bấm gán lại báo lỗi do chạm trần sức chứa của khu chuồng.
- **Khuyến nghị PO:** Giữ nguyên Trigger an toàn ở DB; bổ sung hiển thị trực quan tỷ lệ sức chứa trên UI: `Đã dùng X / 4 chỗ`.

---

### GAP-05: Vai trò Bác sĩ thú y trong luồng tập luyện thông thường
- **Thực trạng quan sát (`[FACT]`):**
  - Tài liệu cũ từng đề xuất Bác sĩ thú y phải vào duyệt ca, giám sát buổi tập song song với Huấn luyện viên trưởng.
  - Tuy nhiên, trong mã nguồn hoàn thiện trên `develop`, luồng huấn luyện thông thường hoàn toàn do HLV trưởng và Nhân viên chuồng phối hợp (`PREPARING` -> `READY` bởi Caretaker -> `IN_PROGRESS` bởi HLV -> `COMPLETED` bởi HLV -> Tự động sinh task hồi phục).
  - Bác sĩ thú y chỉ thực hiện đánh giá lâm sàng độc lập (`VET_TRAINING_ASSESSMENT`) sau khi buổi tập đã xong, hoặc nhận báo động khẩn cấp khi có chấn thương.
- **Hệ quả kinh doanh:** Giải phóng bác sĩ thú y khỏi việc phải "chực chờ" duyệt từng buổi tập chạy hàng ngày, tập trung vào chuyên môn y tế.
- **Khuyến nghị PO:** Khẳng định luồng hiện tại là chuẩn mực chính thức cho SRS.

---

### GAP-06: Phân công nhân viên chăm sóc (`caretaker_user_id`)
- **Thực trạng quan sát (`[FACT]`):**
  - Cột `horses.caretaker_user_id` hiện tại được gán trực tiếp bởi Quản lý CLB (`CLUB_MANAGER`) qua chức năng quản trị hồ sơ ngựa hoặc cấu hình hệ thống.
  - Nhân viên chăm sóc không có nút "Nhận chăm sóc ngựa này" (Self-claim).
- **Hệ quả kinh doanh:** Đảm bảo tính điều phối kỷ luật từ cấp quản trị, tránh tình trạng nhân viên tự ý chọn ngựa khỏe/ngựa đẹp để chăm sóc.
- **Khuyến nghị PO:** Ghi nhận rõ quyền phân công nhân sự thuộc về Quản lý CLB.

---

### GAP-07: Lưu trữ ảnh minh chứng thực tế: Database Byte Array vs Cloudinary URL
- **Thực trạng quan sát (`[FACT]`):**
  - Nghiệm thu nhiệm vụ chuồng (`CareTaskService`): Hỗ trợ tải tệp ảnh trực tiếp lên server, lưu trữ dưới dạng mảng byte (`photo_bytes`, `photo_content_type`) ngay trong bảng `dbo.care_tasks` của SQL Server.
  - Báo cáo sự cố (`IncidentReport`): Frontend tải ảnh lên dịch vụ đám mây Cloudinary thông qua widget upload, sau đó chỉ gửi chuỗi `photoUrl` về backend để lưu trong JSON dữ liệu.
- **Hệ quả kỹ thuật:** Không ảnh hưởng đến người dùng cuối vì ảnh đều xem được bình thường, nhưng gây phân mảnh về mặt kiến trúc lưu trữ media (lưu nội bộ DB vs lưu Cloudinary).
- **Khuyến nghị PO:** Đồng thuận giữ nguyên cho bản v1; xem xét chuẩn hóa lưu toàn bộ media qua Cloudinary ở giai đoạn tối ưu hóa sau này.

---

### GAP-08: Giao diện hiển thị đánh giá lâm sàng thú y sau buổi tập
- **Thực trạng quan sát (`[FACT]`):**
  - Backend đã hỗ trợ API nộp đánh giá lâm sàng `POST /api/training-evaluations/{id}/vet-assessment` và gửi thông báo chuông `VET_ASSESSMENT_COMPLETED` tới Huấn luyện viên trưởng.
  - Bác sĩ thú y có giao diện nộp rất đẹp trong `VetPage.jsx` (`TrainingAssessmentView`).
  - Tuy nhiên, trên giao diện Huấn luyện viên trưởng (`TrainingSchedulePage.jsx`), HLV mới chỉ nhận được chuông thông báo chứ chưa có nút bấm mở xem chi tiết trực tiếp phiếu khám lâm sàng này gắn liền với buổi tập.
- **Khuyến nghị PO:** Bổ sung một dialog xem kết quả lâm sàng thú y trong tab Lịch huấn luyện của Huấn luyện viên trưởng.
