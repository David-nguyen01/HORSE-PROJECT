# TÀI LIỆU KHẢO SÁT & ĐÁNH GIÁ FRONTEND (FE RECONNAISSANCE)

**Dự án:** Racehorse Training & Management System (RTMS / Stride)  
**Phạm vi khảo sát:** Toàn bộ thư mục `FRONTEND/` (READ-ONLY)  
**Mục tiêu:** Đánh giá mức độ hoàn thiện, ngôn ngữ thiết kế (visual style), tính nhất quán của design system hiện tại để phục vụ triển khai **Flow 4 — Stable Care & Nutrition (Groom / Stable Hand)**.  
**Ngày thực hiện:** 25/09/2026  

---

## 1. TỔNG QUAN CÔNG NGHỆ & THƯ VIỆN (TECH STACK)

| Thành phần | Công nghệ / Thư viện | Phiên bản | Ghi chú & Mục đích sử dụng |
| :--- | :--- | :--- | :--- |
| **Core Framework** | React | `^19.3.0` | React 19 mới nhất (`react`, `react-dom`) |
| **Build Tool & Dev Server** | Vite | `^8.2.2` | `@vitejs/plugin-react: ^6.1.1`, proxy cấu hình `/api` & `/actuator` về port `8080` |
| **Routing** | React Router DOM | `^7.18.3` | SPA client-side routing, hooks (`useNavigate`, `useParams`, `useLocation`) |
| **Icon Set** | Lucide React | `^1.44.0` | Bộ icon SVG hiện đại, đồng nhất xuyên suốt toàn bộ ứng dụng |
| **Data Visualization** | Recharts | `^3.3.0` | Biểu đồ đường (`LineChart`), biểu đồ cột (`BarChart`) cho thể lực |
| **Export Utilities** | html2canvas + jsPDF | `^1.4.1` / `^4.2.1` | Chụp canvas và xuất báo cáo PDF / PNG trực tiếp trên trình duyệt |
| **Styling Solution** | Vanilla CSS + CSS Variables | N/A | **Không sử dụng Tailwind CSS, Bootstrap, MUI hay Ant Design**. Hệ thống dùng 1 file CSS nguyên khối (`src/styles.css` ~23.7 KB) tự xây dựng hệ thống design tokens và class BEM/hybrid |

---

## 2. CẤU TRÚC THƯ MỤC & FILE (PROJECT ARCHITECTURE)

Thư mục `FRONTEND/src/` có cấu trúc phẳng (flat structure) với 5 file cốt lõi:

```text
FRONTEND/
├── index.html                  # HTML shell, title "Stride — Racehorse Club", theme-color #113f32
├── package.json                # Dependencies khai báo
├── vite.config.js              # Cấu hình proxy đến backend localhost:8080
└── src/
    ├── main.jsx                # Entry point, bọc BrowserRouter và load styles.css
    ├── styles.css              # File CSS chứa toàn bộ design system, biến theme, animation và responsive (23.7 KB)
    ├── api.js                  # HTTP client (fetch), lưu trữ auth session, tự động refresh JWT token
    ├── App.jsx                 # Component trung tâm chứa AuthProvider, AppLayout, Navbar, Topbar và hầu hết các trang
    └── FitnessPage.jsx         # Trang Dashboard thể lực (FR03/FR07), được load lazy qua React.lazy()
```

*Nhận xét cấu trúc:* Hiện tại dự án chưa chia nhỏ ra các thư mục con như `components/`, `pages/`, `hooks/`. Tuy nhiên, các component bên trong `App.jsx` và `FitnessPage.jsx` được phân tách dạng modular function rất rõ ràng và dễ bóc tách khi cần.

---

## 3. HỆ THỐNG ĐIỀU HƯỚNG & CÁC TRANG HIỆN CÓ (ROUTING & PAGES)

Toàn bộ routing được định nghĩa trong `App.jsx`:

1. **Nhóm Public Routes (Xác thực):**
   * `/login`: Cổng đăng nhập (`AuthShell mode="login"`).
   * `/register`: Cổng đăng ký tài khoản với chọn vai trò (`AuthShell mode="register"`).
2. **Nhóm Protected Routes (Bảo vệ bởi `Protected` & `AppLayout`):**
   * `/`: **Dashboard Tổng quan** — Hiển thị thẻ thống kê câu lạc bộ, danh sách ngựa cập nhật gần đây, thông tin phiên làm việc vai trò.
   * `/horses`: **Danh mục hồ sơ ngựa (FR01)** — Tìm kiếm từ khóa, lọc trạng thái, modal tạo mới hồ sơ ngựa.
   * `/horses/:id`: **Chi tiết hồ sơ ngựa (FR01, FR02, FR07)** — Hero banner, upload file ảnh/PDF lên Cloudinary, định danh chi tiết, chủ sở hữu và tỷ lệ sở hữu, cây phả hệ 3 thế hệ, lịch sử đua, lịch sử đánh giá buổi tập.
   * `/fitness`: **Dashboard Thể lực & Buổi tập (FR03, FR07)** — Lọc thời gian/trạng thái, 3 biểu đồ Recharts, bảng dữ liệu buổi tập gốc, modal nhập đánh giá buổi tập, xuất báo cáo PDF/ảnh.
   * `/users`: **Quản trị người dùng & phân quyền (FR20, FR21)** — Danh sách tài khoản, hiển thị vai trò dạng pill, modal phân quyền vai trò.
3. **Hiện trạng Flow 4:** **Chưa có route nào dành cho Flow 4 (Chuồng, Lịch sinh hoạt, Dinh dưỡng, Chăm sóc, Sự cố, Kho vật tư)** trên Frontend.

---

## 4. BẢN SẮC THIẾT KẾ & HỆ THỐNG GIAO DIỆN (DESIGN SYSTEM & VISUAL STYLE)

### 4.1. Phong cách hình ảnh (Visual Style)
* **Phong cách:** **Equestrian Heritage Club / Modern Country Sports Luxury** (Câu lạc bộ ngựa đua quý tộc phong cách Anh cổ điển kết hợp công nghệ hiện đại).
* **Đặc trưng:**
  - Gam màu xanh ngọc bích đậm (Racing Green) sang trọng phối cùng màu vôi dạ quang (Electric Lime) hiện đại.
  - Phối hợp giữa nghệ thuật chữ Serif cổ điển (Playfair Display) cho các tiêu đề và Sans-serif hình học (DM Sans) cho bảng biểu dữ liệu.
  - Khối thẻ (Card) bo góc lớn, viền thanh mảnh, nền giấy ấm (warm cream paper `#f4f5ef`), hiệu ứng kính mờ (frosted glass blur) trên Topbar.

### 4.2. Bảng màu & Design Tokens (`src/styles.css`)
* **Màu thương hiệu & nền:**
  - `--green: #123f32`: Màu xanh đua ngựa chủ đạo (Sidebar, nút bấm chính, card nổi bật).
  - `--green-2: #1d5c49`: Màu xanh phụ khi hover.
  - `--lime: #c9f17c`: Màu điểm nhấn thương hiệu (Brand mark, menu active, số liệu nổi bật).
  - Nền trang (Body background): `#f4f5ef` (Màu kem giấy ấm).
  - Nền thẻ (Card background): `#ffffff`.
  - Nền panel form / modal: `#f8f8f4` / `#f9faf6`.
  - Đường viền (Border line): `--line: #dfe4da` / `#edf0eb`.
* **Màu trạng thái nghiệp vụ (Status & Health Badges):**
  - **Active / Xanh lá (Fit / Đang hoạt động):** Text `#27704e` / Nền `#e9f5ed` / Dot `#48a66c`.
  - **Resting / Vàng hổ phách (Monitoring / Nghỉ ngơi):** Text `#a87517` / Nền `#fff5db` / Dot `#dcae47`.
  - **Injured / Đỏ hồng (Injured / Chấn thương / Sự cố):** Text `#ad5047` / Nền `#fff0ee` / Dot `#d3665d`.
  - **Retired / Xám (Nghỉ hưu / Khóa):** Text `#718078` / Nền `#edf0ec` / Dot `#8b9290`.
  - **Info / Xanh dương:** Text `#426f9b` / Nền `#eaf2fa`.

### 4.3. Typography
* **Tiêu đề chính, tiêu đề Modal, con số lớn:** `'Playfair Display', serif` (font weight 700).
* **Văn bản hiển thị, label, bảng, nút bấm:** `'DM Sans', sans-serif` (font weight 400, 500, 600, 700).
* **Eyebrow:** Chữ in hoa nhỏ có khoảng cách ký tự rộng (`letter-spacing: .19em`, font size 10-11px).

---

## 5. BỘ COMPONENT CÓ THỂ TÁI SỬ DỤNG (REUSABLE COMPONENTS)

Mặc dù được viết trong cùng file, các component mẫu sau đây đã hoàn chỉnh và sẵn sàng để tái sử dụng trực tiếp cho Flow 4:

1. **`AppLayout` (Shell Layout):**
   - Sidebar cố định 258px, responsive drawer trên màn hình nhỏ.
   - User chip hiển thị avatar viết tắt (`initials`), tên tài khoản và vai trò.
   - Nút đăng xuất tích hợp gọi API logout.
2. **`TopBar` & `NotificationBell`:**
   - Trạng thái hệ thống (đèn xanh nhấp nháy).
   - Chuông thông báo thời gian thực tích hợp polling tự động mỗi 30s với backend `/api/notifications`, badge số lượng chưa đọc, panel dropdown hiển thị mức độ ưu tiên theo màu (`.notification-priority.high/medium/low`). Rất phù hợp để nhận tức thời Báo cáo sự cố khẩn cấp (FR16).
3. **`PageHeading`:**
   - Cung cấp tiêu đề trang chuẩn hóa gồm: `eyebrow` (nhãn FR), `title`, `description`, và cụm nút thao tác `action` (như "Thêm chuồng", "Lập khẩu phần", "Báo cáo sự cố").
4. **`Stat` (Thẻ thống kê nhanh):**
   - Hiển thị các chỉ số tổng quan với 4 accent màu (green, gold, rose, blue).
5. **`Modal` & `Field`:**
   - `Modal`: Nền tối mờ (`backdrop-filter: blur(4px)`), tiêu đề, nút đóng X, tự động ngăn chặn propagation click.
   - `Field`: Wrapper chuẩn cho `<input>`, `<select>`, `<textarea>` kèm label rõ ràng và style focus xanh ngọc.
6. **`Empty`:**
   - Khung trống thân thiện kèm icon Sparkles khi chưa có dữ liệu.
7. **`Status`:**
   - Huy hiệu trạng thái với chấm tròn màu hiển thị trạng thái ngựa, nhiệm vụ hoặc vật tư.
8. **Toolbar & Data Table:**
   - `.toolbar`: Thanh tìm kiếm (`.search-box`) và bộ lọc dropdown (`<select>`).
   - `.data-table` / `.horse-list`: Cấu trúc danh sách dòng thẻ hoặc bảng có phân cột chuẩn xác.

---

## 6. ĐÁNH GIÁ CHI TIẾT TỪNG MÀN HÌNH FLOW 4 THEO DESIGN SYSTEM HIỆN TẠI

| Chức năng Flow 4 | UI/UX Yêu Cầu | Khả Năng Tái Sử Dụng Từ Frontend Hiện Có | Kết Luận Reference |
| :--- | :--- | :--- | :---: |
| **FR13: Bản đồ Chuồng & Lịch Sinh Hoạt** | Sơ đồ lưới các ô chuồng (mã ô, tên ngựa, badge sức khỏe, trạng thái trống/có ngựa); Danh sách lịch sinh hoạt phi tập luyện theo giờ (thả bãi, chải lông, nghỉ) | - Lưới ô chuồng: Tái sử dụng trực tiếp class `.stats-grid` hoặc `.fitness-horses` kết hợp `.stat-icon` và `.status`.<br>- Lịch sinh hoạt: Tái sử dụng cấu trúc dòng của `.training-history-row` hoặc `.horse-row`.<br>- Modal thêm hoạt động: Tái sử dụng `Modal` + `Field`. | **ĐỦ REFERENCE** |
| **FR14: Khẩu Phần Ăn & Checklist Hôm Nay** | Bảng lập khẩu phần ăn theo từng bữa (sáng, trưa, tối); Checklist bữa ăn cho Groom kèm định lượng thức ăn tinh, thô xanh, bổ sung | - Danh sách bữa ăn: Tái sử dụng cấu trúc thẻ `.card` và dòng `.table-row`.<br>- Bảng checklist: Dùng checkbox chuẩn như trong `RoleModal` kết hợp thời gian cho ăn. | **ĐỦ REFERENCE** |
| **FR15: Nhiệm Vụ Chăm Sóc & Bằng Chứng** | Danh sách việc hằng ngày (dọn chuồng, cho ăn, tắm, ngâm đá); Nút bấm hoàn thành; Đánh dấu quá hạn đỏ (`isOverdue`); Modal đính kèm ảnh & ghi chú | - Checklist nhiệm vụ: Tái sử dụng layout danh sách `.horse-list` với nút check hoàn thành.<br>- Đánh dấu quá hạn: Dùng badge màu đỏ `.fitness-badge.red` hoặc icon đỏ `.icon-button.danger`.<br>- Upload ảnh bằng chứng: Tái sử dụng chính xác input file upload đã làm ở `HorseDetail` (dòng 207 của `App.jsx`). | **ĐỦ REFERENCE** |
| **FR16: Báo Cáo Sự Cố Khẩn Cấp (1-Touch)** | Nút bấm nhanh báo cáo sự cố; Modal chọn ngựa, triệu chứng (colic, sốt, bỏ ăn...), mức độ khẩn cấp (`CRITICAL`, `HIGH`), ghi chú và ảnh đính kèm | - Nút khẩn cấp: Tái sử dụng `.button.danger-outline` hoặc nút bấm nổi bật màu rose/red.<br>- Form sự cố: Tái sử dụng `Modal` với `form-grid` tương tự `EvaluationModal` (FR07).<br>- Tự động thông báo: `NotificationBell` trên topbar đã có sẵn logic nhận notification priority `HIGH`. | **ĐỦ REFERENCE** |
| **FR17: Quản Lý Tồn Kho & Đề Xuất Bổ Sung** | Bảng danh mục thức ăn/thuốc/dụng cụ, mức tồn kho, cảnh báo tồn thấp màu vàng/đỏ; Modal gửi đề xuất bổ sung vật tư; Hàng đợi duyệt của Quản lý CLB | - Bảng danh mục kho: Tái sử dụng cấu trúc `.data-table` của trang `Users` (`.table-header`, `.table-row`, `.role-pills`).<br>- Cảnh báo tồn thấp: Dùng badge `.fitness-badge.yellow` / `.red`.<br>- Nút duyệt đề xuất: Tái sử dụng `.button.primary` và `.button.secondary`. | **ĐỦ REFERENCE** |

---

## 7. TRẢ LỜI CÁC CÂU HỎI TRỌNG TÂM

### 1. Visual style hiện tại của project là gì?
Dự án sở hữu phong cách **Equestrian Heritage Club / Modern Country Sports Luxury**. Tông màu chủ đạo là xanh đua ngựa truyền thống (`#123f32`), xanh dạ quang tạo điểm nhấn công nghệ (`#c9f17c`), kết hợp nghệ thuật chữ cổ điển `Playfair Display` và thẻ UI hiện đại. Đây là một phong cách rất độc đáo, hoàn toàn phù hợp với hình ảnh một trang trại đua ngựa chuyên nghiệp cao cấp.

### 2. Có design system / component pattern đủ nhất quán để làm Flow 4 theo không?
**CÓ, HOÀN TOÀN ĐỦ VÀ RẤT NHẤT QUÁN.**  
Toàn bộ form controls, buttons, modals, table rows, stat cards, notification dropdown, typography và layout responsive đã được xây dựng hoàn thiện trong `styles.css`. Mọi màn hình từ Flow 1 (Horses), Flow 2 (Fitness) và User Management đều tuân thủ chặt chẽ design system này mà không cần bổ sung bất kỳ thư viện UI bên ngoài nào.

### 3. Những component / layout nào Flow 4 nên reuse?
* **Khung tổng thể:** `AppLayout`, `topbar`, `sidebar`, `NotificationBell`.
* **Tiêu đề trang & thống kê:** `PageHeading`, `Stat`, `stats-grid`.
* **Bộ lọc & công cụ:** `toolbar`, `search-box`, `<select>`, `<input type="date">`.
* **Hộp thoại (Modal) & Form:** `Modal`, `Field`, `form-grid`, `modal-actions`.
* **Bảng biểu & danh sách:** `.data-table`, `.horse-list`, `.table-row`, `.role-pills`.
* **Huy hiệu & cảnh báo:** `.status`, `.fitness-badge`, `.notice.error`, `.notice.success`.
* **Upload ảnh:** Pattern `<label className="button"><input hidden type="file" ... /></label>` đang dùng ở `HorseDetail`.

### 4. Có thiếu thông tin UI/UX quan trọng đến mức cần tìm thêm screenshot / reference bên ngoài không?
**HOÀN TOÀN KHÔNG THIẾU.**  
Tất cả các thành phần UI của Flow 4 (chuồng, khẩu phần, checklist, báo cáo sự cố, kho vật tư) đều là các biến thể hiển thị của Form, Card Grid, Table, Modal và Upload — những thành phần đã được triển khai xuất sắc trên các trang hiện hữu (`Horses`, `HorseDetail`, `FitnessPage`, `Users`). Việc tìm kiếm screenshot bên ngoài là **không cần thiết** và dễ làm gãy tính đồng nhất về phong cách Racing Green mà dự án đang xây dựng rất tốt.

---

## 8. KẾT LUẬN CUỐI CÙNG

# **ĐỦ REFERENCE → KHÔNG CẦN ẢNH NGOÀI**

Frontend hiện tại có đầy đủ cơ sở mẫu mã giao diện, màu sắc, font chữ, layout responsive và các khối component để đội ngũ phát triển tiến hành implement giao diện cho **Flow 4 — Stable Care & Nutrition (FR13 – FR17)** mà không cần tìm kiếm thêm tài liệu visual reference bên ngoài.
