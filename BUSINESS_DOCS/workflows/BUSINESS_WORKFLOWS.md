# Tài Liệu Quy Trình Nghiệp Vụ Liên Vai Trò (Cross-Role Business Workflows)
## End-to-End System Workflows & Orchestration

> **Thư mục:** `BUSINESS_DOCS/workflows/`  
> **Thời điểm thẩm định:** 12/10/2026  
> **Phạm vi:** 8 quy trình kinh doanh chuẩn hóa liên kết 5 vai trò (`CLUB_MANAGER`, `HEAD_TRAINER`, `STABLE_STAFF`, `HORSE_OWNER`, `VETERINARIAN`) và cơ chế tự động hóa hệ thống.

---

## Danh Mục Quy Trình Nghiệp Vụ Chuẩn (Workflow Catalog)

| Mã Quy Trình | Tên Quy Trình Nghiệp Vụ | Các Vai Trò Tham Gia | Mục Tiêu & Điểm Kết Thúc |
|---|---|---|---|
| **WF-01** | Nhập Chuồng & Tiếp Nhận Ngựa Toàn Diện | `HORSE_OWNER`, `VETERINARIAN`, `CLUB_MANAGER`, `HEAD_TRAINER` | Ngựa được thanh toán, khám thú y, xếp ô chuồng và HLV trưởng tiếp nhận vào danh sách huấn luyện |
| **WF-02** | Lập Kế Hoạch, Đặt Lịch & Điều Phối Buổi Huấn Luyện | `HEAD_TRAINER`, `STABLE_STAFF` | Bài tập hoàn tất đúng khung giờ, sinh nhiệm vụ hồi phục cho nhân viên chuồng |
| **WF-03** | Đánh Giá Thể Lực & Phản Hồi Lâm Sàng Sau Tập | `HEAD_TRAINER`, `HORSE_OWNER`, `VETERINARIAN`, `STABLE_STAFF` | Hoàn tất đánh giá FR07, đo nhịp tim hồi phục, ghi nhận chấn thương và bác sĩ gửi đánh giá lâm sàng |
| **WF-04** | Xử Lý Sự Cố Khẩn Cấp Trên Sân Tập & Chuồng Trại | `HEAD_TRAINER`, `STABLE_STAFF`, `VETERINARIAN`, `HORSE_OWNER` | Cứu hộ kịp thời tại sân đua và xử lý bệnh lý cấp tính tại chuồng nuôi |
| **WF-05** | Chăm Sóc Hàng Ngày & Phục Hồi Thể Trạng | `STABLE_STAFF`, `VETERINARIAN`, `CLUB_MANAGER` | Hoàn thành checklist nhiệm vụ chuồng, điểm danh bữa ăn và lưu ảnh minh chứng |
| **WF-06** | Đăng Ký Đua Ngựa & Bộ Ba Đồng Thuận (Consent Triad) | `CLUB_MANAGER`, `HEAD_TRAINER`, `HORSE_OWNER` | Đăng ký thi đấu hợp lệ khi đủ cả 3 bên đồng thuận theo ràng buộc Trigger DB |
| **WF-07** | Bệnh Án Bất Biến, Chấn Thương 3D & Khóa Huấn Luyện Y Tế | `VETERINARIAN`, `HEAD_TRAINER`, `HORSE_OWNER` | Bảo đảm an toàn sinh học: đình chỉ tập luyện khi chấn thương và giải phóng khi bình phục |
| **WF-08** | Tài Chính, Ví Điện Tử, Kho Vật Tư & Cung Ứng Chuồng | `HORSE_OWNER`, `STABLE_STAFF`, `CLUB_MANAGER`, `AUTOMATION` | Khép kín dòng tiền phí dịch vụ, kiểm soát tiêu hao và cấp phát vật tư chuồng |

---

## WF-01: Quy Trình Nhập Chuồng & Tiếp Nhận Ngựa Toàn Diện (Horse Onboarding Pipeline)

### 1. Mục Tiêu & Ý Nghĩa
Đảm bảo mọi con ngựa lưu trú tại câu lạc bộ đều phải trải qua quy trình kiểm soát nghiêm ngặt 4 bước: Pháp lý/Tài chính -> Kiểm dịch/Y tế thú y -> Cơ sở vật chất/Ô chuồng -> Tiếp nhận giáo án huấn luyện.

### 2. Sơ Đồ Tuần Tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Chủ Sở Hữu (HORSE_OWNER)
    actor Manager as Quản Lý CLB (CLUB_MANAGER)
    actor Vet as Bác Sĩ Thú Y (VETERINARIAN)
    actor Trainer as HLV Trưởng (HEAD_TRAINER)
    participant Sys as Hệ Thống / Database

    Owner->>Sys: 1. Khai báo hồ sơ ngựa mới (POST /api/horses)
    Note over Sys: Trạng thái: PENDING_WAITING_ROOM
    alt Chủ ngựa thanh toán ví
        Owner->>Sys: 2a. Nạp ví PayOS & Mua gói dịch vụ (P30/P90/P365)
        Note over Sys: payment_status = 'PAID', intake_status = 'WAITING_FOR_VET'
    else Quản lý duyệt bảo lãnh
        Manager->>Sys: 2b. Duyệt bảo lãnh dịch vụ (POST /service-override)
        Note over Sys: service_override = 1, intake_status = 'WAITING_FOR_VET'
    end

    Vet->>Sys: 3. Xem phòng chờ (GET /api/medical/queue) & Bấm Tiếp nhận
    Note over Sys: intake_status = 'IN_MEDICAL', diagnosing_vet = Vet
    Vet->>Sys: 4. Khám lâm sàng, lập phiếu khám (POST /medical-examinations)
    Vet->>Sys: 5. Bấm Hoàn tất khám (POST /medical/queue/{id}/complete)
    Note over Sys: intake_status = 'PENDING_STALL', health_status = 'FIT'

    Manager->>Sys: 6. Phân bổ ô chuồng (POST /stables/{id}/stalls/{id}/assign)
    Note over Sys: Trigger kiểm tra: tối đa 4 ngựa/chuồng.<br/>intake_status = 'STABLED'

    Trainer->>Sys: 7. Xem danh sách đủ điều kiện (GET /paid-horse-assignments/trainer/pending)
    Trainer->>Sys: 8. Bấm Tiếp nhận huấn luyện (POST /trainer/{horseId})
    Note over Sys: horses.current_trainer_id = Trainer.<br/>Ngựa xuất hiện trong giáo án và lịch tập của HLV!
```

### 3. Các Điểm Kiểm Soát Nghiệp Vụ Cứng (Hard Business Rules)
- `[FACT]` HLV Trưởng chỉ nhìn thấy ngựa trong danh sách tiếp nhận khi thỏa mãn đồng thời:
  1. `intake_status = 'STABLED'`.
  2. `health_status = 'FIT'`.
  3. `diagnosing_vet_user_id IS NOT NULL`.
  4. Đã có ô chuồng `STALL_ASSIGNMENT` hoạt động.
  5. Đã thanh toán (`PAID`) hoặc có bảo lãnh (`service_override = 1`).
  6. Không có khóa tập y tế (`training_locks`) đang hiệu lực.

---

## WF-02: Quy Trình Lập Kế Hoạch, Đặt Lịch & Điều Phối Huấn Luyện (Training Workflow)

### 1. Mục Tiêu & Ý Nghĩa
Vận hành buổi tập thực địa kỷ luật, đúng khung giờ quy định của câu lạc bộ, phối hợp nhịp nhàng giữa Huấn luyện viên trưởng và Nhân viên chăm sóc chuồng.

### 2. Sơ Đồ Tuần Tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Trainer as HLV Trưởng (HEAD_TRAINER)
    actor Staff as Nhân Viên Chuồng (STABLE_STAFF)
    participant Sys as Hệ Thống / DB Triggers

    Trainer->>Sys: 1. Tạo kế hoạch huấn luyện đa giai đoạn (POST /api/training-plans)
    Trainer->>Sys: 2. Đặt lịch tập chi tiết (POST /api/training-schedules)
    Note over Sys: Khung giờ: 06:00 - 23:00 (giờ tròn 60p).<br/>Đặt làn 1-4 trên sân (trial_run_bookings).<br/>Trạng thái: PREPARING
    Sys-->>Staff: 3. Thông báo lịch tập tới Caretaker của ngựa

    Staff->>Sys: 4. Xem lịch tập ngựa phụ trách (GET /training-schedules/mine)
    Note over Staff: Kiểm tra yên cương, dắt ngựa ra bãi
    Staff->>Sys: 5. Xác nhận sẵn sàng (PATCH /training-schedules/{id}/ready)
    Note over Sys: session_status = 'READY', ghi nhận ready_by_user_id

    Trainer->>Sys: 6. Bấm Bắt đầu huấn luyện (POST /training-schedules/{id}/start)
    Note over Sys: session_status = 'IN_PROGRESS', ghi started_at
    Note over Trainer, Staff: Buổi tập diễn ra trên đường đua

    Trainer->>Sys: 7. Bấm Kết thúc huấn luyện (POST /training-schedules/{id}/finish)
    Note over Sys: session_status = 'COMPLETED', ghi completed_at
    Sys->>Sys: 8. Tự động gọi CareTaskService.createRecoveryTask()!
    Note over Sys: Tạo nhiệm vụ 'Hồi phục sau buổi tập' gán cho Staff.<br/>Task category: SPECIAL, type: HEALTH_CHECK
```

### 3. Các Điểm Kiểm Soát Nghiệp Vụ Cứng
- `[FACT]` **Không có Vet can thiệp trong luồng tập bình thường:** Buổi tập chỉ diễn ra giữa HLV và Nhân viên chuồng phụ trách.
- `[FACT]` **Trigger DB `tr_enforce_training_window`:** Bắt buộc buổi tập nằm trong khung 06:00 đến 23:00, không cho phép đặt khung giờ lẻ phút.
- `[FACT]` **Tự động sinh nhiệm vụ hồi phục:** HLV không cần nhắn tin hay tạo việc thủ công; ngay khi bấm `finish`, nhân viên chuồng lập tức nhận được nhiệm vụ hồi phục trên app.

---

## WF-03: Quy Trình Đánh Giá Thể Lực & Phản Hồi Lâm Sàng Sau Tập (Evaluation Workflow)

### 1. Mục Tiêu & Ý Nghĩa
Thu thập dữ liệu thể lực sinh học sau bài tập (FR07) phục vụ tối ưu hóa thành tích thi đấu, đồng thời kết nối đánh giá lâm sàng từ Bác sĩ thú y để phòng ngừa quá tải cơ tim.

### 2. Sơ Đồ Quy Trình Chi Tiết

```mermaid
flowchart TD
    Start([Buổi tập hoàn thành - COMPLETED]) --> TrainerEval[HLV lập Đánh giá FR07<br/>POST /training-evaluations]
    TrainerEval --> CalcMetrics[Hệ thống tự động tính toán:<br/>- Tốc độ TB = Distance * 3.6 / Seconds<br/>- Cường độ = Speed TB * 100 / MaxSpeed<br/>- Nhịp tim hồi phục = StopBpm - 1MinBpm]
    
    CalcMetrics --> FitnessCheck{Phân loại thể lực theo nhịp tim 1 phút}
    FitnessCheck -->|<= 115 BPM| Green[GREEN - Thể lực Tốt]
    FitnessCheck -->|116 - 129 BPM| Yellow[YELLOW - Thể lực Cần Lưu Ý]
    FitnessCheck -->|>= 130 BPM| Red[RED - Nguy cơ Kiệt Sức / Quá Tải]

    Yellow --> NotifyStaff[Thông báo lưu ý tới Nhân viên chuồng]
    Red --> AlertVet[Phát cảnh báo tới Bác sĩ thú y]

    TrainerEval --> InjuryCheck{Ngựa có chấn thương không?}
    InjuryCheck -->|Có - injured=true| UpdateInjured[Cập nhật horses: status = INJURED<br/>health_source = TRAINING_EVALUATION<br/>Gửi cảnh báo ưu tiên cao tới Vet & Chủ ngựa]
    InjuryCheck -->|Không| NormalFlow[Lưu đánh giá bình thường]

    NormalFlow --> NotifyOwner[Gửi thông báo có đánh giá mới tới Chủ ngựa]
    
    NormalFlow --> VetExam[Bác sĩ thú y kiểm tra lâm sàng độc lập<br/>POST /training-evaluations/{id}/vet-assessment]
    VetExam --> VetNotify[Gửi nhận xét hô hấp, dáng đi, cơ bắp tới HLV Trưởng]
```

---

## WF-04: Quy Trình Xử Lý Sự Cố Khẩn Cấp (Emergency & Incidents Workflow)

### 1. Mục Tiêu & Ý Nghĩa
Cung cấp kênh phản ứng nhanh trong 2 tình huống sự cố: (1) Khẩn cấp trên sân tập khi đang chạy và (2) Sự cố bệnh lý cấp tính tại khu chuồng nuôi.

### 2. Sơ Đồ Phân Nhánh Xử Lý Sự Cố

```mermaid
flowchart LR
    subgraph TinhHuongA [Nhánh 1: Khẩn Cấp Trên Sân Tập]
        A1[Buổi tập IN_PROGRESS gặp sự cố] --> A2[HLV bấm Khẩn cấp trên /training-emergency]
        A2 --> A3[Chọn người cứu hộ: CARETAKER hoặc DIAGNOSING_VET]
        A3 --> A4[POST /incidents/training-emergency]
        A4 --> A5[Phát thông báo ưu tiên cao tức thời tới điện thoại nhân sự được chọn]
        A4 --> A6[Chủ ngựa theo dõi được sự cố qua màn hình chi tiết ngựa]
    end

    subgraph TinhHuongB [Nhánh 2: Sự Cố Bệnh Lý Chuồng Trại]
        B1[Nhân viên chuồng phát hiện Colic/Sốt/Chấn thương móng] --> B2[Chụp ảnh hiện trường & tạo báo cáo sự cố]
        B2 --> B3[POST /api/incidents - Severity: CRITICAL/HIGH]
        B3 --> B4[Bác sĩ thú y nhận thông báo trên Clinical Dashboard]
        B4 --> B5{Bác sĩ đưa ra hướng xử lý}
        B5 -->|Acknowledge| B6[POST /acknowledge -> Tạo phiếu khám bệnh lâm sàng]
        B5 -->|Dismiss| B7[POST /dismiss -> Đóng sự cố nếu không cần can thiệp]
    end
```

---

## WF-05: Quy Trình Chăm Sóc Định Kỳ & Phục Hồi Thể Trạng (Stable Care Workflow)

### 1. Mục Tiêu & Ý Nghĩa
Chuẩn hóa các hoạt động vệ sinh chuồng trại, cho ăn theo chế độ dinh dưỡng và hoàn thành nhiệm vụ chăm sóc phục hồi thể lực sau tập luyện.

### 2. Sơ Đồ Tuần Tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Staff as Nhân Viên Chuồng (STABLE_STAFF)
    actor Vet as Bác Sĩ Thú Y (VETERINARIAN)
    participant Sys as Hệ Thống RTMS

    Vet->>Sys: 1. Lập kế hoạch dinh dưỡng (POST /horses/{id}/nutrition-plan)
    Note over Sys: Khẩu phần chi tiết: Bữa sáng, trưa, tối (kg cám, cỏ, khoáng)
    
    Sys->>Sys: 2. Tự động sinh nhiệm vụ định kỳ trong ngày (Routine Catch-up)
    Note over Sys: Sinh tasks: Dọn phân, tắm rửa, ngâm đá, dắt dạo

    Staff->>Sys: 3. Xem danh sách việc trong ca (GET /stable-care/tasks)
    Staff->>Sys: 4. Thực hiện cho ăn & Điểm danh bữa ăn (GET /daily-checklist)
    Note over Sys: Ghi nhận feeding_logs
    
    Staff->>Sys: 5. Hoàn thành nhiệm vụ chăm sóc & Tải ảnh minh chứng (POST /care-tasks/{id}/complete)
    Note over Sys: status = 'COMPLETED', lưu ảnh photo_bytes/photo_url vào DB
```

---

## WF-06: Quy Trình Đăng Ký Đua Ngựa & Bộ Ba Đồng Thuận (Race Consent Triad)

### 1. Mục Tiêu & Ý Nghĩa
Quy trình tam giác bắt buộc để một con ngựa được bước vào đường đua chính thức: Huấn luyện viên đề cử -> Chủ ngựa chấp thuận -> Quản lý câu lạc bộ xác nhận.

### 2. Sơ Đồ Tuần Tự & Ràng Buộc Trigger DB

```mermaid
sequenceDiagram
    autonumber
    actor Manager as Quản Lý CLB (CLUB_MANAGER)
    actor Trainer as HLV Trưởng (HEAD_TRAINER)
    actor Owner as Chủ Sở Hữu (HORSE_OWNER)
    participant DB as SQL Server Trigger Guard

    Manager->>DB: 1. Tạo giải đua chính thức (POST /api/races)
    Note over DB: Giải mở tiếp nhận đăng ký đến hạn chót registrationDeadline

    Trainer->>DB: 2. Nộp đơn đăng ký ngựa thi đấu (POST /races/{id}/registrations)
    Note over DB: Trạng thái bản ghi: SUBMITTED

    alt Chủ ngựa từ chối
        Owner->>DB: 3a. Quyết định từ chối (POST /registrations/{id}/decision {approve:false})
        Note over DB: Trạng thái: REJECT. Quy trình kết thúc, ngựa không được thi đấu!
    else Chủ ngựa đồng thuận
        Owner->>DB: 3b. Quyết định đồng ý (POST /registrations/{id}/decision {approve:true})
        Note over DB: Trạng thái: APPROVE
    end

    Manager->>DB: 4. Phê duyệt xác nhận thi đấu (POST /registrations/{id}/confirm)
    alt Nếu trạng thái chưa phải APPROVE
        DB-->>Manager: ❌ Trigger tr_module_records_race_registration_status_guard CHẶN LỖI!
    else Nếu trạng thái ĐÃ LÀ APPROVE
        Note over DB: Trạng thái chuyển thành CONFIRMED.<br/>Ngựa chính thức có tên trong danh sách xuất phát của giải đua!
    end
```

---

## WF-07: Bệnh Án Bất Biến, Chấn Thương 3D & Khóa Huấn Luyện Y Tế (Medical & Safety Workflow)

### 1. Mục Tiêu & Ý Nghĩa
Thiết lập hàng rào pháp lý y khoa và cơ chế khóa cứng kỹ thuật nhằm bảo vệ tính mạng động vật, ngăn chặn triệt để tình trạng ép ngựa chấn thương ra sân tập.

### 2. Sơ Đồ Quy Trình Chi Tiết

```mermaid
flowchart TD
    Discovered[Phát hiện ngựa có triệu chứng tổn thương] --> VetExam[Bác sĩ thú y khám lâm sàng<br/>POST /horses/{id}/medical-examinations]
    VetExam --> Immutable[Tạo bản ghi bệnh án BẤT BIẾN<br/>Không cho phép ghi đè/sửa đổi nội dung gốc]
    Immutable --> Addendum[Bổ sung diễn tiến điều trị qua Phụ lục - Addenda]

    VetExam --> Pin3D[Chấm điểm tổn thương trên Mô hình ngựa 3D<br/>POST /horses/{id}/injuries]
    Pin3D --> SetSeverity[Xác định 1 trong 42 vùng cơ thể & Độ nghiêm trọng<br/>Trạng thái ban đầu: ACUTE]

    Pin3D --> ApplyLock[Bác sĩ ban hành Khóa Huấn Luyện Y Tế<br/>POST /horses/{id}/training-locks]
    
    subgraph HardEnforcement [Cơ Chế Khóa Cứng Kỹ Thuật]
        ApplyLock --> BlockSchedule[CHẶN TẠO LỊCH TẬP MỚI]
        ApplyLock --> BlockStart[CHẶN BẮT ĐẦU BUỔI TẬP]
        ApplyLock --> BlockIntake[CHẶN TIẾP NHẬN HLV TRƯỞNG]
    end

    HardEnforcement --> Recovering[Cập nhật tiến trình hồi phục vết thương<br/>POST /injuries/{id}/recovery-updates -> RECOVERING]
    Recovering --> Healed[Vết thương bình phục hoàn toàn - HEALED]
    Healed --> ReleaseLock[Bác sĩ giải phóng khóa tập y tế<br/>POST /training-locks/{id}/release]
    ReleaseLock --> Unblock[Ngựa trở lại trạng thái FIT - HLV được tập luyện bình thường]
```

---

## WF-08: Tài Chính, Ví Điện Tử, Kho Vật Tư & Cung Ứng Chuồng (Finance & Inventory Workflow)

### 1. Mục Tiêu & Ý Nghĩa
Quản lý khép kín dòng tiền của khách hàng và chuỗi cung ứng vật tư nội bộ, đảm bảo câu lạc bộ vận hành ổn định và minh bạch.

### 2. Sơ Đồ Tuần Tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Chủ Sở Hữu (HORSE_OWNER)
    actor Staff as Nhân Viên Chuồng (STABLE_STAFF)
    actor Manager as Quản Lý CLB (CLUB_MANAGER)
    participant Auto as Cron Job Tự Động (06:00 AM)
    participant Sys as Hệ Thống Tài Chính & Kho

    Owner->>Sys: 1. Nạp tiền vào ví qua mã QR PayOS (POST /wallet/top-ups)
    Sys->>Sys: 2. Nhận Webhook PayOS xác nhận -> Cộng số dư wallet_balance_vnd
    Owner->>Sys: 3. Mua gói dịch vụ cho từng con ngựa (P30 / P90 / P365)
    Note over Sys: Trừ số dư ví, cập nhật paid_until cho ngựa

    Auto->>Sys: 4. Chạy quét gia hạn tự động lúc 06:00 (WalletRenewalJob)
    Note over Sys: Nếu ví đủ tiền, tự động trừ tiền và gia hạn thêm kỳ mới
    Auto->>Owner: 5. Gửi thông báo & Báo cáo ngày (DailyAutomationJob -> owner_reports)

    Staff->>Sys: 6. Ghi nhận tiêu hao vật tư tại chuồng (POST /inventory/items/{id}/usage)
    Note over Sys: Trừ trực tiếp số lượng tồn kho thực tế
    Staff->>Sys: 7. Lập đề xuất bổ sung kho khi sắp hết (POST /inventory/replenishments)
    Note over Sys: Phiếu đề xuất ở trạng thái PENDING

    Manager->>Sys: 8. Phê duyệt đề xuất cấp phát (POST /replenishments/{id}/review)
    Note over Sys: Trạng thái chuyển thành APPROVED

    Staff->>Sys: 9. Nhận hàng thực tế tại chuồng & Bấm xác nhận (POST /replenishments/{id}/receive)
    Note over Sys: Trạng thái: RECEIVED. Tự động cộng dồn số lượng vào tồn kho thực tế!
```

---

## 3. Tổng Hợp Ma Trận Chuyển Giao Trách Nhiệm (RACI Cross-Role Matrix)

| Quy Trình / Hoạt Động | CLUB_MANAGER | HEAD_TRAINER | STABLE_STAFF | HORSE_OWNER | VETERINARIAN |
|---|---|---|---|---|---|
| **WF-01: Nhập chuồng & Phân ô** | **A** (Phân ô chuồng) | **R** (Tiếp nhận tập) | **C** (Chuẩn bị ô) | **R** (Đóng phí/Khai báo) | **R** (Khám ban đầu) |
| **WF-02: Lịch & Buổi tập** | **I** (Giám sát) | **A / R** (Lập & Chạy tập) | **R** (Xác nhận Ready & Hồi phục) | **I** (Xem lịch) | **I** (Nhận báo động nếu có) |
| **WF-03: Đánh giá thể lực FR07** | **I** (Xem báo cáo) | **A / R** (Chấm điểm FR07) | **C** (Nhận lưu ý Yellow) | **I** (Xem kết quả) | **R** (Đánh giá lâm sàng) |
| **WF-04: Xử lý sự cố khẩn cấp** | **A** (Điều phối chung) | **R** (Phát báo động tập) | **R** (Báo cáo sự cố chuồng) | **I** (Nhận tin tức thời) | **R** (Can thiệp y tế) |
| **WF-05: Chăm sóc & Dinh dưỡng** | **I** (Kiểm tra) | **I** (Xem chế độ) | **A / R** (Cho ăn & Vệ sinh) | **I** (Xem nhật ký) | **R** (Kê thực đơn dinh dưỡng) |
| **WF-06: Đăng ký đua ngựa** | **A** (Xác nhận cuối) | **R** (Đề xuất thi đấu) | **I** (Hỗ trợ vận chuyển) | **R** (Duyệt đồng thuận) | **C** (Kiểm tra thể lực) |
| **WF-07: Bệnh án & Khóa tập** | **I** (Theo dõi) | **I** (Bị khóa tập) | **I** (Chăm sóc vết thương) | **I** (Theo dõi bệnh án) | **A / R** (Khám & Khóa/Mở khóa) |
| **WF-08: Ví, Tài chính & Vật tư** | **A** (Duyệt vật tư & Kế toán) | **I** | **R** (Dùng & Đề xuất kho) | **R** (Nạp tiền & Mua gói) | **C** (Yêu cầu thuốc men) |

*Chú thích viết tắt:*
- **R (Responsible):** Người trực tiếp thực hiện hành động.
- **A (Accountable):** Người chịu trách nhiệm phê duyệt cuối cùng.
- **C (Consulted):** Người được tham vấn chuyên môn hoặc phối hợp.
- **I (Informed):** Người được thông báo để nắm thông tin.
