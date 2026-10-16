# Danh Sách Câu Hỏi Mở & Quyết Định Nghiệp Vụ Cần Chốt (Open Business Questions & Decisions)
## PO & Stakeholder Decision Log

> **Thư mục:** `BUSINESS_DOCS/reviews/`  
> **Thời điểm thẩm định:** 12/10/2026  
> **Mục đích:** Tập hợp các điểm chưa thống nhất hoặc các quyết định kiến trúc/nghiệp vụ quan trọng cần Product Owner (PO) và Trưởng nhóm kỹ thuật phê duyệt trước khi hoàn thiện tài liệu SRS chính thức.

---

## Bảng Tổng Hợp Câu Hỏi Mở Cần Quyết Định (Decision Log)

| Mã Câu Hỏi | Chủ Đề Nghiệp Vụ | Mức Độ Ưu Tiên | Người Chịu Trách Nhiệm Quyết Định | Trạng Thái |
|---|---|---|---|---|
| **OQ-01** | Kích hoạt bộ lọc chặn chủ ngựa quá hạn nợ phí (`OwnerOverdueFilter`) | **CAO (P1)** | Product Owner / Giám Đốc Vận Hành | Chờ PO chốt |
| **OQ-02** | Cơ chế giới hạn sức chứa 4 ngựa / Khu chuồng trại (`stables`) | **TRUNG BÌNH (P2)** | Product Owner / Quản Lý Chuồng | Đề xuất giải pháp UI |
| **OQ-03** | Chuẩn hóa quy trình tiếp nhận nhập chuồng (`Horse Intake UI`) | **TRUNG BÌNH (P2)** | Product Owner / UX Designer | Đề xuất chọn luồng Admin |
| **OQ-04** | Xác nhận ranh giới vai trò Bác sĩ thú y trong buổi huấn luyện | **CAO (P1)** | PO / Chuyên Gia Huấn Luyện | Đã thống nhất theo mã nguồn |
| **OQ-05** | Chuẩn hóa kiến trúc lưu trữ ảnh: SQL Server Byte Array vs Cloudinary | **THẤP (P3)** | Tech Lead / System Architect | Giữ nguyên cho v1 |
| **OQ-06** | Cơ chế phân công nhân viên chăm sóc (`Caretaker Assignment`) | **TRUNG BÌNH (P2)** | Quản Lý CLB / HR | Giữ cơ chế Quản lý gán |
| **OQ-07** | Bổ sung màn hình xem đánh giá lâm sàng thú y cho Huấn luyện viên trưởng | **TRUNG BÌNH (P2)** | PO / Frontend Lead | Khuyến nghị bổ sung UI |

---

## Chi Tiết Các Câu Hỏi Mở & Phương Án Lựa Chọn

### OQ-01: Chính sách xử lý chủ sở hữu ngựa nợ phí dịch vụ (`OwnerOverdueFilter`)
- **Bối cảnh kỹ thuật:** Mã nguồn Java đã có sẵn `OwnerOverdueFilter.java` với logic: Nếu nợ phí `>= 2 ngày`, hệ thống trả về mã lỗi `403 Forbidden` cho mọi request, chỉ mở riêng cổng nạp ví `/api/wallet/*` và xem hồ sơ ngựa cơ bản. Tuy nhiên trong file cấu hình bảo mật `SecurityConfiguration.java`, filter này đang bị tắt bằng dòng:
  ```java
  registration.setEnabled(false);
  ```
- **Các phương án lựa chọn:**
  - **Phương án A (Khuyến nghị):** Kích hoạt lại bộ lọc (`enabled = true`). Áp dụng đúng chính sách kỷ luật tài chính: Khóa quyền đăng ký giải đua và các tính năng nâng cao sau 2 ngày quá hạn nợ phí.
  - **Phương án B:** Tiếp tục tắt bộ lọc. Chuyển sang chính sách "nhắc nhở mềm": Hiển thị banner cảnh báo nợ phí trên đầu trang và gửi thông báo chuông, không chặn cứng các thao tác của chủ ngựa.
- **Tác động:** Phương án A bảo vệ dòng tiền câu lạc bộ; Phương án B mang lại trải nghiệm mềm dẻo hơn cho khách hàng VIP.

---

### OQ-02: Quy chuẩn giới hạn cứng 4 ngựa / Khu chuồng trại
- **Bối cảnh kỹ thuật:** Trigger SQL Server `tr_module_records_stall_assignment_unique_active` đang cài đặt giới hạn cứng: Mỗi khu chuồng (`stables`) chỉ được chứa tối đa 4 con ngựa có phân bổ hoạt động. Trong khi đó, người quản lý có thể tạo 6 hoặc 8 ô chuồng trong một khu. Khi gán đến con ngựa thứ 5, hệ thống sẽ ném lỗi từ Database.
- **Các phương án lựa chọn:**
  - **Phương án A (Khuyến nghị giữ nguyên):** Giữ nguyên quy tắc an toàn 4 ngựa/chuồng theo quy chuẩn phúc lợi động vật của CLB. Bổ sung trên giao diện màn hình Chuồng trại hiển thị tỷ lệ lấp đầy trực quan: `Đã sử dụng X / 4 chỗ`. Nếu đạt 4, tự động vô hiệu hóa nút gán ngựa mới.
  - **Phương án B:** Nâng cấp bảng `stables` bổ sung cột `max_capacity INT DEFAULT 4`, cho phép người quản lý tùy chỉnh sức chứa cho từng khu chuồng cụ thể.
- **Tác động:** Phương án A không cần thay đổi cấu trúc database; Phương án B linh hoạt hơn cho quy mô mở rộng chuồng trại trong tương lai.

---

### OQ-03: Chuẩn hóa giao diện tiếp nhận ngựa nhập chuồng (`Horse Intake`)
- **Bối cảnh kỹ thuật:** Backend có controller riêng `HorseIntakeController.java` (`/api/horse-intake/...`), nhưng Frontend không có trang riêng mà chia đôi quy trình: Bác sĩ khám ở `VetPage.jsx` (`/api/medical/queue`), còn Quản lý xếp chuồng ở `AdminPages.jsx` (`/admin/stables`).
- **Các phương án lựa chọn:**
  - **Phương án A (Khuyến nghị):** Chuẩn hóa tài liệu SRS theo giao diện thực tế hiện tại. Phân định rõ: Khâu y tế do Thú y phụ trách trong trang Thú y; khâu xếp ô chuồng do Quản lý phụ trách trong trang Chuồng trại. Thu hồi hoặc tái cấu trúc controller `HorseIntakeController`.
  - **Phương án B:** Xây dựng mới một trang Wizard giao diện thống nhất `HorseIntakePage.jsx` để người quản lý theo dõi toàn bộ hành trình ngựa từ phòng chờ -> khám y tế -> xếp chuồng.
- **Tác động:** Phương án A giữ vững mã nguồn đang chạy ổn định; Phương án B bổ sung thêm khối lượng công việc frontend.

---

### OQ-04: Xác nhận dứt khoát ranh giới vai trò Bác sĩ thú y trong buổi huấn luyện
- **Bối cảnh kỹ thuật:** Một số tài liệu ban đầu đề cập việc Bác sĩ thú y phải tham gia duyệt hoặc claim buổi tập huấn luyện cùng Huấn luyện viên trưởng. Mã nguồn hiện tại trên `develop` đã chốt: Buổi tập bình thường chỉ có HLV và Nhân viên chuồng phối hợp. Thú y chỉ gửi nhận xét lâm sàng độc lập (`VET_TRAINING_ASSESSMENT`) sau khi tập xong, hoặc can thiệp khi có báo động khẩn cấp.
- **Quyết định đề xuất:** 
  - Khẳng định 100% quy trình thực tế của mã nguồn là chuẩn mực kinh doanh chính thức: **Bác sĩ thú y không phải là người vận hành buổi tập bình thường**.
- **Ý nghĩa:** Chấm dứt hoàn toàn các tranh luận trái chiều trong các đợt merge code trước đây.

---

### OQ-05: Chuẩn hóa kiến trúc lưu trữ hình ảnh minh chứng
- **Bối cảnh kỹ thuật:** Hiện tại hệ thống tồn tại song song 2 giải pháp lưu trữ:
  - Nghiệm thu công việc chăm sóc chuồng (`CareTask`): Lưu trực tiếp mảng byte vào bảng SQL Server `dbo.care_tasks` (hỗ trợ tệp `<= 10MB`).
  - Báo cáo sự cố khẩn cấp (`IncidentReport`): Tải ảnh lên Cloudinary widget rồi lưu đường dẫn URL vào JSON.
- **Các phương án lựa chọn:**
  - **Phương án A (Khuyến nghị cho MVP v1):** Giữ nguyên kiến trúc hiện tại, không thay đổi mã nguồn.
  - **Phương án B (Tối ưu hóa v2):** Chuyển đổi toàn bộ việc tải ảnh của `CareTask` sang dùng chung widget Cloudinary, giúp giảm kích thước cơ sở dữ liệu SQL Server và tăng tốc độ sao lưu định kỳ.
- **Tác động:** Phương án A tiết kiệm thời gian phát triển; Phương án B tối ưu hóa chi phí lưu trữ hạ tầng dài hạn.

---

### OQ-06: Cơ chế phân công nhân viên chăm sóc (`Caretaker Assignment`)
- **Bối cảnh kỹ thuật:** Hiện tại cột `horses.caretaker_user_id` chỉ được gán và thay đổi bởi Quản lý câu lạc bộ (`CLUB_MANAGER`) thông qua quản trị hồ sơ hoặc cấu hình hệ thống. Nhân viên chuồng không thể tự ý nhận nuôi (self-claim) ngựa.
- **Quyết định đề xuất:**
  - Giữ nguyên cơ chế Quản lý điều phối tập trung. Nhân viên chuồng chỉ tập trung thực thi các nhiệm vụ được giao trên đàn ngựa phụ trách.
- **Ý nghĩa:** Bảo đảm tính kỷ luật trong quản lý nhân sự tại trang trại ngựa.

---

### OQ-07: Bổ sung Modal xem đánh giá lâm sàng thú y cho Huấn luyện viên trưởng
- **Bối cảnh kỹ thuật:** Backend đã có đầy đủ API nộp đánh giá lâm sàng thú y sau buổi tập (`overallCondition`, `heartRateRecoveryBpm`, `respiratoryStatus`, `gaitStatus`, `muscleCondition`) và bắn notification tới Huấn luyện viên trưởng. Bác sĩ thú y đã có giao diện nộp đầy đủ. Tuy nhiên trên màn hình Lịch huấn luyện của HLV, HLV mới chỉ nhận thông báo chuông chứ chưa có nút bấm mở xem chi tiết phiếu đánh giá lâm sàng gắn với buổi tập.
- **Khuyến nghị đề xuất:**
  - Bổ sung một dialog nhỏ "Xem đánh giá lâm sàng thú y" trong component chi tiết buổi tập của Huấn luyện viên trưởng để HLV đọc nhanh tình trạng cơ bắp và dáng đi của ngựa sau bài tập.
- **Mức độ ảnh hưởng:** Tính năng gia tăng giá trị UX, không làm thay đổi luồng nghiệp vụ cốt lõi.
