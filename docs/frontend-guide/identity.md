# Frontend Integration Guide — Identity & Authentication Module

> Với `avatarUrl`, dùng nguyên URL backend trả về. URL có thể đến từ Google hoặc CDN SkillSwap; FE không tự ghép CDN URL từ ID, tên file, `storageKey` hoặc `objectKey`. `STORAGE_PUBLIC_URL_PREFIX` là cấu hình backend, không phải biến `.env` của FE.

---

## 1. Kiến trúc Bảo mật & Nguyên tắc Chung (Security Architecture & Principles)

### 1.1 Mẫu Envelope chuẩn của API Response (`ApiResponse<T>`)
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

- Với response thành công, `data` là payload đúng kiểu `T`.
- Với lỗi validate `400`, `data` thường là mảng `ValidationError`. FE nên gắn `message` vào đúng field nếu `field` có giá trị; nếu không có field thì hiển thị lỗi chung của form.
- Với các lỗi khác, `data` có thể là `null`. Luôn ưu tiên HTTP status và `code` để quyết định xử lý, không chỉ dựa vào `message`.

### 1.2 Cơ chế Token & Cookie (JWT + HttpOnly Cookie)
Hệ thống sử dụng cơ chế bảo mật kết hợp hai thành phần:

1. **Access Token (JWT)**:
   - Dùng để xác thực các API yêu cầu đăng nhập.
   - Thời gian sống ngắn (1 giờ).
   - Được trả về trực tiếp trong response body (`TokenResponse.accessToken`).
   - **Đặc thù Next.js**: Lưu Access Token trong bộ nhớ tạm client (React Context / Zustand / Redux / In-Memory), **KHÔNG** lưu vào `localStorage` để chống XSS.
   - Đính kèm vào HTTP Header khi gọi API:
     ```http
     Authorization: Bearer <accessToken>
     ```

2. **Refresh Token (HttpOnly Cookie)**:
   - Tên cookie: `skillswap_refresh_token`
   - Thời gian sống dài (7 ngày).
   - Được backend tự động đính kèm qua HTTP Header `Set-Cookie` khi đăng nhập hoặc refresh thành công.
   - Cookie do backend cấu hình. Cấu hình hiện tại là `HttpOnly; Secure; Path=/api/auth; SameSite=Lax`.
   - FE không tự tạo, tự xóa hoặc cố thay đổi các thuộc tính cookie này.
   - **FE tuyệt đối không thể truy cập Cookie này bằng JavaScript (`document.cookie`)**.
   - **Lưu ý quan trọng cho Next.js**:
     - **Ở Client Component (Browser)**: Tự động gửi cookie này khi gọi `/api/auth/refresh` hoặc `/api/auth/logout` với tùy chọn `credentials: 'include'` (fetch) hoặc `withCredentials: true` (axios).
     - **Ở Server Component / Server Action / Route Handler (Node.js)**: Không dùng refresh cookie cho các API protected như `GET /api/auth/me`; endpoint này vẫn bắt buộc Bearer access token.

### 1.3 Giới hạn tần suất gọi API (Rate Limiting)
Backend giới hạn một số endpoint xác thực theo IP. Không dùng các số này để tự chặn user trước khi gọi API; FE chỉ dùng chúng để giải thích khi backend trả `429`.

| Endpoint | Giới hạn hiện tại |
| --- | --- |
| `GET /api/auth/google/authorization-context` | 60 lần / 10 phút |
| `POST /api/auth/google` | 60 lần / 10 phút |
| `POST /api/auth/refresh` | 40 lần / 10 phút |
| `POST /api/auth/logout` | Không có rate limit riêng trong controller hiện tại |

```json
{
  "timestamp": "2026-08-12 23:00:00",
  "status": 429,
  "code": "SYS_0010",
  "message": "Bạn đang thao tác quá nhanh, vui lòng thử lại sau ít phút",
  "data": null,
  "retryAfterSeconds": 300
}
```
FE đọc `retryAfterSeconds` để vô hiệu hóa button hoặc hiển thị countdown cho người dùng.

---

## 2. Luồng Đăng nhập Google OAuth 2.0 + PKCE (Next.js Compatible)

SkillSwap áp dụng **OAuth 2.0 Authorization Code Flow kết hợp PKCE** để bảo mật luồng đăng nhập phía client.

```text
FE Next.js (Browser)            Spring Boot Backend          Google OAuth
    │                                │                            │
    ├── 1. GET /authorization-context ───────────────────────────►│ (Khởi tạo state dùng 1 lần)
    │◄── State & ExpiresAt ──────────┤                            │
    │                                │                            │
    ├── 2. Redirect to Google Consent ───────────────────────────►│ (User chọn tài khoản Google)
    │◄── 3. Redirect back to /auth/google/callback ──────────────┤ (Kèm code & state)
    │                                │                            │
    ├── 4. POST /api/auth/google ────────────────────────────►│ (Đổi code + PKCE verifier)
    │    { code, state, verifier }   │                            │
    │◄── 200 OK + AccessToken ───────┤                            │
    │    Set-Cookie: refresh         │                            │
```

### Bước 1: Tạo PKCE & Khởi tạo OAuth Context
Trước khi chuyển hướng người dùng:
1. Sinh `codeVerifier` ngẫu nhiên (43–128 ký tự). Lưu tạm vào `sessionStorage` hoặc Cookie tạm thời để đối chiếu ở Bước 3.
2. Tạo `codeChallenge = BASE64URL(SHA256(codeVerifier))`.
3. Gọi API lấy `state` hợp lệ từ backend:
   - **Endpoint**: `GET /api/auth/google/authorization-context`
   - **Query Parameters**:
     - `redirectUri` (string, required): URI callback của Next.js (ví dụ: `https://skillswap.asia/auth/google/callback` hoặc `http://localhost:3000/auth/google/callback`).
     - `codeChallenge` (string, required): Chuỗi PKCE challenge vừa tạo.

**Response thành công (`200 OK`)**:
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

### Bước 2: Chuyển hướng người dùng sang Google Consent Page
Next.js điều hướng trang bằng `window.location.href`:
```text
https://accounts.google.com/o/oauth2/v2/auth?
  client_id=NEXT_PUBLIC_GOOGLE_CLIENT_ID&
  redirect_uri=https://skillswap.asia/auth/google/callback&
  response_type=code&
  scope=openid%20email%20profile&
  state=f83a91bc-341e-4501-89ab-123456789abc&
  code_challenge=<codeChallenge>&
  code_challenge_method=S256
```

### Bước 3: Nhận Callback tại Next.js Route (`app/auth/google/callback/page.tsx`)
Khi Google chuyển hướng người dùng về Next.js page với query: `?code=4/0AQST...&state=f83a91bc...`

Page Component đọc query params và gửi request đổi lấy token:

- **Endpoint**: `POST /api/auth/google`
- **Request Body**:
```typescript
interface GoogleLoginRequest {
  authorizationCode: string; // "4/0AQSTgQF..." (từ query params)
  redirectUri: string;       // "https://skillswap.asia/auth/google/callback" (khớp với Bước 1)
  codeVerifier: string;      // Chuỗi PKCE codeVerifier đã lưu ở Bước 1
  state: string;             // State nhận từ query params
}
```

**Response thành công (`200 OK`)**:
- Response Header: `Set-Cookie: skillswap_refresh_token=<token>; Path=/api/auth; HttpOnly; SameSite=Lax`
- Response Body:
```json
{
  "timestamp": "2026-08-12 23:06:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer"
  }
}
```

---

## 3. Duy trì Session & Quản lý Token trong Next.js

### 3.1 làm mới Access Token (`POST /api/auth/refresh`)
Thực hiện khi Access Token hết hạn hoặc khi ứng dụng Next.js khởi chạy lại ở Client Side:
- **Endpoint**: `POST /api/auth/refresh`
- **Header Client**: Bắt buộc bật `withCredentials: true` (Axios) hoặc `credentials: 'include'` (Fetch).
- **Request Body**: Không có.

### 3.2 Đăng xuất (`POST /api/auth/logout`)
- **Endpoint**: `POST /api/auth/logout`
- **Header Client**: Bật `withCredentials: true`.
- Backend tự động thu hồi session và trả về header xóa cookie `Set-Cookie: skillswap_refresh_token=; Max-Age=0; Path=/api/auth`.

### 3.3 Lấy thông tin User hiện tại (`GET /api/auth/me`)
- **Endpoint**: `GET /api/auth/me`
- **Header**: `Authorization: Bearer <accessToken>`

**Response Payload (`UserMeResponse`)**:
```typescript
interface UserMeResponse {
  publicId: string;                     // UUID public của user
  email: string;                        // Email Google
  fullName: string;                     // Tên hiển thị
  avatarUrl: string;                    // URL ảnh đại diện Google
  status: "ACTIVE" | "INACTIVE" | "BANNED" | "DELETED";
  roles: Array<"MENTEE" | "MENTOR" | "ADMIN" | "SYSTEM_ADMIN">;
  profileCompleted: boolean;            // true nếu đã hoàn thành student profile
  hasStudentProfile: boolean;           // Alias của profileCompleted
  googleCalendarConnected: boolean;     // true nếu đã kết nối Google Calendar
  googleCalendarSyncEnabled: boolean;    // true nếu backend tự động sync lịch
  googleCalendarEmail?: string | null;  // Email Google Calendar đã liên kết
  googleCalendarNeedsReconnect: boolean;// true nếu cần kết nối lại Calendar
  googleCalendarLastSyncStatus?: string | null;
  googleCalendarLastSyncAt?: string | null;
}
```

### 3.4 Trình tự khi người dùng mở lại trang
Đây là luồng FE nên dùng để khôi phục phiên. Không gọi `GET /api/auth/me` chỉ với refresh cookie.

1. App khởi động ở browser, chưa có access token trong bộ nhớ.
2. Gọi `POST /api/auth/refresh` với `withCredentials: true`.
3. Nếu thành công, lưu `response.data.accessToken` vào state bộ nhớ.
4. Gọi `GET /api/auth/me` với Bearer token vừa nhận.
5. Gọi `GET /api/me/onboarding-status` để chọn màn hình tiếp theo.
6. Nếu refresh trả `401`, xóa state đăng nhập và đưa user về `/login`.

Không gọi refresh lặp vô hạn. Mỗi request thất bại chỉ được retry một lần sau khi refresh thành công.

---

## 4. Tra cứu Danh mục Học thuật (Academic Catalog APIs)

- Các API công khai (Public APIs), không yêu cầu Authorization Token.
- Trả về header `Cache-Control: public, max-age=86400`. Có thể gọi trực tiếp ở Server Component hoặc Client Component.

### 4.1 Lấy danh sách Cơ sở FPT (`GET /api/campuses`)
- **Endpoint**: `GET /api/campuses`
- **Response**: `ApiResponse<CampusResponse[]>`

```typescript
interface CampusResponse {
  id: string;        // UUID của Cơ sở (dùng để submit student profile)
  code: "HCM" | "HL" | "DN" | "CT" | "QNH";
  name: string;      // Ví dụ: "FPT University Hồ Chí Minh"
  city: string;      // Ví dụ: "Hồ Chí Minh"
}
```

### 4.2 Lấy danh sách Ngành học (`GET /api/academic-programs`)
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

### 4.3 Lấy danh sách Chuyên ngành theo Ngành học (`GET /api/academic-programs/{programId}/specializations`)
- **Endpoint**: `GET /api/academic-programs/{programId}/specializations`
- **Path Param**: `programId` (UUID của ngành học được chọn)
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

## 5. Hồ sơ Học thuật & Điều hướng Onboarding trong Next.js

### 5.1 Lấy Hồ sơ Học thuật của tôi (`GET /api/me/student-profile`)
- **Endpoint**: `GET /api/me/student-profile`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<StudentProfileResponse>`

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

Nếu profile chưa được tạo, backend trả `404`. Đây là trạng thái bình thường ngay sau lần đăng nhập đầu tiên; chuyển user tới form onboarding thay vì hiện thông báo lỗi hệ thống.

### 5.2 Lưu / Cập nhật Hồ sơ Học thuật (`PUT /api/me/student-profile`)
- **Endpoint**: `PUT /api/me/student-profile`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`StudentProfileRequest`)**:

```typescript
interface StudentProfileRequest {
  studentCode: string;      // Required. Regex không phân biệt hoa/thường: ^[HSDQC][ESA](0[1-9]|1[0-9]|2[0-2])\d{4}$
  displayName?: string;     // Max 150 chars
  avatarUrl?: string;
  campusId: string;         // UUID Campus
  programId: string;        // UUID Program
  specializationId: string; // UUID Specialization (phải thuộc programId)
  semester: number;         // Integer 0 -> 9
  intakeYear: number;       // Năm nhập học (ví dụ: 2021)
  isAlumni: boolean;        // true nếu đã tốt nghiệp
  graduationYear?: number;  // Bắt buộc nếu isAlumni = true
  bio?: string;
}
```

Quy tắc backend cần phản ánh trên form:

- `specializationId` phải thuộc `programId` đang chọn. Khi đổi ngành, xóa lựa chọn chuyên ngành cũ và tải lại bằng endpoint theo `programId`.
- `intakeYear` và `graduationYear` phải từ năm `2000` đến năm hiện tại.
- Nếu `isAlumni = true`, `graduationYear` là bắt buộc và phải lớn hơn hoặc bằng `intakeYear + 2`.
- Backend luôn lưu `semester = 9` cho cựu sinh viên. FE vẫn phải gửi `semester` hợp lệ từ `0` đến `9`; nên khóa ô chọn học kỳ và gửi `9` khi bật trạng thái cựu sinh viên.

#### Quy tắc Validation dành cho Next.js Form (React Hook Form / Zod):
```typescript
import { z } from 'zod';

export const studentProfileSchema = z.object({
  studentCode: z.string().regex(
    /^[HSDQC][ESA](0[1-9]|1[0-9]|2[0-2])\d{4}$/i,
    'Mã số sinh viên không đúng định dạng (Ví dụ: SE192621)'
  ),
  displayName: z.string().max(150, 'Tên hiển thị không được quá 150 ký tự').optional(),
  avatarUrl: z.string().url('URL ảnh không hợp lệ').optional().or(z.literal('')),
  campusId: z.string().uuid('Vui lòng chọn cơ sở FPT'),
  programId: z.string().uuid('Vui lòng chọn ngành học'),
  specializationId: z.string().uuid('Vui lòng chọn chuyên ngành'),
  semester: z.number().min(0, 'Học kỳ từ 0 đến 9').max(9, 'Học kỳ từ 0 đến 9'),
  intakeYear: z.number().min(2000, 'Năm nhập học không hợp lệ').max(new Date().getFullYear()),
  isAlumni: z.boolean(),
  graduationYear: z.number().optional(),
  bio: z.string().optional(),
}).superRefine((data, ctx) => {
  if (data.isAlumni && data.graduationYear === undefined) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Vui lòng điền năm tốt nghiệp nếu bạn là cựu sinh viên',
      path: ['graduationYear'],
    });
  }
  if (data.graduationYear !== undefined && data.graduationYear < data.intakeYear + 2) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Năm tốt nghiệp phải sau năm nhập học ít nhất 2 năm',
      path: ['graduationYear'],
    });
  }
});
```

### 5.3 Truy vấn Trạng thái Onboarding tổng hợp (`GET /api/me/onboarding-status`)
FE dùng response này để cấu hình Route Protection / Navigation Guard trong Next.js (Middleware hoặc Protected Route Component):

- **Endpoint**: `GET /api/me/onboarding-status`
- **Header**: `Authorization: Bearer <accessToken>`

**Response Payload (`OnboardingStatusResponse`)**:
```typescript
interface OnboardingStatusResponse {
  studentProfileCompleted: boolean;
  mentorProfileCompleted: boolean;
  mentorVerificationStatus: "NOT_STARTED" | "PENDING_REVIEW" | "APPROVED" | "NEEDS_REVISION" | "REJECTED" | "WITHDRAWN";
  roles: Array<"MENTEE" | "MENTOR" | "ADMIN" | "SYSTEM_ADMIN">;
  nextRecommendedAction: 
    | "COMPLETE_STUDENT_PROFILE"              // Redirect -> /onboarding/student-profile
    | "WAIT_FOR_APPROVE"                     // Redirect -> /mentor/verification-status
    | "REVISE_MENTOR_VERIFICATION"            // Redirect -> /mentor/verification-edit
    | "COMPLETE_MENTOR_PROFILE_OR_EXPLORE"   // Có thể chọn điền mentor profile hoặc xem trang chính
    | "SUBMIT_MENTOR_VERIFICATION_OR_EXPLORE" // Có thể nộp xác minh mentor hoặc xem trang chính
    | "EXPLORE";                              // Allow access -> /dashboard
}
```

---

## 6. Tích hợp Google Calendar

Ba endpoint dưới đây yêu cầu `Authorization: Bearer <accessToken>`. Controller hiện kiểm tra user đã đăng nhập; FE chỉ nên hiển thị tính năng này ở khu vực mentor theo UX của sản phẩm, nhưng không tự coi đó là một API chỉ-mentor nếu backend chưa trả `403` cho role khác.

### 6.1 Trạng thái kết nối Google Calendar (`GET /api/me/google-calendar/status`)
- **Endpoint**: `GET /api/me/google-calendar/status`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<GoogleCalendarStatusResponse>`

### 6.2 Kết nối Google Calendar (`POST /api/me/google-calendar/connect`)
- **Endpoint**: `POST /api/me/google-calendar/connect`
- **Header**: `Authorization: Bearer <accessToken>`
- **Body**:

```typescript
interface GoogleCalendarConnectRequest {
  authorizationCode: string;
  redirectUri: string;
  codeVerifier: string;
}
```

### 6.3 Ngắt kết nối Google Calendar (`POST /api/me/google-calendar/disconnect`)
- **Endpoint**: `POST /api/me/google-calendar/disconnect`
- **Header**: `Authorization: Bearer <accessToken>`

```typescript
interface GoogleCalendarStatusResponse {
  connected: boolean;
  syncEnabled: boolean;
  email: string | null;
  grantedScopes: string[] | null;
  needsReconnect: boolean;
  lastSyncStatus: string | null;
  lastSyncAt: string | null;
  lastSyncErrorCode: string | null;
  lastSyncErrorMessage: string | null;
}
```

Khi `needsReconnect = true`, hiển thị nút kết nối lại. Không tự gọi `connect` vì user phải hoàn thành Google consent screen.

---

## 7. Bảng Mã Lỗi Thường Gặp (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho Next.js FE |
|---|---|---|
| `400` | `VAL_3001` | Form dữ liệu không hợp lệ. Khớp thông báo với React Hook Form / Zod. |
| `400` | `AUTH_1006` | Đăng nhập Google thất bại (OAuth code hỏng hoặc hết hạn). Hiển thị Toast thông báo đăng nhập lại. |
| `401` | `AUTH_1001` | Chưa xác thực hoặc Access Token không hợp lệ. Tự động gọi API `/refresh`. |
| `401` | `AUTH_1003` | Refresh Cookie hết hạn. Xóa in-memory token và chuyển hướng tới `/login`. |
| `403` | `AUTH_1002` / `AUTH_1007` | Không có quyền hạn (ví dụ: Mentee cố truy cập API dành cho Mentor/Admin). |
| `403` | `AUTH_1004` | Tài khoản bị khóa (Banned). Redirect tới trang thông báo tài khoản bị khóa. |
| `429` | `SYS_0010` | Rate limit. Đọc `retryAfterSeconds` để khóa nút thao tác và đếm ngược. |

---

## 8. Hướng dẫn Code Mẫu Tích hợp Next.js App Router (Production-Ready)

### 8.1 API Client cho Client Components (`lib/api-client.ts`)

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

// Attach Bearer token vào request header
apiClient.interceptors.request.use((config) => {
  if (memoryToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${memoryToken}`;
  }
  return config;
});

// Auto-Refresh Token Interceptor khi nhận lỗi 401
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
        const newToken = res.data.accessToken;
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

### 8.2 Lưu ý khi dùng Next.js Server Components

Luồng xác thực hiện tại được thiết kế cho browser gọi trực tiếp backend: browser nhận và gửi refresh cookie, còn access token chỉ giữ trong bộ nhớ client.

- Không gọi `POST /api/auth/refresh` từ Server Component rồi kỳ vọng cookie mới tự quay về browser.
- Không forward refresh cookie rồi dùng nó để gọi `GET /api/auth/me`; endpoint này cần Bearer access token.
- Nếu FE muốn dùng BFF/SSR cho các API cần đăng nhập, cần một contract BFF riêng về cookie domain, cách truyền `Set-Cookie` và lifecycle access token. Contract đó chưa có trong backend hiện tại.

Vì vậy, với FE mới hãy dùng `apiClient` ở mục 8.1 để bootstrap phiên và tải user/onboarding sau khi client đã mount.

### 8.3 Điều hướng Onboarding theo Trạng thái Backend (`hooks/useAuthNavigation.ts`)

```typescript
import { useRouter } from 'next/navigation';
import { apiClient } from '@/lib/api-client';

export function useAuthNavigation() {
  const router = useRouter();

  const handlePostLoginRedirect = async () => {
    try {
      const statusRes: any = await apiClient.get('/api/me/onboarding-status');
      const action = statusRes.data.nextRecommendedAction;

      switch (action) {
        case 'COMPLETE_STUDENT_PROFILE':
          router.push('/onboarding/student-profile');
          break;
        case 'REVISE_MENTOR_VERIFICATION':
          router.push('/mentor/verification?mode=edit');
          break;
        case 'WAIT_FOR_APPROVE':
          router.push('/mentor/status');
          break;
        case 'EXPLORE':
        case 'COMPLETE_MENTOR_PROFILE_OR_EXPLORE':
        case 'SUBMIT_MENTOR_VERIFICATION_OR_EXPLORE':
        default:
          router.push('/dashboard');
          break;
      }
    } catch (err) {
      console.error('Failed to fetch onboarding status:', err);
      router.push('/dashboard');
    }
  };

  return { handlePostLoginRedirect };
}
```
