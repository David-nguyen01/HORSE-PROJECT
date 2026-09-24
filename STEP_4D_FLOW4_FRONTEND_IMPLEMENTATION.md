# STEP 4D — FRONTEND FLOW 4 IMPLEMENTATION REPORT
**Dự án**: Quản lý Câu lạc bộ Huấn luyện Ngựa đua (Racehorse Club Management System)  
**Phạm vi**: Frontend Flow 4 — Stable Care & Nutrition (FR13 – FR17) dành cho Groom / Stable Hand  
**Role trọng tâm**: `STABLE_STAFF` (Groom / Stable Hand), phối hợp cùng `HEAD_TRAINER` và `CLUB_MANAGER`  
**Ngày thực hiện**: 25/09/2026  
**Trạng thái Build**: **SUCCESS (Vite v8.2.2 - 0 errors, built in 578ms)**  

---

## 1. TỔNG QUAN THỰC HIỆN

Dựa trên yêu cầu của **STEP 4D**, chúng tôi đã hiện thực hóa toàn bộ giao diện Frontend cho **Flow 4 — Quản lý Chăm sóc Chuồng & Dinh dưỡng (FR13–FR17)**, tuân thủ tuyệt đối:
1. **SRS chính thức v1.0** trong workspace.
2. **Design System & Visual Style hiện hữu**:
   - Tông màu: *British Racing Green / Equestrian Heritage* (`--green: #123f32`, `--lime: #c9f17c`, `--bg: #f4f5ef`, `--surface: #ffffff`, `--gold: #b38e44`).
   - Typography: *Playfair Display* cho tiêu đề/display, *DM Sans* cho nội dung giao diện.
   - Không đưa vào bất kỳ thư viện UI bên ngoài nào (No Tailwind, no Bootstrap, no MUI, no AntD).
3. **100% Real API Backend**: Kết nối trực tiếp vào các controller Backend vừa hoàn thiện tại STEP 4B (`/api/stables`, `/api/routines`, `/api/nutrition-plans`, `/api/care-tasks`, `/api/incidents`, `/api/inventory`). Không dùng dữ liệu mock cục bộ.
4. **Phân quyền và điều hướng RBAC**:
   - Người dùng có role `STABLE_STAFF` khi đăng nhập sẽ tự động được chuyển hướng (redirect) vào `/stable-care`.
   - Route `/stable-care` và `/stable-care/:tab` được bảo vệ chặt chẽ, chỉ cho phép `STABLE_STAFF`, `HEAD_TRAINER`, `CLUB_MANAGER`. Role `HORSE_OWNER` hoàn toàn không có quyền truy cập.

---

## 2. DANH MỤC CÁC TẬP TIN ĐÃ TẠO VÀ CHỈNH SỬA

| STT | Tập tin | Hành động | Nội dung chi tiết |
|---|---|---|---|
| 1 | `FRONTEND/src/api.js` | **Cập nhật** | Bổ sung module `stableCareApi` với đầy đủ endpoints cho Stables, Daily Routines, Nutrition Plans, Care Tasks, Incidents và Inventory. |
| 2 | `FRONTEND/src/styles.css` | **Cập nhật** | Bổ sung các CSS class cho Flow 4 (`.flow4-tabs`, `.flow4-tab-btn`, `.tab-badge`, `.stall-grid`, `.stall-card`, `.emergency-banner`, `.severity-pill`, `.checklist-card`, `.sub-tabs`, `.sub-tab-btn`) tái sử dụng biến màu CSS hiện hữu. |
| 3 | `FRONTEND/src/StableCarePage.jsx` | **Tạo mới** | Component chính gồm 5 Tabs tương ứng FR13, FR14, FR15, FR16, FR17, tích hợp modal thao tác nhanh cho Groom và Trainer/Manager. |
| 4 | `FRONTEND/src/App.jsx` | **Cập nhật** | Bổ sung Lazy Loading cho `StableCarePage`, đăng ký route `/stable-care`, thêm icon/menu "Chăm sóc chuồng" trên Sidebar cho các role hợp lệ, cấu hình redirect sau login và tại Dashboard cho Groom. |

---

## 3. CHI TIẾT TRIỂN KHAI THEO TỪNG YÊU CẦU (FR13 – FR17)

### FR13 — Sơ đồ chuồng & Lịch sinh hoạt hằng ngày (Stalls & Daily Routine)
- **Sơ đồ chuồng trực quan (Stall Grid)**:
  - Hiển thị danh sách chuồng (`stallLocation`), tên ngựa, mã ngựa, giống, và badge trạng thái sức khỏe/vận động (`ACTIVE`, `INJURED`, `RESTING`, `RETIRED`).
  - Cho phép groom lọc nhanh theo từ khóa vị trí hoặc tên ngựa.
- **Lịch sinh hoạt cố định (Daily Routines Timeline)**:
  - Hiển thị thời gian biểu các hoạt động **phi tập luyện** theo trình tự thời gian (06:00 Ăn sáng, 08:00 Tắm chải, 12:00 Nghỉ ngơi & bổ sung nước, 17:00 Ăn tối, 19:00 Kiểm tra chuồng đêm).
  - Tách biệt rõ ràng với buổi tập của huấn luyện viên (HLV).
  - **RBAC**: Nút "Thêm lịch sinh hoạt" chỉ hiển thị cho `HEAD_TRAINER` và `CLUB_MANAGER`. Groom xem để thực hiện.

### FR14 — Khẩu phần dinh dưỡng & Kế hoạch ăn uống (Nutrition & Feeding Plan)
- **Bộ chọn ngựa & Kế hoạch hiện hành**:
  - Groom chọn ngựa để xem khẩu phần chi tiết: Lượng cỏ khô (kg), ngũ cốc/cám (kg), chất bổ sung vi lượng (supplements), số bữa/ngày và hướng dẫn đặc biệt.
  - Hiển thị Version kế hoạch (`v1`, `v2`...) và ngày hiệu lực (`effectiveDate`).
- **Checklist cho ăn hằng ngày của Groom (Daily Feeding Checklist)**:
  - 4 cữ ăn tiêu chuẩn (Sáng, Trưa, Chiều, Tối). Groom bấm tick hoàn thành theo thời gian thực (lưu trạng thái phiên trong ngày).
- **Lịch sử phiên bản (Version History Modal)**:
  - Cho phép tra cứu các phiên bản dinh dưỡng trước đó để đối chiếu khi ngựa thay đổi chế độ ăn.
- **RBAC**: Nút "Tạo kế hoạch dinh dưỡng" chỉ mở cho `HEAD_TRAINER`, `CLUB_MANAGER` và `VETERINARIAN`.

### FR15 — Nhiệm vụ chăm sóc hằng ngày & Checklist (Daily Care Tasks & Checklist)
- **Bộ lọc & Phân loại**:
  - Lọc theo ngày (Date picker), theo trạng thái (`PENDING`, `COMPLETED`, `CANCELLED`) và lọc nhanh "Cần làm ngay" / "Đã hoàn thành".
- **Tính toán quá hạn (Overdue Calculation)**:
  - Nhiệm vụ chưa hoàn thành có `dueDate` trước ngày hiện tại sẽ được đánh dấu badge **"Quá hạn"** màu đỏ nổi bật.
- **Quy trình hoàn thành của Groom (Complete Task Modal)**:
  - Groom bấm "Hoàn tất nhiệm vụ", mở modal cho phép:
    1. Nhập ghi chú thực tế (`completionNotes`) về biểu hiện của ngựa.
    2. Đính kèm link ảnh minh chứng (`photoUrl`) nếu có.
- **RBAC**: Nút "Giao nhiệm vụ" chỉ mở cho `HEAD_TRAINER` và `CLUB_MANAGER`. Groom chỉ xem và thực hiện các nhiệm vụ được giao.

### FR16 — Báo cáo sự cố khẩn cấp (Emergency Incident Report)
- **Banner khẩn cấp 1-chạm (1-Touch Emergency Trigger)**:
  - Đặt ngay đầu giao diện với nút nổi bật: **"Báo cáo sự cố khẩn cấp"** kèm icon cảnh báo.
- **Modal báo cáo sự cố (Report Incident Modal)**:
  - Bắt buộc chọn ngựa và loại sự cố thường gặp theo SRS: `COLIC` (Đau bụng / chướng hơi), `FEVER` (Sốt cao), `OFF_FEED` (Bỏ ăn đột ngột), `INJURY_HOOF` (Chấn thương móng), `INJURY_LEG` (Chấn thương chân), `OTHER`.
  - Mức độ nghiêm trọng trực quan: `LOW` (Thấp), `MEDIUM` (Vừa), `HIGH` (Nghiêm trọng), `CRITICAL` (Nguy kịch).
  - Ghi chú mô tả triệu chứng và trường nhập ảnh hiện trường (`photoUrl`).
- **Nhật ký & Tiến trình xử lý (Incident Progression Log)**:
  - Bảng theo dõi toàn bộ sự cố với các trạng thái: `REPORTED` -> `UNDER_REVIEW` -> `RESOLVED`.
  - Nút xử lý chuyển trạng thái cho Quản lý / Bác sĩ kèm modal nhập ghi chú giải quyết (`resolutionNotes`).

### FR17 — Theo dõi tồn kho & Đề xuất bổ sung (Inventory & Replenishment)
- **2 Phân hệ con (Sub-tabs)**:
  1. *Danh mục vật tư (Stock Levels)*:
     - Danh sách thức ăn, thực phẩm bổ sung, thuốc men, đệm lót chuồng.
     - Cảnh báo trực quan (Badge đỏ **"Sắp hết hàng"**) khi số lượng tồn kho `quantityOnHand <= reorderThreshold`.
     - Groom có thể bấm nhanh nút "Đề xuất bổ sung" ngay tại dòng vật tư sắp hết.
  2. *Đề xuất bổ sung (Replenishment Proposals)*:
     - Danh sách các đề xuất với trạng thái `PENDING`, `APPROVED`, `REJECTED`.
     - **RBAC**:
       - Groom: Tạo đề xuất (chọn vật tư, số lượng đề xuất, lý do).
       - Quản lý (`CLUB_MANAGER`): Có các nút Duyệt (**Duyệt**) hoặc Từ chối (**Từ chối**). Khi duyệt, hệ thống tự động cộng dồn số lượng vào kho vật tư.

---

## 4. KẾT QUẢ XÁC MINH & BUILD

### Kết quả kiểm tra biên dịch Frontend:
```text
> racehorse-club-frontend@0.1.0 build
> vite build

vite v8.2.2 building client environment for production...
transforming...
✓ 2634 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                             0.65 kB │ gzip:   0.40 kB
dist/assets/index-B4TwvTav.css             25.91 kB │ gzip:   6.42 kB
dist/assets/rolldown-runtime-hePW80VL.js    0.71 kB │ gzip:   0.42 kB
dist/assets/purify.es-7fJ1DZ6H.js          26.92 kB │ gzip:  10.69 kB
dist/assets/StableCarePage-B4k_Qgdw.js     60.69 kB │ gzip:  13.22 kB
dist/assets/index.es--DfjJlCJ.js          151.41 kB │ gzip:  48.90 kB
dist/assets/html2canvas-Dn6bw578.js       199.49 kB │ gzip:  46.77 kB
dist/assets/index-CIkC08zw.js             301.66 kB │ gzip:  94.75 kB
dist/assets/FitnessPage-Bla5-o7i.js       386.95 kB │ gzip: 111.13 kB
dist/assets/jspdf.es.min-Dsarsmdu.js      399.06 kB │ gzip: 129.63 kB

✓ built in 578ms
```
- **Tình trạng lỗi**: 0 lỗi (Zero syntax / JSX / import errors).
- **Code splitting**: Trang `StableCarePage` được tách thành bundle riêng (`StableCarePage-B4k_Qgdw.js`) tối ưu tốc độ tải ban đầu của ứng dụng.

---

## 5. HƯỚNG DẪN KIỂM THỬ THỰC TẾ (USER HANDOFF)

### 1. Khởi chạy ứng dụng
```bash
# Terminal 1: Chạy Backend (Spring Boot)
cd "c:\DEV\RACE HORSE\HORSE-PROJECT\BACKEND"
mvn spring-boot:run

# Terminal 2: Chạy Frontend (Vite)
cd "c:\DEV\RACE HORSE\HORSE-PROJECT\FRONTEND"
npm run dev
```

### 2. Các kịch bản kiểm thử khuyến nghị
1. **Đăng nhập với Groom (`STABLE_STAFF`)**:
   - Đăng nhập tài khoản groom.
   - **Kỳ vọng**: Hệ thống tự động chuyển hướng vào màn hình `/stable-care` với tiêu đề "QUẢN LÝ CHĂM SÓC CHUỒNG & DINH DƯỠNG". Sidebar hiển thị mục "Chăm sóc chuồng" và "Tổng quan".
2. **Kiểm tra FR13 (Sơ đồ chuồng & Lịch sinh hoạt)**:
   - Chuyển tab 1. Xem danh sách chuồng ngựa và timeline lịch sinh hoạt cố định trong ngày.
3. **Kiểm tra FR14 (Dinh dưỡng & Checklist cho ăn)**:
   - Chuyển tab 2. Chọn một con ngựa, xem định lượng cỏ/cám/chất bổ sung. Tick chọn các cữ ăn trong ngày.
4. **Kiểm tra FR15 (Checklist nhiệm vụ chăm sóc)**:
   - Chuyển tab 3. Xem các nhiệm vụ chưa hoàn thành. Bấm "Hoàn tất", nhập ghi chú kiểm tra và lưu.
5. **Kiểm tra FR16 (Báo cáo khẩn cấp 1-chạm)**:
   - Chuyển tab 4 (hoặc bấm Banner khẩn cấp). Chọn ngựa nghi ngờ đau bụng (`COLIC`), mức độ `HIGH`, bấm "Gửi báo cáo sự cố". Sự cố xuất hiện ngay trên bảng nhật ký với cờ cảnh báo đỏ.
6. **Kiểm tra FR17 (Kho & Đề xuất bổ sung)**:
   - Chuyển tab 5. Xem vật tư có cảnh báo "Sắp hết hàng". Bấm "Đề xuất bổ sung" để gửi phiếu đề xuất.
   - Đăng nhập tài khoản `CLUB_MANAGER` để duyệt phiếu đề xuất và kiểm tra số lượng tồn kho tự động tăng lên.

---
**Trạng thái bàn giao**: `IMPLEMENTATION COMPLETE / COMPILATION BUILD PASS / READY FOR RUNTIME VALIDATION`.
