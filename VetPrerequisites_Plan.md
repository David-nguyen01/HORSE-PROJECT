# KẾ HOẠCH SỬA LỖI & BỔ SUNG — Vai trò Bác sĩ thú y (Pre-VetTrainingSupervision)

> **Phiên bản:** 2.0 (Cập nhật sau review UI)
> **Ngày:** 2026-10-04
> **Trạng thái:** CẦN THỰC HIỆN TRƯỚC `VetTrainingSupervision_Plan.md`
> **Branch mục tiêu:** `bacsithuy` — KHÔNG chỉnh sửa branch nào khác

---

## Mục lục

1. [Xóa tất cả đầu mục FR trên giao diện](#1-xóa-tất-cả-đầu-mục-fr-trên-giao-diện)
2. [Lọc ngựa theo trạng thái đã trả phí](#2-lọc-ngựa-theo-trạng-thái-đã-trả-phí)
3. [Lỗi badge thể trạng hiển thị sai — Chỗ chọn ngựa & Bản đồ chuồng](#3-lỗi-badge-thể-trạng-hiển-thị-sai--chỗ-chọn-ngựa--bản-đồ-chuồng)
4. [Bảng điều khiển y tế — Hàng chờ, Chấn thương, Báo cáo sự cố](#4-bảng-điều-khiển-y-tế--hàng-chờ-chấn-thương-báo-cáo-sự-cố)
5. [Bản đồ chuồng — Chỉ xem, không chỉnh khóa lịch](#5-bản-đồ-chuồng--chỉ-xem-không-chỉnh-khóa-lịch)
6. [Hồ sơ bệnh án & chấn thương](#6-hồ-sơ-bệnh-án--chấn-thương)
7. [Bỏ tab "Chăm sóc phòng ngừa" — Tích hợp vào Phiếu khám](#7-bỏ-tab-chăm-sóc-phòng-ngừa--tích-hợp-vào-phiếu-khám)
8. [Trang đánh giá buổi tập](#8-trang-đánh-giá-buổi-tập)
9. [Hồ sơ ngựa — Chỉ xem](#9-hồ-sơ-ngựa--chỉ-xem)
10. [Checklist thực thi](#10-checklist-thực-thi)

---

## 1. Xóa tất cả đầu mục FR trên giao diện

### Vấn đề
Toàn bộ các trang của vai trò bác sĩ thú y đang hiển thị các nhãn `FR08`, `FR09`, `FR10`, `FR11`, `FR12`, ... trực tiếp trên UI. Đây là mã nội bộ tài liệu yêu cầu, không nên hiển thị với người dùng cuối.

### Phạm vi
File chính: `FRONTEND/src/VetPage.jsx`

### Cách sửa
Tìm và xóa/thay thế toàn bộ chuỗi dạng `FR\d+` trong JSX text nodes:

| Trước (cần xóa) | Sau (thay bằng) |
|---|---|
| `FR08 - Bản đồ sức khỏe chuồng` | `Bản đồ sức khỏe chuồng` |
| `FR09 - Hồ sơ khám bệnh` | `Hồ sơ khám bệnh` |
| `FR10 - Theo dõi chấn thương` | `Theo dõi chấn thương` |
| `FR11 - Khoá huấn luyện` | *(tab này đã bị loại bỏ)* |
| `FR12 - Chăm sóc phòng ngừa` | *(tab này sẽ bị loại bỏ — xem §7)* |

---

## 2. Lọc ngựa theo trạng thái đã trả phí

### Vấn đề
Các dropdown/danh sách chọn ngựa đang hiển thị **tất cả ngựa** trong hệ thống, kể cả ngựa chưa trả phí. Theo nghiệp vụ, bác sĩ thú y chỉ tiếp nhận ngựa đã trả phí (đã qua bước thanh toán từ phía Horse Owner).

### Quy tắc
- **Không** thêm label "đã trả phí / chưa trả phí" vào dropdown.
- **Chỉ đơn giản**: lọc sẵn → dropdown chỉ chứa ngựa đủ điều kiện.
- Ngựa chưa trả phí: **ẩn hoàn toàn** khỏi mọi dropdown/danh sách trong VetPage.

### Nơi áp dụng
Tất cả dropdown chọn ngựa trong: MedicalRecordsView, BarnHealthMapView, NutritionProposalView, Dashboard.

### Backend
```
GET /api/horses/eligible-for-medical
→ Trả về ngựa có payment_status = 'PAID'
  hoặc medical_status IN ('IN_MEDICAL', 'PENDING_TRAINING', 'ACTIVE')
```

### Frontend (`vetApi.js`)
```js
getEligibleHorses: () => api('/api/horses/eligible-for-medical'),
// Thay toàn bộ getHorses() trong VetPage bằng getEligibleHorses()
```

---

## 3. Lỗi badge thể trạng hiển thị sai — Chỗ chọn ngựa & Bản đồ chuồng

### Mô tả lỗi (xem ảnh đính kèm)

Trong ảnh chụp màn hình trang Hồ sơ bệnh án:
- Con ngựa **Demo (01345)** được chọn.
- Bên cạnh tên ngựa hiển thị badge: **"Khỏe mạnh / Đủ điều kiện"** (màu xanh).
- Nhưng ngay bên dưới, phiếu khám đã ghi rõ **"Thể trạng sau khám: Bị thương / Chấn thương"**.

**→ Đây là mâu thuẫn trực tiếp**: Ngựa đang bị chấn thương nhưng badge vẫn hiện "Khỏe mạnh". Cùng lỗi này xảy ra ở bản đồ chuồng — ô chuồng hiển thị trạng thái cũ/sai so với kết quả khám gần nhất.

### Nguyên nhân
Badge hiện tại lấy từ một trường `healthStatus` tĩnh trên object ngựa (ví dụ `horse.status` hoặc `horse.healthCondition`) — trường này **không được cập nhật** khi bác sĩ tạo phiếu khám có `healthStatusAfter = "INJURED"`.

### Giải pháp

**Quy tắc nghiệp vụ:**
> Badge thể trạng bên cạnh tên ngựa = **thể trạng hiện tại thực tế** = giá trị `healthStatusAfter` của phiếu khám **gần nhất**.  
> Mỗi khi tạo phiếu khám mới → backend tự động cập nhật `horses.health_status` → badge tự cập nhật theo.

**Backend — `MedicalExaminationService.create()`:**
```java
// Sau khi lưu phiếu khám, cập nhật health_status lên bảng horses:
if (request.healthStatusAfter() != null) {
    horseRepository.updateHealthStatus(horseId, request.healthStatusAfter());
}
// → Đây là bước BẮT BUỘC, không phải tùy chọn
```

**Frontend — Badge hiển thị:**
```jsx
// Nguồn dữ liệu badge phải là horse.healthStatus (đã được sync từ phiếu khám)
// KHÔNG dùng giá trị hardcode hay trường khác

function healthBadge(status) {
  const map = {
    'HEALTHY':      { label: 'Khỏe mạnh',        color: 'green'  },
    'MONITORING':   { label: 'Cần theo dõi',      color: 'yellow' },
    'INJURED':      { label: 'Bị thương',          color: 'red'    },
    'UNDER_TREATMENT': { label: 'Đang điều trị',  color: 'orange' },
    'RECOVERING':   { label: 'Đang hồi phục',     color: 'blue'   },
  };
  const s = map[status] ?? { label: status, color: 'gray' };
  return <span className={`badge badge-${s.color}`}>{s.label}</span>;
}
```

**Bản đồ chuồng:** Ô chuồng cũng phải hiển thị `horse.healthStatus` (đã sync) — không hardcode màu hay label dựa vào trường khác.

---

## 4. Bảng điều khiển y tế — Hàng chờ, Chấn thương, Báo cáo sự cố

### 4.1 Hàng chờ tiếp nhận khám — Đang đúng

**Luồng hiện tại hoạt động đúng (đã confirm từ code):**

```
Horse owner đăng ký ngựa → payment_status = 'PAID'
        ↓ (getPendingQueue() lọc WHERE payment_status='PAID' AND không có MEDICAL_ASSIGNMENT ACTIVE)
Ngựa xuất hiện trong hàng chờ Dashboard
        ↓ bác sĩ bấm [Tiếp nhận]
receiveHorse() → tạo module_records(MEDICAL_ASSIGNMENT, ACTIVE) + payment_status = 'IN_MEDICAL'
        ↓ ngựa biến khỏi hàng chờ, vào "Đang trong ca khám"
Bác sĩ khám xong → bấm [Hoàn tất khám]
        ↓
completeMedical() → MEDICAL_ASSIGNMENT → COMPLETED + payment_status = 'PENDING_TRAINING'
        ↓ gửi thông báo Nhân viên chăm sóc xếp chuồng ✅ (đã có)
Ngựa biến khỏi "Đang trong ca khám"
```

**Vấn đề còn thiếu trong luồng hoàn tất:**
- `completeMedical()` chuyển `payment_status = 'PENDING_TRAINING'` nhưng **không kiểm tra** `healthStatusAfter` từ phiếu khám để tạo Training Lock tự động.
- Nếu ngựa bị chấn thương, HLV Trưởng vẫn có thể lên lịch tập vì không có lock nào được tạo.
- **Cần fix:** Sau `completeMedical()`, đọc phiếu khám gần nhất của ngựa đó, nếu `healthStatusAfter` là `INJURED`/`UNDER_TREATMENT` → tạo Training Lock.

---

### 4.2 Mục "Ngựa đang chấn thương & cần can thiệp y tế" — CÓ LỖI LOGIC

**Phân tích code hiện tại (line 348–351 VetPage.jsx):**
```js
const injuredHorses = useMemo(
  () => horses.filter((h) =>
    ['INJURED', 'UNDER_TREATMENT'].includes(h.healthStatus)
    || h.status === 'INJURED'   // ← trường này không tồn tại trong API response
  ),
  [horses]
)
```

**Vấn đề 1 — Nguồn dữ liệu sai:**
- `horses` được load từ `vetApi.getEligibleHorses()` → endpoint trả về ngựa đã trả phí.
- Nhưng API `/api/horses/eligible-for-medical` (hoặc tương đương) trả về field `healthStatus` là gì? **Cần kiểm tra** — nếu backend trả về `healthStatus = null` hoặc trường tên khác (như `healthCondition`, `condition`...) thì filter sẽ luôn rỗng → mục luôn hiện "Không có ngựa nào chấn thương".

**Vấn đề 2 — `h.status === 'INJURED'` không nhất quán:**
- `h.status` thường là `payment_status` (`PAID`, `IN_MEDICAL`, `PENDING_TRAINING`, `ACTIVE`...) không phải health status.
- Filter này sẽ không bao giờ match → vô nghĩa.

**Vấn đề 3 — Badge thể trạng không được cập nhật (liên quan §3):**
- Khi bác sĩ tạo phiếu khám với `healthStatusAfter = 'INJURED'`, `horses.healthStatus` trong API **không được cập nhật** vì backend chưa viết vào bảng `horses`.
- Kết quả: ngựa thực sự bị chấn thương nhưng `h.healthStatus` vẫn là `null` hoặc `HEALTHY` → không lọc vào `injuredHorses`.

**Hướng fix:**
```js
// Sau khi fix backend cập nhật horses.health_status khi tạo phiếu khám (§3):
const injuredHorses = useMemo(
  () => horses.filter((h) =>
    ['INJURED', 'UNDER_TREATMENT'].includes(h.healthStatus)
  ),
  [horses]
)
// Xóa || h.status === 'INJURED' (không có nghĩa)
```

**Backend cần thêm (cho MedicalExaminationService.create()):**
```java
// Sau khi lưu phiếu khám, cập nhật health_status lên bảng horses:
horseRepository.updateHealthStatus(horseId, request.healthStatusAfter());
// Nếu là INJURED/UNDER_TREATMENT → tạo Training Lock tự động
```

---

### 4.3 Báo cáo sự cố từ Nhân viên chuồng — CÓ BUG: không biến mất sau khi tạo phiếu khám

**Phân tích code hiện tại:**

**Frontend (line 306):**
```js
vetApi.getIncidents({ status: 'REPORTED' })
// Lọc incidents có status = 'REPORTED'
```

**Backend (IncidentReportController.java):**
```java
// GET /api/incidents chỉ có PERM_STABLE_CARE_VIEW
// → Bác sĩ thú y có PERM_STABLE_CARE_VIEW không? Cần kiểm tra
```

**Backend (IncidentReportService.updateStatus):**
```java
// Để đóng incident, phải gọi PUT /api/incidents/{id}/status
// với body { "status": "RESOLVED", "resolutionNotes": "..." }
// Nhưng endpoint này chỉ có PERM_STABLE_CARE_EDIT → bác sĩ KHÔNG có quyền này!
```

**Root cause của bug:**
1. Bác sĩ bấm [Tạo phiếu khám] → chỉ navigate sang trang medical-records, KHÔNG gọi `updateStatus(RESOLVED)` cho incident.
2. Không có bước nào tự động đánh dấu incident là RESOLVED sau khi tạo phiếu khám.
3. → Incident vẫn có `status = 'REPORTED'` → vẫn hiện mãi trên Dashboard.

**Hướng fix — 2 thứ cần làm:**

**Fix 1 — Backend: cấp quyền xử lý incident cho VETERINARIAN:**
```java
// IncidentReportController.java — thêm endpoint riêng cho vet acknowledge:
@PostMapping("/{id}/acknowledge")
@PreAuthorize("hasAuthority('PERM_MEDICAL_EDIT')")  // Vet đã có quyền này
public IncidentReportResponse acknowledge(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) Map<String, String> body) {
    String notes = body != null ? body.getOrDefault("notes", "") : "";
    return service.updateStatus(id, UUID.fromString(jwt.getSubject()),
        new UpdateIncidentStatusRequest("ACKNOWLEDGED", notes));
}
```

**Fix 2 — Frontend: khi bác sĩ bấm [Tạo phiếu khám] từ incident → tự động đánh dấu ACKNOWLEDGED:**
```jsx
// Trong section Báo cáo sự cố, thay onClick của nút [Tạo phiếu khám]:
async function handleCreateExamFromIncident(inc) {
  try {
    // Đánh dấu incident đã được xử lý
    await vetApi.acknowledgeIncident(inc.id, 'Đã tạo phiếu khám')
    // Reload incidents để nó biến khỏi dashboard
    await loadData()
  } catch (e) {
    // không block nếu lỗi acknowledge
    console.warn('Không thể cập nhật trạng thái sự cố:', e)
  }
  // Điều hướng sang trang khám bệnh
  onNavigate(`medical-records?horseId=${inc.horseId}`)
}
```

**Fix 3 — vetApi.js: thêm method:**
```js
acknowledgeIncident: (incidentId, notes = '') =>
  api(`/api/incidents/${incidentId}/acknowledge`, {
    method: 'POST',
    body: JSON.stringify({ notes }),
  }),
```

**Trạng thái lifecycle của Incident (cần thống nhất):**
```
REPORTED → ACKNOWLEDGED (bác sĩ ghi nhận, đang xử lý)
         → RESOLVED (đã xử lý xong, có ghi chú)
         → DISMISSED (không cần can thiệp)
```

**Hiển thị nút hành động trong Dashboard:**
```
[Tạo phiếu khám]   → acknowledge + navigate sang medical
[Theo dõi thêm]    → acknowledge (ghi nhận nhưng không khám ngay)
[Không cần xử lý]  → DISMISSED
```

---

### 4.2 Mục "Ngựa đang chấn thương & cần can thiệp y tế" — Để làm gì?

**Giải thích:**
Đây là danh sách **theo dõi đang diễn ra** — các con ngựa đã được chẩn đoán chấn thương hoặc cần điều trị nhưng **chưa hồi phục xong**. Bác sĩ cần theo dõi tiến trình của chúng hàng ngày.

**Ngựa được đưa vào mục này khi nào:**
- Bác sĩ tạo phiếu khám với `healthStatusAfter = INJURED` hoặc `UNDER_TREATMENT`.
- Hoặc Nhân viên chuồng gửi báo cáo sự cố nghiêm trọng mà bác sĩ xác nhận cần theo dõi.

**Ngựa rời khỏi mục này khi nào:**
- Bác sĩ tạo phiếu khám mới với `healthStatusAfter = HEALTHY` hoặc `RECOVERING` (không còn cần can thiệp).
- Bác sĩ bấm "Giải phóng khỏi danh sách theo dõi" (nếu cần thủ công).

**Hiển thị trong Dashboard:**
```
┌────────────────────────────────────────────────────────────────┐
│  ĐANG CHẤN THƯƠNG & CẦN THEO DÕI Y TẾ                        │
│  (3 ngựa)                                                      │
├──────┬──────────┬───────────────┬──────────────┬──────────────┤
│ Mã   │ Tên ngựa │ Chẩn đoán     │ Từ ngày      │ Hành động    │
├──────┼──────────┼───────────────┼──────────────┼──────────────┤
│ H001 │ Thunder  │ Gãy gân       │ 01/10/2026   │ [Xem hồ sơ] │
│      │          │ (FL_CANNON_L) │              │ [Cập nhật]  │
├──────┼──────────┼───────────────┼──────────────┼──────────────┤
│ H003 │ Storm    │ Viêm khớp     │ 28/09/2026   │ [Xem hồ sơ] │
│      │          │ (HL_HOCK_R)   │              │ [Cập nhật]  │
└──────┴──────────┴───────────────┴──────────────┴──────────────┘
```

**Nút [Cập nhật]** → mở nhanh form thêm phụ lục theo dõi / cập nhật tình trạng hồi phục cho ngựa đó.

---

### 4.3 Báo cáo sự cố từ Nhân viên chuồng — Để làm gì?

**Giải thích:**
Nhân viên chăm sóc hàng ngày phát hiện bất thường ở ngựa (bỏ ăn, đi khập, vết thương nhỏ, hành vi lạ...) và **ghi báo cáo sự cố** trong trang của họ (StableCarePage). Bác sĩ nhận báo cáo này để **quyết định có cần can thiệp y tế hay không**.

**Luồng xử lý:**
```
Nhân viên chuồng phát hiện sự cố → Ghi báo cáo (StableCarePage)
        ↓
Thông báo đến Bác sĩ thú y
        ↓
Bác sĩ xem báo cáo trong Dashboard → 3 lựa chọn:
  [Đã xem - không cần can thiệp] → Đóng báo cáo, ghi chú
  [Tạo phiếu khám]               → Mở form tạo phiếu khám cho ngựa đó
  [Theo dõi thêm]                → Đánh dấu cần theo dõi, không khám ngay
```

**Hiển thị trong Dashboard:**
```
┌────────────────────────────────────────────────────────────────┐
│  BÁO CÁO SỰ CỐ TỪ NHÂN VIÊN CHUỒNG                          │
│  (2 báo cáo chưa xử lý)                                       │
├──────┬──────────┬───────────────────┬──────────┬─────────────┤
│ Mã   │ Tên ngựa │ Nội dung          │ Thời gian│ Xử lý       │
├──────┼──────────┼───────────────────┼──────────┼─────────────┤
│ H002 │ Wind     │ Bỏ ăn sáng,       │ 08:30    │ [Xem]       │
│      │          │ đứng không yên    │          │ [Tạo phiếu] │
│      │          │                   │          │ [Bỏ qua]    │
├──────┼──────────┼───────────────────┼──────────┼─────────────┤
│ H004 │ Flash    │ Có vết trầy xước  │ 11:00    │ [Xem]       │
│      │          │ ở chân trái       │          │ [Tạo phiếu] │
│      │          │                   │          │ [Bỏ qua]    │
└──────┴──────────┴───────────────────┴──────────┴─────────────┘
```

---

## 5. Bản đồ chuồng — Chỉ xem, không chỉnh khóa lịch

### Vấn đề
Trang bản đồ chuồng đang có thao tác chỉnh Training Lock trực tiếp. Việc khóa/mở lịch tập phải xảy ra **tự động qua kết quả chẩn đoán**, không phải thao tác thủ công trên bản đồ.

### Yêu cầu

**Giữ lại:**
- ✅ Xem bản đồ vị trí ô chuồng
- ✅ Click chọn 1 ngựa trong chuồng → panel hiển thị thể trạng của ngựa đó (chỉ đọc)
- ✅ Badge màu trạng thái sức khỏe trên từng ô chuồng (lấy từ `horse.healthStatus` đã sync — xem §3)

**Xóa hoàn toàn:**
- ❌ Nút / form chỉnh sửa Training Lock
- ❌ Dropdown cập nhật thể trạng trực tiếp trên bản đồ (chỉ xem)

### Panel chọn ngựa (chỉ đọc)
```
┌──────────────────────────────────────┐
│  Thunder (H001)                      │
│  Ô chuồng: A-01 | Chuồng A          │
│  Thể trạng: 🔴 Bị thương            │  ← từ horse.healthStatus (đã sync)
│  Trạng thái y tế: Đang điều trị     │
│  Lần khám gần nhất: 01/10/2026       │
│  Lịch tập: 🔒 Bị khóa               │
│                                      │
│  [→ Xem hồ sơ bệnh án]              │
└──────────────────────────────────────┘
```

---

## 6. Hồ sơ bệnh án & chấn thương

### 6.1 Cập nhật thể trạng tự động khi tạo phiếu khám

**Quy tắc (bắt buộc):** Khi bác sĩ lưu phiếu khám có trường `healthStatusAfter`:
1. **Cập nhật `horses.health_status`** ngay lập tức (backend).
2. Nếu `healthStatusAfter` thuộc nhóm chấn thương → **tạo Training Lock tự động**.
3. Thông báo HLV Trưởng biết ngựa bị khóa lịch.

```java
// MedicalExaminationService.create() — LUÔN thực hiện sau lưu phiếu khám:
horseRepository.updateHealthStatus(horseId, request.healthStatusAfter());

boolean isInjured = List.of("INJURED", "UNDER_TREATMENT").contains(request.healthStatusAfter());
if (isInjured) {
    trainingLockService.createAutoLock(horseId,
        "Khóa tự động từ kết quả chẩn đoán: " + request.diagnosis(),
        request.followUpDate()
    );
    notificationService.notifyHeadTrainer(
        "Ngựa " + horse.getCode() + " đã bị khóa lịch tập. Lý do: " + request.diagnosis()
    );
}
```

---

### 6.2 Dropdown vị trí giải phẫu cho phiếu chấn thương

**Vấn đề:** Ô nhập vị trí chấn thương hiện là text tự do → không đồng nhất, không thể ánh xạ lên model 3D sau này.

**Thay bằng Dropdown có nhóm (grouped dropdown), cho phép chọn nhiều:**

```
Nhóm: Đầu & Cổ
  HEAD_POLL        → Đỉnh đầu (Poll)
  HEAD_FOREHEAD    → Trán
  HEAD_EYE_LEFT    → Mắt trái
  HEAD_EYE_RIGHT   → Mắt phải
  NECK_CREST       → Gáy
  NECK_THROAT      → Yết hầu

Nhóm: Thân mình
  BACK_WITHERS     → Vai (Withers)
  BACK_SADDLE      → Lưng (Saddle Region)
  BACK_LOIN        → Hông (Loin)
  CROUP            → Mông (Croup)
  CHEST            → Ngực
  ABDOMEN          → Bụng
  FLANK            → Sườn (Flank)

Nhóm: Chân trước (Forelimb)
  FL_SHOULDER_LEFT/RIGHT   → Vai trái/phải
  FL_ELBOW_LEFT/RIGHT      → Khuỷu tay trái/phải
  FL_KNEE_LEFT/RIGHT       → Gối trước trái/phải (Knee/Carpus)
  FL_CANNON_LEFT/RIGHT     → Ống chân trước trái/phải (Cannon Bone)
  FL_FETLOCK_LEFT/RIGHT    → Cổ chân trước trái/phải (Fetlock)
  FL_HOOF_LEFT/RIGHT       → Móng trước trái/phải

Nhóm: Chân sau (Hindlimb)
  HL_HIP_LEFT/RIGHT        → Háng trái/phải (Hip)
  HL_STIFLE_LEFT/RIGHT     → Gối sau trái/phải (Stifle)
  HL_HOCK_LEFT/RIGHT       → Khoeo trái/phải (Hock)
  HL_CANNON_LEFT/RIGHT     → Ống chân sau trái/phải
  HL_FETLOCK_LEFT/RIGHT    → Cổ chân sau trái/phải
  HL_HOOF_LEFT/RIGHT       → Móng sau trái/phải

Nhóm: Mô mềm / Khác
  TENDON_LEFT/RIGHT        → Gân trái/phải
  LIGAMENT                 → Dây chằng
  SKIN_LACERATION          → Da / Vết rách
  OTHER                    → Khác (kèm ghi chú)
```

**Lưu trữ:** `body_regions = 'FL_KNEE_LEFT,FL_CANNON_LEFT'` (comma-separated string, không tạo bảng mới).

**DTO:**
```java
// CreateInjuryMarkerRequest — thay field locationDescription:
List<String> bodyRegions;   // ["FL_KNEE_LEFT", "FL_CANNON_LEFT"]
String additionalNotes;     // ghi chú thêm, nhất là khi chọn OTHER
```

---

## 7. Bỏ tab "Chăm sóc phòng ngừa" — Tích hợp vào Phiếu khám

### Quyết định
**Loại bỏ hoàn toàn tab "Chăm sóc phòng ngừa / Tiêm phòng & Tẩy giun"** khỏi sidebar VetPage.

**Lý do:** Tất cả chức năng tiêm phòng, tẩy giun, lịch nhắc đều được thực hiện **trong phiếu khám** — không cần trang riêng.

---

### 7.1 Tích hợp vào form tạo phiếu khám

Khi bác sĩ tạo phiếu khám, form có thêm section cuối:

```
┌──────────────────────────────────────────────────────────────┐
│  LỊCH CHĂM SÓC PHÒNG NGỪA (tùy chọn — có thể bỏ qua)      │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  + Thêm lịch tiêm / tẩy giun                          │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  [Lịch 1]                                                   │
│  Loại:          [Dropdown: Tiêm phòng | Tẩy giun | Khác]   │
│  Tên/Loại vắc-xin: [________________________]               │
│  Ngày dự kiến:  [Date picker]                               │
│  Nhắc nhở:      ☑ Gửi thông báo đến Nhân viên chăm sóc     │
│  Ghi chú:       [________________________]                  │
│  [Xóa lịch này ×]                                           │
│                                                              │
│  [+ Thêm lịch phòng ngừa khác]                              │
└──────────────────────────────────────────────────────────────┘
```

---

### 7.2 Xem lịch đã đặt trong hồ sơ ngựa

Khi xem phiếu khám cũ, hiển thị các lịch phòng ngừa đã được đặt trong lần khám đó:

```
PHIẾU KHÁM — 01/10/2026
...
📅 Lịch phòng ngừa đã đặt trong lần khám này:
  • Tiêm phòng Cúm ngựa — Dự kiến: 15/10/2026
    Trạng thái: ⏳ Chờ thực hiện  [Xóa]
  • Tẩy giun Ivermectin — Dự kiến: 01/11/2026
    Trạng thái: ✅ Đã thực hiện (ngày 01/11/2026)
```

---

### 7.3 Thông báo đến Nhân viên chăm sóc

Khi bác sĩ lưu phiếu khám có kèm lịch phòng ngừa → **tự động gửi thông báo** đến Nhân viên chăm sóc:

```
Thông báo: Lịch tiêm phòng mới cho ngựa Thunder (H001)
Loại: Tiêm phòng Cúm ngựa
Ngày dự kiến: 15/10/2026
Ghi chú: [ghi chú của bác sĩ]
→ [Đánh dấu đã thực hiện]
```

**Nhân viên chăm sóc** (StableCarePage) là người **đánh dấu đã tiêm/tẩy giun** sau khi thực hiện.

---

### 7.4 Backend cần sửa/thêm

**`CreateMedicalExaminationRequest` — thêm field:**
```java
List<CreatePreventiveCareRequest> preventiveSchedules; // nullable, có thể rỗng
```

**`MedicalExaminationService.create()` — xử lý thêm:**
```java
if (request.preventiveSchedules() != null) {
    for (var schedule : request.preventiveSchedules()) {
        var care = preventiveCareService.create(horseId, schedule);
        // gửi thông báo đến Nhân viên chăm sóc
        notificationService.notifyStableCare(
            "Lịch " + care.getType() + " mới cho ngựa " + horse.getCode()
            + " vào ngày " + care.getScheduledDate()
        );
    }
}
```

**`PreventiveCareController` — mở thêm endpoint xóa** (đã có `cancelPreventiveCare` trong vetApi.js, cần verify backend):
```
DELETE /api/preventive-care/{id}  → VETERINARIAN được phép xóa lịch do mình tạo
```

---

### 7.5 Cập nhật Sidebar VetPage

**Sidebar sau khi bỏ tab Phòng ngừa:**

| STT | Tab | Ghi chú |
|---|---|---|
| 1 | 🏠 Bảng điều khiển | Dashboard tổng quan |
| 2 | 🗺️ Bản đồ chuồng | Chỉ xem |
| 3 | 📋 Hồ sơ bệnh án | Phiếu khám + Chấn thương (tích hợp lịch phòng ngừa) |
| 4 | 🥗 Dinh dưỡng | Đề xuất khẩu phần |
| 5 | 🐴 Hồ sơ ngựa | Full profile, chỉ xem |
| 6 | 📋 Đánh giá buổi tập | Đánh giá sau tập |
| ~~7~~ | ~~💉 Tiêm phòng & Tẩy giun~~ | **Đã tích hợp vào tab 3** |

---

## 8. Trang đánh giá buổi tập

### Vấn đề
Chưa có tab/trang để bác sĩ đánh giá thể trạng ngựa sau buổi tập. HLV Trưởng đã có trang đánh giá riêng — bác sĩ cần bổ sung đánh giá lâm sàng kèm theo.

### Luồng
```
HLV Trưởng lập lịch tập
        ↓
Buổi tập diễn ra (05:00–11:00)
        ↓
Bác sĩ vào tab "Đánh giá buổi tập" → hệ thống hiển thị ngựa tập hôm nay
   (lấy từ training_schedules của ngày hôm đó)
        ↓
Bác sĩ chọn ngựa → điền đánh giá lâm sàng → [Gửi]
        ↓
Dữ liệu lưu vào training_evaluations
Thông báo gửi cho HLV Trưởng: "Bác sĩ đã hoàn thành đánh giá cho Thunder"
```

### UI — Danh sách ngựa tập trong ngày
```
┌────────────────────────────────────────────────────────────┐
│  ĐÁNH GIÁ BUỔI TẬP                                        │
│  Ngày: [04/10/2026 ▼]                                     │
│                                                            │
├──────┬──────────┬────────────┬─────────────────────────── ┤
│ Mã   │ Tên ngựa │ Sân tập    │ Đánh giá bác sĩ            │
├──────┼──────────┼────────────┼────────────────────────────┤
│ H001 │ Thunder  │ Sân tập 01 │ ⏳ Chưa đánh giá [Đánh giá]│
│ H002 │ Storm    │ Sân tập 02 │ ✅ Đã đánh giá  [Xem lại] │
└──────┴──────────┴────────────┴────────────────────────────┘
```

### Form đánh giá lâm sàng

```
Thể trạng tổng quan:    [Dropdown: Tốt / Bình thường / Cần theo dõi / Nghiêm trọng]
Nhịp tim phục hồi:      [____] bpm (sau 10 phút nghỉ)
Hô hấp:                 [Dropdown: Bình thường / Khó thở nhẹ / Khó thở nặng]
Dáng đi:                [Dropdown: Bình thường / Khập khiễng nhẹ / Khập khiễng rõ]
Tình trạng cơ bắp:      [Dropdown: Tốt / Mệt mỏi / Căng cứng / Co rút]
Kết luận lâm sàng:      [textarea]
Khuyến nghị:            [textarea]
Cần can thiệp y tế:     ○ Có  ● Không
  (Nếu Có → mở thêm link tạo phiếu khám cho ngựa này)
```

### API Backend

```
// Mở GET /api/training-schedules cho VETERINARIAN xem lịch tập:
@PreAuthorize("... hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")

// Endpoint mới lưu đánh giá lâm sàng của bác sĩ:
POST /api/training-evaluations/{scheduleId}/vet-assessment
```

### Frontend (`vetApi.js`)
```js
getTrainingSessionsByDate: (date) =>
  api(`/api/training-schedules?date=${date}`),

submitVetAssessment: (scheduleId, payload) =>
  api(`/api/training-evaluations/${scheduleId}/vet-assessment`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
```

---

## 9. Hồ sơ ngựa — Chỉ xem

### Yêu cầu
Tab "Hồ sơ ngựa" — bác sĩ xem đầy đủ thông tin ngựa, **chỉ đọc**, không chỉnh sửa thông tin cơ bản.

### Thông tin hiển thị

```
Thông tin cơ bản:
  Mã ngựa / Tên / Giống / Ngày sinh / Giới tính / Màu lông
  Cân nặng (đo gần nhất) / Chiều cao
  Chủ sở hữu / Ô chuồng hiện tại

Trạng thái hiện tại:
  Thể trạng:       🔴 Bị thương           ← từ horse.healthStatus (đã sync)
  Trạng thái y tế: Đang điều trị
  Lịch tập:        🔒 Bị khóa (đến 15/10)

Tóm tắt lịch sử y tế:
  Số lần khám trong năm / Lần khám gần nhất
  Chấn thương đang theo dõi
  Lần tiêm phòng / tẩy giun gần nhất

Kế hoạch huấn luyện:
  Giai đoạn hiện tại / HLV phụ trách / Thời gian
```

### API dùng (tất cả đã có trong vetApi.js)
```
GET /api/horses/{id}
GET /api/horses/{id}/health-status
GET /api/horses/{id}/medical-examinations
GET /api/horses/{id}/injuries
GET /api/horses/{id}/training-locks/current
```

---

## 10. Checklist thực thi

> Thực hiện theo thứ tự — mỗi nhóm nên commit riêng.

### Sprint 1 — Cleanup & Data Fixes (Ưu tiên cao nhất)
- [ ] **[FE]** Xóa toàn bộ nhãn `FR\d+` khỏi JSX text trong `VetPage.jsx`
- [ ] **[BE]** `MedicalExaminationService.create()` → tự động cập nhật `horses.health_status` sau mỗi phiếu khám
- [ ] **[FE]** Badge thể trạng cạnh tên ngựa: dùng `horse.healthStatus` (sau sync) — không hardcode
- [ ] **[FE]** Badge trên ô chuồng (BarnHealthMapView): dùng `horse.healthStatus` — không hardcode
- [ ] **[BE]** Thêm endpoint `GET /api/horses/eligible-for-medical` (chỉ ngựa đã trả phí)
- [ ] **[FE]** Thay `getHorses()` → `getEligibleHorses()` toàn bộ VetPage

### Sprint 2 — Dashboard Logic
- [ ] **[BE]** Verify luồng PAID → hàng chờ → IN_MEDICAL → complete
- [ ] **[BE]** `MedicalExaminationService.complete()` → tạo Training Lock tự động nếu diagnosis = chấn thương
- [ ] **[BE]** Gửi thông báo Nhân viên chăm sóc sau khi bác sĩ hoàn thành khám
- [ ] **[BE]** Validate `TrainingScheduleService.create()`: từ chối nếu ngựa đang `IN_MEDICAL`
- [ ] **[FE]** Dashboard: hiển thị đúng 3 section (Hàng chờ / Đang chấn thương & theo dõi / Báo cáo sự cố)
- [ ] **[FE]** Section "Đang chấn thương": lấy ngựa có `healthStatus IN (INJURED, UNDER_TREATMENT)`
- [ ] **[FE]** Section "Báo cáo sự cố": nút [Tạo phiếu khám] dẫn thẳng sang form tạo phiếu

### Sprint 3 — BarnHealthMap & Medical Records
- [ ] **[FE]** BarnHealthMapView: xóa UI chỉnh Training Lock, panel chọn ngựa → chỉ đọc
- [ ] **[FE]** Form phiếu chấn thương: thay text input vị trí → grouped dropdown giải phẫu (§6.2)
- [ ] **[BE]** `CreateInjuryMarkerRequest`: thay `locationDescription` → `List<String> bodyRegions`

### Sprint 4 — Tích hợp Phòng ngừa vào Phiếu khám
- [ ] **[FE]** Xóa tab "Chăm sóc phòng ngừa" khỏi sidebar VetPage
- [ ] **[BE]** `CreateMedicalExaminationRequest`: thêm `List<CreatePreventiveCareRequest> preventiveSchedules`
- [ ] **[FE]** Form tạo phiếu khám: thêm section "Lịch chăm sóc phòng ngừa" (tùy chọn, multi-item)
- [ ] **[BE]** `MedicalExaminationService.create()`: khi có `preventiveSchedules` → tạo lịch + gửi thông báo Nhân viên chăm sóc
- [ ] **[FE]** Xem phiếu khám cũ: hiển thị lịch phòng ngừa đã đặt + trạng thái + nút xóa

### Sprint 5 — Tabs mới
- [ ] **[FE]** Tab "Đánh giá buổi tập": danh sách ngựa tập hôm nay + form đánh giá lâm sàng (§8)
- [ ] **[BE]** Mở `GET /api/training-schedules` cho VETERINARIAN
- [ ] **[BE]** Thêm `POST /api/training-evaluations/{scheduleId}/vet-assessment`
- [ ] **[FE]** `vetApi.js`: thêm `getTrainingSessionsByDate`, `submitVetAssessment`
- [ ] **[FE]** Tab "Hồ sơ ngựa": component `HorseProfileView` chỉ đọc (§9)

### Sprint 6 — Build & Test
- [ ] `mvn clean package -DskipTests` → BUILD SUCCESS
- [ ] `npm run build` → 0 lỗi
- [ ] Test: không còn nhãn FR trên UI
- [ ] Test: badge thể trạng sau khi tạo phiếu khám "Bị thương" → badge hiển thị "Bị thương" (không còn "Khỏe mạnh")
- [ ] Test: dropdown ngựa chỉ hiện ngựa đã trả phí
- [ ] Test: BarnHealthMap không có nút chỉnh Training Lock
- [ ] Test: Tab Phòng ngừa không còn trong sidebar
- [ ] Test: Tạo phiếu khám + đặt 2 lịch phòng ngừa → nhân viên chuồng nhận thông báo
- [ ] Test: ngựa đang IN_MEDICAL → HLV Trưởng không tạo được lịch tập

---

## Tổng kết thay đổi

| Loại | Số | Ghi chú |
|---|---|---|
| FE cleanup (FR labels, badge fix) | 3 | `VetPage.jsx` |
| BE service logic mới | 4 | auto health sync, auto lock, notify, validate IN_MEDICAL |
| BE endpoint mới | 3 | eligible-horses, vet-assessment, mở training cho vet |
| FE component sửa | 4 | Dashboard, BarnHealthMap, MedicalRecords form, sidebar |
| FE component mới | 2 | TrainingAssessmentView, HorseProfileView |
| DTO sửa | 2 | CreateMedicalExaminationRequest, CreateInjuryMarkerRequest |
| **Tổng** | **~18 thay đổi** | **0 bảng DB mới** |
