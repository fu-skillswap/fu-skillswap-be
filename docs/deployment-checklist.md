# Checklist Vận Hành Khóa Release (Manual Steps)

Quy trình này chia làm 3 giai đoạn để đảm bảo an toàn tuyệt đối khi đưa hệ thống từ trạng thái hiện tại (đã khóa Worktree và Pass Integration Tests) lên Production VPS.

---

## 🚀 1. Giai đoạn 1: Trước khi `git push`

Bạn đang ở nhánh `dev` hoặc nhánh làm việc local. Trước khi đẩy code lên remote, bạn cần:

- [ ] **Commit Code Theo Nhóm (Logical Commits):** Không dùng `git commit -a -m "all"`. Hãy chia thành 5 nhóm commit như tôi đã gợi ý trong `walkthrough.md` (Data/Migration, Identity/Payment, Forum Phase 5, Ops/Docs, Test Stabilization).
- [ ] **Kiểm tra File Rác:** Gõ `git status` lần cuối, đảm bảo không commit nhầm các file `.env`, `openapi.json` (nếu không chủ ý), hoặc các thư mục `target/`.
- [ ] **Khởi chạy Local Load Test (k6):** (Tùy chọn nhưng khuyến nghị) Mở terminal, chạy `docker compose up -d` và `mvn spring-boot:run` ở local. Sau đó chạy lệnh `k6 run scripts/k6-load-test.js` để kiểm chứng ngưỡng CPU/RAM local với 200 VUs không bị OOM.
- [ ] **Push Lên Remote:** `git push origin <nhánh-của-bạn>`. Đảm bảo GitHub Actions chạy qua bước "Run Tests & Verify" và "Trivy Security Scan" thành công.

---

## 🔀 2. Giai đoạn 2: Khi tạo Pull Request và Merge vào `main`

Khi tạo PR từ nhánh làm việc vào `main`, quá trình Deploy VPS tự động có thể sẽ được kích hoạt (nếu CI/CD cấu hình trigger khi merge `main`).

- [ ] **Tạo môi trường Staging/Prod-like (Bắt Buộc):** 
  - Đảm bảo bạn đã có file `.env.staging` (Google OAuth staging, PayOS sandbox, R2 test bucket, test SMTP).
  - Provision một VPS giả lập hoặc clone cấu hình VPS production hiện tại.
- [ ] **Drill Test - Diễn tập Backup / Restore:** 
  - Kéo code về VPS Staging, chạy thử script `ops/backup-postgres.sh`.
  - Xác nhận file `.dump.gz` sinh ra, dung lượng khác 0.
  - Drop thử Database trên Staging và Restore từ file dump vừa tạo xem có thành công không.
- [ ] **Drill Test - Diễn tập Rollback:** 
  - Thử đổi `APP_IMAGE` về một SHA cũ, khởi động lại container và kiểm tra xem hệ thống có chạy ổn không.
- [ ] **Review OpenAPI Spec:** Chuyển giao file `openapi.json` vừa generate cho phía FE để họ đồng bộ contract mới nhất (đặc biệt là logic Presigned URL thay cho Multipart).
- [ ] **Merge PR vào `main`:** Xác nhận mọi thứ ổn thỏa và nhấn Merge. Github Action `deploy-vps` sẽ tự động chạy.

---

## 🌍 3. Giai đoạn 3: Trên VPS Production (Sau khi Deploy hoàn tất)

Sau khi Github Action chạy xong bước `Deploy to VPS via SSH`, mã nguồn và image mới đã nằm trên VPS thực. 

- [ ] **Verify Readiness (Health Check):** 
  - Truy cập VPS, gõ `docker compose -f docker-compose.prod.yml ps`. Đảm bảo `spring-backend` đang có trạng thái `Up (healthy)`.
  - Kiểm tra log để tìm lỗi khởi động (nếu có): `docker logs skillswap-backend -n 100`.
- [ ] **Verify Flyway Migration:**
  - Check log khởi động để đảm bảo Flyway đã chạy xong `V52` và `V53` mà không gặp lỗi lock hay crash.
- [ ] **Critical Business Smoke Test (Manual Testing trên Prod):**
  - **Auth:** Đăng nhập thử bằng Google.
  - **Forum:** Tạo 1 Post, thử Like (Reaction) vào 1 comment, và Reply 1 comment để kiểm tra luồng Phase 5.
  - **Notification & Mail:** Kích hoạt một chức năng có gửi email (vd: Booking hoặc Verification), kiểm tra xem email có rơi vào hòm thư thật không (đảm bảo Email Outbox không bị kẹt).
  - **Payment & Storage:** Thử luồng presigned upload (R2) và verify webhook PayOS (nếu có thể test 1 giao dịch nhỏ 10k VND).
- [ ] **Chốt Rollback Tag:** Lưu lại Image Tag (GITHUB_SHA) hiện tại vào sổ tay/Wiki. Nếu ngày mai có lỗi phát sinh nghiêm trọng, dùng chính thẻ SHA này để Rollback khẩn cấp.
