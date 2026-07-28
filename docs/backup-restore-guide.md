# Hướng dẫn Backup & Restore Database

Tài liệu này mô tả quy trình Sao lưu (Backup) và Phục hồi (Restore) dữ liệu cho PostgreSQL Database đang chạy trong Docker container.

## 1. Cơ chế Backup tự động

Pipeline CI/CD đã được cấu hình để tự động gọi script `ops/backup-postgres.sh` trước mỗi lần chạy `docker compose up`. 

- Script này sử dụng lệnh `pg_dump` với định dạng `custom` (-Fc).
- File dump được nén bằng `gzip` và lưu mặc định tại thư mục `/opt/skillswap/backups/`.
- Script có cơ chế tự động xóa các file backup cũ hơn 14 ngày.

**Lưu ý**: Hãy đảm bảo biến môi trường `POSTGRES_DB` và `POSTGRES_USER` có mặt trong file `.env` trước khi chạy script.

## 2. Restore Database (Phục hồi dữ liệu)

Trong trường hợp cần phục hồi dữ liệu từ file backup (ví dụ: `skillswap-20231015T120000Z.dump.gz`), thực hiện các bước sau:

1. **Drain traffic và dừng Backend Container** (để tránh thay đổi dữ liệu trong lúc restore):
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml stop spring-backend
   ```

2. **Chạy Script Restore**:
   Dự án cung cấp sẵn script `ops/restore-postgres.sh` để đẩy file dump vào DB.
   ```bash
   # Nạp biến môi trường
   set -a; source .env; set +a
   
   # Chỉ chạy sau khi reverse proxy đã drain traffic.
   PRODUCTION_TRAFFIC_DRAINED=true \
   SKILLSWAP_RESTORE_CONFIRM=RESTORE_PRODUCTION \
   bash ops/restore-postgres.sh /opt/skillswap/backups/skillswap-20231015T120000Z.dump.gz
   ```
   Script tự kiểm tra checksum cạnh file `.sha256` (nếu có), giải nén tạm và chạy `pg_restore --list` trước khi restore.

3. **Khởi động lại Backend**:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml start spring-backend
   ```

4. **Xác minh**:
   Chạy các bài Smoke Test để đảm bảo dữ liệu đã về đúng trạng thái và App kết nối DB bình thường.
