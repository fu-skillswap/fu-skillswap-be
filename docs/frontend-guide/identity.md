# Frontend Integration Guide — Identity & Xác Thực (Authentication & Onboarding)

> **Quy tắc ảnh & Avatar:** Với trường `avatarUrl`, Frontend sử dụng nguyên bản URL do backend trả về. URL có thể đến từ Google hoặc CDN SkillSwap; Frontend không tự ghép nối CDN URL từ ID, tên file, `storageKey` hoặc `objectKey`. `STORAGE_PUBLIC_URL_PREFIX` là cấu hình nội bộ của backend, không phải biến `.env` của Frontend.

---

## 1. Kiến Trúc Bảo Mật & Nguyên Tắc Chung (Security & Architecture)

### 1.1 Cấu Trúc Envelope Chuẩn Của API Response (`ApiResponse<T>`)
Tất cả các API của SkillSwap đều trả về một envelope chuẩn dạng JSON:

```typescript
interface ApiResponse<T> {
  timestamp: string;          // Định dạng "yyyy-MM-dd HH:mm:ss"
  status: number;             // HTTP status code (ví dụ: 200, 400, 401, 403, 429)
  code: string;               // Mã nghiệp vụ riêng (ví dụ: "SUCCESS_0200", "AUTH_1001", "VAL_3001")
  message: string;            // Thông điệp an toàn có thể hiển thị trực tiếp lên UI
  data: T | ValidationError[] | null;
  retryAfterSeconds?: number; // Số giây cần chờ khi gặp lỗi 429 Too Many Requests
}

interface ValidationError {
  field: string | null;       // Tên field cần hiển thị lỗi, có thể null
  message: string;            // Thông báo có thể hiển thị cho người dùng
  rejectedValue: unknown;     // Giá trị backend từ chối, có thể null
}
```

> [!NOTE]
> - Với response thành công: `data` chứa payload kiểu `T`.
> - Với lỗi validation `400`: `data` là mảng các `ValidationError`. Frontend gắn `message` vào đúng trường nhập liệu nếu `field` có giá trị; nếu `field` là null thì hiển thị ở đầu form.
> - Luôn ưu tiên kết hợp HTTP status code và mã nghiệp vụ `code` để quyết định luồng xử lý trên UI.

---

### 1.2 Cơ Chế Token & Cookie (JWT + HttpOnly Cookie)
Hệ thống sử dụng cơ chế bảo mật kết hợp hai thành phần:

1. **Access Token (JWT)**:
   - Dùng để xác thực các API yêu cầu đăng nhập.
   - Thời gian sống ngắn (1 giờ).
   - Được trả về trực tiếp trong response body (`TokenResponse.accessToken`).
   - **Đặc thù Next.js**: Lưu Access Token trong bộ nhớ tạm client (React Context / Zustand / Redux / In-Memory), **KHÔNG** lưu vào `localStorage` để chống tấn công XSS.
   - Đính kèm vào HTTP Header khi gọi API:
     ```http
     Authorization: Bearer <accessToken>
     ```

2. **Refresh Token (HttpOnly Cookie)**:
   - Tên cookie: `skillswap_refresh_token`
   - Thời gian sống dài (7 ngày).
   - Được backend tự động đính kèm qua HTTP Header `Set-Cookie` khi đăng nhập hoặc làm mới token thành công.
   - Cấu hình cookie của backend: `HttpOnly; Secure; Path=/api/auth; SameSite=Lax`.
   - **Frontend không thể đọc hay can thiệp cookie này bằng JavaScript (`document.cookie`)**.
   - **Lưu ý cho Next.js**:
     - **Client Component (Browser)**: Tự động gửi cookie này khi gọi `/api/auth/refresh` hoặc `/api/auth/logout` với tùy chọn `credentials: 'include'` (fetch) hoặc `withCredentials: true` (axios).
     - **Server Component / Server Action (Node.js)**: Không dùng refresh cookie cho các API bảo mật như `GET /api/auth/me`; endpoint này bắt buộc Bearer access token.

---

### 1.3 Giới Hạn Tần Suất Gọi API (Rate Limiting)

| Endpoint | Giới hạn hiện tại |
|---|---|
| `GET /api/auth/google/authorization-context` | 60 lần / 10 phút |
| `POST /api/auth/google` | 60 lần / 10 phút |
| `POST /api/auth/refresh` | 40 lần / 10 phút |
| `POST /api/auth/logout` | Không giới hạn riêng |

Khi nhận lỗi `429 Too Many Requests`, đọc trường `retryAfterSeconds` để vô hiệu hóa nút bấm hoặc hiển thị đồng hồ đếm ngược.

---

## 2. Luồng Đăng Nhập Google OAuth 2.0 + PKCE (Next.js Compatible)

SkillSwap áp dụng chuẩn **OAuth 2.0 Authorization Code Flow kết hợp PKCE** để bảo mật luồng đăng nhập phía client. Luồng này chỉ xin scope `openid email profile`; đăng nhập **không kết nối Google Calendar**.

```text
FE Next.js (Browser)            Spring Boot Backend          Google OAuth
    │                                │                            │
    ├── 1. GET /authorization-context ───────────────────────────►│ (Khởi tạo state dùng 1 lần)
    │◄── State & ExpiresAt ──────────┤                            │
    │                                │                            │
    ├── 2. Chuyển hướng sang Google Consent ─────────────────────►│ (User chọn tài khoản Google)
    │◄── 3. Google điều hướng về /vi/auth/google/callback ───────┤ (Kèm code & state)
    │                                │                            │
    ├── 4. POST /api/auth/google ────────────────────────────►│ (Đổi code + PKCE verifier)
    │    { code, state, verifier }   │                            │
    │◄── 200 OK + AccessToken ───────┤                            │
    │    Set-Cookie: refresh         │                            │
```

### Bước 1: Tạo PKCE & Khởi Tạo OAuth Context
Trước khi chuyển hướng người dùng:
1. Sinh `codeVerifier` ngẫu nhiên (43–128 ký tự). Lưu tạm vào `sessionStorage` để đối chiếu ở Bước 3.
2. Tạo `codeChallenge = BASE64URL(SHA256(codeVerifier))`.
3. Gọi API lấy `state` hợp lệ từ backend:
   - **Endpoint**: `GET /api/auth/google/authorization-context`
   - **Query Parameters**:
     - `redirectUri` (string, required): URI callback của Next.js (ví dụ: `https://skillswap.asia/vi/auth/google/callback`).
     - `codeChallenge` (string, required): Chuỗi PKCE challenge vừa tạo.

**Response mẫu (`200 OK`)**:
```json
{
  "timestamp": "2026-08-12 23:05:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "state": "f83a91bc-341e-4501-89ab-123456789abc",
    "expiresAt": "2026-08-12T16:10:00Z"
  }
}
```

### Bước 2: Chuyển Hướng Người Dùng Sang Google Consent Page
Next.js điều hướng trang bằng `window.location.href`:
```text
https://accounts.google.com/o/oauth2/v2/auth?
  client_id=NEXT_PUBLIC_GOOGLE_CLIENT_ID&
  redirect_uri=https://skillswap.asia/vi/auth/google/callback&
  response_type=code&
  scope=openid%20email%20profile&
  state=f83a91bc-341e-4501-89ab-123456789abc&
  code_challenge=<codeChallenge>&
  code_challenge_method=S256
```

### Bước 3: Nhận Callback Tại Route Next.js (`app/[locale]/auth/google/callback/page.tsx`)
Khi Google chuyển hướng về kèm query: `?code=4/0AQST...&state=f83a91bc...`

Page Component gửi request đổi mã lấy token:
- **Endpoint**: `POST /api/auth/google`
- **Request Body**:
```typescript
interface GoogleLoginRequest {
  authorizationCode: string; // "4/0AQSTgQF..." (từ query params)
  redirectUri: string;       // "https://skillswap.asia/vi/auth/google/callback" (khớp với Bước 1)
  codeVerifier: string;      // Chuỗi PKCE codeVerifier đã lưu ở Bước 1
  state: string;             // State nhận từ query params
}
```

---

## 3. Duy Trì Session & Quản Lý Token Trong Next.js

### 3.1 Làm Mới Access Token (`POST /api/auth/refresh`)
- **Endpoint**: `POST /api/auth/refresh`
- **Header Client**: Bắt buộc bật `withCredentials: true` (Axios) hoặc `credentials: 'include'` (Fetch).
- **Request Body**: Không có.

### 3.2 Đăng Xuất (`POST /api/auth/logout`)
- **Endpoint**: `POST /api/auth/logout`
- **Header Client**: Bật `withCredentials: true`.
- Backend tự động thu hồi session và xóa refresh cookie.

### 3.3 Lấy Thông Tin Người Dùng Hiện Tại (`GET /api/auth/me`)
- **Endpoint**: `GET /api/auth/me`
- **Header**: `Authorization: Bearer <accessToken>`

```typescript
interface UserMeResponse {
  publicId: string;                     // UUID public của user
  email: string;                        // Email Google
  fullName: string;                     // Tên hiển thị
  avatarUrl: string;                    // URL ảnh đại diện Google
  status: "ACTIVE" | "INACTIVE" | "BANNED" | "DELETED";
  roles: Array<"MENTEE" | "MENTOR" | "ADMIN" | "SYSTEM_ADMIN">;
  profileCompleted: boolean;            // true nếu đã hoàn thành hồ sơ sinh viên
  hasStudentProfile: boolean;           // Alias của profileCompleted
  googleCalendarConnected: boolean;     // true nếu đã liên kết Google Calendar
  googleCalendarSyncEnabled: boolean;    // true nếu backend tự động sync lịch
  googleCalendarEmail?: string | null;  // Email Google Calendar đã liên kết
  googleCalendarNeedsReconnect: boolean;// true nếu cần kết nối lại Calendar
  googleCalendarLastSyncStatus?: string | null;
  googleCalendarLastSyncAt?: string | null;
}
```

### 3.4 Trình Tự Khi Người Dùng Mở Lại Trang (Session Hydration)
1. Ứng dụng khởi động ở browser, chưa có access token trong bộ nhớ.
2. Gọi `POST /api/auth/refresh` với `withCredentials: true`.
3. Nếu thành công, lưu `response.data.accessToken` vào state bộ nhớ.
4. Gọi `GET /api/auth/me` với Bearer token vừa nhận.
5. Gọi `GET /api/me/onboarding-status` để chọn màn hình điều hướng tiếp theo.
6. Nếu refresh trả về `401`, xóa state đăng nhập và đưa người dùng về `/login`.

---

## 4. Danh Mục Học Thuật (Academic Catalog APIs)

Các API này là **công khai (Public)**, không yêu cầu Token xác thực và có header `Cache-Control: public, max-age=86400`.

### 4.1 Lấy Danh Sách Cơ Sở FPT (`GET /api/campuses`)
- **Endpoint**: `GET /api/campuses`
- **Response**: `ApiResponse<CampusResponse[]>`

```typescript
interface CampusResponse {
  id: string;        // UUID của Cơ sở (dùng để chọn khi tạo student profile)
  code: "HCM" | "HL" | "DN" | "CT" | "QNH";
  name: string;      // Ví dụ: "FPT University Hồ Chí Minh"
  city: string;      // Ví dụ: "Hồ Chí Minh"
}
```

### 4.2 Lấy Danh Sách Ngành Học (`GET /api/academic-programs`)
- **Endpoint**: `GET /api/academic-programs`
- **Response**: `ApiResponse<AcademicProgramResponse[]>`

```typescript
interface AcademicProgramResponse {
  id: string;        // UUID Ngành học
  code: string;      // Mã ngành (ví dụ: "CNTT")
  nameVi: string;    // Tên tiếng Việt
  nameEn: string;    // Tên tiếng Anh
}
```

### 4.3 Lấy Danh Sách Chuyên Ngành Theo Ngành Học (`GET /api/academic-programs/{programId}/specializations`)
- **Endpoint**: `GET /api/academic-programs/{programId}/specializations`
- **Response**: `ApiResponse<SpecializationResponse[]>`

```typescript
interface SpecializationResponse {
  id: string;        // UUID Chuyên ngành
  programId: string; // UUID Ngành học cha
  code: string;      // Mã chuyên ngành (ví dụ: "KTPM", "IA")
  nameVi: string;    // Tên tiếng Việt
  nameEn: string;    // Tên tiếng Anh
  isExpected: boolean;
  isOther: boolean;
}
```

---

## 5. Hồ Sơ Sinh Viên & Điều Hướng Onboarding (Student Profile & Onboarding)

### 5.1 Lấy Hồ Sơ Sinh Viên Của Tôi (`GET /api/me/student-profile`)
- **Endpoint**: `GET /api/me/student-profile`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<StudentProfileResponse>`

> [!NOTE]
> Nếu hồ sơ chưa được tạo, backend trả về mã `404 Not Found`. Đây là trạng thái hoàn toàn bình thường sau lần đăng nhập đầu tiên; hãy chuyển hướng người dùng tới form onboarding thay vì hiện thông báo lỗi.

```typescript
interface StudentProfileResponse {
  userId: string;
  email: string;
  studentCode: string;
  displayName: string;
  avatarUrl: string | null;
  campus: CampusResponse | null;
  program: AcademicProgramResponse | null;
  specialization: SpecializationResponse | null;
  semester: number | null;
  intakeYear: number | null;
  isAlumni: boolean;
  graduationYear: number | null;
  bio: string | null;
  createdAt: string;
  updatedAt: string;
}
```

---

### 5.2 Lưu / Cập Nhật Hồ Sơ Sinh Viên (`PUT /api/me/student-profile`)
- **Endpoint**: `PUT /api/me/student-profile`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`StudentProfileRequest`)**:

```typescript
interface StudentProfileRequest {
  studentCode: string;      // Bắt buộc. Regex không phân biệt hoa/thường: ^[HSDQC][ESA](0[1-9]|1[0-9]|2[0-2])\d{4}$
  displayName?: string;     // Tối đa 150 ký tự
  avatarUrl?: string;
  campusId: string;         // UUID Cơ sở
  programId: string;        // UUID Ngành học
  specializationId: string; // UUID Chuyên ngành (phải thuộc programId)
  semester: number;         // Học kỳ từ 0 đến 9
  intakeYear: number;       // Năm nhập học (ví dụ: 2021)
  isAlumni: boolean;        // true nếu đã tốt nghiệp
  graduationYear?: number;  // Bắt buộc nếu isAlumni = true (phải >= intakeYear + 2)
  bio?: string;
}
```

---

### 5.3 Truy Vấn Trạng Thái Onboarding Tổng Hợp (`GET /api/me/onboarding-status`)
Frontend sử dụng API này để cấu hình Route Protection / Middleware trong Next.js:

- **Endpoint**: `GET /api/me/onboarding-status`
- **Header**: `Authorization: Bearer <accessToken>`

```typescript
interface OnboardingStatusResponse {
  studentProfileCompleted: boolean;
  mentorProfileCompleted: boolean;
  mentorVerificationStatus:
    | "NOT_STARTED"
    | "PENDING_REVIEW"
    | "APPROVED"
    | "NEEDS_REVISION"
    | "REJECTED"
    | "WITHDRAWN";
  roles: Array<"MENTEE" | "MENTOR" | "ADMIN" | "SYSTEM_ADMIN">;
  nextRecommendedAction: 
    | "COMPLETE_STUDENT_PROFILE"              // Điều hướng -> /onboarding/student-profile
    | "WAIT_FOR_APPROVE"                     // Điều hướng -> /mentor/status
    | "REVISE_MENTOR_VERIFICATION"            // Điều hướng -> /mentor/verification?mode=edit
    | "COMPLETE_MENTOR_PROFILE_OR_EXPLORE"   // Vào trang chủ hoặc hoàn thiện mentor profile
    | "SUBMIT_MENTOR_VERIFICATION_OR_EXPLORE" // Vào trang chủ hoặc nộp hồ sơ mentor
    | "EXPLORE";                              // Cho phép truy cập -> /dashboard
}
```

---

## 6. Google Calendar Không Thuộc Luồng Đăng Nhập

- Không thêm scope Calendar vào URL đăng nhập.
- Không gọi API Calendar sau khi user đăng nhập hoặc hoàn thành onboarding.
- Chỉ mentor đã được Admin duyệt mới kết nối Calendar khi chuẩn bị tạo service.
- Toàn bộ flow Calendar dành cho FE nằm trong [mentor-service.md](mentor-service.md).
- `googleCalendarConnected` trong `GET /api/auth/me` chỉ là trạng thái hiển thị; không dùng nó thay cho bước kiểm tra mới trước khi tạo service.

---

## 7. Bảng Mã Lỗi Thường Gặp (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho Frontend |
|---|---|---|
| `400` | `VAL_3001` | Form dữ liệu không hợp lệ. Hiển thị thông báo lỗi dưới từng trường. |
| `400` | `AUTH_1006` | Đăng nhập Google thất bại (OAuth code hỏng hoặc hết hạn). Hiển thị Toast thông báo đăng nhập lại. |
| `401` | `AUTH_1001` | Chưa xác thực hoặc Access Token không hợp lệ. Tự động gọi API `/refresh`. |
| `401` | `AUTH_1003` | Refresh Cookie hết hạn. Xóa in-memory token và chuyển hướng tới `/login`. |
| `403` | `AUTH_1002` / `AUTH_1007` | Không có quyền hạn truy cập tài nguyên. |
| `403` | `AUTH_1004` | Tài khoản bị khóa (Banned). Chuyển hướng tới trang thông báo tài khoản bị khóa. |
| `429` | `SYS_0010` | Thao tác quá nhanh. Đọc `retryAfterSeconds` để khóa nút thao tác và đếm ngược. |

---

## 8. Code Mẫu Tích Hợp Next.js App Router (Production-Ready)

### 8.1 API Client Tự Động Refresh Token (`lib/api-client.ts`)

```typescript
import axios from 'axios';

const NEXT_PUBLIC_API_URL = process.env.NEXT_PUBLIC_API_URL || 'https://api.skillswap.asia';

export const apiClient = axios.create({
  baseURL: NEXT_PUBLIC_API_URL,
  withCredentials: true, // BẮT BUỘC: Gửi HttpOnly Refresh Cookie với domain /api/auth
});

let memoryToken: string | null = null;

export const setAccessToken = (token: string | null) => {
  memoryToken = token;
};

export const getAccessToken = () => memoryToken;

// Đính kèm Bearer token vào request header
apiClient.interceptors.request.use((config) => {
  if (memoryToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${memoryToken}`;
  }
  return config;
});

// Interceptor tự động Refresh Token khi nhận lỗi 401
apiClient.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const originalRequest = error.config;
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/api/auth/refresh')
    ) {
      originalRequest._retry = true;
      try {
        // Tự động làm mới token (Browser tự đính kèm Refresh Cookie)
        const res: any = await apiClient.post('/api/auth/refresh');
        const newToken = res.data?.accessToken || res.accessToken;
        setAccessToken(newToken);
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      } catch (refreshErr) {
        setAccessToken(null);
        if (typeof window !== 'undefined') {
          window.location.href = '/login?reason=session_expired';
        }
        return Promise.reject(refreshErr);
      }
    }
    return Promise.reject(error.response?.data || error);
  }
);
```
