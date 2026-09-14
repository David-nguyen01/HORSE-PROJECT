# Racehorse Training Management System — Backend

Backend được tổ chức theo modular monolith và package-by-feature. Phiên bản khởi tạo đã có module hồ sơ ngựa (`horse`), xác thực JWT, phân quyền theo role, Microsoft SQL Server, validation và xử lý lỗi thống nhất.

## Yêu cầu môi trường

- Java 21 trở lên
- Maven 3.9 trở lên
- Microsoft SQL Server và SSMS

## Khởi chạy local

Mở thư mục `../DATABASE` bằng SSMS, đăng nhập `sa` và chạy lần lượt:

1. `00_create_database.sql`
2. `01_create_schema.sql`
3. `02_seed_data.sql`
4. `03_horse_role_features.sql`
5. `04_cloudinary_profile_files.sql`
6. `05_training_evaluations.sql`
7. `06_training_plans_scheduling_monitoring.sql`
8. `07_health_medical.sql`
9. `08_stable_care_inventory.sql`
10. `09_racing_reporting_rbac_audit.sql`

Sau đó chạy backend:

```powershell
mvn spring-boot:run
```

Mặc định API chạy tại `http://localhost:8080`. Script `02_seed_data.sql` tạo tài khoản quản lý câu lạc bộ `admin@gmail.com / Admin123` nếu email này chưa tồn tại. Mật khẩu được mã hóa BCrypt trước khi lưu vào database. Hãy thay thông tin đăng nhập mặc định khi triển khai production.

Chuỗi kết nối mặc định:

```text
jdbc:sqlserver://localhost:1433;databaseName=racehorse;encrypt=true;trustServerCertificate=true
```

Backend dùng SQL Authentication với username `sa` và password `sa123456`. Có thể ghi đè bằng `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` trên môi trường triển khai.

Để bật upload Cloudinary, đặt các biến môi trường sau trước khi chạy backend. Không commit API secret vào repository:

```text
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
CLOUDINARY_FOLDER=racehorse
```

Cấu hình nằm tại `src/main/resources/application.properties`. `spring.jpa.hibernate.ddl-auto=none` bảo đảm ứng dụng không tự tạo hoặc thay đổi schema; mọi thay đổi database phải được viết thành script mới trong `../DATABASE` và chạy chủ động bằng SSMS.

Kiểm tra health endpoint không cần đăng nhập:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Chạy test:

```powershell
mvn test
```

## API khởi tạo

| Method | Endpoint | Chức năng |
|---|---|---|
| `GET` | `/api/auth/register-roles` | Danh sách role được phép tự đăng ký |
| `POST` | `/api/auth/register` | Đăng ký tài khoản với một role bắt buộc |
| `POST` | `/api/auth/login` | Đăng nhập và nhận access/refresh token |
| `POST` | `/api/auth/refresh` | Xoay vòng refresh token và cấp cặp token mới |
| `POST` | `/api/auth/logout` | Thu hồi refresh token |
| `GET` | `/api/auth/me` | Kiểm tra phiên hiện tại và lấy người dùng đang đăng nhập |
| `POST` | `/api/users` | Quản lý câu lạc bộ tạo tài khoản và gán role |
| `GET` | `/api/users?query=&page=&size=&sort=` | Quản lý câu lạc bộ tìm kiếm tài khoản |
| `GET` | `/api/users/{id}` | Quản lý câu lạc bộ xem một tài khoản |
| `GET` | `/api/users/roles` | Quản lý câu lạc bộ xem các role hợp lệ |
| `PUT` | `/api/users/{id}/roles` | Quản lý câu lạc bộ thay role tài khoản khác |
| `POST` | `/api/horses` | Tạo hồ sơ ngựa |
| `GET` | `/api/horses/{id}` | Xem chi tiết hồ sơ |
| `GET` | `/api/horses` | Tìm kiếm, lọc trạng thái và phân trang |
| `GET` | `/api/horses/mine` | Chủ sở hữu xem danh sách ngựa mình sở hữu |
| `PUT` | `/api/horses/{id}` | Chỉnh sửa hồ sơ ngựa |
| `PUT` | `/api/horses/{id}/ownership` | Thay toàn bộ tỷ lệ sở hữu sau khi xác nhận |
| `PATCH` | `/api/horses/{id}/archive` | Lưu trữ hồ sơ ngựa |
| `GET` | `/api/horses/{id}/pedigree` | Xem cây phả hệ gồm 3 đời tổ tiên |
| `GET` | `/api/horses/{id}/race-results` | Xem lịch sử đua và thống kê thắng |
| `POST` | `/api/horses/{id}/race-results` | Thêm kết quả đua lịch sử |
| `PUT` | `/api/horses/{id}/race-results/{resultId}` | Chỉnh sửa kết quả đua lịch sử |
| `GET` | `/api/horses/{id}/main-file` | Mọi role đã đăng nhập xem file hồ sơ chính |
| `PUT` | `/api/horses/{id}/main-file` | Quản lý/HLV trưởng thay trực tiếp file chính |
| `DELETE` | `/api/horses/{id}/main-file` | Quản lý/HLV trưởng xóa file chính |
| `POST` | `/api/horses/{id}/main-file/proposals` | Chủ sở hữu đề xuất file chính mới |
| `GET` | `/api/horses/{id}/main-file/proposals` | Quản lý/HLV trưởng xem đề xuất đang chờ |
| `POST` | `/api/horses/{id}/main-file/proposals/{proposalId}/approve` | Quản lý/HLV trưởng duyệt đề xuất |
| `POST` | `/api/training-evaluations` | Quản lý/HLV trưởng hoàn tất đánh giá buổi tập FR07 |
| `GET` | `/api/training-evaluations/{id}` | Xem và đối chiếu một đánh giá gốc |
| `GET` | `/api/horses/{id}/training-evaluations` | Danh sách đánh giá đã hoàn tất của một ngựa |
| `GET` | `/api/fitness/dashboard` | Dashboard dữ liệu thể lực FR03 |

## Phân quyền API đã triển khai

| Chức năng | CLUB_MANAGER | HEAD_TRAINER | HORSE_OWNER | STABLE_STAFF | VETERINARIAN |
|---|---:|---:|---:|---:|---:|
| Quản lý tài khoản và role | Có | Không | Không | Không | Không |
| Tạo/sửa/lưu trữ hồ sơ ngựa | Có | Có | Không | Không | Không |
| Gán huấn luyện viên và tỷ lệ sở hữu | Có | Có | Không | Không | Không |
| Tìm kiếm toàn bộ ngựa | Có | Có | Không | Không | Không |
| Xem hồ sơ/phả hệ/lịch sử đua | Có | Có | Chỉ ngựa đang sở hữu | Không | Không |
| Thêm/sửa kết quả đua lịch sử | Có | Có | Không | Không | Không |
| Xem file hồ sơ chính | Có | Có | Có | Có | Có |
| Thay/xóa file hồ sơ chính | Có | Có | Chỉ đề xuất | Không | Không |
| Nhập đánh giá buổi tập FR07 | Có | Có | Không | Không | Không |
| Xem đánh giá FR07 | Có | Có | Chỉ ngựa đang sở hữu | Không | Không |
| Xem dashboard thể lực FR03 | Có | Có | Không | Không | Không |

`CLUB_MANAGER` là admin nghiệp vụ. `HORSE_OWNER` bị kiểm tra quyền sở hữu theo dữ liệu `horse_ownerships`, không chỉ kiểm tra role trong JWT. Hồ sơ đã lưu trữ không xuất hiện trong tìm kiếm hoặc danh sách của chủ sở hữu.

Các endpoint phân trang trả cấu trúc ổn định gồm `content` và `page` (`size`, `number`, `totalElements`, `totalPages`). Tham số `sort` có dạng `field,direction`, ví dụ `sort=name,asc`.

Khi `CLUB_MANAGER` thay role của một tài khoản, backend thu hồi toàn bộ phiên đang hoạt động của tài khoản đó. Người dùng phải đăng nhập lại để nhận quyền mới và không thể tiếp tục dùng quyền cũ.

Đăng ký tài khoản:

```powershell
$registerBody = @{
  username = 'trainer.one'
  email = 'trainer@example.com'
  password = 'strong-password'
  confirmPassword = 'strong-password'
  role = 'HEAD_TRAINER'
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/auth/register `
  -ContentType 'application/json' `
  -Body $registerBody
```

`role` là bắt buộc và không được là `CLUB_MANAGER`. Backend kiểm tra quy tắc này kể cả khi client gửi request thủ công.

Đăng nhập:

```powershell
$loginBody = @{
  email = 'admin@gmail.com'
  password = 'Admin123'
} | ConvertTo-Json

$tokens = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/auth/login `
  -ContentType 'application/json' `
  -Body $loginBody

$headers = @{ Authorization = "Bearer $($tokens.accessToken)" }
```

Tạo hồ sơ ngựa bằng access token:

```powershell
$body = @{
  code = 'H-001'
  name = 'Lightning'
  microchipPassportNumber = 'CHIP-001'
  birthDate = '2022-03-10'
  gender = 'STALLION'
  colorMarkings = 'White blaze'
  breed = 'Thoroughbred'
  sireId = $null
  damId = $null
  currentTrainerId = $null
  stableLocation = 'Stable A-01'
  status = 'ACTIVE'
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/horses `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body $body
```

### Cập nhật hồ sơ ngựa

`PUT /api/horses/{id}` dùng body đầy đủ. `code` là định danh bất biến nên không được gửi trong API cập nhật.

```json
{
  "name": "Lightning",
  "microchipPassportNumber": "CHIP-001",
  "birthDate": "2022-03-10",
  "gender": "STALLION",
  "colorMarkings": "White blaze",
  "breed": "Thoroughbred",
  "sireId": null,
  "damId": null,
  "currentTrainerId": "UUID_CUA_HEAD_TRAINER",
  "stableLocation": "Stable A-01",
  "status": "ACTIVE"
}
```

`currentTrainerId` nếu có phải trỏ tới tài khoản đang hoạt động mang role `HEAD_TRAINER`. Backend từ chối trường hợp ngựa bố và ngựa mẹ trùng nhau, tự tham chiếu hoặc tạo vòng lặp phả hệ.

### Thay tỷ lệ sở hữu

`PUT /api/horses/{id}/ownership` thay toàn bộ danh sách hiện tại:

```json
{
  "confirmed": true,
  "owners": [
    { "ownerId": "UUID_OWNER_1", "percentage": 60.00 },
    { "ownerId": "UUID_OWNER_2", "percentage": 40.00 }
  ]
}
```

Quy tắc:

- `confirmed` phải là `true`; frontend phải hiển thị hộp thoại xác nhận trước khi gửi.
- Mỗi `ownerId` phải là tài khoản đang hoạt động có role `HORSE_OWNER`.
- Một owner không được xuất hiện hai lần.
- Mỗi tỷ lệ lớn hơn `0`, không vượt quá `100`; tổng tỷ lệ phải đúng `100.00%`.

### Hồ sơ ngựa trả về

Response hồ sơ có cấu trúc:

```json
{
  "id": "UUID",
  "code": "H-001",
  "name": "Lightning",
  "microchipPassportNumber": "CHIP-001",
  "birthDate": "2022-03-10",
  "gender": "STALLION",
  "colorMarkings": "White blaze",
  "breed": "Thoroughbred",
  "sire": { "id": "UUID", "code": "H-SIRE", "name": "Sire" },
  "dam": null,
  "currentTrainer": {
    "id": "UUID",
    "username": "trainer.one",
    "email": "trainer@example.com"
  },
  "owners": [
    {
      "id": "UUID",
      "username": "owner.one",
      "email": "owner@example.com",
      "percentage": 100.00
    }
  ],
  "stableLocation": "Stable A-01",
  "status": "ACTIVE",
  "archivedAt": null,
  "createdAt": "2026-09-10T00:00:00Z",
  "updatedAt": "2026-09-10T00:00:00Z"
}
```

Enum hợp lệ: `gender` gồm `STALLION`, `MARE`, `GELDING`; `status` gồm `ACTIVE`, `RESTING`, `INJURED`, `RETIRED`.

### Phả hệ

`GET /api/horses/{id}/pedigree` trả về ngựa gốc cùng tối đa ba cấp tổ tiên qua hai nhánh `sire` và `dam`. Giá trị `null` biểu thị chưa có hồ sơ cha/mẹ liên kết. Chủ sở hữu chỉ gọi được endpoint này với ngựa họ đang sở hữu.

### Lịch sử đua

Body cho cả `POST` và `PUT` kết quả đua:

```json
{
  "raceDate": "2026-08-20",
  "raceName": "Autumn Cup",
  "racecourse": "Saigon Racecourse",
  "distance": "1600 m",
  "finishPosition": 1,
  "finishTime": "01:35.20",
  "jockey": "Nguyen Van A",
  "trackCondition": "Good",
  "prizeMoney": "10000000 VND"
}
```

Tài liệu yêu cầu chưa xác định đơn vị chuẩn cho cự ly/thời gian và loại tiền tệ, vì vậy `distance`, `finishTime`, `prizeMoney` đang là chuỗi hiển thị, backend không tự quy đổi. API danh sách sắp xếp mới nhất trước và trả thêm thống kê:

```json
{
  "horseId": "UUID",
  "totalRaces": 3,
  "totalWins": 2,
  "results": []
}
```

`totalWins` được tính lại từ số bản ghi có `finishPosition = 1` mỗi lần đọc.

### File hồ sơ chính trên Cloudinary

Các API upload dùng `multipart/form-data` với trường file tên `file`. Backend chấp nhận:

- Mọi MIME type bắt đầu bằng `image/`; Cloudinary phải nhận diện kết quả là resource `image`.
- PDF hợp lệ có MIME `application/pdf` và chữ ký file `%PDF-`.
- DOCX hợp lệ; backend kiểm tra container ZIP phải có `[Content_Types].xml` và `word/document.xml`.

Giới hạn upload hiện tại là `50MB/file`, cấu hình bằng `spring.servlet.multipart.max-file-size`.

Quản lý câu lạc bộ hoặc huấn luyện viên trưởng thay trực tiếp file chính:

```http
PUT /api/horses/{horseId}/main-file
Authorization: Bearer <access-token>
Content-Type: multipart/form-data

file=<binary>
```

Chủ sở hữu chỉ có thể đề xuất và phải thực sự sở hữu con ngựa:

```http
POST /api/horses/{horseId}/main-file/proposals
Authorization: Bearer <access-token>
Content-Type: multipart/form-data

file=<binary>
```

Đề xuất mới có trạng thái `PENDING`. Sau khi được duyệt, đề xuất trở thành `ACTIVE`; file chính trước đó chuyển thành `REPLACED`. File cũ vẫn được giữ trên Cloudinary để tránh mất dữ liệu vì chưa có yêu cầu xóa lịch sử phiên bản.

`DELETE /api/horses/{horseId}/main-file` xóa file đang active khỏi Cloudinary với `invalidate=true` và đánh dấu metadata `DELETED`. Cloudinary lưu `resource_type` là `image` hoặc `raw`; DOCX dùng `raw`.

Response metadata:

```json
{
  "id": "UUID",
  "horseId": "UUID",
  "uploadedBy": { "id": "UUID", "username": "owner.one" },
  "approvedBy": { "id": "UUID", "username": "manager" },
  "originalFilename": "horse-profile.png",
  "contentType": "image/png",
  "format": "png",
  "size": 123456,
  "secureUrl": "https://res.cloudinary.com/...",
  "status": "ACTIVE",
  "approvedAt": "2026-09-10T08:00:00Z",
  "createdAt": "2026-09-10T07:55:00Z"
}
```

Khi hồ sơ ngựa được lưu trữ bằng `/archive`, backend không thay đổi metadata và không xóa bất kỳ file nào trên Cloudinary.

## FR07 — nhập chỉ số buổi tập

`POST /api/training-evaluations` chỉ dành cho `CLUB_MANAGER` và `HEAD_TRAINER`. Các chỉ số trong request được lưu nguyên giá trị người dùng nhập; backend không suy ra tốc độ, khối lượng hay trạng thái màu.

```json
{
  "horseId": "09f3b358-49b2-4f3c-a523-570928ade54f",
  "sessionDate": "2026-09-14",
  "durationMinutes": 45,
  "distanceMeters": 3200.00,
  "averageSpeedKmh": 42.50,
  "trainingLoad": 72.00,
  "heartRateRecoveryBpm": 58,
  "perceivedExertion": 7,
  "fitnessStatus": "GREEN",
  "trainerNotes": "Ngựa hoàn tất giáo án và hồi phục ổn định.",
  "videoUrl": "https://example.com/training-session.mp4"
}
```

Quy tắc validation chính:

- `sessionDate` không được ở tương lai; `durationMinutes` từ 1 phút.
- `distanceMeters` và `averageSpeedKmh` phải lớn hơn 0; `trainingLoad` không âm.
- `heartRateRecoveryBpm` từ 0–300; `perceivedExertion` từ 1–10.
- `fitnessStatus` nhận `GREEN`, `YELLOW` hoặc `RED` do người nhập quyết định.
- `trainerNotes` bắt buộc, tối đa 4.000 ký tự; `videoUrl` không bắt buộc và phải dùng HTTP/HTTPS.

Không có API sửa/xóa đánh giá vì tài liệu chưa đưa ra quy tắc hiệu chỉnh dữ liệu đã hoàn tất và yêu cầu nhật ký thay đổi. `GET /api/training-evaluations/{id}` trả đúng bản ghi gốc. `GET /api/horses/{id}/training-evaluations?page=0&size=20` trả lịch sử theo ngày mới nhất; chủ sở hữu chỉ truy cập được ngựa đang có liên kết sở hữu.

## FR03 — dashboard thể lực

`GET /api/fitness/dashboard` dành cho `CLUB_MANAGER` và `HEAD_TRAINER`, hỗ trợ:

```text
horseId=<uuid>&from=2026-09-01&to=2026-09-14&fitnessStatus=GREEN&query=horse-code-or-name
```

- `from` và `to` không bắt buộc; mặc định là 30 ngày gần nhất tính cả ngày hiện tại.
- Khoảng ngày không được đảo ngược và `to` không được ở tương lai.
- `fitnessStatus` lọc ngựa theo trạng thái ở đánh giá mới nhất trong khoảng đang xem.
- `horses` là lưới ngựa và trạng thái mới nhất; `dataPoints` là dữ liệu thô cho ba biểu đồ.
- Mỗi `dataPoints[].evaluationId` liên kết trực tiếp tới bản ghi FR07 để đối chiếu.
- API không tổng hợp hoặc tính toán lại các chỉ số. Xuất PNG/PDF được thực hiện ở frontend từ đúng dashboard và bộ lọc đang hiển thị.

## Cấu trúc source

```text
src/main/java/com/racehorse/
├── auth/
│   ├── config/
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   ├── security/
│   └── service/
├── common/
│   ├── config/
│   ├── exception/
│   └── persistence/
├── horse/
    ├── controller/
    ├── dto/
    ├── entity/
    ├── mapper/
    ├── repository/
    └── service/
├── media/
│   ├── config/
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
├── training/
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
└── racing/
    ├── controller/
    ├── dto/
    ├── entity/
    ├── repository/
    └── service/
```

Module `training` hiện chứa FR03/FR07. Các module `scheduling`, `telemetry`, `alert` và `notification` chỉ được thêm khi đủ quy tắc nghiệp vụ; không tạo lớp rỗng hoặc tự suy diễn để giữ chỗ.

## Role

- `CLUB_MANAGER`
- `HEAD_TRAINER`
- `STABLE_STAFF`
- `HORSE_OWNER`
- `VETERINARIAN`

`CLUB_MANAGER` là admin nghiệp vụ: quản lý tài khoản, gán role và quản lý dữ liệu câu lạc bộ. API chỉnh sửa hồ sơ ngựa dành cho `CLUB_MANAGER` và `HEAD_TRAINER`. `HORSE_OWNER` chỉ xem được ngựa có liên kết sở hữu thực tế trong database.

Khi đổi role, `CLUB_MANAGER` gửi toàn bộ danh sách role thay thế tới `PUT /api/users/{id}/roles`. Danh sách không được rỗng. Quản lý câu lạc bộ không thể dùng endpoint này để tự thay đổi role của chính mình.

## Chức năng chưa triển khai vì thiếu đặc tả

Các mục dưới đây được giữ lại và không tự suy diễn:

- FR01 tối đa 10 ảnh bổ sung: tài liệu mới chỉ xác định quyền đối với file chính, chưa xác định quyền thêm/xóa/thay ảnh bổ sung.
- FR01 từ chối đề xuất file chính: đã có luồng đề xuất và phê duyệt, nhưng chưa có quy tắc ai được từ chối, lý do có bắt buộc hay owner có được rút đề xuất không.
- FR03 chưa có bộ lọc theo giai đoạn huấn luyện vì giai đoạn thuộc FR04 và quy tắc liên kết buổi tập với giai đoạn chưa được xác nhận. Dashboard hiện lọc theo ngựa, khoảng ngày, trạng thái thể lực và từ khóa; không tự tính lại bất kỳ chỉ số nào.
- FR04 kế hoạch huấn luyện: dữ liệu giai đoạn đủ rõ, nhưng cần thống nhất trạng thái vòng đời của kế hoạch (`draft/active/completed/cancelled`) và quy tắc một hay nhiều kế hoạch hoạt động trên cùng ngựa.
- FR05 lịch hằng ngày/lượt chạy thử: chưa có mô hình đường đua, làn chạy, khung giờ, trạng thái công bố và kênh gửi thông báo. Xác nhận hoàn thành thuộc FR15 nhưng tài liệu FR15 chưa được cung cấp.
- FR06 cảnh báo realtime: chưa có giao thức nhận telemetry, đơn vị nhịp tim/tốc độ, cấu trúc ngưỡng và kênh push tới web/mobile.
- FR07 hiện chỉ lưu URL video HTTP/HTTPS. Chưa có quyết định về upload file video, dịch vụ lưu trữ, giới hạn dung lượng/định dạng và quyền tải xuống.
- `STABLE_STAFF`: tài liệu hiện chỉ nói được nhận lịch/nhiệm vụ ở FR05; chưa đủ dữ liệu để xây endpoint đúng logic.
- `VETERINARIAN`: chức năng nằm tại FR09 nhưng tài liệu FR09 chưa được cung cấp, nên chưa tạo API thú y.

## Quy ước

- Controller chỉ xử lý HTTP và validation đầu vào.
- Service chứa nghiệp vụ và ranh giới transaction.
- Repository chỉ đảm nhiệm truy cập dữ liệu.
- Entity không được trả trực tiếp ra API; request/response dùng DTO.
- Thay đổi database phải được thêm bằng SQL script mới trong `../DATABASE`, không sửa script đã chạy trên môi trường dùng chung.
- Thời gian hệ thống lưu theo UTC.
- Mỗi lần login tạo một bản ghi trong `login_sessions`, gồm tài khoản, IP, user-agent, thời gian hoạt động, thời gian hết hạn và trạng thái thu hồi.
- Access token mặc định sống 15 phút; refresh token sống 30 ngày và chỉ được lưu trong database dưới dạng SHA-256 hash.
- Refresh token được xoay vòng khi gọi `/api/auth/refresh`.
- JWT chứa `sessionId`; mỗi request được kiểm tra với phiên trong database. Logout thu hồi phiên nên access token của phiên đó cũng bị từ chối ngay.
- Response được bảo vệ có header chống cache. Sau logout, frontend phải xóa cả access token và refresh token, điều hướng về `/login` bằng `replace`, đồng thời gọi `/api/auth/me` trong route guard. Vì vậy nút Back không thể mở lại trang yêu cầu đăng nhập.
