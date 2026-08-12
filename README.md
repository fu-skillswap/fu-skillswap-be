# SkillSwap Backend

> REST API backend application chịu trách nhiệm cho các tính năng authentication, mentor discovery, booking, payment, real-time chat, forum, blog, courses và notification của nền tảng SkillSwap.

## Overview

SkillSwap là nền tảng kết nối mentor và mentee phục vụ việc học tập và trao đổi kỹ năng tại Đại học FPT (FPTU).

Hệ thống cho phép người dùng tìm kiếm mentor, đặt lịch các buổi mentoring 1-on-1, tạo learning course và bài viết blog, tham gia diễn đàn cộng đồng, tham gia các khóa học và quản lý các hoạt động mentoring.

Repository này chứa ứng dụng backend chịu trách nhiệm xử lý business logic, persistence, authentication, authorization, background processing, real-time messaging và tích hợp với các dịch vụ bên ngoài.

Các trách nhiệm chính bao gồm:

- Quản lý thông tin người dùng, authentication, authorization và tích hợp Google OAuth
- Quản lý hồ sơ mentor, quy trình xác thực mentor (verification), lịch rảnh (availability) và danh mục dịch vụ (service catalog)
- Đặt lịch hẹn mentoring 1-on-1, đảm bảo queue safety, báo giá (quotes) và đổi lịch (rescheduling)
- Tích hợp payment gateway (PayOS), quản lý ví (wallet), số dư sổ cái (ledger balances) và payout
- Nhắn tin real-time (chat trực tiếp và chat theo booking) sử dụng WebSocket STOMP messaging relay
- Tạo bài viết blog giáo dục, theo dõi tương tác (engagement) và cào cache các bài viết xu hướng (trending cache)
- Diễn đàn cộng đồng (forum), quản lý reaction, bình luận phân cấp (nested comment replies) và bộ lọc từ cấm (prohibited phrase filtering)
- Quản lý phân hệ khóa học (course subsystem), đăng ký khóa và ký quỹ thanh toán theo session (escrow settlement)
- Gửi notification qua email (Brevo SMTP) và xử lý sự kiện qua domain event outbox pattern
- Tích hợp object storage quản lý public assets và tài liệu xác minh riêng tư (Cloudflare R2)

## Features / Scope

### Features

- JWT-based authentication kết hợp cookie HttpOnly refresh token và tích hợp Google OAuth
- Role-based authorization và cơ chế tự động cấp quyền System Administrator
- Tìm kiếm mentor (mentor discovery) hỗ trợ full-text search (FTS) tiếng Việt và behavioral ranking
- Quản lý template lịch rảnh (availability templates) và tự động sinh time slots
- Đặt lịch session 1-on-1 đảm bảo queue safety và quy tắc hết hạn (expiration rules)
- Tích hợp cổng thanh toán PayOS, giao dịch ví, hạch toán sổ cái (ledger accounting) và payout
- Nhắn tin real-time STOMP WebSocket qua RabbitMQ relay cho các tính năng chat và notification
- Phân hệ thảo luận forum hỗ trợ reaction, phản hồi bình luận lồng nhau và phát hiện từ cấm
- Nền tảng xuất bản blog hỗ trợ theo dõi người dùng (growth follows) và chống trùng lặp lượt xem (view deduplication)
- Phân hệ khóa học (course subsystem) hỗ trợ giữ tiền ký quỹ theo session (per-session escrow) và xử lý thanh toán bất biến (settlement idempotency)
- Xử lý object storage công khai và riêng tư thông qua Cloudflare R2 
- Pattern transactional domain event outbox giúp đảm bảo độ tin cậy khi xử lý sự kiện

### Scope

Backend được thiết kế theo kiến trúc **Spring Modulith** modular monolith.

Repository này sở hữu (owns):

- Core domain logic, quy tắc nghiệp vụ và persistence cho toàn bộ các module backend (`identity`, `mentor`, `booking`, `payment`, `chat`, `forum`, `blog`, `course`, `notification`, `admin`, `filestorage`, `catalog`, `feedback`, `seo`, `system`)
- Quản lý các file SQL migration và vòng đời database schema (Flyway)
- REST API contracts và các endpoint WebSocket STOMP
- Tích hợp các dịch vụ bên ngoài (PayOS, Google OAuth / Calendar, Brevo SMTP, Cloudflare R2, RabbitMQ)

Repository này không sở hữu (does not own):

- Giao diện người dùng trên Web hoặc Mobile (các thành phần UI React / Next.js)
- Hạ tầng xử lý thanh toán cốt lõi của nhà cung cấp dịch vụ thanh toán (PayOS gateway infrastructure)

## Architecture

Ứng dụng backend được triển khai theo mô hình modular monolith với kiến trúc phân tầng (layered architecture) bên trong từng module và adapter hạ tầng.

```text
                                Client (Web / Mobile)
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  ▼                                               ▼
             HTTP / REST                                  WebSocket (STOMP)
                  │                                               │
                  ▼                                               ▼
          Controller Layer                                WebSocket Handler
                  │                                               │
     ┌────────────┼──────────────────────────┬────────────────────┘
     │            ▼                          ▼
     │   Application / Service        Domain Event Outbox
     │            │                          │
     │            ▼                          ▼
     │       Domain Model                RabbitMQ
     │            │                          │
     ▼            ▼                          ▼
    Infrastructure / Repositories      Real-time Relay
                  │
                  ▼
         PostgreSQL Database
```

### Tổ chức các Module

Các nghiệp vụ chính được đóng gói thành từng module độc lập nằm dưới package `com.fptu.exe.skillswap.modules`:

- `identity`: Đăng ký người dùng, authentication, xoay vòng refresh token, Google OAuth và hồ sơ sinh viên (student profile)
- `mentor`: Hồ sơ mentor, upload tài liệu xác minh, time slots / templates lịch rảnh và danh mục dịch vụ mentoring
- `booking`: Đặt lịch session, báo giá chi phí (quotes), hủy lịch và đổi lịch
- `payment`: Tạo đường link thanh toán PayOS, webhook, ví sổ cái, mã giảm giá (coupons) và chiến dịch payout
- `chat`: Tin nhắn thoại/chữ trực tiếp & chat theo booking, quản lý file đính kèm và kiểm soát an toàn cuộc trò chuyện
- `forum`: Bài viết, reaction, cây bình luận và kiểm duyệt từ ngữ vi phạm
- `blog`: Xuất bản bài viết, đếm tương tác, trending cache và theo dõi tác giả
- `course`: Quản lý khóa học, đăng ký tham gia, giữ tiền ký quỹ từng session và xử lý settlement idempotency
- `notification`: Thông báo trong ứng dụng (in-app notifications), deep links và gửi email qua outbox
- `admin`: Phân công ca kiểm duyệt (case assignments), ghi nhận audit log và các thao tác quản trị
- `filestorage`: Tạo pre-signed URL và quản lý asset intent
- `catalog`, `feedback`, `seo`, `system`, `demo`: Danh mục hệ thống, đánh giá phản hồi, SEO metadata và dịch vụ hệ thống

Giao tiếp giữa các module được thực hiện thông qua Spring Modulith domain events và các service interface được public rõ ràng.

## Tech Stack

| Category | Technology | Version / Implementation |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.5 |
| Architecture Framework | Spring Modulith | 1.1.3 |
| Database | PostgreSQL | 17 (Runtime) |
| Database Migration | Flyway | `flyway-core` |
| Cache | Caffeine | Bounded single-instance caches |
| Messaging / Event Relay | RabbitMQ | 3.13 (AMQP + STOMP plugin) |
| Object Storage | Cloudflare R2 / AWS S3 | `software.amazon.awssdk:s3:2.25.53` |
| Payment Gateway | PayOS | `vn.payos:payos-java:2.0.1` |
| Email Service | Brevo (SMTP) | `spring-boot-starter-mail` |
| API Documentation | SpringDoc OpenAPI / Swagger UI | 2.5.0 |
| Boilerplate / Mapping | Lombok / MapStruct | Lombok 1.18.32 / MapStruct 1.6.2 |
| Build Tool | Apache Maven | Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| Containerization | Docker & Docker Compose | Docker Compose v2 |
| Testing Frameworks | JUnit 5, H2, Testcontainers, ArchUnit | Testcontainers 1.19.7, ArchUnit 1.2.1 |

## Prerequisites

Trước khi khởi chạy ứng dụng, hãy đảm bảo môi trường máy tính của bạn đã cài đặt các công cụ sau:

- **JDK 21**
- **Docker Engine** & **Docker Compose** (v2+)
- **Git**

Công cụ khuyến nghị cho quá trình phát triển:

- PostgreSQL client (`psql` hoặc các công cụ GUI như DBeaver)
- IntelliJ IDEA hoặc Eclipse / VS Code hỗ trợ Java 21

## Getting Started

### 1. Clone repository

```bash
git clone https://github.com/fu-skillswap/fu-skillswap-be.git
cd project
```

### 2. Cấu hình môi trường (Environment Variables)

Tạo file cấu hình môi trường local từ mẫu `.env.example`:

Trên Windows PowerShell:
```powershell
Copy-Item .env.example .env
```

Trên Linux / macOS:
```bash
cp .env.example .env
```

Cập nhật các giá trị secret cần thiết trong `.env` (như `JWT_SECRET_KEY`, `CURSOR_AES_KEY`, `CURSOR_HMAC_KEY`, `POSTGRES_PASSWORD`, và `RABBITMQ_DEFAULT_PASS`).

### 3. Khởi chạy các hạ tầng phụ thuộc (Infrastructure Dependencies)

Khởi chạy PostgreSQL và RabbitMQ bằng Docker Compose:

```bash
docker compose up -d postgres-db rabbitmq
```

Lệnh này sẽ khởi chạy:
- **PostgreSQL**: Bound tới port `5444` ở host (`127.0.0.1:5444`)
- **RabbitMQ**: AMQP port `5672` và STOMP relay port `61613`

### 4. Chạy kiểm thử (Tests)

Thực thi bộ test suite bằng Maven wrapper (các unit test sử dụng database H2 in-memory nên không yêu cầu cấu hình database trước):

Trên Windows PowerShell:
```powershell
.\mvnw.cmd test
```

Trên Linux / macOS:
```bash
./mvnw test
```

### 5. Khởi chạy ứng dụng (Run Application)

Khởi chạy ứng dụng Spring Boot ở môi trường local:

Trên Windows PowerShell:
```powershell
.\mvnw.cmd spring-boot:run
```

Trên Linux / macOS:
```bash
./mvnw spring-boot:run
```

### 6. Kiểm tra ứng dụng (Verification)

Ứng dụng backend sẽ khởi chạy tại:

```text
https://api.skillswap.asia
```

Kiểm tra trạng thái health check:

```text
GET https://api.skillswap.asia/actuator/health
```

Kết quả phản hồi kỳ vọng: `{"status":"UP"}`

## Configuration

Ứng dụng được cấu hình thông qua các biến môi trường khai báo trong file `.env` và `src/main/resources/application.yaml`.

| Variable | Required | Default | Description |
|---|---:|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `dev` | Profile Spring đang hoạt động (`dev` hoặc `prod`) |
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5444/skillswapdb` | URL kết nối JDBC tới PostgreSQL |
| `DATABASE_USERNAME` | Yes | `postgres` | Username kết nối database |
| `DATABASE_PASSWORD` | Yes | `change-me` | Password kết nối database |
| `POSTGRES_DB` | Yes | `skillswapdb` | Tên database PostgreSQL trong Docker Compose |
| `JWT_SECRET_KEY` | Yes | - | Secret key ký JWT token (base64, tối thiểu 32 bytes) |
| `JWT_ISSUER` | Yes | `skillswap` | Tên nhà phát hành JWT (issuer) |
| `JWT_AUDIENCE` | Yes | `skillswap-api` | Tên đối tượng nhận JWT (audience) |
| `JWT_EXPIRATION` | No | `3600000` | Thời gian hết hạn của access token tính bằng miligiây (1 giờ) |
| `JWT_REFRESH_EXPIRATION` | No | `604800000` | Thời gian hết hạn của refresh token tính bằng miligiây (7 ngày) |
| `CURSOR_AES_KEY` | Yes | - | Key AES 32-byte (base64) để mã hóa cursor pagination |
| `CURSOR_HMAC_KEY` | Yes | - | Key HMAC 32-byte (base64) để xác thực tính vẹn toàn cursor |
| `RABBITMQ_DEFAULT_USER` | Yes | `skillswap` | Username truy cập RabbitMQ |
| `RABBITMQ_DEFAULT_PASS` | Yes | - | Password truy cập RabbitMQ |
| `REALTIME_OUTBOX_ENABLED` | No | `true` | Bật/tắt tiến trình xử lý domain event outbox |
| `WEBSOCKET_STOMP_ENABLED` | No | `true` | Bật/tắt tính năng WebSocket STOMP relay sang RabbitMQ |
| `FLYWAY_ENABLED` | No | `true` | Tự động chạy các file SQL migration khi ứng dụng khởi chạy |
| `HIBERNATE_DDL_AUTO` | No | `validate` | Chế độ kiểm tra schema của Hibernate (`validate` ở prod/dev) |
| `APPLICATION_SWAGGER_ENABLED` | No | `true` | Bật/tắt các endpoint OpenAPI / Swagger UI |
| `PAYOS_CLIENT_ID` | No | - | Client ID cổng thanh toán PayOS |
| `PAYOS_API_KEY` | No | - | API key cổng thanh toán PayOS |
| `PAYOS_CHECKSUM_KEY` | No | - | Checksum key để xác minh dữ liệu webhook từ PayOS |
| `STORAGE_ENABLED` | No | `true` | Bật/tắt tích hợp lưu trữ file Cloudflare R2 |
| `STORAGE_ENDPOINT` | No | - | URL endpoint dịch vụ Cloudflare R2 |

## Project Structure

```text
src/
├── main/
│   ├── java/com/fptu/exe/skillswap/
│   │   ├── infrastructure/        # Adapter hạ tầng kỹ thuật (Security, WebSocket, Storage, Config)
│   │   │   ├── bunny/             # Tích hợp dịch vụ truyền tải media bên ngoài
│   │   │   ├── config/            # Cấu hình Spring beans, OpenAPI và cài đặt ứng dụng
│   │   │   ├── filter/            # Bộ lọc bảo mật và rate limiting
│   │   │   ├── realtime/          # Tiến trình xử lý RabbitMQ domain event outbox
│   │   │   ├── security/          # JWT filter, OAuth và entrypoint xác thực
│   │   │   ├── storage/           # Client adapter kết nối Cloudflare R2
│   │   │   └── websocket/         # Cấu hình broker STOMP relay
│   │   ├── modules/               # Các module nghiệp vụ Spring Modulith
│   │   │   ├── admin/             # Phân công ca kiểm duyệt và công cụ quản trị
│   │   │   ├── blog/              # Tạo bài viết, theo dõi tương tác và trending cache
│   │   │   ├── booking/           # Đặt lịch mentoring, group session và tính giá
│   │   │   ├── catalog/           # Định nghĩa danh mục hệ thống và kỹ năng
│   │   │   ├── chat/              # Tin nhắn real-time chat trực tiếp & chat booking
│   │   │   ├── course/            # Phân hệ khóa học, đăng ký và ký quỹ escrow
│   │   │   ├── feedback/          # Hệ thống đánh giá và phản hồi
│   │   │   ├── filestorage/       # Xác minh file upload và tạo pre-signed URL
│   │   │   ├── forum/             # Bài viết, reaction, cây bình luận và kiểm duyệt
│   │   │   ├── identity/          # Authentication, Google OAuth và hồ sơ sinh viên
│   │   │   ├── mentor/            # Xác minh mentor, dịch vụ và slots lịch rảnh
│   │   │   ├── notification/      # Thông báo in-app và gửi email outbox
│   │   │   ├── payment/           # Tích hợp PayOS, sổ cái ví và payout
│   │   │   ├── seo/               # Quản lý metadata phục vụ tối ưu hóa tìm kiếm
│   │   │   └── system/            # Cấu hình và metadata toàn hệ thống
│   │   ├── shared/                # Entities dùng chung, ngoại lệ tùy chỉnh, DTOs và utility classes
│   │   └── ProjectApplication.java # File main khởi chạy ứng dụng Spring Boot
│   └── resources/
│       ├── db/migration/          # Các script SQL migration của Flyway (V1__... đến V87__...)
│       ├── application.yaml       # File cấu hình cơ sở
│       ├── application-dev.yml    # File ghi đè cấu hình môi trường development
│       └── application-prod.yml   # File ghi đè cấu hình môi trường production
└── test/
    └── java/com/fptu/exe/skillswap/
        ├── architecture/          # Kiểm tra các quy tắc ranh giới module bằng ArchUnit
        ├── infrastructure/        # Bộ kiểm thử hạ tầng và tích hợp (integration tests)
        ├── modules/               # Bộ unit test và integration test theo từng module
        ├── shared/                # Bộ test cho các thành phần dùng chung
        └── ModulithTest.java      # Kiểm tra cấu trúc phân ranh module của Spring Modulith

ops/                              # Các script vận hành (backup, restore, kiểm tra tiền release)
scripts/                          # Script kiểm thử tải (k6) & kiểm tra migration
```

- `modules/`: Chứa toàn bộ business logic được tổ chức theo ranh giới của từng module nghiệp vụ. Mỗi module đóng gói các controllers, services, repositories và entities riêng.
- `infrastructure/`: Chứa các thành phần hạ tầng kỹ thuật dùng chung như chuỗi lọc bảo mật (security chains), WebSocket STOMP broker và wrapper client Cloudflare R2.
- `shared/`: Chứa các base entity, custom exception, tiện ích mã hóa pagination cursor và idempotency handler được dùng chung giữa các module.

## Development

### Khởi chạy server local

Khởi chạy ứng dụng với chế độ hỗ trợ live reload:

Trên Windows PowerShell:
```powershell
.\mvnw.cmd spring-boot:run
```

Trên Linux / macOS:
```bash
./mvnw spring-boot:run
```

### Build gói ứng dụng (Application Package)

Biên dịch và đóng gói ứng dụng thành file JAR có thể thực thi:

Trên Windows PowerShell:
```powershell
.\mvnw.cmd clean package -DskipTests
```

Trên Linux / macOS:
```bash
./mvnw clean package -DskipTests
```

### Database migrations

Các file migration cơ sở dữ liệu được quản lý bởi Flyway và đặt tại đường dẫn:

```text
src/main/resources/db/migration/
```

Tên file migration mới bắt buộc tuân theo định dạng thứ tự tăng dần (ví dụ: `V88__add_new_feature_table.sql`).

Thử nghiệm chạy các script Flyway schema migration trên môi trường local:

```bash
./ops/rehearse-migration.sh
```

## Testing

### Unit tests

Unit tests được thực thi bằng JUnit 5 và database in-memory H2 giúp tối ưu tốc độ chạy test mà không phụ thuộc hạ tầng bên ngoài:

Trên Windows PowerShell:
```powershell
.\mvnw.cmd test
```

Trên Linux / macOS:
```bash
./mvnw test
```

### Integration tests

Integration tests sử dụng **Testcontainers** để khởi chạy một container PostgreSQL thực sự nhằm kiểm tra các ràng buộc dữ liệu phức tạp và tính tương thích của database schema (`*IT.java` và `*IntegrationTest.java`):

Trên Windows PowerShell:
```powershell
.\mvnw.cmd failsafe:integration-test
```

Trên Linux / macOS:
```bash
./mvnw failsafe:integration-test
```

### Chạy toàn bộ các bước kiểm tra (All Verification Checks)

Thực thi unit tests, integration tests, các quy tắc kiến trúc ArchUnit và kiểm tra build hoàn chỉnh:

Trên Windows PowerShell:
```powershell
.\mvnw.cmd clean verify
```

Trên Linux / macOS:
```bash
./mvnw clean verify
```

## API / Contracts

### REST API Documentation

REST API của hệ thống được tài liệu hóa bằng chuẩn SpringDoc OpenAPI v3.

Khi ứng dụng đang chạy ở môi trường local:

- **Swagger UI**: [https://api.skillswap.asia/swagger-ui/index.html](https://api.skillswap.asia/swagger-ui/index.html)
- **OpenAPI v3 JSON Specification**: [https://api.skillswap.asia/v3/api-docs](https://api.skillswap.asia/v3/api-docs)

### Real-Time WebSocket Contract

Hệ thống nhắn tin chat real-time và thông báo tức thì sử dụng giao thức STOMP over WebSockets kết hợp RabbitMQ broker relay:

- **WebSocket Endpoint**: `/ws-stomp`
- **App Destination Prefix**: `/app`
- **User Destination Prefix**: `/user`
- **Relay Port**: `61613` (RabbitMQ STOMP plugin)

## Build & Deployment

### Build file JAR thực thi

Đóng gói sản phẩm thành file JAR production:

```bash
./mvnw clean package
```

Artifact sau khi build sẽ được tạo tại:

```text
target/skillswap-0.0.1-SNAPSHOT.jar
```

### Build Docker image

Build container image bất biến từ file `Dockerfile` ở thư mục gốc:

```bash
docker build -t skillswap-backend .
```

### Quy trình triển khai (Deployment Workflow)

Quy trình triển khai trên môi trường production sử dụng Docker Compose và các script vận hành:

```text
Pull Request
     │
     ▼
Automated CI Verification (mvnw clean verify)
     │
     ▼
Release Preflight Checks (ops/release-preflight.sh)
     │
     ▼
Container Image Build & Flyway Schema Migration
     │
     ▼
Production Rollout (docker-compose.prod.yml)
```

## Security

### Authentication & Authorization

- **JWT Authentication**: Access token dạng JWT ngắn hạn được truyền qua HTTP Authorization header.
- **Refresh Token Cookie**: Refresh token dài hạn được lưu trữ an toàn trong Cookie HttpOnly, SameSite (`skillswap_refresh_token`).
- **Google OAuth 2.0**: Tích hợp Google OAuth 2.0 cho sinh viên và mentor đăng nhập, kết hợp mã hóa AES 32-byte lưu trữ token.

### Bảo vệ dữ liệu & Quản lý Secret

- **Bảo mật Cursor Pagination**: State phân trang cursor được mã hóa bằng AES 32-byte và xác minh vẹn toàn bằng HMAC SHA-256 nhằm ngăn chặn hành vi can thiệp dữ liệu.
- **Rate Limiting**: Sử dụng bộ giới hạn tần suất Caffeine trên bộ nhớ đơn thể cho các tác vụ bảo mật và nghiệp vụ nhạy cảm.
- **Cô lập Secret**: Tất cả mật khẩu production và API keys bắt buộc khai báo qua biến môi trường, tuyệt đối không lưu trong Git.

## Troubleshooting

### Ứng dụng không thể kết nối tới PostgreSQL

**Cause**: Container PostgreSQL `skillswap-postgres` chưa khởi chạy hoặc chưa đạt trạng thái healthy trên port 5444.

**Solution**:
Kiểm tra trạng thái container:
```bash
docker compose ps
```
Khởi động container PostgreSQL:
```bash
docker compose up -d postgres-db
```

### Lỗi trùng port 8080 (Port 8080 is already in use)

**Cause**: Một ứng dụng hoặc tiến trình khác đang chiếm dụng port 8080 trên máy host.

**Solution**:
Cấu hình port khởi chạy khác cho server:
Trên Windows PowerShell:
```powershell
$env:SERVER_PORT="8081"; .\mvnw.cmd spring-boot:run
```
Trên Linux / macOS:
```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
```

### Lỗi kết nối RabbitMQ hoặc STOMP relay failure

**Cause**: Dịch vụ RabbitMQ không thể truy cập hoặc plugin `rabbitmq_stomp` chưa khởi tạo thành công.

**Solution**:
Xem log của container RabbitMQ:
```bash
docker compose logs rabbitmq
```
Khởi động lại container RabbitMQ:
```bash
docker compose restart rabbitmq
```

## Contributing

Trước khi gửi Pull Request (PR):

1. Đảm bảo ứng dụng biên dịch thành công không có lỗi (`.\mvnw.cmd compile`).
2. Chạy toàn bộ các bộ unit test và kiểm tra quy tắc kiến trúc (`.\mvnw.cmd test`).
3. Chạy quy trình kiểm thử hoàn chỉnh (`.\mvnw.cmd clean verify`).
4. Đặt tất cả các file Flyway migration mới vào `src/main/resources/db/migration/` theo đúng quy tắc đặt tên tuần tự (`V<N>__...sql`).
5. Cập nhật các biến môi trường trong file `.env.example` và `README.md` nếu có bổ sung thêm cấu hình mới.
6. Giữ các commit tập trung vào một thay đổi logic duy nhất với mô tả commit rõ ràng.

