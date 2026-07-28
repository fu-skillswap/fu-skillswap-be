# Cẩm nang Vận hành (Operations Runbook)

Tài liệu này cung cấp các lệnh (commands) và quy trình chuẩn để giám sát, chẩn đoán, và khắc phục sự cố hệ thống trên VPS.

## 1. Giám sát hệ thống cơ bản (Observability Baseline)

### 1.1 Xem trạng thái các container
```bash
cd ~/skillswap-deployment
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```
*Bạn sẽ thấy `spring-backend`, `skillswap-postgres`, và `skillswap-rabbitmq`. Trạng thái cần phải là `Up (healthy)`.*

### 1.2 Xem log của ứng dụng
Để xem log realtime của Backend (đặc biệt khi có lỗi 500 hoặc Crash):
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail=200 spring-backend
```

Để xem log của Database (ví dụ kiểm tra lỗi kết nối hoặc slow queries):
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail=100 postgres-db
```

### 1.3 Kiểm tra tình trạng Server (RAM, CPU, Disk)
Nếu VPS có dấu hiệu quá tải (chạy chậm, deploy bị treo):
```bash
# Xem RAM trống (Cẩn thận nếu Available < 100MB)
free -m

# Xem tiến trình đang chiếm CPU/RAM
htop
# Hoặc `top` nếu chưa cài htop

# Xem dung lượng ổ cứng (Nếu Use% = 100%, Database sẽ ngừng hoạt động)
df -h
```

## 2. Kiểm tra Readiness & Smoke Test (Post-Deploy)

Sau mỗi lần deploy, hoặc khi nghi ngờ ứng dụng bị sập, hãy gọi API Actuator Health:
```bash
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```
*Kết quả trả về phải là HTTP 200 OK cùng JSON `{"status": "UP"}`.*

Kiểm tra RabbitMQ Management (Health):
```bash
docker exec skillswap-rabbitmq rabbitmq-diagnostics status
```

Kiểm tra PostgreSQL (Health):
```bash
docker exec skillswap-postgres pg_isready -U skillswap
```

## 3. Release evidence and rollback decision

Every successful production deployment writes `/opt/skillswap/releases/<git-sha>.env`. Preserve this file with the deploy logs; it identifies the candidate image, previous image, backup manifest/checksum, smoke result, and Flyway history.

Use application-only rollback only when the migration is `EXPAND` and the previous image remains schema-compatible. If data/schema corruption or a `CONTRACT` migration is involved:

1. Drain traffic at the reverse proxy.
2. Stop `skillswap-backend` and preserve logs.
3. Set `PRODUCTION_TRAFFIC_DRAINED=true` and `SKILLSWAP_RESTORE_CONFIRM=RESTORE_PRODUCTION`.
4. Run `ops/restore-postgres.sh <backup.dump.gz>`.
5. Start the previous immutable image, wait for readiness, then run `ops/smoke-test.sh`.

Never run a restore against a running backend or use `docker compose down -v` in production.

## 4. Khắc phục sự cố thường gặp (Troubleshooting)

### Sự cố: Container Backend bị thoát liên tục (Restarting)
- **Nguyên nhân 1**: Sai thông tin cấu hình trong `.env` (ví dụ sai mật khẩu DB).
  - *Cách xử lý*: Xem log backend bằng `docker logs skillswap-backend --tail=50`. Sửa `.env` và chạy lại `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`.
- **Nguyên nhân 2**: Lỗi OOM (Tràn RAM).
  - *Cách xử lý*: Dùng `free -m` kiểm tra. Nếu hết RAM, hãy thử dừng một số dịch vụ không cần thiết hoặc tính đến việc nâng cấp VPS. Cấu hình JVM `-Xmx512m` đã được thiết lập để giữ Backend không chiếm quá nhiều RAM.

### Sự cố: Lỗi "502 Bad Gateway" khi truy cập từ ngoài vào
- **Nguyên nhân**: Nginx không kết nối được tới Backend.
- *Cách xử lý*: 
  1. Kiểm tra Backend có đang chạy không: `docker ps`.
  2. Kiểm tra Backend đã bind đúng cổng 8080 chưa: `curl http://127.0.0.1:8080/actuator/health/readiness`.
  3. Kiểm tra log Nginx để xem nguyên nhân từ chối proxy: `sudo tail -f /var/log/nginx/error.log`.

### Sự cố: Triển khai bản mới gây lỗi (Migration fail, logic lỗi)
- *Cách xử lý*: Kích hoạt quy trình Rollback theo tài liệu [Rollback Guide](rollback.md). Xác định GITHUB_SHA cũ, sửa `APP_IMAGE` và chạy `docker compose up -d --wait`.
