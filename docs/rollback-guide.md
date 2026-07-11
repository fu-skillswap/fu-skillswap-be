# Hướng dẫn Rollback (Rollback Guide)

Tài liệu này hướng dẫn cách khôi phục hệ thống về trạng thái ổn định trước đó (Rollback) trong trường hợp bản deploy mới gặp sự cố (Readiness check fail, lỗi logic nghiêm trọng, OOM...).

## 1. Nguyên tắc Rollback
- **Không bao giờ dùng tag `latest` để rollback**.
- Luôn chỉ định rõ **Git Commit SHA** của phiên bản image ổn định trước đó.
- Ứng dụng Backend và Database là 2 thành phần riêng biệt. Việc Rollback App không đồng nghĩa với Rollback Database.

## 2. Các Bước Rollback Application

Nếu lỗi chỉ xuất phát từ Code backend (không liên quan đến thay đổi DB migration), thực hiện các bước sau trên VPS:

1. SSH vào VPS và di chuyển đến thư mục dự án:
   ```bash
   cd ~/skillswap-deployment
   ```

2. Xác định Image Tag cũ cần rollback (Lấy từ GitHub Actions history hoặc commit log). Ví dụ: `ghcr.io/owner/skillswap-backend:a1b2c3d`.

3. Cập nhật biến môi trường `APP_IMAGE` để trỏ về Image cũ:
   ```bash
   export APP_IMAGE="ghcr.io/owner/skillswap-backend:a1b2c3d"
   ```

4. Kéo Image cũ về (nếu chưa có sẵn trên VPS):
   ```bash
   docker compose -f docker-compose.prod.yml pull spring-backend
   ```

5. Khởi động lại container Backend với image cũ:
   ```bash
   docker compose -f docker-compose.prod.yml up -d --wait spring-backend
   ```

6. Chạy [Smoke Test](operations-runbook.md) để xác minh ứng dụng đã sống lại.

## 3. Rollback Database (Nếu Migration gây lỗi nghiêm trọng)

Nếu bản deploy mới chứa Flyway Migration có tính phá hủy (Destructive) và gây lỗi, bạn bắt buộc phải:
1. Dừng nhận traffic bằng cách Stop Backend container.
2. Khôi phục DB từ file Dump đã tạo trước lúc deploy. (Xem [Backup & Restore Guide](backup-restore-guide.md)).
3. Thực hiện lại các bước Rollback Application ở mục 2.
