# Storage Baseline & Policy Evidence

**Date:** 2026-08-07
**Project:** fu-skillswap-be

## 1. Cơ sở Hạ tầng (Infrastructure)
- **Database Engine:** PostgreSQL (Version 16+)
- **VPS SSD:** 80 GB NVMe (Target: keep usage under 60%)
- **Persistent Object Storage:** Cloudflare R2 (S3-compatible) thông qua `S3StorageGatewayImpl`.
- **Local Fallback:** `LocalFileStorageGatewayImpl` chỉ hoạt động trên profile `local`, `test`. Bị cấm ngặt nghèo (Fail-fast) trên môi trường `Production`.

## 2. Các Tập dữ liệu Archive (Lưu nóng -> Lạnh -> R2)
- `internal_telemetry_events`: Lưu nóng 14 ngày.
- `audit_logs`: Lưu nóng 90 ngày.

## 3. Các Tập dữ liệu Cleanup (Hard Delete)
- `email_outbox`: Cleanup sau 7 ngày (Chỉ áp dụng trạng thái `SENT`, `FATAL_ERROR`).
- `course_outbox_events`: Cleanup sau 7 ngày (Terminal status).
- `bunny_webhook_events`: Cleanup sau 7 ngày (Terminal status).

## 4. Các Ngoại lệ đã Ghi nhận (Known Exclusions)
- Bảng `messages`: Giữ nguyên (KEEP). Hiện tại hệ thống không archive messages do scale chưa bùng nổ (~10k users/year). Chỉ tập trung tracking size.

## 5. Cơ chế Sao lưu (Backup)
- Hình thức: Custom format pg_dump (`pg_dump -Fc`).
- Tần suất/Vị trí: Đẩy lên Cloudflare R2 bucket `db_backups` thông qua AWS CLI.
- Lưu trữ (Retention): 30 ngày, điều khiển bằng S3 Lifecycle Rule trên Cloudflare.
