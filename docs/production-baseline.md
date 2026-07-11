# Production Baseline

## Mục tiêu

Tài liệu này là checklist tối thiểu để triển khai SkillSwap backend trên một VPS duy nhất theo hướng an toàn:

- không boot production bằng `dev`
- không dùng secret/password mặc định
- DB và RabbitMQ chỉ chạy nội bộ trong mạng Docker
- health/readiness phản ánh app, database, và RabbitMQ
- shutdown có graceful stop

## Trước khi deploy

Kiểm tra các biến môi trường bắt buộc:

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET_KEY`
- `JWT_ISSUER`
- `JWT_AUDIENCE`
- `CURSOR_AES_KEY`
- `CURSOR_HMAC_KEY`
- `GOOGLE_CLIENT_ID`
- `RABBITMQ_DEFAULT_PASS`

`docker-compose.yml` không tự sinh profile hoặc credential. Backend/PostgreSQL chỉ bind loopback; reverse proxy là cổng public duy nhất. Release image chạy non-root và chỉ được build sau khi `mvn clean verify` thành công.

Nếu bật các tính năng liên quan:

- Storage: `STORAGE_ENABLED=true` và bộ `STORAGE_*`
- Mail: `APPLICATION_MAIL_ENABLED=true` và bộ `SPRING_MAIL_*`
- Payments: `PAYOS_*`

## Kiểm tra sau khi deploy

- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /api/campuses`
- `GET /api/auth/me` với token hợp lệ

## Backup / restore

- Trước khi nâng cấp schema, backup volume PostgreSQL.
- Nếu cần rollback, dừng container, restore volume backup, rồi chạy lại cùng image/tag cũ.
- Chỉ dùng `docker compose down -v` trên môi trường dev/test khi muốn xóa toàn bộ dữ liệu cũ.

## Rollback tối thiểu

1. Dừng stack hiện tại.
2. Khôi phục database volume từ backup.
3. Quay lại image/tag trước đó.
4. Chạy lại smoke test readiness và các API public tối thiểu.
