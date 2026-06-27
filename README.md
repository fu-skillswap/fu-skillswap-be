# skillswap-be

Backend service cho FU SkillSwap, một ứng dụng Spring Boot modular monolith phục vụ nền tảng mentoring giữa sinh viên và cựu sinh viên Đại học FPT. Project hiện tập trung cung cấp nền tảng chung: xác thực Google OAuth, JWT, hồ sơ học thuật, hồ sơ mentor, mentor verification, upload tài liệu, phân quyền admin, response format, exception handling, database config và kiến trúc module để phát triển tiếp.

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Spring Modulith
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- H2 cho test
- JWT authentication
- Google OAuth login
- Cloudinary cho ảnh
- Cloudflare R2 cho PDF/document
- Maven Wrapper
- Docker Compose

## Yêu Cầu Cài Đặt

- JDK 21
- Docker Desktop
- Git

Không cần cài Maven global. Dùng Maven Wrapper có sẵn:

```powershell
.\mvnw.cmd
```

## Chạy Nhanh Local

Clone repository:

```powershell
git clone -b dev git@github.com:fu-skillswap/fu-skillswap-be.git
cd fu-skillswap-be
```

Tạo file môi trường local:

```powershell
copy .env.example .env
```

Chạy PostgreSQL:

```powershell
docker compose up -d postgres-db
```

Chạy test:

```powershell
.\mvnw.cmd test
```

Chạy backend:

```powershell
.\mvnw.cmd spring-boot:run
```

API chạy tại:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

Endpoint smoke test public:

```text
http://localhost:8080/api/campuses
```

Lưu ý: `/actuator/health` hiện đang được bảo vệ bởi Spring Security. Khi cần smoke test không cần token, dùng catalog endpoint như `/api/campuses`.

## Environment Variables

Dùng `.env.example` làm template. Không commit `.env`.

Các biến quan trọng:

```text
DATABASE_URL=jdbc:postgresql://localhost:5444/skillswapdb
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=123456

JWT_SECRET_KEY=replace-with-a-base64-encoded-secret-at-least-32-bytes
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

GOOGLE_CLIENT_ID=
SYSTEM_ADMIN_EMAILS=
CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:3000,http://localhost:5173,http://localhost:8080

CLOUDINARY_ENABLED=false
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

R2_ENABLED=false
R2_ENDPOINT=
R2_ACCESS_KEY_ID=
R2_SECRET_ACCESS_KEY=
R2_BUCKET=
R2_REGION=auto
R2_DOCUMENTS_PREFIX=skillswap/verification-documents

FLYWAY_ENABLED=false
HIBERNATE_DDL_AUTO=update
```

Khi chạy bằng Docker Compose, các biến này không nên để trống:

```text
JWT_SECRET_KEY
JWT_EXPIRATION
JWT_REFRESH_EXPIRATION
GOOGLE_CLIENT_ID
CORS_ALLOWED_ORIGIN_PATTERNS
```

Nếu bật mentor verification upload trên VPS:

```text
CLOUDINARY_ENABLED=true
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

R2_ENABLED=true
R2_ENDPOINT=
R2_ACCESS_KEY_ID=
R2_SECRET_ACCESS_KEY=
R2_BUCKET=
R2_REGION=auto
R2_DOCUMENTS_PREFIX=skillswap/verification-documents
```

`SYSTEM_ADMIN_EMAILS` là danh sách email được cấp role `SYSTEM_ADMIN` khi login Google:

```text
SYSTEM_ADMIN_EMAILS=quangtam2005.lttg@gmail.com,another-admin@fpt.edu.vn
```

Nếu một biến được khai báo rỗng trong `.env`, nó sẽ override default trong `application.yaml`. Ví dụ `JWT_EXPIRATION=` có thể làm app lỗi startup vì Spring không bind được empty string sang `long`.

## Profiles

### dev

Profile mặc định khi chạy local.

- PostgreSQL port `5444`
- `ddl-auto: create`
- Bật SQL logging
- Phù hợp để tạo schema sạch trong giai đoạn phát triển

Vì project dùng UUID v7 cho primary key, sau các thay đổi entity lớn nên reset database volume ở môi trường dev/test.

### test

Dùng cho Maven tests.

- H2 in-memory database
- `ddl-auto: create-drop`
- Flyway disabled

### prod

Dùng cho môi trường deploy.

- Đọc database config từ environment variables
- `HIBERNATE_DDL_AUTO` default hiện tại là `update` trong giai đoạn MVP.
- Dự án đã tích hợp sẵn các SQL Migration (`V1` đến `V16`) tại `src/main/resources/db/migration`. Flyway được khuyến nghị bật khi deploy Beta/Production (`FLYWAY_ENABLED=true`) để đảm bảo các index được khởi tạo đầy đủ.

Các bản cập nhật Migration gần đây giúp tối ưu hóa hiệu năng và tránh Lost Update cho Beta V1.0:
- **`V15__add_booking_expiry_indexes.sql`**: Thêm composite index cho bảng `bookings` để hỗ trợ scheduler quét và bulk update các pending booking quá hạn:
  - `(status, requested_start_time)`
  - `(mentee_user_id, status, requested_start_time, requested_end_time)`
- **`V16__add_chat_message_indexes.sql`**: Thêm composite index cho bảng chat để tối ưu hóa tốc độ tải lịch sử tin nhắn và sắp xếp hộp thư thoại:
  - `messages(conversation_id, created_at DESC)`
  - `conversations(last_message_at DESC)`

Khi chuyển sang production ổn định:

```text
HIBERNATE_DDL_AUTO=validate
FLYWAY_ENABLED=true
```

## Cấu Trúc Project

```text
src/main/java/com/fptu/exe/skillswap
├── ProjectApplication.java
├── infrastructure
│   ├── config
│   ├── filter
│   ├── security
│   └── storage
├── modules
│   ├── academic
│   ├── identity
│   ├── mentor
│   └── system
└── shared
    ├── constant
    ├── controller
    ├── dto
    ├── entity
    ├── exception
    ├── persistence
    └── util
```

Project đi theo hướng Spring Modulith. Mỗi package trong `modules` đại diện cho một business module. Code dùng chung nằm ở:

- `shared`: response model, exception, base entity, UUID v7, utility
- `infrastructure`: security, config, filter, storage integration

## Authentication Và Authorization

Luồng đăng nhập hiện tại:

- `POST /api/auth/google`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

FE lấy Google `idToken` qua Google Identity Services, gửi về:

```text
POST /api/auth/google
```

BE verify `idToken`, tạo hoặc liên kết tài khoản, sau đó trả:

```json
{
  "accessToken": "...",
  "refreshToken": "..."
}
```

Khi gọi API cần xác thực:

```text
Authorization: Bearer <accessToken>
```

Google login cần `GOOGLE_CLIENT_ID`. Nếu email nằm trong `SYSTEM_ADMIN_EMAILS`, access token trả về sẽ có thêm role `SYSTEM_ADMIN`.

## Role Hiện Có

```text
MENTEE
MENTOR
ADMIN
SYSTEM_ADMIN
```

Quy tắc hiện tại:

- `MENTEE`: user mặc định sau khi login Google.
- `MENTOR`: user đã được duyệt mentor.
- `ADMIN`: xử lý nghiệp vụ vận hành, hiện dùng cho mentor verification.
- `SYSTEM_ADMIN`: quản trị hệ thống, cấp hoặc thu hồi quyền `ADMIN`.
- `SYSTEM_ADMIN` không kế thừa quyền `ADMIN`.

## API Chính

### Academic Catalog

- `GET /api/campuses`
- `GET /api/academic-programs`
- `GET /api/specializations`
- `GET /api/academic-programs/{programId}/specializations`

### Student Profile

- `GET /api/me/student-profile`
- `PUT /api/me/student-profile`

FE dùng `GET /api/auth/me` để đọc `profileCompleted` và `hasStudentProfile`, từ đó quyết định cho user vào dashboard hay chuyển sang form hoàn thiện hồ sơ học thuật.

### Mentor Profile

- `GET /api/me/mentor-profile`
- `PUT /api/me/mentor-profile`

`GET /api/me/mentor-profile` trả `exists=false` nếu user chưa có mentor profile, để FE bắt đầu onboarding mà không coi đây là lỗi.

`PUT /api/me/mentor-profile` lưu toàn bộ hồ sơ mentor trong một request. Các trường chính:

- `headline`: bắt buộc, là câu định vị ngắn cho mentor, ví dụ `Backend Developer | Spring Boot Mentor`.
- `expertiseDescription`: bắt buộc, text tự do tối đa `1000` ký tự để mentor mô tả năng lực, kinh nghiệm, điểm mạnh và phạm vi có thể hỗ trợ.
- `supportingSubjects`: tùy chọn, text tự do tối đa `1000` ký tự để mô tả các môn học có thể hỗ trợ.
- `isAvailable`: tùy chọn khi cập nhật; nếu user tạo profile mới và không truyền field này thì backend mặc định `true`. Mentor có thể tự tắt/bật sau.
- `helpTopicIds`: bắt buộc, danh sách chủ đề có thể hỗ trợ.
- `teachingMode`: bắt buộc, một trong `ONLINE`, `OFFLINE`, `HYBRID`.
- `sessionDuration`: bắt buộc, là thời lượng tối đa mentor nhận cho một buổi mentoring; chỉ nhận một trong `15`, `30`, `60`, `90`.
- `linkedinUrl`, `githubUrl`, `portfolioUrl`: tùy chọn.

`bio` chỉ còn nằm ở `StudentProfile` và được tái sử dụng làm phần giới thiệu chung trong mentor detail/discovery. `MentorProfile` hiện chỉ giữ `headline`, `expertiseDescription`, `supportingSubjects`, `helpTopicIds`, `teachingMode`, `sessionDuration`, `isAvailable` và các link cá nhân; các field cũ như `currentPosition`, `currentCompany`, `industry`, `yearsOfExperience` và `hourlyRate` đã bị loại khỏi contract và schema.

Avatar không cập nhật qua API này. Backend lấy avatar từ tài khoản người dùng, ví dụ Google login hoặc hồ sơ học thuật.

### Mentor Verification Cho User

- `POST /api/me/mentor-verification/request`
- `GET /api/me/mentor-verification`
- `POST /api/me/mentor-verification/documents`
- `GET /api/me/mentor-verification/documents/{documentId}`
- `DELETE /api/me/mentor-verification/documents/{documentId}`
- `GET /api/me/mentor-verification/timeline`
- `POST /api/me/mentor-verification/submit`
- `POST /api/me/mentor-verification/withdraw`

Khi gọi `POST /api/me/mentor-verification/submit`, request cần gửi thêm:

- `termsAccepted=true` ở lần nộp đầu tiên cho version điều khoản hiện tại.

Backend chỉ cho phép submit khi các điều kiện sau đều đạt:

- Hoàn tất `Student Profile` nếu `MENTOR_VERIFICATION_REQUIRE_STUDENT_PROFILE_COMPLETED=true`.
- Hoàn tất `Mentor Profile` nếu `MENTOR_VERIFICATION_REQUIRE_MENTOR_PROFILE_COMPLETED=true`.
- Có ít nhất một `FPTU_AFFILIATION_PROOF`.
- Có ít nhất một `EXPERTISE_PROOF`.

Các cấu hình liên quan:

- `MENTOR_TERMS_VERSION`
- `MENTOR_VERIFICATION_REQUIRE_STUDENT_PROFILE_COMPLETED`
- `MENTOR_VERIFICATION_REQUIRE_MENTOR_PROFILE_COMPLETED`

Upload document:

- Ảnh `JPG`, `JPEG`, `PNG` lưu qua Cloudinary.
- PDF lưu qua Cloudflare R2.
- File upload qua BE tối đa `1MB`.
- `FPTU_AFFILIATION_PROOF`: minh chứng là sinh viên/cựu sinh viên FPTU, tối đa `1` file đang hoạt động.
- `EXPERTISE_PROOF`: minh chứng năng lực mentor, tối đa `3` file đang hoạt động.
- API upload không dùng `isPrimary`; FE chỉ cần gửi `documentType` và `file`.

Các `documentType` hiện dùng:

```text
FPTU_AFFILIATION_PROOF
EXPERTISE_PROOF
```

### Mentor Verification Cho Admin

- `GET /api/admin/mentor-verification/requests`
- `GET /api/admin/mentor-verification/requests/{requestId}`
- `GET /api/admin/mentor-verification/requests/{requestId}/lock`
- `POST /api/admin/mentor-verification/requests/{requestId}/lock/refresh`
- `POST /api/admin/mentor-verification/requests/{requestId}/request-revision`
- `POST /api/admin/mentor-verification/requests/{requestId}/approve`
- `POST /api/admin/mentor-verification/requests/{requestId}/reject`

Admin queue hỗ trợ filter/search theo hướng tối ưu cho vận hành:

- filter theo `status`
- search theo email hoặc tên
- pagination
- sort theo các field an toàn

Soft lock:

- Khi admin mở detail request, hệ thống có thể ghi lock.
- Admin khác vẫn xem được request.
- Admin khác không được approve/reject/request-revision nếu lock còn hiệu lực.
- `POST /lock/refresh` gia hạn lock thêm 5 phút.

### System Admin

- `POST /api/system/users/admin-role/grant`
- `POST /api/system/users/admin-role/revoke`
- `GET /api/system/users/admins`

Các API này yêu cầu role `SYSTEM_ADMIN`.

Request cấp hoặc thu hồi quyền admin:

```json
{
  "email": "admin@fpt.edu.vn"
}
```

Rule:

- Email phải thuộc user đã tồn tại trong hệ thống.
- User đã có `ADMIN` mà grant lại sẽ trả `409 Conflict`.
- User chưa có `ADMIN` mà revoke sẽ trả `409 Conflict`.
- Grant `ADMIN` chuyển tài khoản sang vai trò vận hành admin-only: backend gỡ `MENTEE` và `MENTOR` khỏi user để chặn các API self-service/mentoring dành cho user thường.
- Revoke `ADMIN` trả tài khoản về role mặc định `MENTEE`; role `MENTOR` không được tự khôi phục sau khi thu hồi admin.
- `ADMIN` không gọi được API thuộc `/api/system/**`.

## Mentor Verification State

Trạng thái request:

```text
DRAFT
READY_FOR_SUBMISSION
PENDING_REVIEW
NEEDS_REVISION
APPROVED
REJECTED
WITHDRAWN
```

Quy tắc nghiệp vụ:

- `DRAFT`: user mới tạo request, chưa nộp.
- `READY_FOR_SUBMISSION`: request đủ điều kiện nộp.
- `PENDING_REVIEW`: đã nộp, chờ admin duyệt.
- `NEEDS_REVISION`: admin yêu cầu chỉnh sửa, user sửa trên request cũ.
- `APPROVED`: request khóa, flow hoàn tất, user được kích hoạt mentor theo rule backend.
- `REJECTED`: request khóa, muốn nộp lại phải tạo request mới.
- `WITHDRAWN`: request khóa, muốn làm lại phải tạo request mới.

Timeline dùng để FE hiển thị lịch sử:

```text
created
submitted
revision requested
resubmitted
approved
rejected
withdrawn
```

## API Response Format

Success response:

```json
{
  "timestamp": "2026-06-09 20:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "success",
  "data": {}
}
```

Created response dùng `201`:

```json
{
  "timestamp": "2026-06-09 20:00:00",
  "status": 201,
  "code": "CREATED_0201",
  "message": "Created successfully",
  "data": {}
}
```

Error response cũng dùng cùng shape và được xử lý tập trung bởi `GlobalExceptionHandler`. Message trả về ưu tiên tiếng Việt UTF-8, không expose raw exception cho client.

## ID Convention

Toàn bộ primary key entity dùng UUID v7.

Rule:

- Không thêm `Long id` cho entity mới.
- Không trộn `Long` và `UUID`.
- Không thêm `publicId` riêng nếu không có lý do mạnh.
- Public API identifier dùng UUID của entity.
- Entity mới cần generated id thì dùng `@GeneratedUuidV7`.

Generator nằm tại:

```text
shared/persistence
```

## Docker Compose

Chạy backend và database:

```powershell
docker compose up -d --build
```

Chạy riêng PostgreSQL:

```powershell
docker compose up -d postgres-db
```

Kiểm tra config:

```powershell
docker compose config
```

Dừng service:

```powershell
docker compose down
```

Reset database volume local/test:

```powershell
docker compose down -v
docker compose up -d --build
```

`docker compose down -v` sẽ xóa toàn bộ dữ liệu PostgreSQL trong volume. Chỉ dùng cho môi trường dev/test khi cần schema sạch.

## Test

Chạy toàn bộ test:

```powershell
.\mvnw.cmd test
```

Chạy targeted test cho mentor verification:

```powershell
.\mvnw.cmd "-Dtest=MentorVerificationFlowIntegrationTest,MentorVerificationServiceTest,MentorVerificationControllerTest,AdminMentorVerificationServiceTest,AdminMentorVerificationControllerTest" test
```

Chạy targeted test cho system role:

```powershell
.\mvnw.cmd "-Dtest=IdentityLoginTransactionServiceTest,SystemUserRoleServiceTest,SystemUserRoleControllerTest" test
```

Build jar:

```powershell
.\mvnw.cmd package
```

Build không chạy test:

```powershell
.\mvnw.cmd package -DskipTests
```

## Deploy Test Trên VPS

Branch deploy test hiện tại là `dev`.

```bash
cd /opt/fu-skillswap/backend
git fetch origin
git checkout dev
git pull origin dev
docker compose config
docker compose down -v
docker compose up -d --build
docker logs -f skillswap-backend
```

Smoke test sau startup:

```bash
curl http://localhost:8080/api/campuses
```

Nếu không muốn xóa dữ liệu test, bỏ dòng:

```bash
docker compose down -v
```

## Git Workflow

Branch flow:

- `main`: base ổn định
- `dev`: nhánh tích hợp active
- `feat/<short-name>`: phát triển feature mới
- `fix/<short-name>`: sửa lỗi ở dev hoặc staging
- `hotfix/<short-name>`: sửa lỗi production khẩn cấp
- `refactor/<short-name>`: tái cấu trúc không đổi behavior
- `docs/<short-name>`: chỉ sửa tài liệu
- `chore/<short-name>`: build, config, dependency hoặc maintenance

Commit message format:

```text
<type>(<scope>): <description>
```

Ví dụ:

```text
feat(mentor): add mentor verification APIs
chore(config): update docker compose environment
docs(readme): update backend handover guide
```

Rule:

- Description viết thường chữ đầu.
- Dùng động từ ở thì hiện tại.
- Không kết thúc bằng dấu chấm.
- `type` phải khớp với prefix của branch.

Trước khi mở pull request:

```powershell
.\mvnw.cmd test
```

## Không Commit

Không commit các file/thư mục sau:

- `.env`
- `target/`
- `uploads/`
- `logs/`
- IDE local metadata

## Troubleshooting

### App không kết nối được PostgreSQL

Kiểm tra Docker:

```powershell
docker compose ps
```

Restart database:

```powershell
docker compose up -d postgres-db
```

### Schema mismatch sau khi đổi entity

Với local dev, reset database volume:

```powershell
docker compose down -v
docker compose up -d postgres-db
```

Sau đó chạy lại app.

### Google login lỗi

Kiểm tra:

- `GOOGLE_CLIENT_ID`
- FE gửi đúng Google `idToken`
- Token audience khớp `GOOGLE_CLIENT_ID`

### User không có role SYSTEM_ADMIN sau khi thêm env

Kiểm tra:

- Email có nằm trong `SYSTEM_ADMIN_EMAILS`
- Không có khoảng trắng sai hoặc typo
- User đã login Google lại sau khi cập nhật env
- Access token mới có chứa role `SYSTEM_ADMIN`

### Cloudinary lỗi khi startup

Nếu `CLOUDINARY_ENABLED=true`, cần đủ:

```text
CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET
```

### R2 lỗi khi upload PDF

Nếu `R2_ENABLED=true`, cần đủ:

```text
R2_ENDPOINT
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
R2_BUCKET
R2_REGION
R2_DOCUMENTS_PREFIX
```

`R2_ENDPOINT` không để nguyên placeholder. Cần thay bằng endpoint thật trong Cloudflare Dashboard, dạng:

```text
https://<account-id>.r2.cloudflarestorage.com
```
