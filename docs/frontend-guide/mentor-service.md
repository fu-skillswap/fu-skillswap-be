# Frontend Guide — Quản Lý Dịch Vụ & Lịch Rảnh Mentor (Mentor Services & Availability)

> **Quy tắc URL:** Chỉ sử dụng trực tiếp các URL hình ảnh do backend trả về. Tuyệt đối không tự ghép nối URL CDN, `storageKey` hay `objectKey`. Xem [identity.md](identity.md) để biết cấu trúc envelope chung, cơ chế refresh token và cách xử lý mã lỗi `429 Retry-After`.

> **Tài liệu liên quan:**
> - Tài liệu này dành cho giao diện quản trị của Mentor (đã có Role `MENTOR`): quản lý dịch vụ 1:1 (`ONE_TO_ONE`), chính sách đặt lịch (Booking Policy), lịch rảnh trực tiếp (Direct Slots) và mẫu lịch lặp tuần (Availability Templates).
> - Xem [mentor-discovery.md](mentor-discovery.md) để biết cách hiển thị thẻ/profile công khai và cách Mentee chọn khung giờ.
> - Xem [mentor-verification.md](mentor-verification.md) để nắm quy trình xét duyệt hồ sơ mentor.
> - Xem [booking_1T1.md](booking_1T1.md) để biết cách xử lý accept booking kèm tùy chọn meeting link.

---

## 1. Điều Kiện & Luồng Vận Hành (Eligibility & Flow)

Tất cả các endpoint trong tài liệu này yêu cầu Bearer token với Role `MENTOR`.

```text
Hồ sơ Mentor đã được Admin phê duyệt (Role MENTOR)
 ➔ [Tùy chọn] Kết nối Google Calendar bằng OAuth PKCE
 ➔ Tạo Dịch vụ 1:1 đang hoạt động (Active ONE_TO_ONE Service)
 ➔ Tạo Lịch rảnh trực tiếp (Direct Slot) hoặc Mẫu lịch lặp tuần (Weekly Template)
 ➔ Mentor đủ điều kiện xuất hiện trên trang Tìm kiếm công khai (Discovery)
 ➔ Mentee lựa chọn khung giờ và tiến hành Đặt lịch (Booking)
```

> [!NOTE]
> - Việc được duyệt hồ sơ xác thực (Verification) không tự động tạo sẵn dịch vụ hay lịch rảnh.
> - **Google Calendar là Tùy chọn (Optional)**: Mentor có thể chọn kết nối Google Calendar hoặc không khi tạo và kích hoạt dịch vụ.
> - **Nếu Mentor ĐÃ kết nối Google Calendar**: Hệ thống tự động đồng bộ lịch và sinh Google Meet link khi mentor chấp nhận booking.
> - **Nếu Mentor CHƯA kết nối Google Calendar**: Mentor vẫn tạo/kích hoạt dịch vụ và mở slot bình thường. Khi chấp nhận booking, mentor sẽ chủ động nhập link phòng học (Google Meet, Zoom, Discord, MS Teams, Offline...).

---

## 2. Kết Nối Google Calendar (Tùy Chọn)

### 2.1 Các endpoint FE cần dùng

| Endpoint | Khi nào gọi |
|---|---|
| `GET /api/me/google-calendar/status` | Khi mở trang quản lý service / cài đặt để hiển thị trạng thái kết nối |
| `GET /api/me/google-calendar/authorization-context?redirectUri=...&codeChallenge=...` | Sau khi mentor bấm “Kết nối Google Calendar” |
| `POST /api/me/google-calendar/connect` | Tại trang callback sau khi Google trả `code` và `state` |
| `POST /api/me/google-calendar/disconnect` | Khi mentor chủ động ngắt kết nối Google Calendar |

Tất cả endpoint trên yêu cầu Bearer token. Endpoint tạo context và connect chỉ chấp nhận mentor đã được Admin duyệt.

```typescript
interface GoogleCalendarStatusResponse {
  connected: boolean;
  syncEnabled: boolean;
  email: string | null;
  grantedScopes: string[];
  needsReconnect: boolean;
  lastSyncStatus: string | null;
  lastSyncAt: string | null;
  lastSyncErrorCode: string | null;
  lastSyncErrorMessage: string | null;
}
```

### 2.2 Flow OAuth Calendar riêng

1. FE sinh `codeVerifier` và `codeChallenge = BASE64URL(SHA256(codeVerifier))`.
2. Lưu `codeVerifier` vào `sessionStorage`; không lưu access token Google.
3. Gọi `GET /api/me/google-calendar/authorization-context` với callback đúng cấu hình, ví dụ `https://skillswap.asia/vi/mentor/google-calendar/callback`.
4. Chuyển mentor sang Google với scope `openid email profile https://www.googleapis.com/auth/calendar`, kèm `state` và PKCE challenge.
5. Ở callback, so sánh `state` nhận về với state đang chờ, rồi gọi:

```typescript
interface GoogleCalendarConnectRequest {
  authorizationCode: string;
  redirectUri: string;
  codeVerifier: string;
  state: string;
}
```

6. Khi connect thành công, xóa `codeVerifier` và state tạm, gọi lại `GET /api/me/google-calendar/status` để cập nhật UI.

> [!WARNING]
> State của đăng nhập và state của Calendar không dùng chung. Mỗi state chỉ dùng một lần, gắn với đúng mentor, callback và PKCE verifier. Nếu callback bị refresh sau khi đã connect, FE không tự retry code cũ mà bắt đầu flow kết nối mới.

### 2.3 Trạng thái UI và lỗi nghiệp vụ

| HTTP / code | FE cần làm |
|---|---|
| `409 / CAL_4402` | Hồ sơ mentor chưa được duyệt. Điều hướng về trang trạng thái verification. |
| `409 / CAL_4403` | Không thể disconnect vì còn booking `PAID` trong tương lai đang dùng Calendar. Giữ kết nối đến khi các lịch này kết thúc. |
| `400 / AUTH_1006` | State, PKCE, callback hoặc authorization code không hợp lệ/hết hạn. Bắt đầu flow mới. |

---

## 3. Quản Lý Dịch Vụ Mentoring (Service Management)

1. Gọi `GET /api/me/mentor-services/constraints` trước khi mở form tạo mới để lấy các ràng buộc về thời lượng (`durationMinutes`: 30, 60, 90, 120) và khoảng giá Scoin do nền tảng quy định (tối thiểu 500 SCoin/phút, tương ứng 30p = 15.000, 60p = 30.000, 90p = 45.000, 120p = 60.000).
2. Tạo dịch vụ mới qua `POST /api/me/mentor-services`.
3. Sử dụng `GET /api/me/mentor-services?isActive=true|false` để quản lý danh sách dịch vụ của mình (bỏ query `isActive` để lấy toàn bộ).
4. Sử dụng `PUT /api/me/mentor-services/{serviceId}` để chỉnh sửa và `PATCH /api/me/mentor-services/{serviceId}/active` để bật/tắt trạng thái hoạt động.

```typescript
interface CreateMentorServiceRequest {
  title: string;                    // 1 - 200 ký tự (Bắt buộc)
  description: string;              // 1 - 1000 ký tự (Bắt buộc)
  expectedOutcome: string;          // 1 - 1000 ký tự (Bắt buộc)
  durationMinutes: number;          // Bắt buộc, thuộc danh sách allowedDurationMinutes (30, 60, 90, 120)
  isFree: boolean;                  // true: miễn phí, false: có phí
  priceScoin: number;               // Giá Scoin (0 nếu isFree = true; tối thiểu 500 Scoin/phút nếu có phí)
  maintainPostSessionChat?: boolean;// Duy trì phòng chat sau buổi học
  deliveryMode?: string;            // Mặc định "ONE_TO_ONE"
}

interface UpdateMentorServiceRequest {
  title: string;
  description: string;
  expectedOutcome: string;
  isFree: boolean;
  priceScoin: number;
  maintainPostSessionChat?: boolean;
  expectedVersion: number;          // Optimistic locking version
}

interface MentorServiceActiveRequest {
  isActive: boolean;
  expectedVersion: number;
  rejectPendingBookings?: boolean;
  pendingRejectionToken?: string;
}

interface MentorServiceManagementResponse {
  serviceId: string;
  mentorUserId: string;
  title: string;
  description: string;
  expectedOutcome: string;
  durationMinutes: number;
  isFree: boolean;
  basePriceScoin: number;             // Giá mentor đặt
  publicPriceScoin: number;           // Giá mentee nhìn thấy
  estimatedMentorPayoutScoin: number; // Payout dự kiến
  isActive: boolean;
  maintainPostSessionChat: boolean;
  deliveryMode: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}
```

> [!IMPORTANT]
> - `durationMinutes` và `deliveryMode` không thể chỉnh sửa sau khi dịch vụ đã được tạo. Hãy hiển thị các trường này ở dạng chỉ đọc (Read-only) trong form cập nhật.
> - Khi nhận mã lỗi `409 Conflict`, hãy tải lại dữ liệu dịch vụ mới nhất và yêu cầu mentor xác nhận lại thay đổi; **không tự ý gửi lại `expectedVersion` cũ**.
> - Không tự tạo chuỗi `pendingRejectionToken`. Nếu backend yêu cầu xác nhận hủy các booking đang chờ duyệt (Pending), hãy hiển thị dialog xác nhận kèm thông tin conflict mà backend trả về và gửi kèm token đó trong lần gọi tiếp theo.

---

## 4. Chính Sách Đặt Lịch & Ràng Buộc Hệ Thống (Booking Policy & Constraints)

| Endpoint | Mục đích |
|---|---|
| `GET /api/me/mentor-booking-policy` | Đọc thời gian báo trước tối thiểu (Lead Time), thời hạn đặt lịch tối đa (Horizon) và múi giờ hiện tại |
| `PATCH /api/me/mentor-booking-policy` | Chỉnh sửa chính sách đặt lịch của mentor |
| `GET /api/me/mentor-scheduling-constraints` | Lấy các giới hạn cấu hình của nền tảng trước khi mở form tạo lịch |

Cập nhật chính sách đặt lịch bắt buộc phải gửi kèm trường `expectedVersion`. Frontend không hard-code các giới hạn thời gian mà luôn lấy từ API scheduling constraints.

```typescript
interface UpdateMentorBookingPolicyRequest {
  minimumBookingLeadTimeMinutes?: number;
  maximumBookingHorizonDays?: number;
  timezone?: string;
  expectedVersion: number;
}
```

---

## 5. Lịch Rảnh Trực Tiếp (Direct Availability Slots)

Lịch rảnh trực tiếp (Direct Slot) là các khung giờ rảnh dùng một lần.
- Thời gian gửi lên request là chuỗi **ISO-8601** (chấp nhận cả UTC `2026-06-29T01:00:00Z` hoặc offset địa phương `2026-06-29T08:00:00+07:00`). Backend tự động chuẩn hóa (auto-truncate) về phút tròn (`:00.000Z`), FE không lo dính lỗi do mili-giây lẻ từ datepicker.
- Giao diện người dùng hiển thị theo múi giờ (`Asia/Ho_Chi_Minh`) nhận được từ backend.

| Endpoint | Mục đích |
|---|---|
| `POST /api/me/availability-slots` | Tạo slot rảnh dùng một lần |
| `GET /api/me/availability-slots?isActive=&fromDate=&toDate=` | Lấy danh sách slot trực tiếp do mentor quản lý |
| `PUT /api/me/availability-slots/{slotId}` | Chỉnh sửa slot trực tiếp (khi chưa bị khóa bởi booking) |
| `POST /api/me/availability-slots/{slotId}/deactivate` | Hủy/thu hồi slot rảnh |

```typescript
interface CreateAvailabilitySlotRequest {
  startAt: string;                       // Chuỗi ISO Instant UTC hoặc ISO Offset
  endAt: string;                         // Chuỗi ISO Instant UTC hoặc ISO Offset
  note?: string;                         // Ghi chú, tối đa 200 ký tự
  serviceIds: string[];                  // Danh sách UUID dịch vụ áp dụng cho slot này
  replaceGeneratedOccurrences?: boolean; // Cho phép ghi đè lên slot tự động sinh từ template
  rejectPendingBookings?: boolean;
  expectedTemplateVersions?: Array<{
    templateId: string;
    expectedVersion: number;
  }>;
}

interface UpdateAvailabilitySlotRequest extends CreateAvailabilitySlotRequest {
  expectedVersion: number;
  pendingRejectionToken?: string;
}

interface DeactivateAvailabilitySlotRequest {
  expectedVersion: number;
  rejectPendingBookings?: boolean;
  pendingRejectionToken?: string;
  expectedTemplateVersion?: number;
}
```

> [!WARNING]
> Nếu slot trực tiếp bị trùng lặp thời gian với khung giờ đã sinh từ template, backend có thể trả về lỗi `409 GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED`. Frontend cần hiển thị hộp thoại xác nhận ghi đè, tải lại version template hiện tại và gửi lại request với `replaceGeneratedOccurrences = true` kèm danh sách `expectedTemplateVersions`.

---

## 6. Mẫu Lịch Lặp Tuần (Availability Templates)

Template là lịch lặp cố định theo các thứ trong tuần sử dụng múi giờ `Asia/Ho_Chi_Minh`. Backend sẽ tự động sinh (materialize) thành các slot rảnh cụ thể trong **28 ngày tới (~4 tuần)**. Không dùng trực tiếp template để đặt lịch.

| Endpoint | Mục đích |
|---|---|
| `POST /api/me/availability-templates` | Tạo mới mẫu lịch lặp |
| `GET /api/me/availability-templates` | Danh sách template theo phân trang Cursor |
| `GET /api/me/availability-templates/{templateId}` | Xem chi tiết template |
| `PUT /api/me/availability-templates/{templateId}` | Chỉnh sửa template |
| `POST /api/me/availability-templates/{templateId}/pause` | Tạm dừng sinh lịch tự động |
| `POST /api/me/availability-templates/{templateId}/resume` | Tiếp tục sinh lịch tự động |
| `POST /api/me/availability-templates/{templateId}/archive` | Lưu trữ / Hủy bỏ template |
| `PUT /api/me/availability-templates/{templateId}/exceptions/{occurrenceDate}` | Thêm ngày ngoại lệ (bỏ qua không sinh slot vào ngày cụ thể `YYYY-MM-DD`) |
| `POST /api/me/availability-templates/{templateId}/exceptions/{occurrenceDate}/restore` | Khôi phục lại ngày đã bỏ qua |

```typescript
interface AvailabilityTemplateResponse {
  templateId: string;
  startTime: string;            // LocalTime định dạng HH:mm:ss (vd: "09:00:00")
  endTime: string;              // LocalTime định dạng HH:mm:ss
  weekdays: string[];           // ["MONDAY", "WEDNESDAY", "FRIDAY",...]
  effectiveFrom: string;        // LocalDate định dạng YYYY-MM-DD
  effectiveTo: string | null;   // LocalDate hoặc null nếu lặp vô thời hạn
  timezone: string;             // "Asia/Ho_Chi_Minh"
  note: string | null;
  configuredStatus: "ACTIVE" | "PAUSED" | "ARCHIVED";
  effectiveStatus: "ACTIVE" | "PAUSED" | "EXPIRED" | "ARCHIVED";
  configVersion: number;
  services: AvailabilitySlotServiceBasicResponse[];
  generationBlockedReason: string | null;
  skippedDates: string[];       // Danh sách ngày ngoại lệ do mentor chủ động bỏ qua
  blockedOccurrences: Array<{
    date: string;
    reason: string;
    slotId: string | null;
  }>;
  createdAt: string;
  updatedAt: string;
}
```

> [!TIP]
> - `skippedDates`: Danh sách các ngày cụ thể mà mentor chủ động bấm nghỉ/ngoại lệ.
> - `blockedOccurrences`: Danh sách ngày mà hệ thống không thể tự sinh slot do bị trùng với một slot trực tiếp (manual slot) đã tồn tại từ trước.

---

## 7. Tra Cứu Candidate Segments & Lựa Chọn Khung Giờ

Khi Mentee chọn một Slot rảnh và một Service cụ thể, Frontend gọi API:
`GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/services/{serviceId}/candidates`

Backend sẽ trả về **toàn bộ timeline các segment candidate** được chia nhỏ theo `durationMinutes` của service trong slot, kèm theo cờ `isSelectable` và lý do nếu bị khóa.

```typescript
interface ServiceSlotCandidateItemResponse {
  startTime: string;                  // ISO UTC: "2026-06-29T08:00:00Z"
  endTime: string;                    // ISO UTC: "2026-06-29T09:00:00Z"
  pendingCount: number;               // Số request PENDING hiện có trên segment này (tối đa 3)
  remainingPendingQuota: number;      // Quota còn lại = max(0, 3 - pendingCount)
  isSelectable: boolean;              // true: cho phép click chọn; false: disable/xám ô
  reasonIfBlocked: string | null;     // Mã lý do chặn
  blockedByAcceptedBooking: boolean;  // true nếu đã có booking đã được chốt slot
  blockedBySameService: boolean;      // true nếu booking chốt slot thuộc cùng service
  blockedByDifferentService: boolean; // true nếu booking chốt slot thuộc service khác
  bookingConflictNote: string | null; // Tooltip giải thích chi tiết cho UI
}
```

### Các Mã `reasonIfBlocked` & Hướng Dẫn Hiển Thị UI:

| `reasonIfBlocked` | Ý nghĩa | Trạng thái hiển thị trên FE |
|---|---|---|
| `null` (`isSelectable: true`) | Khung giờ hoàn toàn khả dụng | Render ô màu xanh / cho phép bấm đặt lịch |
| `"Segment này đã bắt đầu hoặc đã trôi qua"` | Khung giờ trong quá khứ | Render ô xám (disabled), tooltip: *"Khung giờ đã trôi qua"* |
| `"Yêu cầu đặt trước tối thiểu"` | Vi phạm Lead Time của mentor (vd: đặt trước < 2 giờ) | Render ô xám (disabled), tooltip: `bookingConflictNote` (vd: *"Khung giờ này yêu cầu đặt trước tối thiểu 2 giờ"*) |
| `"Vượt quá thời hạn mở lịch cho phép"` | Vi phạm Horizon của mentor (vd: đặt trước > 30 ngày) | Render ô xám (disabled), tooltip: `bookingConflictNote` |
| `"Đã có booking được mentor chấp nhận trùng với khoảng thời gian này"` | Đã có mentee khác được chấp nhận | Render ô đỏ/xám (disabled), tooltip: `bookingConflictNote` |
| `"Segment này đã đạt tối đa 3 yêu cầu chờ xác nhận"` | Hết hàng đợi PENDING (3/3) | Render ô vàng/xám (disabled), tooltip: *"Đã đủ 3 yêu cầu chờ xác nhận"* |

---

## 8. Xử Lý Lỗi Thường Gặp (Error Handling)

| HTTP Status / Code | Hướng xử lý cho Frontend |
|---|---|
| `400 Bad Request` | Hiển thị thông báo lỗi validation theo từng ô nhập liệu. Không retry tự động. |
| `401 Unauthorized` | Thực hiện quy trình Refresh Token theo [identity.md](identity.md). |
| `403 Forbidden` | API này chỉ dành cho tài khoản có Role `MENTOR`. Ẩn các thao tác nếu user chưa được duyệt. |
| `404 Not Found` | Dịch vụ, slot rảnh hoặc template không còn tồn tại hoặc không thuộc quyền sở hữu của mentor hiện tại. |
| `409 Conflict` | Xung đột phiên bản (Optimistic Locking) hoặc slot bị khóa bởi booking đang xử lý. Tải lại dữ liệu và dùng version mới nhất. |
| `429 Too Many Requests` | Đọc `retryAfterSeconds`, khóa nút thao tác và hiển thị đếm ngược thời gian chờ. |
