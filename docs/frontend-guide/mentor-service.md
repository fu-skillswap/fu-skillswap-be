# Frontend Guide — Quản Lý Dịch Vụ & Lịch Rảnh Mentor (Mentor Services & Availability)

> **Quy tắc URL:** Chỉ sử dụng trực tiếp các URL hình ảnh do backend trả về. Tuyệt đối không tự ghép nối URL CDN, `storageKey` hay `objectKey`. Xem [identity.md](identity.md) để biết cấu trúc envelope chung, cơ chế refresh token và cách xử lý mã lỗi `429 Retry-After`.

> **Tài liệu liên quan:**
> - Tài liệu này dành cho giao diện quản trị của Mentor (đã có Role `MENTOR`): quản lý dịch vụ 1:1 (`ONE_TO_ONE`), chính sách đặt lịch (Booking Policy), lịch rảnh trực tiếp (Direct Slots) và mẫu lịch lặp tuần (Availability Templates).
> - Xem [mentor-discovery.md](mentor-discovery.md) để biết cách hiển thị thẻ/profile công khai và cách Mentee chọn khung giờ.
> - Xem [mentor-verification.md](mentor-verification.md) để nắm quy trình xét duyệt hồ sơ mentor.

---

## 1. Điều Kiện & Luồng Vận Hành (Eligibility & Flow)

Tất cả các endpoint trong tài liệu này yêu cầu Bearer token với Role `MENTOR`.

```text
Hồ sơ Mentor đã được Admin phê duyệt (Role MENTOR)
 ➔ Tạo Dịch vụ 1:1 đang hoạt động (Active ONE_TO_ONE Service)
 ➔ Tạo Lịch rảnh trực tiếp (Direct Slot) hoặc Mẫu lịch lặp tuần (Weekly Template)
 ➔ Mentor đủ điều kiện xuất hiện trên trang Tìm kiếm công khai (Discovery)
 ➔ Mentee lựa chọn khung giờ và tiến hành Đặt lịch (Booking)
```

> [!NOTE]
> Việc được duyệt hồ sơ xác thực (Verification) không tự động tạo sẵn dịch vụ hay lịch rảnh. Dịch vụ và lịch rảnh không phải là điều kiện bắt buộc lúc nộp hồ sơ, nhưng là điều kiện tiên quyết để mentor có thể nhận booking và hiển thị trên trang tìm kiếm.

---

## 2. Quản Lý Dịch Vụ Mentoring (Service Management)

1. Gọi `GET /api/me/mentor-services/constraints` trước khi mở form tạo mới để lấy các ràng buộc về thời lượng (`durationMinutes`) và khoảng giá Scoin do nền tảng quy định.
2. Tạo dịch vụ mới qua `POST /api/me/mentor-services`.
3. Sử dụng `GET /api/me/mentor-services?isActive=true|false` để quản lý danh sách dịch vụ của mình (bỏ query `isActive` để lấy toàn bộ).
4. Sử dụng `PUT /api/me/mentor-services/{serviceId}` để chỉnh sửa và `PATCH /api/me/mentor-services/{serviceId}/active` để bật/tắt trạng thái hoạt động.

```typescript
interface CreateMentorServiceRequest {
  title: string;                    // 1 - 200 ký tự (Bắt buộc)
  description: string;              // 1 - 1000 ký tự (Bắt buộc)
  expectedOutcome: string;          // 1 - 1000 ký tự (Bắt buộc)
  durationMinutes: number;          // Bắt buộc, phải thuộc danh sách allowedDurationMinutes từ constraints
  isFree: boolean;                  // true: miễn phí, false: có phí
  priceScoin: number;               // Giá Scoin (0 nếu isFree = true)
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
  priceScoin: number;
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

## 3. Chính Sách Đặt Lịch & Ràng Buộc Hệ Thống (Booking Policy & Constraints)

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

## 4. Lịch Rảnh Trực Tiếp (Direct Availability Slots)

Lịch rảnh trực tiếp (Direct Slot) là các khung giờ rảnh dùng một lần.
- Thời gian gửi lên request bắt buộc là chuỗi **`Instant` UTC** (ví dụ: `2026-06-29T01:00:00Z`).
- Giao diện người dùng chuyển đổi giờ địa phương mà mentor nhập sang UTC trước khi gửi, và hiển thị theo múi giờ (`Asia/Ho_Chi_Minh`) nhận được từ backend.

| Endpoint | Mục đích |
|---|---|
| `POST /api/me/availability-slots` | Tạo slot rảnh dùng một lần |
| `GET /api/me/availability-slots?isActive=&fromDate=&toDate=` | Lấy danh sách slot trực tiếp do mentor quản lý |
| `PUT /api/me/availability-slots/{slotId}` | Chỉnh sửa slot trực tiếp (khi chưa bị khóa bởi booking) |
| `POST /api/me/availability-slots/{slotId}/deactivate` | Hủy/thu hồi slot rảnh |

```typescript
interface CreateAvailabilitySlotRequest {
  startAt: string;                       // Chuỗi ISO Instant UTC
  endAt: string;                         // Chuỗi ISO Instant UTC
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

## 5. Mẫu Lịch Lặp Tuần (Availability Templates)

Template là lịch lặp cố định theo các thứ trong tuần sử dụng múi giờ `Asia/Ho_Chi_Minh`. Backend sẽ tự động sinh (materialize) thành các slot rảnh cụ thể trong tương lai. Không dùng trực tiếp template để đặt lịch.

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

## 6. Xử Lý Lỗi Thường Gặp (Error Handling)

| HTTP Status | Hướng xử lý cho Frontend |
|---|---|
| `400 Bad Request` | Hiển thị thông báo lỗi validation theo từng ô nhập liệu. Không retry tự động. |
| `401 Unauthorized` | Thực hiện quy trình Refresh Token theo [identity.md](identity.md). |
| `403 Forbidden` | API này chỉ dành cho tài khoản có Role `MENTOR`. Ẩn các thao tác nếu user chưa được duyệt. |
| `404 Not Found` | Dịch vụ, slot rảnh hoặc template không còn tồn tại hoặc không thuộc quyền sở hữu của mentor hiện tại. |
| `409 Conflict` | Xung đột phiên bản (Optimistic Locking) hoặc slot bị khóa bởi booking đang xử lý. Tải lại dữ liệu và dùng version mới nhất. |
| `429 Too Many Requests` | Đọc `retryAfterSeconds`, khóa nút thao tác và hiển thị đếm ngược thời gian chờ. |
