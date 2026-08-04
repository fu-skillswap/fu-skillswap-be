# Authentication Service (`01-authentication.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Xác thực (Authentication Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Authentication Service** quản lý toàn bộ vòng đời xác thực danh tính, đăng nhập Google OAuth 2.0 PKCE, quản lý phiên đăng nhập (Session Management), xoay vòng Refresh Token (RTR), thu hồi phiên (Logout), truy vấn thông tin người dùng hiện tại (`/api/auth/me`) và hợp nhất trạng thái onboarding (`/api/me/onboarding-status`) cho hệ thống SkillSwap.

### Trách nhiệm chính của Service
- **Khởi tạo Ngữ cảnh Google OAuth 2.0 PKCE**: Phát hành `state` dùng một lần được ràng buộc mã hóa với `redirectUri` và PKCE `code_challenge`.
- **Đăng nhập & Cấp phát Tài khoản Người dùng**: Trao đổi Google Authorization Code bằng PKCE Code Verifier, xác minh thông tin tài khoản Google, tự động tạo mới tài khoản (mặc định vai trò `MENTEE`), liên kết danh tính OAuth và cấp phát JWT Access Token.
- **Xoay vòng Refresh Token (RTR) & Phát lại trong Grace Period**: Cấp phát Refresh Token trong cookie `HttpOnly` do trình duyệt quản lý. Tự động xoay vòng Refresh Token mỗi khi làm mới phiên, đồng thời hỗ trợ cơ chế phát lại token cho các request song song trong cửa sổ thời gian an toàn (`ROTATING_GRACE`).
- **Thu hồi Phiên Đăng nhập (Logout)**: Thu hồi gia đình phiên đăng nhập trong cơ sở dữ liệu và yêu cầu trình duyệt xóa cookie refresh token.
- **Truy vấn Thông tin Người dùng (`/api/auth/me`)**: Giải mã danh tính người dùng hiện tại, danh sách vai trò (roles), các cờ hoàn thành hồ sơ học thuật và trạng thái liên kết Google Calendar.
- **Hợp nhất Trạng thái Onboarding (`/api/me/onboarding-status`)**: Hợp nhất tiến độ hồ sơ học thuật, hồ sơ mentor, nhu cầu kết nối và trạng thái duyệt mentor thành một mã hành động duy nhất (`nextRecommendedAction`) để Frontend điều hướng trang chính xác.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Loại bỏ Rủi ro Lưu trữ Mật khẩu**: Sử dụng chuẩn Google OAuth 2.0 PKCE (RFC 7636) giúp sinh viên và mentor FPTU đăng nhập an toàn bằng tài khoản Google Edu mà hệ thống không cần lưu trữ mật khẩu.
2. **Chống Đánh cắp Token & Tấn công CSRF**: Refresh Token được lưu trữ độc quyền trong cookie `HttpOnly` do trình duyệt quản lý (`Path=/`, `SameSite=Lax`, `Secure`), chỉ để lộ Access Token ngắn hạn trong bộ nhớ ứng dụng Frontend.
3. **Xử lý An toàn Các Request Refresh Song song**: Triển khai cơ chế Refresh Token Rotation có cửa sổ thời gian chờ (`ROTATING_GRACE`), giải quyết triệt để rủi ro race condition khi nhiều request API song song đồng thời kích hoạt refresh token.
4. **Thu hồi Gia đình Phiên khi Phát hiện Lạm dụng Token**: Tự động thu hồi toàn bộ cây phiên đăng nhập (Session Family) nếu phát hiện một refresh token cũ đã bị xoay vòng được tái sử dụng sau khi hết thời gian grace period.
5. **Bảo vệ Ma trận Trạng thái Tài khoản**: Chặn ngay lập tức khả năng đăng nhập và truy cập API đối với các tài khoản ở trạng thái `BANNED` (Bị khóa), `INACTIVE` (Chưa kích hoạt), hoặc `DELETED` (Đã xóa mềm).
6. **Tự động Cấp quyền Quản trị hệ thống**: Tự động gán vai trò `SYSTEM_ADMIN` khi đăng nhập nếu email Google của người dùng nằm trong danh sách whitelist quản trị viên hệ thống.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                    LUỒNG ĐĂNG NHẬP GOOGLE OAUTH 2.0                                   |
+-------------------------------------------------------------------------------------------------------+

  Frontend (Browser)                  Backend (SkillSwap API)                 Google OAuth 2.0 Server
          |                                     |                                         |
   1. User bấm "Đăng nhập bằng Google"          |                                         |
          |                                     |                                         |
   2. Tạo PKCE verifier & challenge             |                                         |
          |                                     |                                         |
   3. GET /api/auth/google/authorization-context?redirectUri=...&codeChallenge=...      |
          |------------------------------------>|                                         |
          |                                     | Tạo state dùng 1 lần                    |
          |<------------------------------------| (TTL: 10 phút trong Caffeine Cache)     |
          | 200 OK (state, expiresAt)           |                                         |
          |                                                                               |
   4. Chuyển hướng trình duyệt sang Google Consent URL ----------------------------------->|
          | (client_id, redirect_uri, state, code_challenge, code_challenge_method=S256) |
          |                                                                               |
          |<------------------------------------------------------------------------------|
   5. User đồng ý cấp quyền; Google redirect về callback với (code, state)                |
          |                                     |                                         |
   6. POST /api/auth/google                      |                                         |
      { authorizationCode, redirectUri,         |                                         |
        codeVerifier, state }                   |                                         |
          |------------------------------------>|                                         |
          |                                     | 7. Xác minh state & mã PKCE             |
          |                                     | 8. Đổi code lấy Google User Info        |
          |                                     | 9. Tạo/Cập nhật User + OauthAccount     |
          |                                     | 10. Sinh JWT AccessToken                |
          |                                     | 11. Tạo UserSession & Set Cookie        |
          |<------------------------------------|                                         |
          | 200 OK + Set-Cookie (skillswap_refresh_token)                                 |
          | Body: { accessToken, tokenType: "Bearer" }                                    |
          |                                     |                                         |
   12. GET /api/auth/me (Bearer token)          |                                         |
          |------------------------------------>|                                         |
          |<------------------------------------| 200 OK (UserMeResponse)                 |
          |                                     |                                         |
   13. GET /api/me/onboarding-status            |                                         |
          |------------------------------------>|                                         |
          |<------------------------------------| 200 OK (nextRecommendedAction)          |
          |                                     |                                         |
   14. Điều hướng User vào Onboarding hoặc Dashboard dựa vào nextRecommendedAction        |
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Mô hình Mô hình Token Đôi (Dual-Token Model)
- **AccessToken**: Chuỗi JWT ngắn hạn chứa `userId` (Public ID UUID), `email`, và danh sách `roles`. Được truyền qua HTTP Header: `Authorization: Bearer <accessToken>`.
- **RefreshToken**: Chuỗi token ngẫu nhiên bảo mật (`SecureRandom`). Chuỗi gốc **chỉ** được gửi về client qua `Set-Cookie`. Backend chỉ lưu bản băm SHA-256 (`refreshTokenHash`) trong bảng `user_sessions`.

### 4.2 Xoay vòng Refresh Token & Cửa sổ Thời gian Grace Period
- **Xoay vòng (Rotation)**: Mỗi khi gọi `POST /api/auth/refresh`, backend vô hiệu hóa token hash cũ và cấp phát một cặp refresh token cookie + access token mới.
- **Trạng thái `ROTATING_GRACE`**: Khi token bị xoay vòng, trạng thái phiên chuyển thành `ROTATING_GRACE` trong một khoảng thời gian ngắn (cấu hình qua `application.security.jwt.refresh-token.rotation-grace-period-millis`, mặc định 30 giây).
- **Phát lại Trong Grace Period**: Nếu các request FE song song gửi lại refresh token cũ trong cửa sổ grace period này, backend giải mã và trả lại đúng phản hồi token đã cấp trước đó (`graceReplayCiphertext`) mà không báo lỗi hay hủy phiên.
- **Thu hồi Gia đình Phiên (Family Revocation)**: Nếu refresh token cũ được gửi lên **sau khi** hết thời gian grace period, backend xác định có nguy cơ bị lộ token và lập tức thu hồi toàn bộ các phiên liên quan (`REVOKED`).

### 4.3 Vòng đời Trạng thái Tài khoản (`UserStatus`)
- `ACTIVE`: Tài khoản hoạt động bình thường.
- `INACTIVE`: Tài khoản chưa kích hoạt (Backend chặn với lỗi `403 USER_INACTIVE`).
- `BANNED`: Tài khoản bị Quản trị viên khóa (Backend chặn với lỗi `403 USER_BANNED`).
- `DELETED`: Tài khoản đã xóa mềm (Backend trả về `404 USER_NOT_FOUND`). Tài khoản đã xóa mềm không thể tự khôi phục hoặc đăng ký lại qua luồng Google Login.

### 4.4 Quy tắc Gợi ý Onboarding (`nextRecommendedAction`)
Được đánh giá tự động trong `OnboardingStatusController`:
1. Nếu `!studentProfileCompleted` -> **`COMPLETE_STUDENT_PROFILE`**
2. Nếu `roles.contains(MENTOR)` -> **`EXPLORE`**
3. Nếu `mentorVerificationStatus == "PENDING_REVIEW"` -> **`WAIT_FOR_APPROVE`**
4. Nếu `mentorVerificationStatus == "NEEDS_REVISION"` -> **`REVISE_MENTOR_VERIFICATION`**
5. Nếu `mentorVerificationStatus == "APPROVED"` -> **`EXPLORE`**
6. Trường hợp khác:
   - Nếu `!mentorProfileCompleted` -> **`COMPLETE_MENTOR_PROFILE_OR_EXPLORE`**
   - Nếu `mentorProfileCompleted` -> **`SUBMIT_MENTOR_VERIFICATION_OR_EXPLORE`**

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Rate Limit | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/auth/google/authorization-context` | Public | 20 req / 10 min | Phát hành `state` dùng 1 lần được buộc mã hóa với `redirectUri` & PKCE `codeChallenge` | Trang Login trước khi chuyển sang Google |
| `POST` | `/api/auth/google` | Public | 20 req / 10 min | Đổi Google authorization code + PKCE lấy Access Token & Refresh Cookie | Trang OAuth Callback Handler |
| `POST` | `/api/auth/refresh` | Public (Cookie) | 40 req / 10 min | Xoay vòng refresh token và cấp Access Token mới | HTTP Interceptor khi gặp `401` / Timer gia hạn phiên |
| `POST` | `/api/auth/logout` | Public (Cookie) | Không giới hạn | Thu hồi gia đình phiên hiện tại và xóa cookie refresh token | Hành động nút Đăng xuất của User |
| `GET` | `/api/auth/me` | Authenticated | Không giới hạn | Lấy thông tin user, vai trò và cờ hồ sơ của user đăng nhập | Khi ứng dụng khởi tạo / Sau khi Login & Refresh |
| `GET` | `/api/me/onboarding-status` | Authenticated | Không giới hạn | Lấy trạng thái onboarding tổng hợp và hành động gợi ý tiếp theo | Khi ứng dụng khởi tạo / Route Guard Onboarding |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `GET /api/auth/google/authorization-context`

#### Mục đích
Phát hành `state` dùng một lần, lưu vào cache bộ nhớ (Caffeine Cache, TTL: 10 phút) để bảo vệ luồng OAuth khỏi các cuộc tấn công CSRF và Authorization Code Injection.

#### Khi nào gọi
- Ngay khi người dùng bấm nút **"Login with Google"** trên giao diện Frontend.

#### Không gọi khi
- Người dùng đã có phiên đăng nhập hợp lệ.
- Nghiêm cấm tự tạo chuỗi `state` ngẫu nhiên ở Frontend; bắt buộc phải lấy từ API này.

#### Query Parameters
- `redirectUri` (`String`, Bắt buộc): URI callback của Frontend (phải khớp chính xác với URI gửi sang Google và URI gửi tại `POST /api/auth/google`).
- `codeChallenge` (`String`, Bắt buộc): Chuỗi Base64URL(SHA-256(codeVerifier)) do Frontend tạo theo chuẩn PKCE (RFC 7636).

#### Response Body
```json
{
  "timestamp": "2026-08-04T09:00:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "state": "aBC123xyz...",
    "expiresAt": "2026-08-04T09:10:00Z"
  }
}
```

---

### 6.2 `POST /api/auth/google`

#### Mục đích
Đổi Google Authorization Code và PKCE Code Verifier lấy phiên đăng nhập. Backend xác minh `state` và PKCE, gọi Google Token API, đồng bộ user/oauth account, cấp JWT Access Token và thiết lập Refresh Cookie.

#### Điều kiện tiên quyết
- Đã gọi `GET /api/auth/google/authorization-context` thành công.
- User đã hoàn tất đăng nhập ở Google và trình duyệt chuyển hướng quay lại `redirectUri` mang theo `code` và `state`.

#### Request Body (`GoogleLoginRequest`)
```json
{
  "authorizationCode": "4/0AQSTgQF...",
  "redirectUri": "https://skillswap.asia/auth/google/callback",
  "codeVerifier": "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
  "state": "aBC123xyz..."
}
```

#### Response Body (`TokenResponse`)
```json
{
  "timestamp": "2026-08-04T09:00:05Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer"
  }
}
```

#### Response Headers
`Set-Cookie`: `skillswap_refresh_token=<token_string>; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=604800`

---

### 6.3 `POST /api/auth/refresh`

#### Mục đích
Xoay vòng (rotate) refresh token và cấp access token mới. Request đọc refresh token từ Cookie `skillswap_refresh_token` (Trình duyệt tự động gửi). **Request Body rỗng**.

#### Response Body (`TokenResponse`)
```json
{
  "timestamp": "2026-08-04T09:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer"
  }
}
```

---

### 6.4 `POST /api/auth/logout`

#### Mục đích
Thu hồi phiên đăng nhập hiện tại trong DB (`user_sessions`), xóa gia đình phiên liên quan và gửi `Set-Cookie` với `Max-Age=0` để trình duyệt xóa cookie refresh token.

#### Response Body
```json
{
  "timestamp": "2026-08-04T10:00:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": "Đăng xuất thành công"
}
```

---

### 6.5 `GET /api/auth/me`

#### Mục đích
Lấy thông tin profile cơ bản, trạng thái tài khoản, vai trò và cờ hồ sơ của user đang đăng nhập dựa vào `Authorization: Bearer <accessToken>`.

#### Response Body (`UserMeResponse`)
```json
{
  "timestamp": "2026-08-04T09:00:10Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "publicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "student@fpt.edu.vn",
    "fullName": "Nguyễn Văn A",
    "avatarUrl": "https://lh3.googleusercontent.com/a/default-avatar",
    "status": "ACTIVE",
    "roles": ["MENTEE"],
    "profileCompleted": false,
    "hasStudentProfile": false,
    "googleCalendarConnected": false,
    "googleCalendarSyncEnabled": false,
    "googleCalendarEmail": null,
    "googleCalendarNeedsReconnect": false,
    "googleCalendarLastSyncStatus": null,
    "googleCalendarLastSyncAt": null
  }
}
```

---

### 6.6 `GET /api/me/onboarding-status`

#### Mục đích
Truy vấn trạng thái onboarding tổng hợp của user hiện tại và nhận gợi ý hành động tiếp theo (`nextRecommendedAction`).

#### Response Body (`OnboardingStatusResponse`)
```json
{
  "timestamp": "2026-08-04T09:00:12Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "studentProfileCompleted": false,
    "mentorProfileCompleted": false,
    "mentoringNeedsCompleted": false,
    "mentorVerificationStatus": "NOT_STARTED",
    "roles": ["MENTEE"],
    "nextRecommendedAction": "COMPLETE_STUDENT_PROFILE"
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Đăng nhập Google & Điều hướng Trang Onboarding

```
Frontend                           Backend                                    Database / External
   |                                  |                                                |
   |-- 1. GET /authorization-context->|                                                |
   |   (?redirectUri&codeChallenge)   |-- Phát hành state (Caffeine Cache, TTL 10m) -->|
   |<-- 200 OK (state) ---------------|                                                |
   |                                  |                                                |
   |-- 2. Redirect sang Google Consent -> (User đăng nhập trên Google)                 |
   |<-- 3. Google Callback (code,state)|                                               |
   |                                  |                                                |
   |-- 4. POST /api/auth/google ----->|                                                |
   |   (code, redirectUri, verifier,  |-- Xác minh state & mã PKCE ------------------->|
   |    state)                        |-- Đổi code với Google API -------------------->|
   |                                  |-- Tạo/Cập nhật User + OauthAccount ----------->|
   |                                  |-- Sinh JWT + UserSession --------------------->|
   |<-- 200 OK + Set-Cookie ----------|                                                |
   |    Body: { accessToken }         |                                                |
   |                                  |                                                |
   |-- 5. GET /api/auth/me ---------->|                                                |
   |<-- 200 OK (UserMeResponse) ------|                                                |
   |                                  |                                                |
   |-- 6. GET /me/onboarding-status ->|                                                |
   |<-- 200 OK (nextAction) ----------|                                                |
   |                                  |                                                |
   |-- 7. Điều hướng dựa trên nextAction -> (Ví dụ: COMPLETE_STUDENT_PROFILE -> /onboarding)
```

---

## 8. State Machine (Ma trận Trạng thái Session & Onboarding)

### 8.1 Ma trận Trạng thái Phiên Đăng nhập (`UserSessionState`)

```
             +-----------------------+
             |        ACTIVE         |
             +-----------------------+
                         |
           POST /api/auth/refresh được gọi
                         |
                         v
             +-----------------------+
             |    ROTATING_GRACE     |
             +-----------------------+
              /                     \
   Trong Cửa sổ Grace Window       Hết Hạn Grace Window HOẶC Tái Sử dụng Trái phép
            /                         \
           v                           v
  Phát lại Cặp Token           +-----------------------+
  Đã Cấp Trước Đó              |   EXPIRED / REVOKED   |
                               +-----------------------+
```

### 8.2 Ma trận Kiểm soát Trạng thái Tài khoản (`UserStatus`)

| UserStatus | Cho phép Đăng nhập? | Cho phép Gọi API Protected? | Phản hồi từ Backend / Code Lỗi |
| --- | --- | --- | --- |
| `ACTIVE` | **CÓ** | **CÓ** | `200 OK` |
| `INACTIVE` | **KHÔNG** | **KHÔNG** | `403 Forbidden` (`USER_INACTIVE`) |
| `BANNED` | **KHÔNG** | **KHÔNG** | `403 Forbidden` (`USER_BANNED`) |
| `DELETED` | **KHÔNG** | **KHÔNG** | `404 Not Found` (`USER_NOT_FOUND`) |

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `BAD_REQUEST` | Parameter rỗng, thiếu `redirectUri`, `state` hoặc `codeVerifier`. | Hiển thị thông báo lỗi form, không retry tự động. |
| `400 BAD_REQUEST` | `OAUTH_VERIFICATION_FAILED` | `state` hết hạn/không tồn tại, hoặc PKCE `codeVerifier` không khớp `codeChallenge`. | Báo lỗi phiên đăng nhập hết hạn, khởi tạo lại luồng Google Login. |
| `401 UNAUTHENTICATED` | `UNAUTHENTICATED` | Bearer token rỗng, hết hạn, hoặc signature không hợp lệ khi gọi API bảo vệ. | Gọi `POST /api/auth/refresh`. Nếu thất bại, xóa token local và về màn Login. |
| `401 UNAUTHENTICATED` | `SESSION_EXPIRED` | Cookie `skillswap_refresh_token` hết hạn, bị thu hồi, hoặc đã xóa trong DB. | Xóa auth state local và chuyển hướng người dùng về trang Login. |
| `403 FORBIDDEN` | `USER_BANNED` | Tài khoản đã bị Admin khóa (`status = BANNED`). | Chuyển hướng đến màn hình **"Account Banned"**, hiển thị lý do khóa. |
| `403 FORBIDDEN` | `USER_INACTIVE` | Tài khoản chưa được kích hoạt (`status = INACTIVE`). | Chuyển hướng đến màn hình **"Account Inactive"**. |
| `404 NOT_FOUND` | `USER_NOT_FOUND` | User ID không tồn tại hoặc tài khoản đã bị xóa mềm (`status = DELETED`). | Xóa local session, báo lỗi không tìm thấy người dùng. |
| `429 TOO_MANY_REQUESTS` | `TOO_MANY_REQUESTS` | Vượt quá Rate Limit (20 req/10 min cho login/context, 40 req/10 min cho refresh). | Thấy thông báo backoff, vô hiệu hóa nút submit tạm thời. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Chuẩn PKCE (RFC 7636)**: Đảm bảo Authorization Code không thể bị nghe lén để đổi token nếu không có `codeVerifier` ngẫu nhiên lưu tại bộ nhớ Frontend.
2. **Rate Limiting**:
   - `GET /api/auth/google/authorization-context`: 20 requests / 10 phút / Client IP.
   - `POST /api/auth/google`: 20 requests / 10 phút / Client IP.
   - `POST /api/auth/refresh`: 40 requests / 10 phút / Client IP.
3. **Bảo vệ Cookie HttpOnly**:
   - Tên Cookie: `skillswap_refresh_token`
   - Thuộc tính: `HttpOnly=true`, `SameSite=Lax`, `Path=/`, `Secure=true`.
   - Mã JavaScript (React/Vue) **không thể** truy cập cookie này qua `document.cookie`.
4. **Tự động Cấp quyền System Admin**: Khi người dùng đăng nhập Google, backend kiểm tra email với danh sách cấu hình `application.security.system-admin.emails`. Nếu trùng khớp, backend tự động gắn vai trò `SYSTEM_ADMIN`.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Lưu Access Token duy nhất trong Bộ nhớ Ứng dụng (React Context / Zustand Store).
- Cấu hình `withCredentials: true` (Axios/Fetch) cho tất cả API auth để trình duyệt tự động gửi cookie `skillswap_refresh_token`.
- Thực thi `GET /api/auth/me` và `GET /api/me/onboarding-status` ngay sau khi đăng nhập hoặc khởi chạy ứng dụng.
- Xử lý lỗi `401 UNAUTHENTICATED` bằng cách gọi `POST /api/auth/refresh` duy nhất một lần thông qua cơ chế Queue/Mutex Interceptor để tránh vòng lặp refresh.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** đọc hoặc lưu trữ Refresh Token trong `localStorage`, `sessionStorage`, hoặc `document.cookie`.
- **KHÔNG ĐƯỢC** gửi `refreshToken` trong request body hoặc query parameters.
- **KHÔNG ĐƯỢC** tự giải mã hay chỉnh sửa chuỗi `state` trả về từ `GET /api/auth/google/authorization-context`.
- **KHÔNG ĐƯỢC** bỏ qua `nextRecommendedAction` từ `GET /api/me/onboarding-status` khi điều hướng người dùng mới.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Request Refresh Song song Khi Access Token Hết hạn**:
   - Khi nhiều request API song song đồng thời bị lỗi `401`, interceptor ở Frontend khóa luồng và gửi 1 request `POST /api/auth/refresh`. Cửa sổ `ROTATING_GRACE` ở Backend đảm bảo các request đệm gửi refresh token cũ vẫn nhận lại cặp token hợp lệ mà không bị đứt phiên.
2. **Tài khoản Đã Bị Xóa Mềm (`DELETED`)**:
   - Nếu tài khoản đã bị xóa mềm, khi đăng nhập lại bằng Google OAuth, backend khớp danh tính cũ và lập tức ném lỗi `404 USER_NOT_FOUND` ("Tài khoản đã bị xóa khỏi hệ thống"). Frontend phải hiển thị thông báo lỗi xóa tài khoản và không tự động retry.
3. **Mismatch OAuth State hoặc PKCE Code Verifier**:
   - Nếu `state` hoặc `codeVerifier` không khớp với dữ liệu đã lưu khi tạo context, backend trả về `400 OAUTH_VERIFICATION_FAILED`. Frontend phải reset luồng đăng nhập và lấy context mới.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Academic Service**: Cung cấp cờ trạng thái `studentProfileCompleted` cho `/api/auth/me` và `/api/me/onboarding-status`.
- **Mentor Profile Service**: Cung cấp cờ trạng thái `mentorProfileCompleted` cho `/api/me/onboarding-status`.
- **Mentor Verification Service**: Cung cấp trạng thái duyệt `mentorVerificationStatus` (`NOT_STARTED`, `PENDING_REVIEW`, `APPROVED`, `NEEDS_REVISION`, `REJECTED`, `WITHDRAWN`) cho `/api/me/onboarding-status`.
- **Google Calendar Connection Service**: Cung cấp các cờ tích hợp Google Calendar (`googleCalendarConnected`, `googleCalendarSyncEnabled`, `googleCalendarNeedsReconnect`) cho `/api/auth/me`.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Màn hình Đăng nhập (`/login`)
- **React Components**: `LoginPage.tsx`, `GoogleLoginButton.tsx`, `GoogleLoginErrorModal.tsx`
- **APIs Triggered**:
  1. `GET /api/auth/google/authorization-context` (Khi bấm nút "Login with Google")
- **Expected Behavior**:
  - Khi user bấm nút, gọi API lấy `state`. Lưu `codeVerifier` vào `sessionStorage` tạm thời.
  - Chuyển hướng trình duyệt sang Google Consent URL.
  - Nếu gặp lỗi `429 TOO_MANY_REQUESTS`, hiển thị `GoogleLoginErrorModal` báo user chờ 10 phút.

#### B. Màn hình Điều phối OAuth Callback (`/auth/google/callback`)
- **React Components**: `OAuthCallbackPage.tsx`, `LoadingSpinner.tsx`
- **APIs Triggered**:
  1. `POST /api/auth/google` (Lấy code và state từ URL params, đọc `codeVerifier` từ `sessionStorage`)
  2. `GET /api/auth/me` (Ngay sau khi login thành công)
  3. `GET /api/me/onboarding-status` (Ngay sau khi có thông tin user)
- **Expected Behavior**:
  - Nếu thành công: Lưu Access Token vào Memory (React Context/Zustand Store), xóa `codeVerifier` khỏi `sessionStorage`, đọc `nextRecommendedAction` và điều hướng người dùng.
  - Nếu thất bại `400` (`OAUTH_VERIFICATION_FAILED`): Hiển thị Toast "Phiên xác thực Google không hợp lệ hoặc đã hết hạn" và chuyển về `/login`.
  - Nếu thất bại `403` (`USER_BANNED`): Điều hướng ngay sang trang `/account-suspended`.

#### C. App Shell / Auth Guard Wrapper (`AppLayout.tsx`, `AuthProvider.tsx`)
- **React Components**: `HeaderNavbar.tsx`, `UserProfileDropdown.tsx`, `BannedBanner.tsx`
- **APIs Triggered**:
  1. `GET /api/auth/me` (Khi F5 / nạp lại trang)
  2. `POST /api/auth/refresh` (Tự động qua Axios Interceptor khi gặp lỗi `401`)
  3. `POST /api/auth/logout` (Khi bấm nút Đăng xuất)
- **Expected Behavior**:
  - Khi F5 trang: Nếu chưa có Access Token trong memory, tự động gọi `POST /api/auth/refresh` trước. Nếu refresh thành công -> gọi `GET /api/auth/me` -> Khởi tạo App Shell. If refresh 401 -> Đưa App về trạng thái Anonymous.

#### D. Màn hình Onboarding Gate (`/onboarding/*`)
- **React Components**: `StudentProfileForm.tsx`, `MentorProfileForm.tsx`, `VerificationPendingView.tsx`
- **APIs Triggered**: `GET /api/me/onboarding-status`
- **Expected Behavior**:
  - Ánh xạ trực tiếp `nextRecommendedAction`:
    - `COMPLETE_STUDENT_PROFILE` -> Route `/onboarding/student-profile`
    - `WAIT_FOR_APPROVE` -> Route `/onboarding/verification-pending`
    - `REVISE_MENTOR_VERIFICATION` -> Route `/onboarding/mentor-verification`
    - `COMPLETE_MENTOR_PROFILE_OR_EXPLORE` -> Route `/onboarding/mentor-profile` hoặc `/explore`
    - `EXPLORE` -> Route `/explore`

---

### 14.2 Frontend Auth State Machine (Ma trận Trạng thái Auth ở Client)

```
                       +-----------------------+
                       |      INITIALIZING     | (App vừa load / F5)
                       +-----------------------+
                                   |
                         Gửi POST /auth/refresh
                                   |
                     +-------------+-------------+
                     |                           |
               Refresh 200                 Refresh 401
                     |                           |
                     v                           v
         +-----------------------+   +-----------------------+
         |   CHECKING_SESSION    |   |    UNAUTHENTICATED    |
         +-----------------------+   +-----------------------+
                     |                           ^
              GET /auth/me 200                   |
                     |                      Logout / 401
                     v                           |
         +-----------------------+---------------+
         |     AUTHENTICATED     |
         +-----------------------+
                     |
              GET /auth/me 403
                     |
                     v
         +-----------------------+
         |        BLOCKED        | (USER_BANNED / USER_INACTIVE)
         +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | App Startup | After Login | Route Change | Page Refresh (F5) | After Profile Edit | Thao tác Người dùng |
| --- | --- | --- | --- | --- | --- | --- |
| `GET /api/auth/google/authorization-context` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ Khi user bấm nút "Login with Google" |
| `POST /api/auth/google` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ Duy nhất 1 lần tại trang OAuth Callback (`/auth/google/callback`) |
| `POST /api/auth/refresh` | ✅ CÓ (Nếu chưa có Token) | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ✅ Tự động qua Axios Interceptor khi gặp lỗi `401` |
| `POST /api/auth/logout` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ Khi user bấm "Đăng xuất" |
| `GET /api/auth/me` | ✅ CÓ | ✅ CÓ | ❌ KHÔNG (Dùng Cache) | ✅ CÓ | ✅ CÓ (Sau khi sửa thông tin) | ❌ KHÔNG |
| `GET /api/me/onboarding-status` | ✅ CÓ | ✅ CÓ | ❌ KHÔNG (Dùng Cache) | ✅ CÓ | ✅ CÓ (Sau khi nộp hồ sơ/xác thực) | ❌ KHÔNG |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi `USER_BANNED` (`HTTP 403`)
- **Trang hiển thị**: `/account-suspended` (`AccountSuspendedPage.tsx`)
- **UI Element**: Banner cảnh báo đỏ bảo mật + Modal giải thích.
- **Toast Message**: *"Tài khoản của bạn đã bị khóa do vi phạm điều khoản dịch vụ của SkillSwap."*
- **Nút bấm khả dụng**: Nút "Liên hệ Hỗ trợ" (mở mailto/support ticket) và Nút "Đăng xuất".

#### B. Lỗi `USER_INACTIVE` (`HTTP 403`)
- **Trang hiển thị**: `/account-inactive` (`AccountInactivePage.tsx`)
- **UI Element**: Màn hình thông báo chờ kích hoạt tài khoản.
- **Toast Message**: *"Tài khoản của bạn chưa được kích hoạt. Vui lòng kiểm tra email hoặc liên hệ quản trị viên."*

#### C. Lỗi `USER_NOT_FOUND` (`HTTP 404`)
- **Hành động UI**: Xóa toàn bộ auth state ở memory, chuyển về `/login`.
- **Toast Message**: *"Tài khoản không tồn tại hoặc đã bị xóa khỏi hệ thống."*

#### D. Lỗi `OAUTH_VERIFICATION_FAILED` (`HTTP 400`)
- **Component**: `GoogleLoginErrorModal.tsx` trên trang `/login`.
- **Toast Message**: *"Phiên xác thực Google không hợp lệ hoặc đã hết hạn. Vui lòng thử đăng nhập lại."*
- **Hành động UI**: Reset luồng OAuth, xóa `codeVerifier` cũ trong `sessionStorage`.

#### E. Lỗi `TOO_MANY_REQUESTS` (`HTTP 429`)
- **Hành động UI**: Vô hiệu hóa nút "Login with Google" hoặc nút "Refresh" trong 60 giây (đếm ngược Countdown Timer).
- **Toast Message**: *"Bạn đang thực hiện thao tác quá nhanh. Vui lòng thử lại sau ít phút."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['auth', 'me']` | 5 phút (`5 * 60 * 1000`) | 30 phút | `false` | Mutation cập nhật User Profile, Mutation kết nối Google Calendar, `POST /api/auth/logout` |
| `['auth', 'onboarding-status']` | 0 ms (Hoặc cho đến Mutation) | 10 phút | `true` | Mutation nộp Hồ sơ học thuật, Mutation nộp Profile Mentor, Mutation gửi Hồ sơ xác thực |
| `accessToken` | Không cache vào LocalStorage | N/A (Lưu tại React Memory Context/Zustand) | N/A | Expiry `401`, Logout, Refresh Token thất bại |
| `refreshToken` | **NGHIÊM CẤM CACHE MANUAL** | N/A (Do Trình duyệt quản lý qua HttpOnly Cookie) | N/A | N/A |
