# Racehorse Training Management System — Backend

Backend được tổ chức theo modular monolith và package-by-feature. Phiên bản khởi tạo đã có module hồ sơ ngựa (`horse`), xác thực JWT, phân quyền theo role, Microsoft SQL Server, validation và xử lý lỗi thống nhất.

## Yêu cầu môi trường

- Java 21 trở lên
- Maven 3.9 trở lên
- Microsoft SQL Server và SSMS

## Khởi chạy local

Tạo `racehorse_local` theo `../DATABASE/README.md` bằng `../DATABASE/setup-local.ps1`. Script chạy baseline `00`–`10` rồi các bản vá `32`–`35`. Không dùng `-RecreateDatabase` khi muốn giữ dữ liệu hiện có. Đặt `JWT_SECRET` như hướng dẫn bên dưới, sau đó chạy backend với profile `local`:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

API chạy tại `http://localhost:8080`. Script `09_seed_reference_data.sql` tạo tài khoản quản lý mẫu `admin@gmail.com / Admin123` nếu email này chưa tồn tại. Hãy thay thông tin đăng nhập mẫu khi triển khai production.

Backend dùng profile `local` để kết nối SQL Server trên máy phát triển bằng tài khoản thử nghiệm. Khi chạy trên host, không bật profile `local`; đặt `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` bằng biến môi trường của host. Không lưu mật khẩu DB thật trong source hoặc image.

Để bật upload Cloudinary, đặt các biến môi trường sau trước khi chạy backend. Không commit API secret vào repository:

```text
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
CLOUDINARY_FOLDER=racehorse
```

Cấu hình nằm tại `src/main/resources/application.properties`. `spring.jpa.hibernate.ddl-auto=none` bảo đảm ứng dụng không tự tạo hoặc thay đổi schema; mọi thay đổi database phải được viết thành script mới trong `../DATABASE` và chạy chủ động bằng SSMS.

`JWT_SECRET` là bắt buộc cho cả profile mặc định và `local`: cung cấp chuỗi Base64 của ít nhất 32 byte ngẫu nhiên qua biến môi trường hoặc secret store trước khi chạy backend. Khóa phát triển cũ đã công khai và bị từ chối. Có thể tạo một khóa mới cho môi trường local trong PowerShell:

```powershell
$jwtBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

Lưu khóa riêng ở nơi an toàn và dùng lại qua các lần khởi động. Đổi khóa sẽ khiến access token hiện có hết hiệu lực; refresh token còn hiệu lực vẫn có thể cấp access token mới. Nếu cần buộc tất cả người dùng đăng nhập lại, phải thu hồi các phiên đăng nhập đang hoạt động. Không commit giá trị thật vào `.env.example` hoặc source code.

## Triển khai ví demo lên host

Luồng thanh toán hiện tại là mô phỏng: mã hiển thị không thể quét để chuyển tiền thật; nút xác nhận demo cộng tiền vào ví. Chỉ dùng dữ liệu demo, kể cả khi triển khai công khai. Để chạy trên host:

1. Cấp một địa chỉ HTTPS ổn định cho web và API. Nếu cùng origin, reverse proxy chuyển `/api/**` và `/actuator/health` tới backend, phục vụ frontend tĩnh và chuyển các đường dẫn giao diện như `/wallet` về `index.html`; để `VITE_API_URL` trống khi build frontend. Nếu khác origin, đặt `VITE_API_URL` thành URL API **trước khi build** và đặt `CORS_ALLOWED_ORIGINS` bằng origin của frontend.
2. Thiết lập biến môi trường backend theo `.env.example`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `PAYOS_GATEWAY=fake`. Không cần khóa PayOS hay tunnel cho bản demo. Không đưa mật khẩu vào biến `VITE_*`.
3. Với database triển khai ngoài local, đánh giá và áp dụng các migration phù hợp lên đúng database đích; script `setup-local.ps1` và `32`–`35` hiện dùng tên `racehorse_local`, **không tự áp dụng lên Azure**. Không chạy script lên Azure chỉ vì deploy code.
4. Build JAR Java 21, chạy JAR hoặc `Dockerfile` hiện có với biến môi trường của host; build và phục vụ thư mục `FRONTEND/dist` bằng web server tĩnh. Xác nhận `/actuator/health` trả trạng thái khỏe.
5. Kiểm tra tạo mã demo, xác nhận nạp ví, mua gói cho từng ngựa và tự gia hạn khi đến hạn. Mỗi ngựa có kỳ hạn và gói riêng; gói được đổi có hiệu lực ở kỳ tiếp theo.

`run-local-real.cmd` và webhook PayOS vẫn còn trong mã nguồn cho thí nghiệm riêng; giao diện hiện tại không cung cấp checkout thanh toán thật.

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
| `GET` | `/api/horse-intake/waiting-room/pending` | Staff/Club Manager xem ngựa đã thanh toán hoặc được bảo lãnh, đang chờ đưa vào phòng chờ |
| `POST` | `/api/horse-intake/{horseId}/waiting-room` | Staff/Club Manager xác nhận đã đưa ngựa vào phòng chờ; không đổi `paymentStatus` |
| `GET` | `/api/medical/queue` | Bác sĩ xem ngựa đang ở phòng chờ |
| `GET` | `/api/medical/queue/overview` | Mọi bác sĩ xem ngựa chờ khám và ca đã được bác sĩ khác tiếp nhận (chỉ xem trạng thái) |
| `POST` | `/api/medical/queue/{horseId}/receive` | Bác sĩ nhận ngựa từ phòng chờ để khám |
| `POST` | `/api/medical/queue/{horseId}/complete` | Bác sĩ hoàn tất sau khi lưu hồ sơ khám; ngựa chuyển sang chờ ô chuồng |
| `GET` | `/api/horse-intake/stall/pending` | Staff/Club Manager xem ngựa đã khám xong, chờ phân ô chuồng, kể cả ngựa bị thương |
| `POST` | `/api/stables/{stableId}/stalls/{stallId}/assign` | Staff/Club Manager phân ô chuồng cho ngựa đã khám xong |
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
| `POST` | `/api/training-plans` | Tạo kế hoạch nhiều giai đoạn FR04 |
| `PUT` | `/api/training-plans/{id}` | Sửa kế hoạch chưa phát sinh lịch |
| `POST` | `/api/training-plans/{id}/copy` | Sao chép kế hoạch cho ngựa khác |
| `GET` | `/api/training-plans/{id}` | Xem chi tiết kế hoạch và các giai đoạn |
| `GET` | `/api/horses/{id}/training-plans` | Danh sách kế hoạch của một ngựa |
| `POST` | `/api/training-schedules` | Tạo lịch từ giai đoạn active; body bắt buộc có `trainingDate` và `slotStart` (06:00–23:00, đúng giờ) |
| `PUT` | `/api/training-schedules/{id}` | Sửa lịch; body cũng cần `slotStart` |
| `POST` | `/api/training-schedules/{id}/publish` | Công bố lịch và tạo thông báo cho nhân viên |
| `GET` | `/api/training-schedules?from=&to=&horseId=` | Lịch huấn luyện dành cho quản lý/HLV trưởng |
| `GET` | `/api/training-schedules/mine?from=&to=` | Nhân viên xem lịch đã công bố của mình |
| `POST` | `/api/racetracks` | Quản lý tạo đường đua và số làn |
| `GET` | `/api/racetracks` | Quản lý/HLV trưởng xem danh sách đường đua |
| `GET` | `/api/racetracks/{id}/availability?date=YYYY-MM-DD&slotStart=HH:mm` | Xem làn còn trống trong slot 1 giờ đã chọn |
| `POST` | `/api/trial-run-bookings` | Quản lý/HLV trưởng đặt một slot chạy thử |
| `GET` | `/api/notifications?unreadOnly=` | Người dùng xem thông báo của chính mình |
| `PATCH` | `/api/notifications/{id}/read` | Đánh dấu thông báo đã đọc |
| `POST` | `/api/horses/{id}/training-locks` | Bác sĩ/Quản lý khóa huấn luyện FR11 |
| `POST` | `/api/training-locks/{id}/release` | Bác sĩ thú y gỡ khóa sau ngày tái khám |
| `GET` | `/api/horses/{id}/training-locks/current` | Xem khóa đang hoạt động |
| `GET` | `/api/horses/{id}/training-locks` | Xem lịch sử khóa huấn luyện |

Luồng tiếp nhận dùng `intakeStatus` trong `HorseResponse`: `PENDING_WAITING_ROOM` → `WAITING_FOR_VET` → `IN_MEDICAL` → `PENDING_STALL` → `STABLED`. `paymentStatus` vẫn chỉ biểu thị thanh toán; bảo lãnh của Club Manager dùng `serviceOverride`. Khi nhân viên đưa ngựa vào phòng chờ, mọi bác sĩ đang hoạt động được thông báo. Không có bước phân công trước: bác sĩ đầu tiên tiếp nhận sẽ phụ trách lần khám ban đầu; bác sĩ khác xem trạng thái qua `/api/medical/queue/overview` nhưng không thể tiếp nhận lại hoặc ghi hồ sơ khám ban đầu cho ca này. Sau khi hoàn tất, các lần tái khám không bị ràng buộc vào bác sĩ tiếp nhận ban đầu. Ngựa bị thương vẫn được phân ô chuồng để chăm sóc, nhưng chỉ có thể gán HLV và tạo/bắt đầu lịch tập khi ở `STABLED`, sức khỏe `FIT`, có phân ô còn hiệu lực và không còn khóa huấn luyện.

## Phân quyền API đã triển khai

| Chức năng | CLUB_MANAGER | HEAD_TRAINER | HORSE_OWNER | STABLE_STAFF | VETERINARIAN |
|---|---:|---:|---:|---:|---:|
| Quản lý tài khoản và role | Có | Không | Không | Không | Không |
| Tạo/sửa/lưu trữ hồ sơ ngựa | Có | Có | Không | Không | Không |
| HLV tự tiếp nhận ngựa đã khám, đủ điều kiện và có ô chuồng | Không | Có | Không | Không | Không |
| Quản lý tỷ lệ sở hữu | Có | Có | Không | Không | Không |
| Tìm kiếm toàn bộ ngựa | Có | Có | Không | Không | Không |
| Xem hồ sơ/phả hệ/lịch sử đua | Có | Có | Chỉ ngựa đang sở hữu | Không | Không |
| Thêm/sửa kết quả đua lịch sử | Có | Có | Không | Không | Không |
| Xem file hồ sơ chính | Có | Có | Có | Có | Có |
| Thay/xóa file hồ sơ chính | Có | Có | Chỉ đề xuất | Không | Không |
| Nhập đánh giá buổi tập FR07 | Có | Có | Không | Không | Không |
| Xem đánh giá FR07 | Có | Có | Chỉ ngựa đang sở hữu | Không | Không |
| Xem dashboard thể lực FR03 | Có | Có | Không | Không | Không |
| Tạo/sửa/sao chép kế hoạch FR04 | Có | Có | Không | Không | Không |
| Tạo/công bố và xem toàn bộ lịch FR05 | Có | Có | Không | Không | Không |
| Quản lý đường đua | Có | Không | Không | Không | Không |
| Xem/đặt slot chạy thử | Có | Có | Không | Không | Không |
| Xem lịch đã công bố được giao | Không | Không | Không | Có | Không |
| Tạo khóa huấn luyện FR11 | Có | Không | Không | Không | Có |
| Gỡ khóa huấn luyện FR11 | Không | Không | Không | Không | Có |
| Xem khóa huấn luyện | Có | Có | Không | Không | Có |
| Xem thông báo của chính mình | Có | Có | Có | Có | Có |

`CLUB_MANAGER` là admin nghiệp vụ. `HORSE_OWNER` bị kiểm tra quyền sở hữu theo dữ liệu `horse_ownerships`, không chỉ kiểm tra role trong JWT. Hồ sơ đã lưu trữ không xuất hiện trong tìm kiếm hoặc danh sách của chủ sở hữu.

Các endpoint phân trang trả cấu trúc ổn định gồm `content` và `page` (`size`, `number`, `totalElements`, `totalPages`). Tham số `sort` có dạng `field,direction`, ví dụ `sort=name,asc`.

Hệ thống có đúng 5 role cố định. Khi `CLUB_MANAGER` thay role của một tài khoản khác, backend đọc lại role từ database trên mỗi request nên quyền mới có hiệu lực ở request kế tiếp mà không buộc người dùng đăng nhập lại. JWT không được dùng làm nguồn quyền sau khi phát hành.

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

Module `training` chứa FR03–FR07; telemetry FR06 do Head Trainer nhập thủ công. Module `notification` cung cấp thông báo trong web; module `health` quản lý trạng thái, cân nặng, chăm sóc phòng ngừa và khóa huấn luyện.

## Role

- `CLUB_MANAGER`
- `HEAD_TRAINER`
- `STABLE_STAFF`
- `HORSE_OWNER`
- `VETERINARIAN`

`CLUB_MANAGER` là admin nghiệp vụ: quản lý tài khoản, gán role và quản lý dữ liệu câu lạc bộ. API chỉnh sửa hồ sơ ngựa dành cho `CLUB_MANAGER` và `HEAD_TRAINER`. `HORSE_OWNER` chỉ xem được ngựa có liên kết sở hữu thực tế trong database.

Khi đổi role, `CLUB_MANAGER` gửi toàn bộ danh sách role thay thế tới `PUT /api/users/{id}/roles`. Danh sách không được rỗng. Quản lý câu lạc bộ không thể dùng endpoint này để tự thay đổi role của chính mình.

## API nghiệp vụ mới đã chốt

### Telemetry thủ công và cảnh báo FR06

- `POST /api/horses/{horseId}/metric-thresholds` — `HEAD_TRAINER` cấu hình ngưỡng `HEART_RATE` hoặc `SPEED` theo từng ngựa.
- `GET|PUT|DELETE /api/horses/{horseId}/metric-thresholds[/{thresholdId}]` — xem, sửa hoặc kết thúc hiệu lực ngưỡng.
- `POST /api/horses/{horseId}/telemetry` — `HEAD_TRAINER` nhập danh sách chỉ số với ngữ cảnh `RESTING`, `TRAINING` hoặc `RECOVERY`.
- `GET /api/horses/{horseId}/telemetry` — xem lịch sử telemetry, lọc theo ngữ cảnh và thời gian.
- `GET /api/horses/{horseId}/alerts` và `POST /api/horses/{horseId}/alerts/{alertId}/acknowledge` — lọc và xác nhận cảnh báo.
- Mỗi dòng phải có ít nhất `heartRateBpm` hoặc `speedKmh`. Backend tự tạo cảnh báo và thông báo khi vượt ngưỡng đang hiệu lực.

### Cân nặng và sẵn sàng thi đấu

- `POST /api/horses/{horseId}/weights` — chỉ `VETERINARIAN` được nhập.
- `GET /api/horses/{horseId}/weights` — quản lý, HLV trưởng, bác sĩ và chủ sở hữu liên quan được xem.
- `GET /api/horses/{horseId}/race-readiness?raceEventId={id}` — trả `ready` và danh sách quy tắc chưa đạt. Điều kiện hiện tại: sức khỏe `FIT`, không có khóa huấn luyện, đánh giá thể lực mới nhất `GREEN`, có cân nặng, nhịp tim nghỉ 30–42 bpm; nếu truyền giải đua thì cân nặng còn phải nằm trong giới hạn của giải. Thiếu dữ liệu được xem là chưa sẵn sàng.

Không đặt một ngưỡng tốc độ chung cho mọi ngựa. Tốc độ và các ngưỡng tập luyện phải được HLV trưởng cấu hình theo từng ngựa.

Nguồn nghiệp vụ cho ngưỡng nghỉ: [AAEP Care Guidelines](https://aaep.org/wp-content/uploads/2024/01/AAEPCareGuidelinesRR2012.pdf) nêu mạch bình thường của ngựa trưởng thành là 30–42 bpm. Không dùng ngưỡng 64 bpm của luật endurance FEI làm chuẩn chung cho ngựa đua vì đó là tiêu chí phục hồi của một môn thi cụ thể. Cân nặng cũng không có một khoảng bình thường chung cho mọi giống; backend chỉ yêu cầu có lần cân gần nhất và đối chiếu giới hạn của giải nếu giải có khai báo.

### Chăm sóc phòng ngừa FR12

- `POST /api/horses/{horseId}/preventive-care` — bác sĩ tạo lịch, chu kỳ và số ngày báo trước.
- `GET /api/horses/{horseId}/preventive-care` — xem và lọc lịch theo loại, trạng thái, khoảng ngày.
- `PUT|DELETE /api/preventive-care/{id}` — bác sĩ sửa hoặc hủy lịch chưa hoàn thành.
- `POST /api/preventive-care/{id}/complete` — bác sĩ xác nhận hoàn thành; lịch kế tiếp tự sinh nếu có `recurrenceDays`.
- Job chạy 06:00 theo `app.club-time-zone`, tạo nhắc lịch đến hạn và báo cáo ngày cho chủ sở hữu. Báo cáo hiện dùng VND; phân bổ chi phí cho nhiều chủ sở hữu được để lại cho giai đoạn sau.

### Ma trận quyền và audit

- `GET /api/admin/role-permissions` — Club Manager xem các quyền backend thực sự hỗ trợ. `APPROVE` hiện chỉ áp dụng cho `PROFILE` và `RACING`; các module còn lại dùng `VIEW/EDIT`.
- `PUT /api/admin/role-permissions/{role}` — Club Manager bật/tắt một quyền của role khác; body gồm `module`, `action`, `granted`. Quyền không được module hỗ trợ sẽ bị từ chối.
- Quyền đã cấp được nạp thành authority `PERM_{MODULE}_{ACTION}` ở lần xác thực request tiếp theo. Ma trận có thể thu hồi/khôi phục quyền trong phạm vi trách nhiệm nghiệp vụ của role, nhưng không mở rộng qua các giới hạn bắt buộc như chỉ bác sĩ nhập cân nặng hoặc chỉ HLV trưởng nhập telemetry. Năm role vẫn cố định và không cho hạ quyền `CLUB_MANAGER`.
- Mọi request tạo/sửa/xóa thành công và mọi lần login thành công được tự ghi vào `audit_logs`. Bảng audit vẫn chỉ cho thêm, không cho sửa/xóa.
- `GET /api/admin/audit-logs` — Club Manager lọc audit theo người thao tác, module, action, entity và thời gian.

### API quản trị nghiệp vụ bổ sung

- `/api/admin/accounts` — lọc tài khoản theo từ khóa, role và trạng thái; sửa thông tin, khóa/mở khóa và đặt lại mật khẩu. Khóa, đặt lại mật khẩu hoặc đổi role sẽ thu hồi các phiên hiện tại.
- `/api/admin/horses/archived` và `/api/admin/horses/{id}/restore` — xem và khôi phục hồ sơ ngựa đã lưu trữ.
- `/api/admin/dashboard?month=YYYY-MM&compareTo=YYYY-MM` — dashboard tháng gồm số buổi tập, số ngựa được tập, trạng thái xanh/vàng/đỏ, doanh thu, chi phí và chênh lệch VND.
- `/api/admin/audit-logs` và `/api/admin/audit-logs/{id}` — tra cứu danh sách và chi tiết nhật ký; thao tác quản trị tài khoản lưu dữ liệu trước/sau thay đổi.
- `/api/stables` — CRUD chuồng; thêm/ngừng ô chuồng; phân bổ hoặc kết thúc phân bổ ngựa.
- `GET /api/inventory/items` — cùng nguồn tồn kho cho Club Manager và các màn hình nghiệp vụ; `status=ACTIVE|INACTIVE|ALL`, `category`, `lowStockOnly`.
- `POST /api/inventory/items/{id}/usage` — Club Manager ghi xuất dùng; body `{ "quantity": 2.5, "unitPriceVnd": 45000, "horseId": null, "stableId": "UUID", "notes": "Ca sáng" }`. Chọn đúng một ngựa hoặc chuồng; vật tư y tế bắt buộc chọn ngựa. Trong một transaction, API khóa dòng hàng, chặn xuất quá tồn, trừ `quantityOnHand`, lưu `STOCK_USAGE` trong `module_records` và tạo khoản chi VND trong `financial_transactions` với `source_entity_type=INVENTORY_USAGE`. Khoản chi phát sinh lúc xuất dùng theo đơn giá được xác nhận, không phát sinh khi duyệt/nhập kho.
- `GET /api/inventory/usage?limit=20` — Club Manager xem lịch sử xuất dùng, số tồn sau xuất và ID giao dịch tài chính liên kết; `limit` từ 1 đến 100.
- `GET /api/inventory/replenishments` — Club Manager xem đề xuất, bao gồm mặt hàng chưa có trong danh mục (`itemId=null`).
- `POST /api/inventory/replenishments/{id}/review` — body `{ "status": "APPROVED", "reviewNotes": "...", "receiveImmediately": false }`. Club Manager có thể duyệt trước hoặc vừa duyệt vừa xác nhận nhập kho; từ chối không thay đổi tồn.
- `POST /api/inventory/replenishments/{id}/receive` — body `{ "catalogItemId": null, "referencePriceVnd": 45000, "minThreshold": 10, "location": "Kho A" }`. Chỉ áp dụng đề xuất `APPROVED`; tăng tồn đúng một lần và đổi thành `FULFILLED`. Nếu đề xuất là vật tư mới, API tạo mặt hàng mới khi nhập kho hoặc gắn với `catalogItemId` cùng danh mục/đơn vị.
- `/api/financial-transactions` — ghi sổ thu/chi VND, tra cứu và tổng hợp. Giao dịch là sổ cái chỉ thêm, không sửa/xóa.
- `/api/races` — CRUD giải đua; đăng ký, xác nhận và rút đăng ký ngựa.
- `/api/owner-reports` — xem, tạo và tải báo cáo JSON. Job 06:00 tạo báo cáo ngày và thông báo cho chủ sở hữu.
- `/api/trial-run-bookings` — đặt, xem danh sách và hủy lượt chạy thử.
- `/api/files` — upload, xem metadata và xóa file Cloudinary dùng chung; chỉ nhận ảnh, PDF hoặc DOCX.
- `GET|PUT /api/horses/{horseId}/health-status` — xem trạng thái; bác sĩ cập nhật `FIT`, `MONITOR`, `INJURED`, `QUARANTINED`.

Các API trên kết hợp năm role cố định với authority động trong ma trận RBAC. Theo yêu cầu hiện tại, chưa triển khai API tổng quát cho `module_records` (mục 11), backend khám/điều trị/thuốc/mô hình chấn thương 3D (mục 12), và xuất báo cáo PDF/ảnh (mục 14).

## Chức năng chưa triển khai vì thiếu đặc tả

Các mục dưới đây được giữ lại và không tự suy diễn:

- FR01 tối đa 10 ảnh bổ sung: tài liệu mới chỉ xác định quyền đối với file chính, chưa xác định quyền thêm/xóa/thay ảnh bổ sung.
- FR01 từ chối đề xuất file chính: đã có luồng đề xuất và phê duyệt, nhưng chưa có quy tắc ai được từ chối, lý do có bắt buộc hay owner có được rút đề xuất không.
- FR03 chưa có bộ lọc theo giai đoạn vì bản ghi FR07 hiện chưa nhận `trainingScheduleId` từ request. Dashboard vẫn không tự tính lại bất kỳ chỉ số nào.
- FR04 hiện dùng cờ `active`, không tự giới hạn số kế hoạch active trên một ngựa vì tài liệu không quy định. Kế hoạch đã phát sinh lịch không cho thay toàn bộ giai đoạn để bảo toàn dữ liệu tham chiếu.
- FR05 đã có lịch nháp, công bố, kiểm tra trùng nhân viên, đường đua/làn chạy và đúng 18 slot một giờ từ 06:00 đến 24:00 mỗi ngày. Thông báo chỉ hiển thị trên web bằng polling; không gửi email/SMS/mobile push.
- FR07 hiện chỉ lưu URL video HTTP/HTTPS. Chưa có quyết định về upload file video, dịch vụ lưu trữ, giới hạn dung lượng/định dạng và quyền tải xuống.
- Quy tắc phân bổ chi phí đồng sở hữu của FR19/FR22 vẫn chờ quyết định; hiện báo cáo dùng VND và liệt kê toàn bộ giao dịch của các ngựa thuộc owner.

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
