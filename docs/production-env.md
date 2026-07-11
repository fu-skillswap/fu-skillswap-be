# Môi trường Production (.env)

Môi trường Production yêu cầu cấu hình khắt khe để đảm bảo bảo mật.
File `.env` trên VPS **KHÔNG ĐƯỢC** lưu trữ trong Git Repository và **KHÔNG ĐƯỢC** in ra trong log của CI/CD.

Dưới đây là template cho file `.env` chuẩn trên VPS Production:

```env
# ==== HỆ THỐNG ====
# Bắt buộc để sử dụng cấu hình application-prod.yaml (nếu có)
SPRING_PROFILES_ACTIVE=prod
# Biến APP_IMAGE sẽ được CI/CD ghi đè khi chạy lệnh deploy, nhưng có thể điền default:
APP_IMAGE=ghcr.io/owner/skillswap-backend:latest

# ==== POSTGRESQL ====
# Mật khẩu mạnh, không dùng chung với staging
POSTGRES_USER=skillswap
POSTGRES_PASSWORD=your_strong_db_password
POSTGRES_DB=skillswapdb

# ==== RABBITMQ ====
# Mật khẩu mạnh cho RabbitMQ broker
RABBITMQ_DEFAULT_USER=skillswap
RABBITMQ_DEFAULT_PASS=your_strong_rmq_password

# ==== JWT AUTHENTICATION ====
# JWT_SECRET_KEY phải >= 256 bit (32 ký tự)
JWT_SECRET_KEY=your_very_long_and_secure_jwt_secret_key_here
JWT_ISSUER=skillswap
JWT_AUDIENCE=skillswap_web
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

# ==== BẢO MẬT ENCRYPTION ====
# Khóa dùng để mã hóa Cursor Pagination & Google Tokens (Yêu cầu độ dài 32 bytes)
CURSOR_AES_KEY=32_byte_aes_key_here!
CURSOR_HMAC_KEY=32_byte_hmac_key_here!
GOOGLE_TOKEN_ENCRYPTION_KEY=32_byte_encryption_key_here!

# ==== GOOGLE AUTH ====
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# ==== PAYOS PAYMENT ====
# Cấp tại portal của PayOS
PAYOS_CLIENT_ID=your_payos_client_id
PAYOS_API_KEY=your_payos_api_key
PAYOS_CHECKSUM_KEY=your_payos_checksum_key

# ==== S3 STORAGE (MINIO / AWS) ====
STORAGE_ENABLED=true
STORAGE_ACCESS_KEY=your_storage_access_key
STORAGE_SECRET_KEY=your_storage_secret_key
STORAGE_BUCKET=skillswap-prod
STORAGE_ENDPOINT=https://s3.your-region.amazonaws.com

# ==== MAIL SMTP ====
APPLICATION_MAIL_ENABLED=true
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_password

# ==== SWAGGER ====
# Bắt buộc tắt trên production để tránh lộ APIs
APPLICATION_SWAGGER_ENABLED=false
```

**Quy định cập nhật:**
- Khi thêm biến môi trường mới vào Code, bắt buộc phải cập nhật `.env.example` và thông báo cho người quản trị VPS để thêm vào `.env`.
- Chỉ người có quyền Root/Deploy trên VPS mới được đọc/sửa file `.env`.
