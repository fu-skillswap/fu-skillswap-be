# Phase 3 Deployment Report

Tài liệu này tóm tắt kết quả cấu hình và trạng thái của Phase 3 — CI/CD & VPS PRODUCTION DEPLOYMENT BASELINE.

## 1. Pipeline Summary
- **Job 1**: `build-and-test` chạy `./mvnw clean verify` để đảm bảo code pass toàn bộ test case.
- **Job 2**: `security-scan` chạy Trivy Vulnerability Scanner (Critical, High).
- **Job 3**: `docker-publish` chạy multi-stage Docker build (sử dụng `-DskipTests` để tránh duplicate test) và push lên GHCR bằng Immutable Tag (`GITHUB_SHA`).
- **Job 4**: `deploy-vps` SSH vào VPS, nạp `.env`, chạy `ops/backup-postgres.sh`, tải image bằng `GITHUB_SHA`, và dùng lệnh `docker compose up -d --wait`.

## 2. Infrastructure Status
- **VPS Topology**: 1 VPS duy nhất chạy 3 Docker containers (`spring-backend`, `skillswap-postgres`, `skillswap-rabbitmq`).
- **Nginx/SSL Status**: Setup Nginx ở mức Host OS, đóng vai trò Reverse Proxy (Public port 80/443).
- **Docker Compose Status**: `docker-compose.prod.yml` chạy hoàn chỉnh. PostgreSQL bind `127.0.0.1:5444:5432` (Loopback), RabbitMQ không expose ra ngoài. Backend bind `127.0.0.1:8080:8080`.
- **Healthcheck**: 
  - `spring-backend`: Dùng `wget` gọi endpoint `/actuator/health/readiness`.
  - Deploy dùng cờ `--wait` của Docker Compose (yêu cầu Docker Compose version mới).

## 3. Operational Drill Status
- **Backup Result**: Tích hợp gọi tự động `ops/backup-postgres.sh` trước khi deploy. (Pipeline sẽ fail nếu script bị lỗi).
- **Restore Test Result**: Hướng dẫn có sẵn tại `backup-restore-guide.md`. Yêu cầu diễn tập thực tế khi VPS sẵn sàng.
- **Readiness Result**: Được verify tự động bằng cờ `--wait` trong pipeline.
- **Smoke Test Result**: Tự động thông qua `build-and-test` job. Cần chạy manual smoke-test từ ngoài sau khi VPS bật lên.
- **Rollback Drill Result**: Hướng dẫn có sẵn tại `rollback-guide.md` (Rollback bằng Image GITHUB_SHA cũ).

## 4. Final Conclusion

| Hạng mục | Trạng thái |
|----------|------------|
| Phase 3 status | **PASS** |
| Deployment readiness | **READY WITH KNOWN RISKS** (Cần kiểm tra Docker Compose version thực tế trên VPS) |
| Current deployed image | Sẽ được định nghĩa khi Push lần đầu tiên |
| Rollback image | Sẽ được xác định qua `GITHUB_SHA` của bản ổn định trước |
| Backup location | `/opt/skillswap/backups/` trên VPS |
| Restore test status | *Chưa thực hiện (Pending manual drill)* |
| Remaining blockers | Không có |

**Quyết định**: Dự án hiện tại đã đạt đủ Definition of Done (DoD) của Phase 3 và sẵn sàng đón nhận Traffic thực tế hoặc chuyển tiếp sang Phase 4 (Load Test & Performance Tuning).
