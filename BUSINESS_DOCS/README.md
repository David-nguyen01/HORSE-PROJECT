# Racehorse Training Management System (RTMS)
## Reconstructed Business Requirements & Current Implementation Draft

> **Tài liệu Dự thảo Tái cấu trúc Nghiệp vụ từ Mã nguồn Thực tế**  
> **Thư mục:** `BUSINESS_DOCS/`  
> **Thời điểm lập:** 12/10/2026  
> **Nhánh mã nguồn đối chiếu:** `develop`  
> **Tình trạng:** DỰ THẢO ĐỐI SOÁT (REVIEW DRAFT) — Phục vụ Chủ dự án (Project Owner) và Đội ngũ phát triển rà soát, hiệu chỉnh trước khi ban hành SRS chính thức.

---

## 1. Mục đích và Phạm vi Tài liệu

Tài liệu trong thư mục `BUSINESS_DOCS/` được lập ra nhằm **tái hiện trung thực toàn bộ hành vi nghiệp vụ hiện tại** của hệ thống Quản lý Huấn luyện Ngựa đua (Racehorse Training Management System - RTMS) dựa trên việc phân tích mã nguồn thực tế (Backend Spring Boot, Frontend React SPA, Cơ sở dữ liệu Microsoft SQL Server).

Mục tiêu chính:
1. **Minh bạch hóa hiện trạng:** Xác định chính xác hệ thống đang làm được gì, hỗ trợ những vai trò nào và ràng buộc nghiệp vụ ra sao.
2. **Cơ sở cho SRS chính thức:** Làm tài liệu đầu vào chuẩn xác, có bằng chứng truy vết code để Chủ dự án phê duyệt hoặc điều chỉnh các quyết định còn bỏ ngỏ.
3. **Phát hiện sai lệch (Gaps & Conflicts):** Đối chiếu giữa tài liệu cũ (SRS sơ khai, báo cáo kiểm toán cũ) với mã nguồn hiện tại, chỉ ra những điểm mâu thuẫn hoặc tính năng chưa hoàn thiện.

---

## 2. Hệ Thống Phân Cấp Bằng Chứng (Evidence Hierarchy)

Nhằm đảm bảo tính chính xác và loại bỏ giả định chủ quan, tài liệu tuân thủ thứ bậc bằng chứng nghiêm ngặt:

1. **Nguồn Chân lý Tối cao (Primary Source - Backend Implementation):**
   - Mã nguồn Java Backend (`BACKEND/src/main/java/com/racehorse/`).
   - Ràng buộc tầng dịch vụ (`Service`), kiểm soát bảo mật (`@PreAuthorize`), DTO validation (`@Valid`).
   - Ràng buộc CSDL vật lý: Khóa ngoại, `CHECK` constraints, Triggers (`DATABASE/*.sql`).
2. **Nguồn Bằng chứng Thứ cấp (Secondary Source - Frontend Implementation):**
   - Mã nguồn React SPA (`FRONTEND/src/`).
   - Điều hướng người dùng (`App.jsx`), bảo vệ tuyến (`Route Guards`), màn hình UI và các cuộc gọi API thực tế (`api.js`, `vetApi.js`).
   - Cho biết khả năng nào của Backend thực sự đã được đưa ra giao diện người dùng.
3. **Nguồn Tham chiếu & Ngữ cảnh Lịch sử (Reference Source):**
   - `README.md`, `BUSINESS_DECISIONS_PENDING.md`, `CONVERSATION_HISTORY.md`, `FULL_RBAC_FLOW_AUDIT.md`, `PROJECT_COMPREHENSIVE_SUMMARY.md`.
   - Được sử dụng để nắm bắt bối cảnh và ý đồ thiết kế ban đầu; **không được coi là chân lý nếu xung đột với mã nguồn đang chạy**.

---

## 3. Quy Ước Nhãn Trạng Thái Triển Khai (Implementation Status Labels)

Mọi tính năng, luồng nghiệp vụ và API đều được gắn nhãn thống nhất:

| Nhãn Trạng Thái | Ý Nghĩa Kỹ Thuật |
|:---|:---|
| `IMPLEMENTED` | Tính năng đã được hiện thực hóa hoàn chỉnh cả Backend lẫn Frontend, hoạt động thông suốt từ UI tới Database. |
| `PARTIAL` | Tính năng mới chỉ hoàn thành một phần luồng nghiệp vụ (ví dụ: tạo được nhưng chưa có giao diện sửa/hủy, hoặc thiếu một số bước chuyển trạng thái). |
| `BACKEND_ONLY` | Backend đã có đầy đủ Controller, Service, Database logic nhưng Frontend chưa xây dựng giao diện tương ứng (người dùng không thao tác được qua UI thông thường). |
| `UI_ONLY_OR_UNWIRED` | Frontend đã dựng giao diện/nút bấm nhưng chưa nối API Backend thật, hoặc gọi endpoint giả lập/chưa tồn tại. |
| `SUSPECTED_BUG` | Phát hiện logic mã nguồn có dấu hiệu bất thường, mâu thuẫn điều kiện hoặc gây lỗi runtime tiềm ẩn, cần xác nhận lại ý đồ kỹ thuật. |
| `NEEDS_REVIEW` | Quy tắc nghiệp vụ đang chạy nhưng chưa rõ có đúng với định hướng kinh doanh của Chủ dự án hay không. |
| `NOT_FOUND` | Tính năng có trong tài liệu mô tả cũ nhưng không tìm thấy dấu vết mã nguồn trong repository hiện tại. |

---

## 4. Cấu Trúc Tài Liệu Trong `BUSINESS_DOCS/`

```text
BUSINESS_DOCS/
├── README.md                      # Tài liệu chỉ dẫn này
├── 00_PROJECT_CONTEXT.md          # Tổng quan hệ thống, kiến trúc nghiệp vụ & 5 vai trò
├── roles/                         # Chi tiết 5 vai trò người dùng trong hệ thống
│   ├── ROLE_CLUB_MANAGER.md       # Quản lý Câu lạc bộ (CM)
│   ├── ROLE_HEAD_TRAINER.md       # Huấn luyện viên trưởng (HT)
│   ├── ROLE_STABLE_STAFF.md       # Nhân viên chăm sóc chuồng (SS)
│   ├── ROLE_HORSE_OWNER.md        # Chủ sở hữu ngựa (HO)
│   └── ROLE_VETERINARIAN.md       # Bác sĩ thú y (VT)
├── workflows/
│   └── BUSINESS_WORKFLOWS.md      # Đặc tả 8 luồng nghiệp vụ xuyên vai trò (End-to-End)
├── reviews/
│   ├── CODE_BUSINESS_GAPS.md      # Báo cáo sai lệch giữa Code và Tài liệu cũ, lỗi tiềm ẩn
│   └── OPEN_QUESTIONS.md          # Các câu hỏi nghiệp vụ cần Chủ dự án quyết định
└── technical/
    ├── DATA_MODEL.md              # Mô hình dữ liệu thực tế, bảng, trường JSON & Triggers
    └── API_CATALOG.md             # Danh mục API chi tiết theo phân hệ kèm quyền truy cập
```

---

## 5. Mối Quan Hệ Giữa Tài Liệu Vai Trò và Luồng Nghiệp Vụ

- **Tài liệu Vai trò (`roles/ROLE_*.md`):** Mô tả hệ thống dưới góc nhìn của từng nhóm người dùng: Quyền hạn, màn hình được vào, tính năng họ chủ động thực hiện, yêu cầu họ gửi đi và công việc họ nhận về.
- **Tài liệu Luồng Nghiệp vụ (`workflows/BUSINESS_WORKFLOWS.md`):** Định nghĩa chuẩn mực duy nhất (Canonical Definition) cho các luồng nghiệp vụ phức tạp đi qua nhiều vai trò (ví dụ: *Quy trình Tiếp nhận & Nhập chuồng*, *Quy trình Huấn luyện & Đánh giá*, *Quy trình Đăng ký Đua*). Các tài liệu vai trò sẽ tham chiếu chéo về các luồng này thay vì định nghĩa lại một cách rời rạc.

---

## 6. Thứ Tự Đọc Khuyến Nghị Dành Cho Chủ Dự Án (Project Owner)

Để thẩm định tài liệu nhanh chóng và hiệu quả nhất, khuyến nghị duyệt theo lộ trình sau:

1. **Bước 1 — Bức tranh tổng thể:** Đọc [00_PROJECT_CONTEXT.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/00_PROJECT_CONTEXT.md) để nắm bắt phạm vi, kiến trúc phân quyền và các nguyên tắc cốt lõi của hệ thống.
2. **Bước 2 — Trải nghiệm theo từng vai trò:** Đọc lần lượt 5 tài liệu vai trò trong thư mục `roles/` để kiểm tra quyền hạn và thao tác của từng nhân sự có khớp với vận hành thực tế tại câu lạc bộ hay không:
   - [ROLE_CLUB_MANAGER.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/roles/ROLE_CLUB_MANAGER.md)
   - [ROLE_HEAD_TRAINER.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/roles/ROLE_HEAD_TRAINER.md)
   - [ROLE_STABLE_STAFF.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/roles/ROLE_STABLE_STAFF.md)
   - [ROLE_HORSE_OWNER.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/roles/ROLE_HORSE_OWNER.md)
   - [ROLE_VETERINARIAN.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/roles/ROLE_VETERINARIAN.md)
3. **Bước 3 — Luồng tương tác xuyên vai trò:** Đọc [BUSINESS_WORKFLOWS.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/workflows/BUSINESS_WORKFLOWS.md) để đánh giá tính liền mạch khi các vai trò phối hợp làm việc (Huấn luyện viên lên lịch → Nhân viên chuẩn bị → Bác sĩ theo dõi → Huấn luyện viên đánh giá).
4. **Bước 4 — Thẩm định sai lệch & Quyết định:** Đọc [CODE_BUSINESS_GAPS.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/reviews/CODE_BUSINESS_GAPS.md) và [OPEN_QUESTIONS.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/reviews/OPEN_QUESTIONS.md) để đưa ra câu trả lời cho các vấn đề đang bỏ ngỏ trước khi chốt SRS.
5. **Bước 5 — Tham khảo kỹ thuật:** Đọc [DATA_MODEL.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/technical/DATA_MODEL.md) và [API_CATALOG.md](file:///c:/DEV/RACE%20HORSE/HORSE-PROJECT/BUSINESS_DOCS/technical/API_CATALOG.md) khi cần đối chiếu cấu trúc dữ liệu hoặc giao tiếp kỹ thuật.
