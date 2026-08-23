# Frontend Guide — Tìm Kiếm Mentor & Hồ Sơ Công Khai (Mentor Discovery & Public Profile)

> **Quy tắc ảnh & CDN:** Frontend chỉ sử dụng trực tiếp các URL hình ảnh nhận được từ response của backend. Tuyệt đối không tự ghép nối `https://cdn.skillswap.asia` với ID, tên file, `storageKey` hoặc `objectKey`. `STORAGE_PUBLIC_URL_PREFIX` là cấu hình nội bộ của backend, không phải biến `.env` của Frontend.

> **Chuẩn Envelope:** Tất cả phản hồi từ backend đều được bọc trong `ApiResponse<T>`. Vui lòng xem [identity.md](identity.md) để biết cấu trúc envelope, cách xử lý validation và mã lỗi `429 Retry-After`.

> **Tài liệu liên quan:**
> - Guide này dành cho màn hình công khai và Mentee: tìm kiếm mentor, xem profile, đánh giá (reviews), bài viết blog và chọn khung giờ để bắt đầu đặt lịch.
> - Nếu đang làm giao diện cho Mentor quản lý dịch vụ và lịch rảnh: xem [mentor-service.md](mentor-service.md).
> - Nếu đang làm quy trình đăng ký (onboarding) và Admin duyệt mentor: xem [mentor-verification.md](mentor-verification.md).

---

## 1. Phân Quyền & Luồng Trải Nghiệm Người Dùng (Journey)

| Thao tác | Ai được phép gọi API |
|---|---|
| Xem danh sách mentor, profile chi tiết, đánh giá và lịch xem trước công khai | Mọi người (Public, không cần đăng nhập) |
| Xem phân đoạn khung giờ chính xác (Candidate Segments) để đặt lịch | Người dùng đã đăng nhập (`MENTEE` hoặc `MENTOR`) |
| Lấy danh sách gợi ý mentor được cá nhân hóa (Recommendations) | Người dùng đã đăng nhập (`MENTEE` hoặc `MENTOR`) |

```text
Danh sách Discovery (Duyệt/Tìm kiếm)
 ➔ Chi tiết Mentor (Profile, Bio, Minh chứng, Dịch vụ)
 ➔ Xem trước lịch rảnh công khai (Public Availability Preview)
 ➔ Đăng nhập tài khoản (nếu chưa đăng nhập)
 ➔ Tải lại lịch rảnh đã xác thực và danh sách Candidate chính xác
 ➔ Tiến hành tạo Booking theo hướng dẫn trong booking_1T1.md
```

> [!WARNING]
> **Không dùng dữ liệu preview cũ để tạo booking**: Sau khi người dùng đăng nhập, Frontend bắt buộc phải gọi lại API lịch rảnh đã xác thực để lấy `slotId` và `candidate` mới nhất.

---

## 2. Tìm Kiếm & Lọc Mentor (Discovery Search APIs)

API `GET /api/mentors` là **công khai (Public)**. Có thể đính kèm Bearer token nếu người dùng đã đăng nhập để backend tính toán thêm điểm phù hợp (matching context); tuy nhiên token không bắt buộc để xem danh sách.

- **Endpoint**: `GET /api/mentors`
- **Query Parameters**:

| Tham số | Kiểu dữ liệu | Mặc định | Ghi chú |
|---|---|---|---|
| `page` | number | `0` | Trang bắt đầu từ `0` |
| `size` | number | `12` | Kích thước trang (Backend giới hạn từ `1` đến `30`) |
| `keyword` | string | - | Tìm kiếm theo họ tên, headline, bio, môn học, dự án và thành tích |
| `campusId` | UUID | - | Lọc theo cơ sở đào tạo (FPTU Campus) |
| `specializationId` | UUID | - | Lọc theo chuyên ngành học |
| `sortBy` | string | `relevance` | Các tùy chọn: `relevance`, `ratingAverage`, `reviewCount`, `completedSessions`, `updatedAt` |
| `direction` | `"ASC"` \| `"DESC"` | `DESC` | Áp dụng cho các kiểu sắp xếp khác `relevance` |

- **Cấu trúc Response**: `ApiResponse<PageResponse<MentorDiscoveryCardResponse>>`

```typescript
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

interface MentorDiscoveryCardResponse {
  identity: {
    mentorUserId: string;
    displayName: string;
    avatarUrl: string | null;
    headline: string | null;
    isVerified: boolean;
    verifiedAt: string | null;
  };
  mentoring: {
    expertiseDescription: string | null;
    foundationSupportLevel: number | null;
    outputReviewSupportLevel: number | null;
    directionSupportLevel: number | null;
  };
  evidence: {
    campusId: string | null;
    campusName: string | null;
    programId: string | null;
    programName: string | null;
    specializationId: string | null;
    specializationName: string | null;
    subjectHighlights: MentorSubjectResult[]; // Tối đa 2 item nổi bật
    featuredProjects: MentorFeaturedProject[]; // Tối đa 2 item nổi bật
    achievements: MentorAchievement[];         // Tối đa 2 item nổi bật
  };
  reputation: {
    ratingState: "NO_REVIEWS" | "RATED";
    ratingAverage: number | null;
    reviewCount: number;
    completedSessions: number;
  };
  availability: {
    isAvailable: boolean;
  };
  match: {
    score: number | null;
  };
}
```

> [!NOTE]
> - Nếu `ratingState === "NO_REVIEWS"`, trường `ratingAverage` luôn là `null`. Frontend **không tự ý hiển thị mặc định 5.0**.
> - `match.score` có thể là `null`, đặc biệt khi người dùng chọn sort khác `relevance`. Không coi score là trường bắt buộc phải có để render card.
> - Tất cả mentor xuất hiện trong kết quả discovery đều đã được xác thực (`isVerified = true`), có profile hợp lệ và sở hữu ít nhất một dịch vụ `ONE_TO_ONE` đang mở (`isActive = true`).

---

## 3. Xem Chi Tiết Hồ Sơ Mentor (Mentor Detail API)

- **Endpoint**: `GET /api/mentors/{mentorUserId}`
- **Quyền hạn**: Công khai (Public)
- **Response**: `ApiResponse<MentorDiscoveryDetailResponse>`

```typescript
interface MentorDiscoveryDetailResponse {
  identity: {
    mentorUserId: string;
    displayName: string;
    avatarUrl: string | null;
    headline: string | null;
    isVerified: boolean;
    verifiedAt: string | null;
  };
  mentoring: {
    bio: string | null;
    expertiseDescription: string | null;
    supportLevels: {
      foundation: number | null;     // Mức độ hỗ trợ kiến thức nền tảng (1-4)
      outputReview: number | null;   // Mức độ hỗ trợ review bài tập/sản phẩm (1-4)
      direction: number | null;      // Mức độ định hướng chuyên môn/nghề nghiệp (1-4)
    };
  };
  services: MentorServiceResponse[];
  evidence: {
    education: {
      campusId: string | null;
      campusName: string | null;
      programId: string | null;
      programName: string | null;
      specializationId: string | null;
      specializationName: string | null;
      semester: number | null;
      alumni: boolean | null;
    };
    subjectResults: MentorSubjectResult[];
    featuredProjects: MentorFeaturedProject[];
    achievements: MentorAchievement[];
    portfolioUrl: string | null;
    githubUrl: string | null;
    authorityContent: {
      publishedArticleCount: number;
      latestPublishedAt: string | null;
      recentPublicArticles: MentorPublicArticlePreview[];
    };
  };
  reputation: {
    ratingState: "NO_REVIEWS" | "RATED";
    ratingAverage: number | null;
    reviewCount: number;
    completedSessions: number;
  };
  availability: {
    isAvailable: boolean | null;
    suspendedUntil: string | null;
    canRequestBooking: boolean;
  };
}

interface MentorServiceResponse {
  serviceId: string;
  mentorUserId: string;
  title: string;
  description: string;
  expectedOutcome: string;
  durationMinutes: number;
  isFree: boolean;
  priceScoin: number;
  isActive: boolean;
  maintainPostSessionChat: boolean;
  deliveryMode: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

interface MentorPublicArticlePreview {
  id: string;
  title: string;
  slug: string;
  excerpt: string;
  coverImageUrl: string | null;
  readingTimeMinutes: number;
  publishedAt: string;
}

interface MentorSubjectResult {
  id: string;
  subjectCode: string;
  subjectName: string | null;
  scoreValue: number;
  displayOrder: number;
}

interface MentorFeaturedProject {
  id: string;
  title: string;
  pictureUrl: string | null;
  content: string | null;
  projectDescription: string | null;
  liveDemoUrl: string | null;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

interface MentorAchievement {
  id: string;
  title: string;
  awardDescription: string | null;
  achievedAt: string | null;
  productHeader: string | null;
  productDescription: string | null;
  demoUrl: string | null;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}
```

> [!IMPORTANT]
> - `availability.canRequestBooking`: Cho biết mentor hiện có dịch vụ sẵn sàng nhận đặt lịch hay không. Khi tạo booking thật sự, backend vẫn sẽ kiểm tra lại quota, trạng thái hồ sơ học thuật và lịch trống thực tế.
> - **Bảo mật thông tin cá nhân**: Số điện thoại của mentor là thông tin riêng tư và không bao giờ xuất hiện trong response công khai này.

---

## 4. Đánh Giá & Gợi Ý Cá Nhân Hóa (Reviews & Recommendations)

### 4.1 Danh Sách Đánh Giá của Mentor (`GET /api/mentors/{mentorUserId}/reviews`)
- **Endpoint**: `GET /api/mentors/{mentorUserId}/reviews`
- **Quyền hạn**: Public
- **Query Params**:
  - `page` (number, default: `0`)
  - `size` (number, default: `10`, max: `20`)
  - `sortBy` (`"createdAt"` hoặc `"rating"`, default: `"createdAt"`)
  - `direction` (`"ASC"` hoặc `"DESC"`, default: `"DESC"`)
- **Response**: `ApiResponse<PageResponse<MentorReviewResponse>>`

```typescript
interface MentorReviewResponse {
  reviewId: string;
  reviewerUserId: string;
  reviewerDisplayName: string;
  reviewerAvatarUrl: string | null;
  rating: number;           // Điểm sao (1 đến 5)
  comment: string | null;   // Nhận xét chi tiết
  createdAt: string;
}
```

---

### 4.2 Gợi Ý Mentor Phù Hợp (`GET /api/mentors/recommendations`)
- **Endpoint**: `GET /api/mentors/recommendations`
- **Header**: `Authorization: Bearer <accessToken>` (Bắt buộc đăng nhập)
- **Query Params**: `limit` (number, default: `12`)
- **Response**: `ApiResponse<MentorRecommendationResponse[]>`

Mỗi phần tử trả về gồm thông tin mentor (`mentor`), điểm phù hợp (`matchScore`) và danh sách các lý do đề xuất (`matchReasons`).

---

## 5. Tra Cứu Lịch Trước & Sau Khi Đăng Nhập (Availability Flow)

### 5.1 Xem Trước Lịch Rảnh Công Khai (Public Availability Preview)

- **Endpoint**: `GET /api/mentors/{mentorUserId}/availability-preview`
- **Query Params**: `fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD` (Tùy chọn)

> [!NOTE]
> Cửa sổ xem trước công khai được giới hạn từ **Thứ Hai của tuần hiện tại** đến **Chủ Nhật của tuần tiếp theo** (theo múi giờ `Asia/Ho_Chi_Minh`). Nếu không truyền query param, backend sẽ tự động áp dụng toàn bộ cửa sổ mặc định này.

```typescript
interface MentorPublicAvailabilityPreviewResponse {
  timezone: string; // "Asia/Ho_Chi_Minh"
  isPublicOfferAvailable: boolean;
  nextAvailableAt: string | null;
  slots: Array<{
    startTime: string;
    endTime: string;
    services: Array<{
      serviceId: string;
      title: string;
      durationMinutes: number;
      isFree: boolean;
      priceScoin: number;
    }>;
  }>;
}
```

Response công khai **không chứa `slotId`, hạn mức quota hay trạng thái booking**. Nếu người dùng chọn giờ trước khi đăng nhập, Frontend chỉ lưu tạm thông tin vào state client; sau khi đăng nhập thành công bắt buộc phải gọi API authenticated ở mục 5.2.

---

### 5.2 Chọn Slot & Candidate Chính Xác Sau Khi Đăng Nhập

1. Gọi `GET /api/mentors/{mentorUserId}/availability-slots?fromDate=...&toDate=...` với Bearer token.
2. Người dùng chọn một dịch vụ mong muốn trong `slot.services`.
3. Gọi `GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId={serviceId}`.
4. Chỉ cho phép người dùng bấm chọn các candidate có `isSelectable === true`.
5. Sử dụng candidate vừa tải để tiếp tục quy trình tạo booking.

```typescript
interface MentorAvailabilitySlotResponse {
  slotId: string;
  startTime: string;
  endTime: string;
  timezone: string;
  pendingRequestCount: number;
  acceptedSlotCount: number;
  services: AvailabilitySlotServiceBasicResponse[];
}

interface AvailabilitySlotServiceBasicResponse {
  serviceId: string;
  title: string;
  durationMinutes: number;
  isFree: boolean;
  priceScoin: number;
  bindingRemoval: {
    mode: "ALLOWED" | "REQUIRES_PENDING_REJECTION" | "BLOCKED_BY_LOCKING_BOOKING";
    restrictionCode: string | null;
    affectedPendingBookingCount: number;
  };
}

interface ServiceSlotCandidatesResponse {
  slotId: string;
  serviceId: string;
  serviceDurationMinutes: number;
  candidateServiceSlots: Array<{
    startTime: string;                  // ISO Instant UTC dùng để tạo booking
    endTime: string;
    pendingCount: number;
    remainingPendingQuota: number;
    isSelectable: boolean;              // Bắt buộc true mới được chọn
    reasonIfBlocked: string | null;     // Lý do nếu bị khóa
    blockedByAcceptedBooking: boolean;
    blockingBookingId: string | null;
    blockingServiceId: string | null;
    blockingServiceTitle: string | null;
    blockedBySameService: boolean;
    blockedByDifferentService: boolean;
    bookingConflictNote: string | null;
    blockedByGoogleCalendar?: boolean;
    calendarAvailabilityUnknown?: boolean; // vẫn có thể chọn; mentor accept sẽ kiểm tra lại
  }>;
}
```

> [!TIP]
> - Mã lỗi `404 Not Found`: Mentor, slot hoặc dịch vụ không còn tồn tại.
> - Mã lỗi `409 Conflict`: Khung giờ vừa bị người khác đặt hoặc đã bị khóa.
> Trong cả hai trường hợp, hãy đóng lựa chọn cũ và tải lại lịch khả dụng thay vì retry tự động.

---

## 6. Quản Lý Hồ Sơ Mentor Của Chính Tôi (My Mentor Profile)

Các endpoint `GET /api/me/mentor-profile` và `PUT /api/me/mentor-profile` yêu cầu Bearer token với Role `MENTEE` hoặc `MENTOR`.

- `GET /api/me/mentor-profile` luôn trả `200 OK`. Nếu người dùng chưa từng khởi tạo hồ sơ mentor, trường `data.exists` sẽ là `false` (đây không phải lỗi).
- `requiredFieldsCompleted`: Cờ hỗ trợ giao diện kiểm tra xem các trường bắt buộc đã điền đủ chưa.

```typescript
interface MentorProfileUpsertRequest {
  headline: string;              // 1 - 200 ký tự (Bắt buộc)
  expertiseDescription: string;  // 1 - 1000 ký tự (Bắt buộc)
  isAvailable?: boolean;
  subjectResults: Array<{
    subjectCode: string;         // Tối đa 80 ký tự, không trùng lặp trong cùng request
    subjectName?: string;        // Tối đa 200 ký tự
    scoreValue: number;          // Điểm số từ 0 đến 10
  }>;                            // 1 đến 20 môn học
  foundationSupportLevel: 1 | 2 | 3 | 4;   // Mức hỗ trợ kiến thức nền tảng
  outputReviewSupportLevel: 1 | 2 | 3 | 4; // Mức hỗ trợ review sản phẩm
  directionSupportLevel: 1 | 2 | 3 | 4;    // Mức hỗ trợ định hướng nghề nghiệp
  githubUrl?: string | null;
  portfolioUrl?: string | null;
  phoneNumber: string;           // 10 chữ số định dạng Việt Nam (03/05/07/08/09...)
  minimumBookingLeadTimeMinutes?: number;
  maximumBookingHorizonDays?: number;
  bookingTimezone?: string;
}

interface MentorProfileResponse {
  exists: boolean;
  requiredFieldsCompleted: boolean;
  userId: string;
  email: string | null;
  displayName: string | null;
  avatarUrl: string | null;
  mentorStatus: string | null;
  headline: string | null;
  expertiseDescription: string | null;
  isAvailable: boolean | null;
  bookingSuspendedUntil: string | null;
  lateCancellationPenaltyPoints: number;
  verifiedAt: string | null;
  minimumBookingLeadTimeMinutes: number;
  maximumBookingHorizonDays: number;
  bookingTimezone: string;
  subjectResults: MentorSubjectResult[];
  foundationSupportLevel: number | null;
  outputReviewSupportLevel: number | null;
  directionSupportLevel: number | null;
  featuredProjects: MentorFeaturedProject[];
  achievements: MentorAchievement[];
  githubUrl: string | null;
  portfolioUrl: string | null;
  phoneNumber: string | null;
  ratingAverage: number | null;
  reviewCount: number;
  completedSessions: number;
  createdAt: string | null;
  updatedAt: string | null;
}
```

---

### 6.1 Quản Lý Dự Án Nổi Bật & Thành Tích (Projects & Achievements)

| Mục đích | Endpoint |
|---|---|
| Lấy danh sách / Tạo mới dự án | `GET`, `POST /api/me/mentor-projects` (Hỗ trợ `pictureAssetId` tùy chọn) |
| Cập nhật / Xóa dự án | `PUT`, `DELETE /api/me/mentor-projects/{projectId}` |
| Tạo Upload Intent cho ảnh dự án | `POST /api/me/mentor-projects/picture/upload-intents` hoặc `POST /api/me/mentor-projects/{projectId}/picture/upload-intents` (`PublicAssetUploadIntentRequest`) |
| Xác nhận ảnh dự án sau khi PUT lên R2 | `POST /api/me/mentor-projects/{projectId}/picture/confirm` (`{ uploadIntentId }`) |
| Gỡ ảnh khỏi dự án | `DELETE /api/me/mentor-projects/{projectId}/picture` |
| Lấy danh sách / Tạo mới thành tích | `GET`, `POST /api/me/mentor-achievements` (Hỗ trợ `pictureAssetId` tùy chọn) |
| Cập nhật / Xóa thành tích | `PUT`, `DELETE /api/me/mentor-achievements/{achievementId}` |
| Tạo Upload Intent cho ảnh thành tích | `POST /api/me/mentor-achievements/picture/upload-intents` hoặc `POST /api/me/mentor-achievements/{achievementId}/picture/upload-intents` (`PublicAssetUploadIntentRequest`) |
| Xác nhận ảnh thành tích sau khi PUT lên R2 | `POST /api/me/mentor-achievements/{achievementId}/picture/confirm` (`{ uploadIntentId }`) |
| Gỡ ảnh khỏi thành tích | `DELETE /api/me/mentor-achievements/{achievementId}/picture` |

- **Project**: Trường `title` là bắt buộc; các trường `content`, `projectDescription`, `liveDemoUrl`, `pictureAssetId` là tùy chọn.
- **Achievement**: Trường `title` là bắt buộc; các trường `awardDescription`, `achievedAt`, `productHeader`, `productDescription`, `demoUrl`, `pictureAssetId` là tùy chọn.
- **Quy trình Upload ảnh Dự án & Thành tích**:
  1. Gọi `POST /api/me/mentor-projects/picture/upload-intents` (hoặc `POST /api/me/mentor-achievements/picture/upload-intents`) với body `{ filename, contentType }` (chấp nhận JPG, PNG, WebP; tối đa 5MB).
  2. Client gửi HTTP `PUT` binary file trực tiếp lên `uploadUrl` của Cloudflare R2.
  3. Gọi `POST .../{id}/picture/confirm` với `{ uploadIntentId }` để hoàn tất và nhận `pictureUrl` hiển thị.

---

## 7. Xử Lý Lỗi (Error Handling)

| HTTP Status | Hướng xử lý cho Frontend |
|---|---|
| `400 Bad Request` | Hiển thị thông báo lỗi hoặc lỗi validation theo từng ô nhập liệu. Không tự động retry. |
| `401 Unauthorized` | Thực hiện quy trình Refresh Token theo [identity.md](identity.md); nếu thất bại thì chuyển về trang Đăng nhập. |
| `403 Forbidden` | Người dùng không có quyền truy cập tính năng này. Ẩn nút thao tác. |
| `404 Not Found` | Mentor, slot hoặc dịch vụ không còn tồn tại hoặc đã ngừng công khai. Quay lại trang danh sách. |
| `409 Conflict` | Dữ liệu vừa bị thay đổi hoặc khung giờ đã bị khóa. Tải lại trang/dữ liệu trước khi cho người dùng thử lại. |
| `429 Too Many Requests` | Đọc trường `retryAfterSeconds` từ response, khóa nút thao tác và hiển thị đếm ngược thời gian chờ. |
