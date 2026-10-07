# BÁO CÁO TỔNG HỢP TOÀN DIỆN DỰ ÁN HORSE-PROJECT
## TIẾN TRÌNH THỰC HIỆN, CÁC TÍNH NĂNG ĐÃ HOÀN THÀNH & BẢN ĐỒ LUỒNG DỮ LIỆU (DATA FLOW)

> **Dự án:** Racehorse Training Management System (RTMS)  
> **Nhánh phát triển:** `bacsithuy` / `main`  
> **Cơ sở dữ liệu:** Microsoft Azure SQL Database (`racehorse-training-management.database.windows.net`) / Local MSSQL  
> **Phiên bản tài liệu:** 2.0 (Tổng hợp từ toàn bộ lịch sử hội thoại, các tài liệu kỹ thuật và mã nguồn thực tế)

---

## MỤC LỤC
1. [Lịch sử & Tiến trình Hội thoại Dự án (Conversation History)](#1-lịch-sử--tiến-trình-hội-thoại-dự-án)
2. [Tổng Hợp Các Phân Hệ & Chức Năng Đã Hoàn Thành](#2-tổng-hợp-các-phân-hệ--chức-năng-đã-hoàn-thành)
   - [Flow 1: Hồ sơ Gốc của Ngựa & Phả hệ (FR01, FR02)](#flow-1-hồ-sơ-gốc-của-ngựa--phả-hệ-fr01-fr02)
   - [Flow 2: Huấn luyện, Lập kế hoạch, Thể lực & Telemetry (FR03 – FR07)](#flow-2-huấn-luyện-lập-kế-hoạch-thể-lực--telemetry-fr03--fr07)
   - [Flow 3: Y tế, Mô hình 3D, Khóa tập & Chăm sóc phòng ngừa (FR08 – FR12)](#flow-3-y-tế-mô-hình-3d-khóa-tập--chăm-sóc-phòng-ngừa-fr08--fr12)
   - [Flow 4: Vận hành Chuồng trại, Dinh dưỡng & Kho vật tư (FR13 – FR17)](#flow-4-vận-hành-chuồng-trại-dinh-dưỡng--kho-vật-tư-fr13--fr17)
   - [Flow 5: Đăng ký Đua, Tài chính, Báo cáo & Quản trị RBAC (FR18 – FR23)](#flow-5-đăng-ký-đua-tài-chính-báo-cáo--quản-trị-rbac-fr18--fr23)
   - [Các tính năng nâng cao chuyên biệt (3D Three.js, Supervision Claiming, Auto-Pick Job)](#các-tính-năng-nâng-cao-chuyên-biệt)
3. [Duyệt Mã Nguồn & Kiến Trúc Luồng Dữ Liệu Chi Tiết (Data Flow)](#3-duyệt-mã-nguồn--kiến-trúc-luồng-dữ-liệu-chi-tiết)
   - [3.1. Luồng Xác thực & Phân quyền Kép (Dual RBAC Flow)](#31-luồng-xác-thực--phân-quyền-kép-dual-rbac-flow)
   - [3.2. Luồng Vòng đời Y tế: Tiếp nhận Điều trị & Khóa Huấn luyện](#32-luồng-vòng-đời-y-tế-tiếp-nhận-điều-trị--khóa-huấn-luyện)
   - [3.3. Luồng Đánh dấu Chấn thương Mô hình 3D (3D Anatomical Injury Tracking)](#33-luồng-đánh-dấu-chấn-thương-mô-hình-3d-3d-anatomical-injury-tracking)
   - [3.4. Luồng Phân tán Tự nhận ca & Cân bằng tải Giám sát Buổi tập (Supervision Claiming)](#34-luồng-phân-tán-tự-nhận-ca--cân-bằng-tải-giám-sát-buổi-tập)
   - [3.5. Luồng Báo cáo Sự cố & Xử lý Y tế Dashboard (Incident Lifecycle)](#35-luồng-báo-cáo-sự-cố--xử-lý-y-tế-dashboard)
   - [3.6. Luồng Lên Kế hoạch, Lịch tập & Đánh giá Buổi tập (Training Flow)](#36-luồng-lên-kế-hoạch-lịch-tập--đánh-giá-buổi-tập)
   - [3.7. Luồng Quản lý Chuồng, Dinh dưỡng & Đề xuất Kho (Stable Care & Inventory)](#37-luồng-quản-lý-chuồng-dinh-dưỡng--đề-xuất-kho)
   - [3.8. Luồng Đua, Báo cáo Chủ sở hữu & Sổ cái Tài chính (Racing & Finance)](#38-luồng-đua-báo-cáo-chủ-sở-hữu--sổ-cái-tài-chính)
   - [3.9. Luồng Ràng buộc Chéo & Triggers Toàn vẹn Dữ liệu (Cross-System Integrity)](#39-luồng-ràng-buộc-chéo--triggers-toàn-vẹn-dữ-liệu)
4. [Bảng Tổng Hợp API Backend & DTOs](#4-bảng-tổng-hợp-api-backend--dtos)
5. [Hiện Trạng Kiểm Thử & Triển Khai (Verification & Status)](#5-hiện-trạng-kiểm-thử--triển-khai)

---

## 1. LỊCH SỬ & TIẾN TRÌNH HỘI THOẠI DỰ ÁN

Qua toàn bộ dữ liệu lịch sử các phiên làm việc và các transcript hội thoại, dự án đã trải qua 5 giai đoạn phát triển chính:

### Giai đoạn 1: Khởi tạo Giao diện Mẫu & Thiết kế Khung (Early Phase)
- Bắt đầu từ các trang giao diện mẫu tĩnh (`FRONTEND/public/*.html` gồm `horses.html`, `fitness.html`, `health.html`, `training-plan.html`).
- Nghiên cứu mô hình 3D giải phẫu ngựa (da, cơ, xương) nhúng từ Sketchfab; sau đó tối ưu hóa chuyển sang sử dụng 1 file model 3D chuẩn `.glb` duy nhất lưu trong assets/public nhằm đạt hiệu năng render cao.
- Thử nghiệm các điểm đánh dấu chấn thương (đầu, cổ, chân trước, chân sau, vai, lưng, bụng, đùi) và kết nối ban đầu giữa giao diện và backend.

### Giai đoạn 2: Chuẩn hóa Monorepo, CSDL Microsoft SQL Server & Kiến trúc Backend
- Chuyển đổi mã nguồn giao diện sang **Single Page Application (SPA)** hiện đại sử dụng **React 19 + Vite + Recharts + Lucide-react**.
- Khởi tạo kiến trúc máy chủ **Spring Boot 4.x / Java 21**, tích hợp Spring Security JWT Token (kèm xoay vòng refresh token, thu hồi session).
- Thiết lập hệ thống cơ sở dữ liệu Microsoft SQL Server với 13 migration scripts (từ `00_create_database.sql` đến `12_compact_schema.sql`), chuẩn hóa cấu trúc thành **30 bảng cốt lõi** phục vụ vận hành.
- Kết nối trực tiếp máy chủ đám mây **Azure SQL Database** (`racehorse-training-management.database.windows.net`), loại bỏ hoàn toàn việc phụ thuộc vào DB local không đồng bộ.

### Giai đoạn 3: Kiểm toán Phân quyền RBAC & Tích hợp Luồng 4 (Flow 4 Integration)
- Thực hiện đợt kiểm toán phân quyền toàn diện (`FULL_RBAC_FLOW_AUDIT.md`) đối chiếu SRS, Backend Controller và Frontend Routing.
- Xử lý xung đột phân quyền của Quản lý CLB (`CLUB_MANAGER`): Sửa lỗi quyền review đề xuất vật tư kho tại `POST /api/inventory/replenishments/{id}/review` và điều chỉnh sidebar menu.
- Tích hợp các phân hệ của Flow 4 vào nhánh chính: Nhiệm vụ chăm sóc chuồng (`caretask`), Báo cáo sự cố khẩn cấp (`incident`), Kho vật tư (`inventory`), Khẩu phần dinh dưỡng (`nutrition`), Quản lý ảnh Cloudinary (`media`).
- Duy trì nghiêm ngặt các quy tắc nghiệp vụ: Giới hạn mỗi khu chuồng tối đa 4 ô chuồng (4 ngựa); cơ chế bảo lãnh thanh toán phí dịch vụ (`serviceOverride`); khóa chống deadlock `@Lock(PESSIMISTIC_WRITE)`; Unicode `@Nationalized`.

### Giai đoạn 4: Tái cấu trúc & Nâng cấp Toàn diện Bảng Điều Khiển Bác Sĩ Thú Y (`bacsithuy`)
- Tái cấu trúc toàn bộ giao diện `VetPage.jsx`: Xóa thanh tab thừa `flow4-tabs`, xây dựng Sidebar 6 phân hệ y tế chuyên sâu.
- **Tiền đề y tế (Medical Prerequisites):** Bắt buộc ngựa phải đóng phí (`PAID`) hoặc có bảo lãnh CLB mới được tiếp nhận khám; luồng chuyển giao rõ ràng: `PAID` $\rightarrow$ `IN_MEDICAL` $\rightarrow$ `PENDING_TRAINING` $\rightarrow$ `TRAINING`.
- **Tự động hóa Khóa Huấn luyện (FR11):** Bỏ trang thao tác thủ công, tự động khóa lịch tập khi phát hiện chấn thương (`INJURED`) và gửi thông báo ưu tiên cao tới Huấn luyện viên trưởng; tự động gỡ khóa khi thể trạng hồi phục (`FIT`).
- **Tích hợp Chăm sóc phòng ngừa vào Phiếu khám (FR12):** Cho phép đặt lịch tiêm vắc-xin, tẩy giun ngay trong modal tạo phiếu khám bệnh, tự động sinh chu kỳ lặp lại và bổ sung nút hủy/xóa lịch.
- **Vòng đời Báo cáo Sự cố từ nhân viên chuồng (FR16):** Bác sĩ có thể [Ghi nhận], [Bỏ qua], hoặc bấm [Tạo phiếu khám] – sự cố lập tức đổi trạng thái sang `ACKNOWLEDGED` và được giải phóng khỏi danh sách chờ trên Dashboard.
- **Chuẩn hóa giải phẫu học:** Phân nhóm 31 vùng cơ thể ngựa thành 5 nhóm có cấu trúc (`<optgroup>`).

### Giai đoạn 5: Tích hợp Mô hình 3D Three.js & Mô hình Phân tán Nhận ca Giám sát Buổi tập
- **Mô hình ngựa 3D Three.js (`HorseBodyModel3D.jsx`):**
  - Tích hợp Three.js, `@react-three/fiber` (R3F) và `@react-three/drei`.
  - Tải file model chuẩn `horse.glb` (181 KB), tự động scale và căn tâm `y = 0`.
  - Ánh xạ 31 tọa độ giải phẫu không gian thực `[x, y, z]` (`bodyRegionMap.js`).
  - Điểm đánh dấu chấn thương `InjuryMarker.jsx` có hiệu ứng nhịp đập (pulse animation), vòng hào quang phát sáng theo 4 cấp độ nghiêm trọng (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), tooltip HTML nổi hiển thị chẩn đoán chi tiết.
  - Tích hợp 2 chiều trong `VetPage.jsx` (tab Bệnh án & Chấn thương, tab Hồ sơ chi tiết ngựa) và cơ chế Fallback SVG khi thiết bị không hỗ trợ WebGL.
- **Phân tán Tự nhận ca Giám sát Huấn luyện (Training Supervision & Claiming):**
  - Cập nhật khung giờ vận hành sân tập **05:00 – 11:00**; khung giờ trực y tế lâm sàng **10:00 – 11:00**.
  - Bác sĩ Thú y chủ động tự nhận ca trước **23:00 ngày T-1** (tối đa 1 ca/ngày/bác sĩ, cơ chế khóa bi quan chống race-condition).
  - Cron Job `TrainingAutoPickJob` chạy tự động lúc **23:00 hàng ngày** theo múi giờ `Asia/Ho_Chi_Minh`, thuật toán cân bằng tải gán bác sĩ có ít ca nhất trong 30 ngày cho các phiên tập còn trống ngày hôm sau.
  - Giao diện đánh giá lâm sàng sau buổi tập: Đo nhịp tim hồi phục sau 10 phút, hô hấp, dáng đi, cơ bắp, kết luận lâm sàng gửi sang Huấn luyện viên trưởng.

---

## 2. TỔNG HỢP CÁC PHÂN HỆ & CHỨC NĂNG ĐÃ HOÀN THÀNH

Toàn bộ hệ thống hiện thực hóa trọn vẹn **23 Yêu cầu Chức năng (FR01 – FR23)** theo SRS, phân chia thành 5 Luồng nghiệp vụ (Flows):

```mermaid
flowchart TD
    subgraph Flow1["Flow 1: Hồ sơ Ngựa & Phả hệ"]
        FR01["FR01: Hồ sơ gốc & Tỷ lệ sở hữu"]
        FR02["FR02: Phả hệ 3 đời & Thành tích đua"]
    end

    subgraph Flow2["Flow 2: Huấn luyện & Thể lực"]
        FR04["FR04: Kế hoạch nhiều giai đoạn"]
        FR05["FR05: Lịch tập & Đặt làn sân tập"]
        FR06["FR06: Telemetry & Cảnh báo quá tải"]
        FR07["FR07: Đánh giá buổi tập (RPE, BPM, Video)"]
        FR03["FR03: Dashboard thể lực Recharts"]
    end

    subgraph Flow3["Flow 3: Y tế & Xử lý Chấn thương"]
        Queue["Hàng chờ tiếp nhận khám (PAID)"]
        FR08["FR08: Bản đồ chuồng & Thể trạng"]
        FR09["FR09: Bệnh án & Phụ lục bất biến"]
        FR10["FR10: Mô hình 3D đánh dấu chấn thương"]
        FR11["FR11: Khóa huấn luyện khẩn cấp tự động"]
        FR12["FR12: Chăm sóc phòng ngừa (Vắc-xin, Tẩy giun)"]
        Supervision["Giám sát buổi tập & Tự nhận ca trực"]
    end

    subgraph Flow4["Flow 4: Chuồng trại & Dinh dưỡng"]
        FR13["FR13: Phân bổ ô chuồng & Lịch sinh hoạt"]
        FR14["FR14: Khẩu phần ăn 4 bữa & Checklist"]
        FR15["FR15: Nhiệm vụ chăm sóc & Ảnh minh chứng"]
        FR16["FR16: Báo cáo sự cố khẩn cấp 1 chạm"]
        FR17["FR17: Tồn kho & Đề xuất bổ sung"]
    end

    subgraph Flow5["Flow 5: Đua & Quản trị CLB"]
        FR18["FR18: Đăng ký giải đua (Check readiness)"]
        FR19["FR19: Báo cáo chủ sở hữu định kỳ"]
        FR20["FR20: Quản lý danh mục gốc & Tài khoản"]
        FR21["FR21: Quản trị ma trận quyền (RBAC)"]
        FR22["FR22: Sổ cái thu/chi & Dashboard KPI"]
        FR23["FR23: Nhật ký kiểm toán bất biến (Audit Log)"]
    end

    Flow1 --> Flow2
    Flow1 --> Flow3
    Flow3 -.->|Tự động khóa tập khi chấn thương| Flow2
    Flow3 -.->|Chỉ định khẩu phần bệnh lý| Flow4
    Flow2 --> Flow5
    Flow4 -.->|Báo cáo sự cố| Flow3
```

### Flow 1: Hồ sơ Gốc của Ngựa & Phả hệ (FR01, FR02)
- **FR01 – Hồ sơ Ngựa Toàn Diện:**
  - Định danh ngựa: Mã đăng ký, tên, ngày sinh, giống, giới tính (`STALLION`, `MARE`, `GELDING`), số vi mạch (microchip), số hộ chiếu, màu sắc.
  - Phân bổ quyền sở hữu: Cho phép chia sẻ tỷ lệ phần trăm giữa nhiều chủ sở hữu (`dbo.horse_ownerships`), ràng buộc tổng tỷ lệ luôn bằng $100.00\%$.
  - Tích hợp lưu trữ Cloudinary: Upload file ảnh/tài liệu chính (`horse_profile_files`). Chủ sở hữu được quyền đề xuất file mới (`PENDING`), Quản lý CLB hoặc HLV trưởng xem xét phê duyệt (`ACTIVE`).
  - Lưu trữ hồ sơ (`archive`): Ẩn ngựa khỏi danh mục hoạt động mà vẫn bảo lưu lịch sử giao dịch và thành tích.
- **FR02 – Phả hệ & Thành tích Thi Đấu:**
  - Cây phả hệ tự tham chiếu 3 thế hệ (bố/mẹ `sireId`/`damId`, ông/bà nội ngoại, cụ).
  - Thuật toán DFS phát hiện và ngăn chặn vòng lặp gia phả (ngựa không thể làm bố/mẹ của chính mình hoặc tổ tiên mình).
  - Quản lý lịch sử kết quả thi đấu: Tên giải, ngày đua, trường đua, cự ly, thứ hạng về đích, thời gian chạy, nài ngựa, tiền thưởng. Tự động tính tổng số trận tham gia và số trận thắng.

### Flow 2: Huấn luyện, Lập kế hoạch, Thể lực & Telemetry (FR03 – FR07)
- **FR04 – Kế hoạch Huấn luyện Giai đoạn:**
  - Thiết lập kế hoạch huấn luyện theo nhiều giai đoạn (`WARMUP`, `ENDURANCE`, `SPEED`, `RECOVERY`, `PEAK`).
  - Database Trigger `tr_training_plan_phases_no_overlap` ngăn chặn hoàn toàn việc các giai đoạn bị trùng lặp thời gian trong cùng một kế hoạch.
  - Hỗ trợ nhân bản / sao chép kế hoạch mẫu sang ngựa khác.
- **FR05 – Phân công Lịch trình Huấn luyện & Đặt Sân tập:**
  - Lên lịch buổi tập cụ thể theo ngày, phân công nhân viên chuồng và sân tập (`racetracks`).
  - Khung vận hành sân tập: **05:00 – 11:00** sáng.
  - Quản lý các làn chạy (`racetrack_lanes`) và đặt chỗ chạy thử (`trial_run_bookings`).
  - Trigger `tr_trial_run_bookings_no_lane_overlap` chặn trùng lịch đặt làn chạy; Trigger `tr_training_schedules_plan_and_lock` chặn xếp lịch khi ngựa đang bị khóa y tế.
- **FR06 – Telemetry & Cảnh báo Vượt ngưỡng Thể lực:**
  - Thiết lập ngưỡng an toàn cá nhân hóa cho từng con ngựa (`HEART_RATE_MAX`, `SPEED_MAX`).
  - Nhập chỉ số phiên đo (nhịp tim, tốc độ chạy) thủ công; hệ thống tự động đối chiếu ngưỡng và kích hoạt sự kiện cảnh báo (`alert_events`) kèm thông báo in-app.
- **FR07 – Đánh giá Buổi tập Sau Huấn luyện:**
  - Biểu mẫu ghi nhận chỉ số sau buổi tập: Thời lượng, cự ly, tốc độ trung bình, khối lượng tải trọng, nhịp tim hồi phục (sau 5p/10p), thang đo nỗ lực RPE (1–10), liên kết video buổi tập.
  - Dữ liệu đánh giá là bất biến (không cho phép sửa/xóa sau khi hoàn tất).
- **FR03 – Bảng điều khiển Thể lực Toàn CLB (`FitnessPage.jsx`):**
  - Trực quan hóa dữ liệu qua 3 biểu đồ Recharts: Khối lượng tập, Tốc độ trung bình, Tỷ lệ hồi phục nhịp tim.
  - Bộ lọc theo ngày, theo ngựa, mã màu thể trạng (`GREEN`, `YELLOW`, `RED`).
  - Hỗ trợ xuất báo cáo thể lực ra định dạng ảnh PNG hoặc tài liệu PDF ngay trên trình duyệt (`html2canvas`, `jspdf`).

### Flow 3: Y tế, Mô hình 3D, Khóa tập & Chăm sóc phòng ngừa (FR08 – FR12)
- **Điều kiện Dịch vụ Y tế & Hàng chờ Tiếp nhận (`MedicalQueue`):**
  - Ngựa chỉ được tiếp nhận khám khi đã thanh toán phí (`paymentStatus = PAID`) hoặc có bảo lãnh từ Quản lý CLB (`serviceOverride = true`).
  - Quy trình tiếp nhận 3 bước rõ ràng: `PAID` (Chờ khám) $\rightarrow$ Bác sĩ bấm *Tiếp nhận* $\rightarrow$ `IN_MEDICAL` (Đang khám y tế) $\rightarrow$ Bác sĩ bấm *Hoàn tất* $\rightarrow$ `PENDING_TRAINING` (Bàn giao HLV Trưởng).
- **FR08 – Bản đồ Chuồng Y tế & Thể trạng (`BarnHealthMapView`):**
  - Hiển thị trực quan theo lưới ô chuồng của từng khu chuồng (Chuồng A, Chuồng B...).
  - Thống kê tỷ lệ lấp đầy, số ô trống.
  - Hiển thị thể trạng mã màu (`FIT`, `MONITOR`, `INJURED`, `QUARANTINED`) và trạng thái tập luyện (`🔒 Khóa huấn luyện` vs `🟢 Sẵn sàng tập luyện`).
- **FR09 – Khám Lâm Sàng & Hồ Sơ Bệnh Án:**
  - Phiếu khám bệnh đầy đủ: Lý do khám, triệu chứng, chẩn đoán, phác đồ điều trị, thuốc chỉ định, ngày tái khám.
  - Tính năng phụ lục bệnh án (Addenda): Cho phép ghi chú bổ sung diễn biến bệnh theo thời gian mà không làm biến dạng hồ sơ gốc.
  - Tự động đồng bộ trạng thái sức khỏe của ngựa khi lưu phiếu khám.
- **FR10 – Theo dõi Chấn thương & Mô hình 3D:**
  - Ghi nhận chấn thương kèm phân độ nghiêm trọng (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), phân loại 31 vùng giải phẫu thuộc 5 nhóm chuẩn.
  - Cập nhật tiến trình hồi phục theo từng giai đoạn.
- **FR11 – Khóa Huấn Luyện Khẩn Cấp Tự Động:**
  - Tự động tạo lệnh khóa `training_locks` ngay khi ngựa được chẩn đoán chấn thương (`INJURED` / `UNDER_TREATMENT`) lúc hoàn tất khám y tế.
  - Tự động phát thông báo ưu tiên cao tới tất cả HLV Trưởng.
  - Tự động gỡ bỏ khóa tập khi thể trạng ngựa được cập nhật về `FIT`.
- **FR12 – Chăm Sóc Phòng Ngừa Định Kỳ:**
  - Lên lịch tiêm phòng vắc-xin, tẩy giun, chăm sóc móng với chu kỳ lặp lại (`recurrenceDays`).
  - Tích hợp trực tiếp vào phiếu khám lâm sàng; tự động sinh lịch của kỳ tiếp theo khi hoàn thành.
  - Hỗ trợ thao tác xóa/hủy lịch tiêm phòng chưa thực hiện.

### Flow 4: Vận hành Chuồng trại, Dinh dưỡng & Kho vật tư (FR13 – FR17)
- **FR13 – Phân bổ Ô Chuồng & Lịch sinh hoạt:**
  - Quản lý khu chuồng (`stables`) và ô chuồng (`stalls`).
  - Gán ngựa vào ô chuồng, kiểm tra quy tắc mỗi khu chuồng tối đa 4 ô chuồng.
  - Soạn thảo và hiển thị lịch sinh hoạt hàng ngày (giờ ăn, giờ tắm, giờ thả đồng).
- **FR14 – Khẩu phần Dinh dưỡng:**
  - Bác sĩ Thú y hoặc HLV soạn thảo thực đơn dinh dưỡng 4 bữa/ngày (Sáng 07:00, Trưa 11:30, Chiều 15:30, Tối 19:00).
  - Phân loại chi tiết cám tinh, loại cám, cỏ thô, loại cỏ, thực phẩm bổ sung/thuốc uống kèm.
  - Nhân viên chuồng xem danh sách kiểm tra (daily checklist) để thực hiện cho ăn.
  - Thống kê tải trọng dinh dưỡng theo chuồng (`/api/nutrition/stables-workload`).
- **FR15 – Nhiệm vụ Chăm sóc Hằng ngày:**
  - Danh mục nhiệm vụ theo ca: Dọn chuồng (mucking), chải lông (grooming), dắt đi dạo (walking).
  - Nhân viên đánh dấu hoàn tất kèm tải ảnh hiện trường minh chứng (hỗ trợ multipart upload).
- **FR16 – Báo cáo Sự cố Khẩn cấp 1 chạm:**
  - Nhân viên chuồng gửi báo cáo sự cố khẩn cấp (ngựa đau bụng collic, kẹt móng, sốt...) kèm ảnh chụp.
  - Hệ thống gửi thông báo ưu tiên cao đến Bác sĩ Thú y và HLV Trưởng.
  - Bác sĩ Thú y có thể xác nhận [Ghi nhận], [Bỏ qua] hoặc [Tạo phiếu khám] để xử lý ngay trên Dashboard.
- **FR17 – Quản lý Tồn kho & Đề xuất Bổ sung:**
  - Danh mục vật tư: Thức ăn tinh, cỏ khô, thuốc thú y, dụng cụ chuồng kèm đơn giá tham chiếu `referencePrice` và ngưỡng tồn kho tối thiểu.
  - Nhân viên chuồng tạo đề xuất bổ sung kho khi sắp hết hàng.
  - Quản lý CLB xem xét phê duyệt hoặc từ chối đề xuất (`/api/inventory/replenishments/{id}/review`).

### Flow 5: Đăng ký Đua, Tài chính, Báo cáo & Quản trị RBAC (FR18 – FR23)
- **FR18 – Đăng Ký Giải Đua:**
  - Quản lý sự kiện giải đua (`race_events`).
  - Huấn luyện viên trưởng đăng ký ngựa tham dự giải; kiểm tra nghiêm ngặt điều kiện sẵn sàng thi đấu (`race-readiness`): Độ tuổi tối thiểu, trạng thái sức khỏe bắt buộc là `FIT`, cân nặng hợp lệ, không có lệnh khóa huấn luyện đang kích hoạt.
  - Hỗ trợ rút đơn đăng ký trước hạn chót.
- **FR19 – Báo Cáo Chủ Sở Hữu Định Kỳ:**
  - Tổng hợp toàn bộ chi phí chăm sóc, huấn luyện, thú y và doanh thu tiền thưởng của các con ngựa thuộc sở hữu của chủ ngựa.
  - Đóng băng dữ liệu báo cáo dạng JSON định kỳ (DailyAutomationJob chạy lúc 06:00 sáng).
  - Xuất báo cáo tài chính và hoạt động ra file JSON / PDF cho chủ sở hữu.
- **FR20 – Quản lý Người dùng & Danh mục Gốc:**
  - Quản lý tài khoản (tạo tài khoản, khóa/mở khóa tài khoản, phân vai trò).
  - Quản lý danh mục đường đua, khu chuồng, ô chuồng; ràng buộc cấm xóa danh mục đang có ngựa hoặc lịch tập tham chiếu.
- **FR21 – Quản trị Phân quyền Động (RBAC):**
  - Ma trận quyền động kiểm soát 3 hành động (`VIEW`, `EDIT`, `APPROVE`) cho 5 vai trò trên 6 phân hệ (`PROFILE`, `TRAINING`, `MEDICAL`, `STABLE_CARE`, `RACING`, `REPORTING`).
  - Lưu trữ trong bảng `dbo.role_permissions`.
  - Cơ chế tự bảo vệ: Cấm hạ quyền quản trị của vai trò `CLUB_MANAGER` để tránh nguy cơ khóa cứng hệ thống.
- **FR22 – Báo cáo Vận hành & Sổ cái Tài chính CLB:**
  - Sổ cái giao dịch tài chính ghi nhận thu/chi bằng tiền tệ VND (`dbo.financial_transactions`).
  - Bảng điều khiển KPI dành cho Quản lý CLB: Tỷ lệ tuân thủ huấn luyện, doanh thu giải đua, chi phí vận hành chuồng trại, tỷ lệ lấp đầy ô chuồng.
- **FR23 – Nhật ký Kiểm toán Bất biến (Audit Log):**
  - Tự động ghi vết mọi hành vi thêm/sửa/xóa dữ liệu và đăng nhập của người dùng (`AuditMutationFilter`).
  - Bảng `dbo.audit_logs` có trigger cấp database `tr_audit_logs_append_only` chặn tuyệt đối mọi thao tác `UPDATE` và `DELETE`.
  - Màn hình tra cứu, lọc lịch sử kiểm toán dành riêng cho `CLUB_MANAGER`.

### Các tính năng nâng cao chuyên biệt
1. **Mô hình ngựa 3D Three.js WebGL:**
   - Thư viện: Three.js, `@react-three/fiber`, `@react-three/drei`.
   - Asset 3D: `horse.glb` dung lượng 181 KB tải tối ưu.
   - Bảng tọa độ giải phẫu 31 điểm (`bodyRegionMap.js`) liên kết trực tiếp với dữ liệu chấn thương từ Azure SQL.
   - Hiệu ứng nhịp đập (pulse animation) phát sáng và Tooltip HTML nổi 3D.
   - Bọc trong `WebGLErrorBoundary` và tự động hiển thị sơ đồ 2D fallback nếu trình duyệt không hỗ trợ WebGL.
2. **Mô hình Phân tán Tự nhận ca Giám sát Buổi tập (Supervision Claiming):**
   - Bác sĩ Thú y chủ động đăng ký ca kiểm tra lâm sàng sau buổi tập (10:00 – 11:00).
   - Quy tắc: Hạn chót trước 23:00 ngày T-1, tối đa 1 ca/ngày/bác sĩ, cơ chế khóa bi quan chống nhận trùng ca.
   - Lập trình viên không cần can thiệp: Job tự động `TrainingAutoPickJob` (cron 23:00 hàng ngày) tự động cân bằng tải phân bổ ca trực cho ngày hôm sau.

---

## 3. DUYỆT MÃ NGUỒN & KIẾN TRÚC LUỒNG DỮ LIỆU CHI TIẾT

### 3.1. Luồng Xác thực & Phân quyền Kép (Dual RBAC Flow)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Client as Frontend SPA (React)
    participant AuthCtrl as AuthController
    participant SecFilter as Security Filter Chain
    participant DB as Azure SQL Database

    User->>Client: Nhập email & mật khẩu
    Client->>AuthCtrl: POST /api/auth/login {email, password}
    AuthCtrl->>DB: Truy vấn user & password_hash (BCrypt)
    AuthCtrl->>DB: Tạo bản ghi login_sessions (Refresh Token hash SHA-256)
    AuthCtrl-->>Client: Trả về {accessToken (15m), refreshToken (30d), user}
    Client->>Client: Lưu token vào sessionStorage

    Note over Client,SecFilter: Mỗi HTTP Request tiếp theo
    Client->>SecFilter: Gửi Header: Authorization: Bearer <accessToken>
    SecFilter->>SecFilter: Giải mã JWT (kiểm tra chữ ký, sub=userId, sid=sessionId)
    SecFilter->>DB: Kiểm tra session còn hiệu lực & user.isEnabled()
    SecFilter->>DB: Truy vấn quyền động từ dbo.role_permissions theo user_id
    SecFilter->>SecFilter: Thiết lập SecurityContext (Roles + GrantedAuthorities: PERM_*)
    SecFilter->>Controllers: Cho phép vào Controller xử lý logic nghiệp vụ
```

- **Mã nguồn thực thi:**
  - Backend: [SecurityConfiguration.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/common/config/SecurityConfiguration.java), [AuthController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/auth/controller/AuthController.java), [AuthService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/auth/service/AuthService.java), [LoginSessionService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/auth/service/LoginSessionService.java).
  - Frontend: [api.js](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/api.js) (tự động đính kèm Bearer token, interceptor tự bắt 401 để gọi `/api/auth/refresh`), [App.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/App.jsx) (`AuthProvider` định kỳ 30s gọi `/api/auth/me`).

---

### 3.2. Luồng Vòng đời Y tế: Tiếp nhận Điều trị & Khóa Huấn luyện

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Chủ sở hữu
    actor Manager as Quản lý CLB
    actor Vet as Bác sĩ Thú y
    actor Trainer as HLV Trưởng
    participant BE as Backend Services
    participant DB as Azure SQL Database

    Owner->>BE: Thanh toán phí nuôi dưỡng / dịch vụ
    Note over BE,DB: Hoặc Quản lý CLB cấp bảo lãnh: serviceOverride = true
    BE->>DB: Cập nhật horses.payment_status = 'PAID'
    
    Vet->>BE: GET /api/medical/queue
    BE-->>Vet: Danh sách ngựa PAID đang chờ tiếp nhận
    
    Vet->>BE: POST /api/medical/queue/{horseId}/receive
    BE->>DB: Cập nhật horses.payment_status = 'IN_MEDICAL'
    
    Vet->>BE: POST /api/horses/{id}/medical-examinations (Khám lâm sàng)
    BE->>DB: Lưu phiếu khám vào module_records & cập nhật health_status
    
    alt Có phát hiện chấn thương (INJURED)
        Vet->>BE: POST /api/horses/{id}/injuries (Ghi nhận điểm chấn thương)
        BE->>DB: Lưu chấn thương với body_region & severity
        Vet->>BE: POST /api/medical/queue/{horseId}/complete
        BE->>DB: Tự động tạo training_locks (khóa lịch tập khẩn cấp)
        BE->>Trainer: Đẩy thông báo ưu tiên cao: Ngựa đã bị khóa huấn luyện!
        Note over Trainer,BE: Trainer cố tình tạo lịch tập -> Bị Trigger DB chặn lỗi!
    else Khỏe mạnh (FIT)
        Vet->>BE: POST /api/medical/queue/{horseId}/complete
        BE->>DB: Cập nhật horses.payment_status = 'PENDING_TRAINING'
        BE->>Trainer: Thông báo: Ngựa đã hoàn tất khám y tế, sẵn sàng nhận lịch tập!
        Trainer->>BE: Bắt đầu gán Kế hoạch & Lên Lịch tập luyện
        BE->>DB: Cập nhật horses.payment_status = 'TRAINING'
    end

    alt Khi ngựa điều trị khỏi bệnh (Hồi phục)
        Vet->>BE: PUT /api/horses/{id}/health-status {status: "FIT"}
        BE->>DB: Tự động gỡ bỏ (release) lệnh training_locks đang kích hoạt
        BE->>Trainer: Thông báo: Ngựa đã bình phục, dỡ bỏ khóa huấn luyện!
    end
```

- **Mã nguồn thực thi:**
  - [MedicalQueueController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/medical/controller/MedicalQueueController.java), [MedicalQueueService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/medical/service/MedicalQueueService.java).
  - [MedicalExaminationController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/MedicalExaminationController.java), [MedicalExaminationService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/MedicalExaminationService.java).
  - [TrainingLockService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/TrainingLockService.java).
  - Frontend: [VetPage.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/VetPage.jsx) (`VetDashboard`, `MedicalRecordsView`).

---

### 3.3. Luồng Đánh dấu Chấn thương Mô hình 3D (3D Anatomical Injury Tracking)

```mermaid
flowchart TD
    A["Bác sĩ Thú y mở tab Bệnh án & Chấn thương (/vet/medical-records)"] --> B["Chọn ngựa từ danh sách đã lọc thanh toán"]
    B --> C["Frontend gọi: GET /api/horses/{id}/injuries"]
    C --> D["Nhận mảng chấn thương: [{bodyRegion: 'FL_KNEE_LEFT', severity: 'HIGH', ...}]"]
    
    D --> E["HorseBodyModel3D.jsx nạp /models/horse.glb qua useGLTF"]
    E --> F["Tự động chuẩn hóa kích thước (Box3 auto-scale về 2.5 units, y=0)"]
    
    D --> G["Duyệt qua danh sách chấn thương active"]
    G --> H["Tra cứu tọa độ [x, y, z] trong bodyRegionMap.js"]
    H --> I["Component InjuryMarker.jsx vẽ mesh Sphere & Halo Ring"]
    
    I --> J["useFrame tạo hiệu ứng nhịp đập pulse animation"]
    I --> K["Ánh xạ màu sắc theo Severity: LOW (Vàng), MEDIUM (Cam), HIGH (Đỏ), CRITICAL (Tím)"]
    
    UserClick["Người dùng click vào điểm đánh dấu trên 3D"] --> L["Drei Html component hiển thị Tooltip nổi"]
    L --> M["Hiển thị: Ngày khám, Chẩn đoán, Mức độ, Ghi chú lâm sàng"]
```

- **Mã nguồn thực thi:**
  - Frontend: [HorseBodyModel3D.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/components/horse3d/HorseBodyModel3D.jsx), [InjuryMarker.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/components/horse3d/InjuryMarker.jsx), [bodyRegionMap.js](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/components/horse3d/bodyRegionMap.js), [HorseModelFallback.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/components/horse3d/HorseModelFallback.jsx).
  - Backend: [InjuryController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/controller/InjuryController.java), [InjuryService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/health/service/InjuryService.java).

---

### 3.4. Luồng Phân tán Tự nhận ca & Cân bằng tải Giám sát Buổi tập

```mermaid
sequenceDiagram
    autonumber
    actor Vet as Bác sĩ Thú y
    participant FE as VetPage (/vet/training)
    participant Ctrl as TrainingScheduleController
    participant Svc as TrainingScheduleService
    participant Repo as TrainingScheduleRepository
    participant Cron as TrainingAutoPickJob (23:00)
    participant DB as Azure SQL Database

    Note over Vet,FE: Trước 23:00 ngày T-1: Tự nhận ca chủ động
    Vet->FE: Xem danh sách phiên tập mở ngày mai (05:00 - 11:00)
    FE->Ctrl: GET /api/training-schedules/open
    Ctrl->Svc: findOpenSessions(callerId, isVet)
    Svc-->>FE: Trả về danh sách kèm cờ canClaim (true nếu Vet chưa có ca trong ngày)
    
    Vet->FE: Bấm "Đăng ký theo dõi" trên 1 phiên tập
    FE->Ctrl: POST /api/training-schedules/{id}/claim
    Ctrl->Svc: claim(scheduleId, vetId, isVet=true)
    Svc->Repo: findByIdWithLock(scheduleId) [PESSIMISTIC_WRITE Lock]
    Svc->Svc: Kiểm tra giờ < 23:00 T-1 & Chưa có ca nào trong ngày
    Svc->DB: Gán veterinarian_user_id, vet_assigned_by = 'USER', vet_assigned_at = NOW()
    Svc-->>FE: Đăng ký ca thành công!

    Note over Cron,DB: 23:00 Hàng ngày: Tự động điều phối (Auto-pick)
    Cron->Svc: Quét các phiên tập ngày T+1 chưa có Vet: findNeedingAssignmentForDate(T+1)
    loop Từng phiên tập chưa có bác sĩ
        Cron->DB: findLeastAutoPickedVet(T+1) (tìm Vet có ít ca nhất 30 ngày qua)
        alt Tìm thấy Vet còn trống lịch
            Cron->DB: Gán veterinarian_user_id, vet_assigned_by = 'SYSTEM'
        else Không còn Vet nào rảnh
            Cron->DB: Gửi Notification cấp bách (HIGH) đến toàn bộ Quản lý CLB!
        end
    end

    Note over Vet,FE: 10:00 - 11:00 Ngày T: Khám lâm sàng sau tập
    Vet->FE: Mở tab "Lịch của tôi", bấm "Đánh giá lâm sàng"
    FE->Ctrl: POST /api/training-evaluations (nhịp tim hồi phục sau 10p, hô hấp, dáng đi...)
    Ctrl->DB: Lưu kết quả đánh giá lâm sàng cho buổi tập
```

- **Mã nguồn thực thi:**
  - [TrainingScheduleController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/TrainingScheduleController.java).
  - [TrainingScheduleService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/service/TrainingScheduleService.java).
  - [TrainingAutoPickJob.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/job/TrainingAutoPickJob.java).
  - [15_training_operational_window.sql](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/DATABASE/15_training_operational_window.sql).

---

### 3.5. Luồng Báo cáo Sự cố & Xử lý Y tế Dashboard

```mermaid
flowchart TD
    Staff["Nhân viên chuồng phát hiện sự cố (ngựa ốm, chấn thương, sự cố chuồng)"] --> Form["Mở form Báo cáo Sự cố 1 chạm trên /stable-care/incidents"]
    Form --> PostInc["POST /api/incidents {horseId, description, severity, photoUrl}"]
    PostInc --> Alert["Backend tạo sự cố status='REPORTED' & đẩy Notification HIGH tới Bác sĩ Thú y"]
    
    Alert --> VetDash["Sự cố xuất hiện ngay trên Bảng điều khiển y tế /vet"]
    
    VetDash --> Actions{"Bác sĩ Thú y xử lý sự cố"}
    
    Actions -- "Chỉ cần theo dõi" --> Ack["Bấm [Ghi nhận]: POST /api/incidents/{id}/acknowledge\n-> Trạng thái: ACKNOWLEDGED -> Ẩn khỏi Dashboard"]
    Actions -- "Không cần can thiệp" --> Dismiss["Bấm [Bỏ qua]: POST /api/incidents/{id}/dismiss\n-> Trạng thái: DISMISSED -> Ẩn khỏi Dashboard"]
    Actions -- "Cần khám chữa bệnh" --> CreateExam["Bấm [Tạo phiếu khám]:\n1. Tự động gọi acknowledgeIncident\n2. Điều hướng sang /vet/medical-records?horseId=...\n3. Sự cố biến mất khỏi Dashboard, bác sĩ bắt đầu khám"]
```

- **Mã nguồn thực thi:**
  - [IncidentReportController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/incident/controller/IncidentReportController.java), [IncidentReportService.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/incident/service/IncidentReportService.java).
  - Frontend: [VetPage.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/VetPage.jsx) (`VetDashboard`), [StableCarePage.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/StableCarePage.jsx).

---

### 3.6. Luồng Lên Kế hoạch, Lịch tập & Đánh giá Buổi tập (Training Flow)

```mermaid
sequenceDiagram
    autonumber
    actor Trainer as Huấn luyện viên trưởng
    actor Staff as Nhân viên chuồng
    participant PlanCtrl as TrainingPlanController
    participant SchedCtrl as TrainingScheduleController
    participant EvalCtrl as TrainingEvaluationController
    participant DB as Azure SQL Database

    Trainer->>PlanCtrl: POST /api/training-plans (Tạo kế hoạch nhiều phases: Warmup, Speed...)
    PlanCtrl->>DB: Trigger tr_training_plan_phases_no_overlap kiểm tra không trùng ngày
    PlanCtrl-->>Trainer: Kế hoạch hợp lệ được lưu
    
    Trainer->>SchedCtrl: POST /api/training-schedules (Xếp lịch tập cụ thể & phân công Staff)
    SchedCtrl->>DB: Trigger tr_training_schedules_plan_and_lock kiểm tra Training Lock y tế!
    SchedCtrl->>DB: Trigger tr_training_schedules_no_staff_overlap kiểm tra trùng ca Staff!
    SchedCtrl-->>Trainer: Tạo lịch thành công
    
    Trainer->>SchedCtrl: POST /api/training-schedules/{id}/publish (Công bố lịch)
    SchedCtrl->>Staff: Gửi Notification in-app tới nhân viên được gán
    
    Staff->>SchedCtrl: GET /api/training-schedules/mine (Xem lịch làm việc của mình)
    
    Note over Trainer,Staff: Tiến hành buổi tập thực tế trên sân (05:00 - 11:00)
    
    Trainer->>EvalCtrl: POST /api/training-evaluations (Chỉ số buổi tập: Duration, Distance, Avg Speed, Recovery BPM, RPE, Video)
    EvalCtrl->>DB: Lưu đánh giá bất biến vào dbo.training_evaluations
    EvalCtrl-->>Trainer: Đánh giá buổi tập hoàn tất
    
    Trainer->>Trainer: Xem Dashboard thể lực toàn CLB (/fitness) cập nhật biểu đồ Recharts tức thì
```

- **Mã nguồn thực thi:**
  - [TrainingPlanController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/TrainingPlanController.java), [TrainingScheduleController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/TrainingScheduleController.java), [TrainingEvaluationController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/TrainingEvaluationController.java), [FitnessDashboardController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/training/controller/FitnessDashboardController.java).
  - Frontend: [FitnessPage.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/FitnessPage.jsx).

---

### 3.7. Luồng Quản lý Chuồng, Dinh dưỡng & Đề xuất Kho

```mermaid
flowchart LR
    subgraph Stables["Quản lý Chuồng & Ô chuồng (Club Manager)"]
        CreateStable["POST /api/stables"] --> AddStall["POST /api/stables/{id}/stalls (Max 4 stalls/barn)"]
        AddStall --> AssignHorse["POST /api/stables/{id}/stalls/{sId}/assign"]
    end

    subgraph Nutrition["Dinh dưỡng & Cho ăn (Vet / Staff)"]
        VetPlan["POST /api/horses/{id}/nutrition-plan (Khẩu phần 4 bữa/ngày)"] --> StaffCheck["GET /daily-checklist (Checklist cho ăn)"]
        StaffCheck --> Workload["GET /api/nutrition/stables-workload (Tải trọng cám/cỏ theo chuồng)"]
    end

    subgraph Inventory["Kho vật tư & Bổ sung (Staff / Manager)"]
        StaffView["GET /api/inventory/items"] --> StaffReq["POST /api/inventory/replenishments (Đề xuất vật tư)"]
        StaffReq --> ManagerReview["POST /api/inventory/replenishments/{id}/review\n(Quản lý CLB Duyệt/Từ chối)"]
    end
```

- **Mã nguồn thực thi:**
  - [StableController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/stable/controller/StableController.java).
  - [NutritionPlanController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/nutrition/controller/NutritionPlanController.java), [NutritionOverviewController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/nutrition/controller/NutritionOverviewController.java).
  - [InventoryController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/inventory/controller/InventoryController.java).
  - Frontend: [StableCarePage.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/StableCarePage.jsx), [AdminPages.jsx](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/FRONTEND/src/AdminPages.jsx).

---

### 3.8. Luồng Đua, Báo cáo Chủ sở hữu & Sổ cái Tài chính

```mermaid
sequenceDiagram
    autonumber
    actor Trainer as Huấn luyện viên trưởng
    actor Owner as Chủ sở hữu
    actor Manager as Quản lý CLB
    participant RaceCtrl as RaceEventController
    participant FinCtrl as FinanceController
    participant RepCtrl as OwnerReportController
    participant Cron as DailyAutomationJob (06:00)
    participant DB as Azure SQL Database

    Manager->>RaceCtrl: POST /api/races (Tạo giải đua mới)
    Trainer->>RaceCtrl: POST /api/races/{id}/registrations (Đăng ký ngựa dự đua)
    RaceCtrl->>DB: Kiểm tra Race-Readiness (Sức khỏe FIT, không có Training Lock, đủ tuổi, đủ cân nặng)
    RaceCtrl-->>Trainer: Đăng ký thành công
    
    Manager->>FinCtrl: POST /api/financial-transactions (Ghi nhận thu/chi VND: phí chuồng, thưởng đua, y tế)
    FinCtrl->>DB: Ghi sổ cái bất biến dbo.financial_transactions
    
    Cron->>RepCtrl: Tự động chạy 06:00 sáng định kỳ
    RepCtrl->>DB: Tính toán phân bổ thu/chi theo % sở hữu thực tế (dbo.horse_ownerships)
    RepCtrl->>DB: Đóng băng báo cáo JSON vào dbo.owner_reports
    
    Owner->>RepCtrl: GET /api/owner-reports (Xem danh sách báo cáo tài chính của mình)
    Owner->>RepCtrl: GET /api/owner-reports/{id}/download (Tải báo cáo JSON/PDF)
```

- **Mã nguồn thực thi:**
  - [RaceEventController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/racing/controller/RaceEventController.java).
  - [FinanceController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/finance/controller/FinanceController.java).
  - [OwnerReportController.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/reporting/controller/OwnerReportController.java).
  - [DailyAutomationJob.java](file:///c:/Users/admin/OneDrive/Documents/GitHub/HORSE-PROJECT/BACKEND/src/main/java/com/racehorse/automation/DailyAutomationJob.java).

---

### 3.9. Luồng Ràng buộc Chéo & Triggers Toàn vẹn Dữ liệu (Cross-System Integrity)

Hệ thống duy trì tính toàn vẹn cấp cao thông qua 5 Database Triggers và các ràng buộc liên phân hệ:

| Tên Trigger / Cơ Chế | Bảng Dữ Liệu | Ràng Buộc Nghiệp Vụ Thực Thi |
|---|---|---|
| `tr_training_plan_phases_no_overlap` | `dbo.training_plan_phases` | Chặn tuyệt đối việc các giai đoạn huấn luyện của cùng một con ngựa có khoảng thời gian trùng nhau. |
| `tr_training_schedules_no_staff_overlap` | `dbo.training_schedules` | Một nhân viên chuồng (`STABLE_STAFF`) không thể bị phân công vào hai buổi tập trùng giờ. |
| `tr_trial_run_bookings_no_lane_overlap` | `dbo.trial_run_bookings` | Một làn chạy của một đường đua trong cùng một khung giờ chỉ cho phép tối đa 1 lượt đặt chỗ. |
| `tr_training_schedules_plan_and_lock` | `dbo.training_schedules` | Chặn xếp lịch buổi tập nếu ngựa đang có lệnh khóa y tế (`training_locks`) chưa được gỡ hoặc kế hoạch huấn luyện không ở trạng thái hoạt động. |
| `tr_audit_logs_append_only` | `dbo.audit_logs` | Chặn tuyệt đối mọi câu lệnh `UPDATE` hoặc `DELETE` trên bảng nhật ký kiểm toán. |
| **Lock Bi Quan (`PESSIMISTIC_WRITE`)** | `TrainingScheduleRepository` | Khi Bác sĩ Thú y nhận ca (`claim`), câu truy vấn khóa bản ghi trực tiếp trên SQL Server để ngăn ngừa race-condition khi 2 bác sĩ bấm đồng thời. |
| **Quy tắc 4 Ô Chuồng / Khu** | `StableService.java` | Giới hạn cứng mỗi khu chuồng chỉ được tạo tối đa 4 ô chuồng (4 con ngựa). |

---

## 4. BẢNG TỔNG HỢP API BACKEND & DTOS

Hệ thống xây dựng hoàn chỉnh **35 RestControllers** với hơn **60 REST Endpoints**:

| Phân hệ | HTTP Method | Endpoint | Quyền hạn (Security PreAuthorize) |
|---|---|---|---|
| **Auth** | `POST` | `/api/auth/login` | Public (`permitAll`) |
| | `POST` | `/api/auth/refresh` | Public (`permitAll`) |
| | `POST` | `/api/auth/logout` | Public (`permitAll`) |
| | `GET` | `/api/auth/me` | `isAuthenticated()` |
| **Users & RBAC** | `GET/POST` | `/api/users` | `hasRole('CLUB_MANAGER')` |
| | `PUT` | `/api/users/{id}/roles` | `hasRole('CLUB_MANAGER')` |
| | `GET/PUT` | `/api/admin/role-permissions` | `hasRole('CLUB_MANAGER')` |
| | `GET` | `/api/admin/audit-logs` | `hasRole('CLUB_MANAGER')` |
| **Horses** | `GET` | `/api/horses` | `PERM_PROFILE_VIEW` (Manager, Trainer, Vet, Owner) |
| | `GET` | `/api/horses/mine` | `PERM_PROFILE_VIEW` & `HORSE_OWNER` |
| | `POST/PUT` | `/api/horses` | `PERM_PROFILE_EDIT` & (`CLUB_MANAGER`, `HEAD_TRAINER`) |
| | `GET` | `/api/horses/{id}/pedigree` | `PERM_PROFILE_VIEW` |
| | `GET/POST` | `/api/horses/{id}/race-results` | `PERM_RACING_VIEW` / `PERM_RACING_EDIT` |
| | `POST` | `/api/horses/{id}/main-file/proposals` | `HORSE_OWNER` |
| | `POST` | `/api/horses/{id}/main-file/proposals/{pId}/approve` | `CLUB_MANAGER`, `HEAD_TRAINER` |
| **Medical Queue** | `GET` | `/api/medical/queue` | `PERM_MEDICAL_VIEW` (Ngựa PAID) |
| | `POST` | `/api/medical/queue/{id}/receive` | `PERM_MEDICAL_EDIT` & `VETERINARIAN` |
| | `POST` | `/api/medical/queue/{id}/complete` | `PERM_MEDICAL_EDIT` & `VETERINARIAN` |
| **Health & 3D** | `GET` | `/api/health/barn-map` | `PERM_MEDICAL_VIEW` |
| | `POST/GET`| `/api/horses/{id}/medical-examinations` | `PERM_MEDICAL_EDIT` / `PERM_MEDICAL_VIEW` |
| | `POST/GET`| `/api/horses/{id}/injuries` | `PERM_MEDICAL_EDIT` / `PERM_MEDICAL_VIEW` |
| | `POST/DEL`| `/api/horses/{id}/training-locks` | `PERM_MEDICAL_EDIT` (`VETERINARIAN`, `CLUB_MANAGER`) |
| | `POST/GET`| `/api/horses/{id}/preventive-care` | `PERM_MEDICAL_EDIT` / `PERM_MEDICAL_VIEW` |
| **Incidents** | `POST` | `/api/incidents` | `PERM_STABLE_CARE_EDIT` |
| | `GET` | `/api/incidents` | `PERM_MEDICAL_VIEW` & `PERM_STABLE_CARE_VIEW` |
| | `POST` | `/api/incidents/{id}/acknowledge` | `PERM_MEDICAL_VIEW` |
| | `POST` | `/api/incidents/{id}/dismiss` | `PERM_MEDICAL_VIEW` |
| **Training & Supervision** | `GET` | `/api/training-schedules/open` | `PERM_TRAINING_CLAIM` |
| | `POST` | `/api/training-schedules/{id}/claim` | `PERM_TRAINING_CLAIM` |
| | `GET` | `/api/training-schedules/mine` | `PERM_TRAINING_VIEW` (`VETERINARIAN`, `STABLE_STAFF`) |
| | `POST` | `/api/training-evaluations` | `PERM_TRAINING_EDIT` & (`CLUB_MANAGER`, `HEAD_TRAINER`) |
| | `GET` | `/api/fitness/dashboard` | `PERM_TRAINING_VIEW` |
| **Stables & Nutrition** | `GET/POST`| `/api/stables` | `PERM_STABLE_CARE_VIEW` / `PERM_STABLE_CARE_EDIT` |
| | `POST/GET`| `/api/horses/{id}/nutrition-plan` | `PERM_STABLE_CARE_EDIT`, `PERM_MEDICAL_EDIT` |
| | `GET` | `/api/nutrition/stables-workload` | `PERM_STABLE_CARE_VIEW` |
| | `GET/POST`| `/api/care-tasks` | `PERM_STABLE_CARE_VIEW` / `PERM_STABLE_CARE_EDIT` |
| | `GET/POST`| `/api/inventory/items` | `PERM_STABLE_CARE_VIEW` / `PERM_STABLE_CARE_EDIT` |
| | `POST` | `/api/inventory/replenishments/{id}/review` | `hasRole('CLUB_MANAGER')` |
| **Racing & Reports** | `GET/POST`| `/api/races` | `PERM_RACING_VIEW` / `PERM_RACING_EDIT` |
| | `POST` | `/api/races/{id}/registrations` | `PERM_RACING_EDIT` |
| | `GET` | `/api/owner-reports` | `PERM_REPORTING_VIEW` |
| | `POST/GET`| `/api/financial-transactions` | `PERM_REPORTING_EDIT` (`CLUB_MANAGER`) |

---

## 5. HIỆN TRẠNG KIỂM THỬ & TRIỂN KHAI

| Hạng mục kiểm tra | Hiện trạng | Ghi chú kỹ thuật |
|---|:---:|---|
| **Backend Test Suite (Maven Surefire)** | ✅ **137/137 PASS** | Toàn bộ 21 packages đều có unit/integration test bao phủ đầy đủ. |
| **Frontend Production Build (Vite)** | ✅ **0 Lỗi cú pháp** | `npm run build` hoàn tất xuất sắc (7.4s), sinh các bundles tối ưu. |
| **Azure SQL Database Connectivity** | ✅ **Hoạt động ổn định** | Kết nối trực tiếp máy chủ Azure SQL: 30 bảng schema nén + dữ liệu vận hành. |
| **Mô hình 3D Three.js WebGL** | ✅ **Hoàn thành 100%** | Render GLB mượt mà, 31 tọa độ chấn thương, Tooltip nổi, fallback SVG an toàn. |
| **Mô hình Giám sát Buổi tập (Claiming & Auto-Pick)** | ✅ **Hoàn thành 100%** | Khóa bi quan chống nhận trùng, Cron Job 23:00 cân bằng tải chính xác. |
| **Phân quyền RBAC & Vòng đời Sự cố** | ✅ **Khép kín 100%** | Không còn rò rỉ quyền hay sự cố bị treo trên Dashboard. |

---
*Tài liệu này được biên soạn tự động bởi Google DeepMind Antigravity AI Assistant sau khi rà soát toàn bộ lịch sử hội thoại, các tệp Markdown và duyệt toàn bộ mã nguồn của dự án HORSE-PROJECT.*
