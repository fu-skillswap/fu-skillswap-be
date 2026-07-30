# Authentication

## Mục tiêu
File này mô tả luồng đăng nhập, refresh token, logout, current user và các cờ session mà FE cần dùng để quyết định:
- user đã đăng nhập hay chưa
- token còn hợp lệ hay không
- user có phải đi onboarding hay không
- user đang bị ban / inactive / thiếu quyền nào

Google Calendar connect/disconnect không thuộc file này; phần đó nằm ở `06-calendar.md`.

## API inventory
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/auth/google/authorization-context` | Public | - | `redirectUri`, `codeChallenge` | `GoogleAuthorizationContextResponse` | - | Lấy `state` dùng một lần trước khi redirect sang Google |
| POST | `/api/auth/google` | Public | - | `GoogleLoginRequest` | `TokenResponse` | - | Đổi authorization code + PKCE lấy access token |
| POST | `/api/auth/refresh` | Public | - | cookie refresh token | `TokenResponse` | - | Làm mới phiên bằng refresh token trong cookie |
| POST | `/api/auth/logout` | Public | - | cookie refresh token | `String` | - | Thu hồi session hiện tại |
| GET | `/api/auth/me` | Authenticated | any logged-in user | - | `UserMeResponse` | - | Trạng thái đăng nhập / role / session flags |

## Auth lifecycle
### Trạng thái FE nên hiểu
1. `Anonymous`
   - chưa có access token hợp lệ
2. `OAuthContextReady`
   - đã xin được `state` từ `/api/auth/google/authorization-context`
3. `RedirectingToGoogle`
   - FE chuyển user sang Google OAuth
4. `GoogleCallbackReady`
   - FE đã nhận authorization code và PKCE data
5. `Authenticated`
   - `/api/auth/google` hoặc `/api/auth/refresh` thành công
6. `SessionExpiredOrRevoked`
   - refresh thất bại hoặc token bị thu hồi
7. `BannedOrInactive`
   - backend chặn dù token còn thể đọc được
8. `Deleted`
   - account đã xóa không tự được khôi phục bằng Google Login; FE hiển thị lỗi và không retry OAuth tự động

### Call order chuẩn cho login Google
1. FE gọi `GET /api/auth/google/authorization-context`.
2. FE redirect sang Google bằng `state` vừa nhận.
3. Google trả authorization code về FE.
4. FE gọi `POST /api/auth/google` với:
   - `authorizationCode`
   - `redirectUri`
   - `codeVerifier`
   - `state`
5. Backend trả `accessToken` trong body và set refresh token qua cookie.
6. FE gọi `GET /api/auth/me` để lấy vai trò và cờ session.
7. FE gọi `GET /api/me/onboarding-status` nếu cần điều hướng onboarding.

### Call order chuẩn cho refresh
1. Gặp `401` hoặc access token gần hết hạn.
2. FE gọi `POST /api/auth/refresh`.
3. Nếu refresh thành công:
   - dùng access token mới
   - giữ nguyên session hiện tại
4. Nếu refresh thất bại:
   - xóa state auth trên FE
   - chuyển về login

### Call order chuẩn cho logout
1. FE gọi `POST /api/auth/logout`.
2. Backend thu hồi refresh token hiện tại.
3. FE xóa access token local state.
4. FE điều hướng về màn login/public.

## Token lifecycle
- `accessToken`
  - dùng cho Authorization header
  - ngắn hạn
  - chỉ cần giữ trong memory/state quản lý auth của FE
- `refreshToken`
  - không phải token nghiệp vụ ở FE
  - backend rotate qua response header `Set-Cookie` trong cookie `HttpOnly`, `Path=/api/auth`
  - `Secure` và `SameSite` được quyết định bởi environment/config; production không cho FE đọc cookie này
  - FE không nên đọc/ghi thủ công
- `tokenType`
  - hiện là `Bearer`
  - FE không tự đổi scheme

### Điều FE phải nhớ
- `POST /api/auth/google` và `POST /api/auth/refresh` đều có thể trả token mới.
- Nếu backend set cookie refresh token, FE chỉ cần làm việc với body access token.
- `TokenResponse.refreshToken` là field nội bộ/hidden; FE không nên phụ thuộc vào nó.
- `POST /api/auth/refresh` và `POST /api/auth/logout` không nhận `refreshToken` trong body, query hay Bearer input. Browser tự gửi cookie khi gọi cùng application origin hợp lệ.
- Swagger UI chỉ thử được refresh/logout khi đang cùng origin và browser đã có cookie hợp lệ; không dán/copy refresh cookie vào Swagger UI.

## User session state
`GET /api/auth/me` là nguồn sự thật để FE biết user đang ở đâu trong hệ thống.

### `UserMeResponse` quan trọng nhất
- `status`
  - trạng thái tài khoản: `ACTIVE`, `INACTIVE`, `BANNED`, `DELETED`
- `roles`
  - vai trò hiện tại: `MENTEE`, `MENTOR`, `ADMIN`, `SYSTEM_ADMIN`
- `profileCompleted`
  - đã xong hồ sơ học thuật hay chưa
- `hasStudentProfile`
  - alias của `profileCompleted`
- `googleCalendarConnected`
  - đã connect Google Calendar hay chưa
- `googleCalendarSyncEnabled`
  - backend có thể đồng bộ lịch hay chưa
- `googleCalendarNeedsReconnect`
  - cần reconnect vì token/scope lỗi hay không
- `googleCalendarLastSyncStatus`
  - trạng thái sync gần nhất
- `googleCalendarLastSyncAt`
  - thời điểm sync gần nhất

### FE nên đọc `GET /api/auth/me` khi nào
- ngay sau login
- ngay sau refresh
- sau khi app reload
- khi cần kiểm tra quyền hiển thị menu / route
- khi thấy `401` để xác nhận session thực sự còn hay không

## Permission rules
- `GET /api/auth/me` cần bearer token hợp lệ.
- Trạng thái `BANNED` hoặc `INACTIVE` có thể làm backend chặn dù token vẫn parse được.
- `roles` là để render UI và route guard, nhưng FE không nên tự quyết định business bằng role nếu backend đã có endpoint check cụ thể.

## Error handling matrix
| HTTP / code | Khi nào xảy ra | FE nên làm |
| --- | --- | --- |
| `400 BAD_REQUEST` / `INVALID_INPUT` | missing `state`, sai PKCE, sai redirectUri, refresh cookie rỗng | hiển thị lỗi form hoặc lỗi flow, không retry tự động |
| `401 UNAUTHENTICATED` / `SESSION_EXPIRED` | token hết hạn, refresh không hợp lệ, chưa có session | gọi refresh 1 lần; nếu vẫn fail thì về login |
| `403 ACCESS_DENIED` / `USER_BANNED` / `USER_INACTIVE` | tài khoản bị khóa hoặc không được phép truy cập | chặn action, hiển thị màn blocked/forbidden |
| `404 USER_NOT_FOUND` | account đã bị xóa hoặc không còn truy cập được | xóa auth state FE, không retry Google Login tự động |
| `409 RESOURCE_CONFLICT` | code/state hết hạn hoặc đã dùng | restart OAuth context flow |
| `429 TOO_MANY_REQUESTS` | spam tạo context/login/refresh | backoff và chờ |
| `500 UNCATEGORIZED_EXCEPTION` | lỗi hệ thống | cho phép retry có kiểm soát |

## FE implementation rules
- Chỉ gọi `/api/auth/google` sau khi đã có `state` từ `/api/auth/google/authorization-context`.
- `state` là opaque one-time value, không tự chỉnh sửa.
- Access token là nguồn duy nhất để gọi API authenticated.
- Sau login/refresh, luôn gọi lại `/api/auth/me`.
- `googleCalendarConnected` là session flag phụ, không thay cho API calendar.

## FE anti-patterns
- Không dùng `idToken` cho login flow hiện tại.
- Không giữ refresh token trong localStorage/sessionStorage.
- Không gọi `/api/auth/refresh` trong loop vô hạn.
- Không coi Google Login là account-recovery flow cho account đã bị xóa.
- Không suy diễn onboarding từ role một mình.
- Không lấy trạng thái Google Calendar ở file auth để thay thế file calendar.

## Deprecated / legacy fields
- `TokenResponse.refreshToken`
  - hidden ở response body, chỉ phục vụ backend/cookie flow
- `googleCalendar*` trong `/api/auth/me`
  - chỉ là session flags, không thay cho API `/api/me/google-calendar/status`

## Response JSON example
### Login Google thành công
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer"
  }
}
```

### Current user
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "publicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "nguyenvana@gmail.com",
    "fullName": "Nguyễn Văn A",
    "avatarUrl": "https://lh3.googleusercontent.com/...",
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

## UI mapping
- Login screen:
  - nút "Continue with Google" gọi authorization-context trước
- Redirect handler:
  - lấy `state`, `authorizationCode`, `codeVerifier`, `redirectUri`
- App shell:
  - đọc `/api/auth/me` để quyết định menu, route guard và banner bị ban/inactive
- Onboarding gate:
  - nếu `profileCompleted=false`, đẩy user sang onboarding thay vì vào dashboard

## API success/error behavior
- `GET /api/auth/google/authorization-context`
  - success: FE redirect sang Google ngay
  - error 429/500: show toast và cho user retry thủ công
- `POST /api/auth/google`
  - success: lưu access token, set cookie refresh token, gọi `/api/auth/me`
  - error 400: sai state/PKCE, phải làm lại OAuth flow
  - error 403: user bị ban/inactive, không được vào app
- `POST /api/auth/refresh`
  - success: update access token state
  - error 401: xóa session FE và về login
  - error 403: request không đến từ frontend origin đã đăng ký; không retry từ origin khác
- `POST /api/auth/logout`
  - success: xóa session FE và cookie refresh
  - error 401: vẫn nên coi như logout xong ở FE nếu token local đã bị clear
  - error 403: clear access token local, nhưng phải gọi lại từ application origin hợp lệ để browser nhận Set-Cookie xóa refresh token

## Ghi chú cho AI Agent và FE dev
- `state` là opaque one-time value, không parse.
- `refreshToken` không phải data cho FE business logic.
- Refresh token chỉ nằm trong cookie `HttpOnly; Secure; SameSite=Lax; Path=/api/auth`. FE không đọc hoặc gửi token trong body. `POST /api/auth/refresh` và `POST /api/auth/logout` bắt buộc có browser `Origin` khớp `CORS_ALLOWED_ORIGIN_PATTERNS`; khi browser không gửi `Origin`, backend kiểm tra `Referer` cùng allowlist. Nếu cả hai thiếu/sai, backend trả `403`. Production chỉ dùng các origin cụ thể, không wildcard.
- `googleCalendarConnected` trong auth me chỉ là flag phụ cho shell, không thay calendar detail API.
