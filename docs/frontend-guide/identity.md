# Frontend Integration Guide — Identity & Xác Thực (Authentication & Onboarding)

> **Quy tắc ảnh & Avatar:** Với trường `avatarUrl`, Frontend sử dụng nguyên bản URL do backend trả về. URL có thể đến từ Google hoặc CDN SkillSwap; Frontend không tự ghép nối CDN URL từ ID, tên file, `storageKey` hoặc `objectKey`. `STORAGE_PUBLIC_URL_PREFIX` là cấu hình nội bộ của backend, không phải biến `.env` của Frontend.

---

## 1. Kiến Trúc Bảo Mật & Nguyên Tắc Chung (Security & Architecture)

### 1.1 Cấu Trúc Envelope Chuẩn Của API Response (`ApiResponse<T>`)
Tất cả các API của SkillSwap đều trả về một envelope chuẩn dạng JSON:

```typescript
interface ApiResponse<T> {
  timestamp: string;          // ISO-8601, ví dụ: "2026-06-22T14:20:25Z"
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
> - Khi API trả lỗi, Frontend đọc `X-Correlation-ID` từ response header để hiển thị/mở ticket hỗ trợ. Header này được CORS expose; `X-Request-Id` là alias tương thích cũ. Chi tiết vận hành xem [error-contract-operations.md](../architecture/error-contract-operations.md).

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
| `GET /api/auth/google/nonce` | 60 lần / 10 phút |
| `POST /api/auth/google` | 60 lần / 10 phút |
| `POST /api/auth/refresh` | 40 lần / 10 phút |
| `POST /api/auth/logout` | Không giới hạn riêng |

Khi nhận lỗi `429 Too Many Requests`, đọc trường `retryAfterSeconds` để vô hiệu hóa nút bấm hoặc hiển thị đồng hồ đếm ngược.

---

## 2. Luồng Đăng Nhập Google Identity Services (GIS)

Đăng nhập dùng **GIS ID Token** để hỗ trợ popup, nút Google và One Tap. Luồng này không dùng authorization code, callback route, `state`, PKCE hoặc Google Client Secret ở Frontend.

```text
FE Next.js (Browser)             Spring Boot Backend               Google GIS
    │                                  │                               │
    ├── 1. GET /api/auth/google/nonce ►│                               │
    │◄── nonce + expiresAt ────────────┤                               │
    │                                  │                               │
    ├── 2. Khởi tạo GIS với nonce ────────────────────────────────────►│
    │◄── 3. credential (Google ID Token) ─────────────────────────────┤
    │                                  │                               │
    ├── 4. POST /api/auth/google ─────►│                               │
    │      { credential, nonce }       │ xác minh token + dùng nonce   │
    │◄── accessToken + refresh cookie ┤                               │
```

### Bước 1: Lấy nonce dùng một lần

- **Endpoint**: `GET /api/auth/google/nonce`
- **Authentication**: không cần Bearer token.
- Không có query parameter.
- Nonce chỉ dùng cho một lần đăng nhập và hết hạn sau thời gian ngắn.

```typescript
interface GoogleLoginNonceResponse {
  nonce: string;
  expiresAt: string; // ISO-8601 UTC
}
```

### Bước 2: Khởi tạo GIS bằng đúng nonce

Load thư viện GIS từ `https://accounts.google.com/gsi/client`, sau đó truyền nguyên nonce Backend vừa cấp vào `google.accounts.id.initialize`.

```typescript
const nonceResponse = await apiClient.get('/api/auth/google/nonce');
const { nonce } = nonceResponse.data;

google.accounts.id.initialize({
  client_id: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID!,
  nonce,
  callback: async ({ credential }) => {
    await completeGoogleLogin(credential, nonce);
  },
});

// Dùng một trong hai cách tùy UI:
google.accounts.id.renderButton(buttonElement, {
  theme: 'outline',
  size: 'large',
});
// Hoặc gọi google.accounts.id.prompt() cho One Tap.
```

`NEXT_PUBLIC_GOOGLE_CLIENT_ID` phải đúng cùng OAuth Web Client ID với `GOOGLE_CLIENT_ID` của Backend. Frontend không được chứa `GOOGLE_CLIENT_SECRET`.

### Bước 3: Gửi credential về Backend

- **Endpoint**: `POST /api/auth/google`
- **Request Body**:

```typescript
interface GoogleLoginRequest {
  credential: string; // Google ID Token do callback GIS trả về
  nonce: string;      // Đúng nonce đã dùng khi initialize GIS
}
```

```typescript
async function completeGoogleLogin(credential: string, nonce: string) {
  const response = await apiClient.post('/api/auth/google', {
    credential,
    nonce,
  });

  setAccessToken(response.data.accessToken);
  // Xóa credential và nonce khỏi state tạm ngay sau request.
}
```

Backend xác minh chữ ký, thời hạn, audience, issuer, `email_verified` và nonce trong ID Token. Nonce được tiêu thụ nguyên tử sau khi token hợp lệ; gửi lại cùng credential/nonce sẽ bị từ chối.

> [!WARNING]
> - Không decode ID Token ở Frontend rồi tự tin dữ liệu bên trong là hợp lệ. Backend là nơi duy nhất quyết định token có hợp lệ hay không.
> - Không lưu `credential` hoặc nonce vào `localStorage`, cookie hay database Frontend. Chỉ giữ tạm trong memory cho request hiện tại.
> - Khi request thất bại do nonce hết hạn hoặc đã dùng, lấy nonce mới và khởi tạo lại GIS. Không retry lại cặp cũ.
> - ID Token này chỉ dùng để đăng nhập SkillSwap. Tuyệt đối không gửi nó vào API kết nối Google Calendar.

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

- Login dùng GIS ID Token; không thêm scope Calendar vào cấu hình GIS.
- Không gọi API Calendar sau khi user đăng nhập hoặc hoàn thành onboarding.
- Chỉ mentor đã được Admin duyệt mới kết nối Calendar khi chuẩn bị tạo service.
- Calendar dùng một Authorization Code flow riêng với `state` + PKCE để Backend nhận refresh token. Không tái sử dụng ID Token đăng nhập.
- Toàn bộ flow Calendar dành cho FE nằm trong [mentor-service.md](mentor-service.md).
- `googleCalendarConnected` trong `GET /api/auth/me` chỉ là trạng thái hiển thị; không dùng nó thay cho bước kiểm tra mới trước khi tạo service.

---

## 7. Bảng Mã Lỗi Thường Gặp (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho Frontend |
|---|---|---|
| `400` | `VAL_3001` | Form dữ liệu không hợp lệ. Hiển thị thông báo lỗi dưới từng trường. |
| `400` | `AUTH_1006` | Google ID Token hoặc nonce không hợp lệ, đã dùng hay hết hạn. Lấy nonce mới rồi mở lại GIS; không retry credential cũ. |
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
