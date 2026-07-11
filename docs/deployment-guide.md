# Hướng dẫn Deploy SkillSwap Backend lên VPS

Tài liệu này hướng dẫn các bước thiết lập một lần duy nhất để pipeline CI/CD (GitHub Actions) có thể tự động build và deploy dự án lên VPS của bạn.

## 1. Cấu hình GitHub Actions Secrets

Để pipeline có thể kết nối vào VPS của bạn thông qua SSH, bạn cần cấu hình các secrets sau trong Repository của GitHub:
Vào **Settings** > **Secrets and variables** > **Actions** > **New repository secret**:

*   `VPS_HOST`: Địa chỉ IP public của VPS (VD: `103.11.22.33`).
*   `VPS_USER`: Tên user dùng để SSH vào VPS (VD: `root` hoặc `ubuntu`).
*   `VPS_SSH_KEY`: Private Key để SSH vào VPS (Nội dung file `~/.ssh/id_rsa`).
    *   *Lưu ý: Không dùng password, bạn cần tạo cặp key RSA/Ed25519 bằng lệnh `ssh-keygen` trên máy tính/VPS, sau đó thêm public key vào file `~/.ssh/authorized_keys` trên VPS.*

## 2. Thiết lập trên VPS

Thực hiện SSH vào VPS của bạn và chạy các bước sau:

### 2.1 Cài đặt Docker & Docker Compose
Nếu VPS chưa có Docker, hãy cài đặt:
```bash
# Cài đặt Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Khởi động Docker cùng hệ thống
sudo systemctl enable docker
sudo systemctl start docker

# Cài đặt Docker Compose Plugin (nếu chưa có)
sudo apt-get install docker-compose-plugin -y
```

> **Lưu ý quan trọng**: Pipeline CI/CD sử dụng lệnh `docker compose up --wait`. Lệnh này yêu cầu **Docker Compose V2 mới**. Vui lòng đảm bảo VPS của bạn cài đặt phiên bản Docker Compose cập nhật nhất. Nếu version quá cũ, bước deploy có thể bị treo hoặc fail.

### 2.2 Đăng nhập GHCR (GitHub Container Registry)
Vì file `docker-compose.prod.yml` cấu hình pull image từ GHCR, VPS của bạn cần có quyền tải image.
Nếu Package Docker Image của bạn trên GitHub để chế độ **Private**, bạn phải tạo một **Personal Access Token (Classic)** trên GitHub với quyền `read:packages`, sau đó đăng nhập trên VPS:

```bash
docker login ghcr.io -u <YOUR_GITHUB_USERNAME> -p <YOUR_PERSONAL_ACCESS_TOKEN>
```
*Nếu bạn chỉnh Package trên GitHub thành **Public**, bước đăng nhập này có thể bỏ qua.*

### 2.3 Tạo thư mục dự án & Cấu hình `.env`
Pipeline sẽ triển khai code vào thư mục `~/skillswap-deployment`. Bạn cần tạo thư mục này và đặt file `.env` chứa các bí mật (secrets) của ứng dụng vào đó:

```bash
mkdir -p ~/skillswap-deployment
cd ~/skillswap-deployment
nano .env
```

Nội dung file `.env` tham khảo (Hãy điền các giá trị thực tế cho Production):
```env
SPRING_PROFILES_ACTIVE=prod

POSTGRES_USER=skillswap
POSTGRES_PASSWORD=your_strong_db_password
POSTGRES_DB=skillswapdb

RABBITMQ_DEFAULT_USER=skillswap
RABBITMQ_DEFAULT_PASS=your_strong_rmq_password

JWT_SECRET_KEY=your_very_long_and_secure_jwt_secret_key_here
JWT_ISSUER=skillswap
JWT_AUDIENCE=skillswap_web
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

# Google Auth
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
GOOGLE_TOKEN_ENCRYPTION_KEY=16_or_32_byte_secret_key!

# Cursor Encryption
CURSOR_AES_KEY=32_byte_aes_key_here!
CURSOR_HMAC_KEY=32_byte_hmac_key_here!

# PayOS
PAYOS_CLIENT_ID=your_payos_client_id
PAYOS_API_KEY=your_payos_api_key
PAYOS_CHECKSUM_KEY=your_payos_checksum_key
PAYOS_WEBHOOK_URL=https://yourdomain.com/api/payments/webhook/payos
```

## 3. Thiết lập Nginx & Domain (Reverse Proxy)

Vì bạn tự quản lý Nginx ở mức OS trên VPS, hãy đảm bảo bạn cấu hình file `/etc/nginx/sites-available/skillswap` như sau:

```nginx
server {
    server_name api.yourdomain.com; # Thay bằng domain thực tế

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_addrs;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSockets support
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Chạy certbot --nginx để tự động gắn SSL chứng chỉ Let's Encrypt
}
```

Sau đó kích hoạt và chạy certbot:
```bash
sudo ln -s /etc/nginx/sites-available/skillswap /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d api.yourdomain.com
```

## 4. Theo dõi & Cập nhật
Từ giờ trở đi, mỗi khi bạn push code mới lên branch `main`:
1. GitHub Actions sẽ tự động test, build Image, và đẩy lên GHCR.
2. Nó sẽ SSH vào thư mục `~/skillswap-deployment` trên VPS.
3. Chạy `docker compose pull` và restart lại ứng dụng mà không cần bạn can thiệp.

Để xem logs trực tiếp trên VPS:
```bash
cd ~/skillswap-deployment
docker compose -f docker-compose.prod.yml logs -f spring-backend
```
