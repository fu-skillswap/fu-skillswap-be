# Frontend Guide — Đặt Lịch Mentoring 1:1 (Booking 1:1)

> **Phạm vi áp dụng:** Tài liệu này chỉ áp dụng cho hình thức mentoring 1:1 (`ONE_TO_ONE`). Không áp dụng cho khóa học (Course).

> **Chuẩn Envelope:** Tất cả phản hồi từ backend đều được bọc trong `ApiResponse<T>`. Vui lòng xem [identity.md](identity.md) để biết cách xử lý Access Token, Refresh Token, Validation và mã lỗi `429 Retry-After`.

> **Tài liệu liên quan:**
> - Tìm kiếm mentor và chọn slot/candidate: xem [mentor-discovery.md](mentor-discovery.md).
> - Mentor quản lý dịch vụ và lịch rảnh: xem [mentor-service.md](mentor-service.md).
> - Thanh toán sau khi mentor chấp nhận: xem [payment.md](payment.md).
> - Tính năng Chat phòng họp (chỉ mở khi booking đủ điều kiện): xem [realtime-chat-notification.md](realtime-chat-notification.md).

---

## 1. Phân quyền và Quy tắc Thời gian (Security & Timestamps)

### 1.1 Phân quyền Thao tác

| Thao tác | Role được phép |
|---|---|
| Lấy báo giá (Quote), tạo booking, xem danh sách/chi tiết booking của mình | `MENTEE` hoặc `MENTOR` |
| Chấp nhận (Accept), từ chối (Reject), hủy với tư cách mentor, cập nhật link họp, hoàn thành (Complete) với tư cách mentor | `MENTOR` (và bắt buộc phải là mentor phụ trách booking đó) |
| Hủy với tư cách mentee, xác nhận buổi học (Confirm), báo cáo sự cố (Issue), đánh giá (Feedback) | Thành viên tham gia trực tiếp (Participant) của booking |
| Tạo booking hoặc trực tiếp can thiệp booking của người dùng | `ADMIN` và `SYSTEM_ADMIN` **bị chặn hoàn toàn** |

> [!NOTE]
> Mentor vẫn có thể đặt lịch với một mentor khác như một mentee bình thường. Backend luôn tự động kiểm tra quyền sở hữu (ownership) của booking; Frontend không tự truyền `mentorUserId` để gán quyền.

---

### 1.2 Quy tắc Xử lý Thời gian (Timestamp Guidelines)

- `startAt` khi lấy báo giá (Quote) và tạo booking (`createBooking`) là chuẩn **`Instant` UTC** (ví dụ: `2026-06-30T12:00:00Z`).
- Giá trị này **bắt buộc phải lấy từ `candidateServiceSlots[].startTime`** của API candidate, tuyệt đối không tự tính toán lại từ giờ xem trước (preview).
- Các trường thời gian trong `BookingResponse` là chuỗi **ISO-8601 có offset** theo múi giờ kinh doanh `Asia/Ho_Chi_Minh`, ví dụ `2026-06-30T19:00:00+07:00`. FE parse trực tiếp chuỗi này; không tự thêm `Z` hoặc cộng thêm 7 giờ.

---

## 2. Luồng Vận hành Booking 1:1 (Workflow)

```text
Mentee chọn mentor, dịch vụ (Service) và khung giờ chính xác (Exact Candidate)
 ➔ Lấy báo giá: POST /api/bookings/quote (Chưa giữ chỗ)
 ➔ Tạo booking: POST /api/bookings (Trạng thái: REQUESTED / PENDING)
 ➔ Mentor phản hồi (Accept / Reject):
    ├─ Dịch vụ miễn phí (Free): Booking chuyển ngay sang CONFIRMED
    └─ Dịch vụ có phí (Paid): Chuyển sang WAITING_PAYMENT
        ➔ Mentee thanh toán và cổng thanh toán xác nhận thành công
        ➔ Booking chuyển sang CONFIRMED
 ➔ Đến giờ học: mentor và mentee check-in độc lập
 ➔ Buổi học diễn ra và kết thúc
 ➔ Mentor bấm hoàn thành (Complete Session)
 ➔ Mentee xác nhận (Confirm) hoặc Báo cáo sự cố (Submit Issue)
    ├─ Xác nhận thành công ➔ Chuyển sang COMPLETED ➔ Mở form Feedback
    └─ Báo cáo sự cố ➔ Chuyển sang UNDER_REVIEW để Admin/Hệ thống xử lý
```

> [!IMPORTANT]
> **Source of Truth cho UI**: Các trường `bookingStatus`, `paymentStatus`, `displayState`, `nextAction` và các cờ `can*` trong response là nguồn dữ liệu chuẩn để điều khiển giao diện. **Không tự suy đoán hành động từ `status`, `sessionId` hay `sessionStatus`** (đây là các trường legacy cũ).

---

### 2.1 Check-in / Attendance

- Khi đến đúng `selectedStartTime`, hiển thị nút **Check-in** nếu `attendance.canCheckIn = true`.
- Gọi `POST /api/me/bookings/{bookingId}/check-in` với header `Idempotency-Key`; request **không có body**.
- Nút chỉ mở từ giờ bắt đầu đến trước giờ kết thúc. `canJoin` có thể mở sớm 15 phút nhưng không thay thế check-in.
- Sau khi thành công, dùng `attendance.currentUserCheckedIn`, `mentorCheckedInAt`, `menteeCheckedInAt` và `summary` từ response để cập nhật màn hình. Không tự tính từ thời gian trên client.
- Check-in là xác nhận có mặt được ghi thời gian bởi server, là evidence hỗ trợ khi có no-show; nó **không** tự hoàn tiền, release tiền hoặc kết luận buổi học đã diễn ra.
- Nếu timeout, retry bằng cùng `Idempotency-Key`. Nếu nhận `409`, tải lại booking detail vì có thể đã hết cửa sổ check-in hoặc session đã đóng.

---

### 2.2 Bảng Trạng thái Hiển thị (State & Action Mapping)

| Field | Giá trị quan trọng | Ý nghĩa & Hướng xử lý cho Frontend |
|---|---|---|
| `bookingStatus` | `REQUESTED` | Đang chờ mentor phản hồi chấp nhận hoặc từ chối. |
| `bookingStatus` | `WAITING_PAYMENT` | Mentor đã đồng ý; Mentee cần thanh toán trong thời hạn cho phép. |
| `bookingStatus` | `CONFIRMED` | Lịch hẹn đã được xác nhận chính thức (đã thanh toán hoặc dịch vụ miễn phí). |
| `bookingStatus` | `REJECTED_BY_MENTOR` | Mentor đã từ chối yêu cầu đặt lịch. |
| `bookingStatus` | `CANCELED_BY_MENTEE`, `CANCELED_BY_MENTOR`, `REQUEST_EXPIRED` | Booking đã bị hủy hoặc hết hạn phản hồi. |
| `bookingStatus` | `UNDER_REVIEW` | Đang có khiếu nại/sự cố cần xem xét giải quyết. |
| `bookingStatus` | `COMPLETED` | Buổi học đã hoàn tất thành công. |
| `paymentStatus` | `NOT_REQUIRED`, `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `REFUNDED` | Trạng thái thanh toán độc lập với trạng thái buổi học. |
| `displayState` | `PAYMENT_REQUIRED`, `MENTOR_ACTION_REQUIRED`, `WAITING_CONFIRMATION`, `FEEDBACK_REQUIRED`,... | Trạng thái tổng hợp backend đã tính sẵn theo vai trò (Role) và mốc thời gian. |
| `nextAction` | `PAY_NOW`, `ACCEPT_OR_REJECT`, `JOIN_SESSION`, `COMPLETE_SESSION`, `CONFIRM_SESSION`, `LEAVE_FEEDBACK`, `VIEW_ISSUE`, `NONE` | Hành động ưu tiên cao nhất (Call-to-Action) trên màn hình. |

> [!TIP]
> - Sử dụng `actionDeadlineAt` khi có giá trị để hiển thị đồng hồ đếm ngược (Countdown).
> - FE mới dùng các cờ rõ vai trò: `canPay`, `canAccept`, `canReject`, `canCancel`, `canJoin`, `attendance.canCheckIn`, `canCompleteByMentor`, `canConfirmByMentee`, `canReportIssue`, `canRespondIssue`, `canSubmitFeedback`.
> - `canComplete` chỉ là alias legacy; không dùng cho màn hình mới.
> - Luôn phải xử lý mã lỗi `409 Conflict` vì trạng thái dữ liệu có thể thay đổi giữa lúc render và lúc người dùng nhấn nút.

---

## 3. Mentee Đặt Lịch (Mentee Booking Flow)

### 3.1 Tải lại Candidate sau khi Đăng nhập

1. Từ màn hình xem trước công khai (Public Preview), chỉ lưu `mentorUserId`, dịch vụ dự kiến và giờ dự kiến vào state của client.
2. Sau khi người dùng đăng nhập, gọi API lấy lịch khả dụng đã xác thực (Authenticated Availability) và candidate theo hướng dẫn trong [mentor-discovery.md](mentor-discovery.md).
3. Chọn candidate có `isSelectable = true`.
4. Gọi API lấy báo giá (`quote`) ngay trước khi mở Modal xác nhận đặt lịch.
5. Tạo booking với cùng bộ tham số `slotId`, `serviceId`, `startAt` của candidate vừa tải.

> [!WARNING]
> **Không sử dụng `availability-preview` để tạo booking**: Endpoint preview không trả về `slotId`, hạn mức (quota) cũng như không kiểm tra các xung đột đặt lịch hiện tại.

---

### 3.2 Lấy Báo Giá Trước Khi Đặt (`POST /api/bookings/quote`)

- **Endpoint**: `POST /api/bookings/quote`
- **Header**: `Authorization: Bearer <accessToken>` (Role `MENTEE` hoặc `MENTOR`)
- **Đặc điểm**: Báo giá chỉ là ước tính chi phí tạm thời tại thời điểm gọi, **không giữ chỗ trước**.

```typescript
interface BookingQuoteRequest {
  slotId: string;
  serviceId: string;
  startAt: string; // Chuỗi ISO Instant UTC lấy từ candidate (vd: "2026-06-30T12:00:00Z")
}

interface BookingQuoteResponse {
  slotId: string;
  serviceId: string;
  serviceTitle: string;
  durationMinutes: number;
  scheduledStartAt: string; // ISO-8601 theo contract của quote
  scheduledEndAt: string;   // ISO-8601 theo contract của quote
  pendingExpireAt: string | null;
  paymentWindowMinutes: number;
  paymentPreparationBufferMinutes: number;
  pricing: {
    pricingVersion: string;
    calculatedAt: string;
    serviceId: string;
    priceScoin: number;
    priceBeforeCampaignScoin: number;
    campaignDiscountScoin: number;
    estimatedPayableScoin: number;
    campaignName: string | null;
    isEstimate: boolean;
    disclaimer: string | null;
  };
  cancellationRefundPolicy: {
    earlyMenteeCancellationDeadlineMinutes: number;
    earlyMenteeRefundPercent: number;
    lateMenteeRefundPercent: number;
    lateMentorSharePercent: number;
    latePlatformSharePercent: number;
    mentorCancellationRefundPercent: number;
    mentorNoShowRefundPercent: number;
  };
  isEstimate: boolean;
  disclaimer: string | null;
}
```

> [!NOTE]
> Hiển thị `pricing.estimatedPayableScoin` và `disclaimer` chính xác như backend trả về. Frontend **không tự tính phụ phí, chiết khấu chiến dịch hay chính sách hoàn tiền**.
> `paymentWindowMinutes` hiện là **60 phút**. Deadline thật vẫn phải lấy từ `actionDeadlineAt` của booking sau khi mentor accept vì có thể ngắn hơn nếu sát giờ học.

---

### 3.3 Tạo Booking Mới (`POST /api/bookings`)

Yêu cầu bắt buộc khi gọi `POST /api/bookings`:
1. Header `Authorization: Bearer <accessToken>`.
2. Header **bắt buộc**: `Idempotency-Key: <uuid-hoac-chuoi-ngau-nhien-duy-nhat>`.
3. Cùng một `Idempotency-Key` chỉ được dùng lại khi gọi đúng `POST /api/bookings` với đúng payload đó. Key được lưu để replay lại response thành công nếu bị timeout; nếu dùng lại cùng key nhưng đổi payload sẽ nhận lỗi `409 Conflict`.

```typescript
interface CreateBookingRequest {
  slotId: string;
  serviceId: string;
  startAt: string;                  // ISO Instant UTC, làm tròn phút, lấy từ candidate
  learningGoalTitle: string;        // 1 - 200 ký tự (Bắt buộc)
  learningGoalDescription?: string; // Tối đa 2000 ký tự (Tùy chọn)
}
```

#### Ví dụ gọi API bằng TypeScript:

```typescript
await apiClient.post(
  "/api/bookings",
  {
    slotId,
    serviceId,
    startAt: selectedCandidate.startTime,
    learningGoalTitle: "Review lộ trình Spring Boot",
    learningGoalDescription: "Cần gợi ý project và cách chuẩn bị kiến thức phỏng vấn Intern Backend.",
  },
  {
    headers: {
      "Idempotency-Key": crypto.randomUUID(),
    },
  }
);
```

> [!TIP]
> - Nếu request bị **timeout mạng**, hãy retry lại bằng **cùng key và cùng payload**.
> - Nếu người dùng sửa mục tiêu học tập hoặc chọn slot khác, hãy tạo một key mới (`crypto.randomUUID()`).
> - Không tự động retry khi nhận lỗi `409 Conflict`; hãy tải lại danh sách lịch rảnh/candidate trước.

---

## 4. Danh Sách & Chi Tiết Booking (Listing & Detail APIs)

### 4.1 Danh Sách Booking của Tôi (`GET /api/me/bookings`)

- **Endpoint**: `GET /api/me/bookings`
- **Header**: `Authorization: Bearer <accessToken>`
- **Query Parameters**:
  - `role`: `"MENTEE"` hoặc `"MENTOR"` (Mặc định: `"MENTEE"`).
  - `status` (Tùy chọn): Lọc theo trạng thái `BookingStatus`:
    - `PENDING`, `ACCEPTED_AWAITING_PAYMENT`, `PAID`, `REJECTED`, `EXPIRED`, `CANCELLED_BY_MENTEE`, `CANCELLED_BY_MENTOR`, `AWAITING_MENTOR_COMPLETION`, `AWAITING_MENTEE_CONFIRMATION`, `COMPLETED`, `UNDER_REVIEW`. Những kết quả sau buổi học như tự đóng hoặc no-show nằm trong `completionOutcome`, không phải status riêng.
  - `page`: Số trang (Bắt đầu từ `0`).
  - `size`: Kích thước trang (Mặc định: `10`).
  - `sortBy`: Trường sắp xếp (Mặc định: `selectedStartTime`).
  - `direction`: `"ASC"` hoặc `"DESC"` (Mặc định: `"DESC"`).

> [!NOTE]
> - API hiện tại không có query `fromDate` hay `toDate`.
> - Khi không truyền `status`, backend trả dashboard từ 7 ngày trước đến 90 ngày tới, ưu tiên booking cần thao tác rồi đến lịch sắp tới; các lịch sắp tới được xếp theo giờ bắt đầu tăng dần.
> - Khi truyền `status`, backend không giới hạn cửa sổ ngày. FE dùng cách này để phân trang toàn bộ lịch sử theo từng nhóm trạng thái.

---

### 4.2 Chi Tiết Booking (`GET /api/me/bookings/{bookingId}`)

- **Endpoint**: `GET /api/me/bookings/{bookingId}`
- **Header**: `Authorization: Bearer <accessToken>`
- **Quyền hạn**: Chỉ mentee hoặc mentor tham gia booking này mới có quyền xem. Nhận `403 Forbidden` nếu booking không thuộc về user hiện tại.

#### Cấu trúc Payload đầy đủ (`BookingResponse`):

```typescript
interface BookingResponse {
  bookingId: string;
  actualSessionId: string | null;
  actualSessionStatus: string | null;
  attendance: {
    mentorCheckedInAt: string | null;
    menteeCheckedInAt: string | null;
    summary: "NONE" | "MENTOR_ONLY" | "MENTEE_ONLY" | "BOTH";
    currentUserCheckedIn: boolean;
    canCheckIn: boolean;
    checkInOpensAt: string | null;
    checkInClosesAt: string | null;
  };
  mentorUserId: string;
  mentorDisplayName: string;
  mentorAvatarUrl: string | null;
  menteeUserId: string;
  menteeDisplayName: string;
  menteeAvatarUrl: string | null;
  availabilitySlotId: string | null;
  serviceId: string | null;
  serviceTitle: string | null;
  serviceDescriptionSnapshot: string | null;
  serviceExpectedOutcomeSnapshot: string | null;
  serviceDurationSnapshot: number | null;
  serviceIsFreeSnapshot: boolean | null;
  servicePriceScoinSnapshot: number | null;
  maintainPostSessionChatSnapshot: boolean;
  servicePriceWithSurchargeScoin: number | null;

  bookingStatus: string;
  paymentStatus: string;
  settlementStatus: string | null;
  releasedAt: string | null;
  refundedAt: string | null;
  refundedScoin: number | null;
  refundReason: string | null;

  learningGoalTitle: string;
  learningGoalDescription: string | null;
  mentorResponseNote: string | null;
  rejectReason: string | null;
  cancelReason: string | null;

  meetingPlatform: string | null;
  meetingLink: string | null;
  location: string | null;
  googleMeetAutoGenerated: boolean | null;
  googleCalendarManaged: boolean | null;
  calendarSyncStatus: string | null;
  calendarSyncErrorCode: string | null;
  calendarSyncErrorMessage: string | null;

  selectedStartTime: string | null;
  selectedEndTime: string | null;
  reviewDeadlineAt: string | null; // selectedEndTime + 24 giờ
  acceptedAt: string | null;
  pendingExpireAt: string | null;
  cancelledAt: string | null;
  completedAt: string | null;
  finalizedAt: string | null;
  autoClosedAt: string | null;
  completionOutcome: string | null;

  issueSubmittedAt: string | null;
  issueType: string | null;
  issueDescription: string | null;
  issueRespondedAt: string | null;
  issueResponseNote: string | null;
  issueResolvedAt: string | null;
  issueResolutionNote: string | null;

  conversationId: string | null;
  canCancel: boolean;
  canComplete: boolean; // legacy
  canPay: boolean;
  canAccept: boolean;
  canReject: boolean;
  canCompleteByMentor: boolean;
  canConfirmByMentee: boolean;
  canJoin: boolean;
  // POST /api/me/bookings/{bookingId}/check-in, không có request body
  canReportIssue: boolean;
  canRespondIssue: boolean;
  joinAvailableAt: string | null; // selectedStartTime - 15 phút
  joinClosesAt: string | null;    // selectedEndTime + 15 phút
  canSubmitFeedback: boolean;
  cancellationRefundPolicy: {
    earlyMenteeCancellationDeadlineMinutes: number;
    earlyMenteeRefundPercent: number;
    lateMenteeRefundPercent: number;
    lateMentorSharePercent: number;
    latePlatformSharePercent: number;
    mentorCancellationRefundPercent: number;
    mentorNoShowRefundPercent: number;
  };
  displayState: string;
  nextAction: string | null;
  actionDeadlineAt: string | null;
}
```

---

## 5. Thao Tác Dành Cho Mentor (Mentor Actions)

Tất cả endpoint trong phần này yêu cầu Role `MENTOR`. Backend sẽ tự động xác thực xem booking có đúng do mentor hiện tại phụ trách hay không.

### 5.1 Chấp Nhận (Accept) hoặc Từ Chối (Reject) Yêu Cầu

| Thao tác | Endpoint | Request Body |
|---|---|---|
| Chấp nhận (Accept) | `POST /api/mentor/bookings/{bookingId}/accept` | `{ mentorResponseNote?: string }` (Tối đa 2000 ký tự) |
| Từ chối (Reject) | `POST /api/mentor/bookings/{bookingId}/reject` | `{ rejectReason: string, mentorResponseNote?: string }` (Mỗi trường tối đa 2000 ký tự) |

> [!IMPORTANT]
> - Chỉ hiển thị nút Accept/Reject theo `canAccept` và `canReject`; `nextAction === "ACCEPT_OR_REJECT"` dùng để chọn CTA nổi bật.
> - Chấp nhận một booking sẽ tự động từ chối (Auto Reject) các yêu cầu pending khác bị trùng lịch.
> - Lịch và khả năng accept chỉ dựa trên dữ liệu SkillSwap. Google Calendar không thể chặn accept hoặc làm booking thất bại. Khi đồng bộ Calendar lỗi, FE hiển thị `calendarSyncStatus` và hướng mentor kiểm tra lịch/link trong app.
> - Đối với dịch vụ có phí: Sau khi Accept, booking sẽ chuyển sang `WAITING_PAYMENT`; **chưa mở chat/session cho đến khi mentee thanh toán thành công**.
> - Đối với dịch vụ miễn phí: Booking được xác nhận (`CONFIRMED`) ngay lập tức.

---

### 5.2 Mentor Hủy Booking (`POST /api/mentor/bookings/{bookingId}/cancel`)

- **Endpoint**: `POST /api/mentor/bookings/{bookingId}/cancel`
- **Request Body (`CancelBookingRequest`)**:
```typescript
interface CancelBookingRequest {
  cancelReason: string; // 1 - 1000 ký tự (Bắt buộc)
}
```
- **Điều kiện hiển thị**: Chỉ hiển thị nút khi `canCancel === true`.
- **Hạn mức**: Rate limit tối đa **3 request / giờ / user**.

---

### 5.3 Hoàn Thành Buổi Học & Cập Nhật Link Họp

| Thao tác | Endpoint | Request Body |
|---|---|---|
| Mentor hoàn thành (Complete) | `POST /api/mentor/bookings/{bookingId}/complete` | `{ completionNote?: string }` (Tối đa 2000 ký tự) |
| Lưu link họp (Meeting Link) | `PATCH /api/mentor/bookings/{bookingId}/meeting-link` | Xem schema bên dưới |

```typescript
interface SaveMeetingLinkRequest {
  meetingPlatform:
    | "GOOGLE_MEET"
    | "ZOOM"
    | "MICROSOFT_TEAMS"
    | "DISCORD"
    | "OFFLINE"
    | "OTHER";
  meetingLink: string; // Bắt buộc, tối đa 1000 ký tự
  location?: string;   // Tùy chọn (địa điểm nếu offline), tối đa 500 ký tự
}
```

> [!NOTE]
> Nếu `googleCalendarManaged === true`, không hiển thị form chỉnh sửa link họp thủ công vì link Google Meet được tạo và quản lý tự động. Việc đồng bộ Google Calendar diễn ra bất đồng bộ; `calendarSyncStatus` chỉ để biểu thị trạng thái kết nối, không phải là điều kiện chặn mentee tham gia buổi học.

### 5.4 Điểm vi phạm nội bộ của mentor

`GET /api/me/mentor-violations?page=0&size=20` chỉ cho mentor hiện tại xem điểm phạt của chính mình. Response có `lifetimePenaltyScore`, `activePenaltyScore` (90 ngày gần nhất), `bookingSuspendedUntil` và lịch sử. Không đưa dữ liệu này lên mentor discovery hoặc màn hình mentee.

Admin dùng `GET /api/admin/mentors/{mentorUserId}/violations?page=0&size=20`. Ledger chung nhận lỗi booking (`LATE_CANCELLATION`, `COMPLETION_OVERDUE`, `MENTOR_NO_SHOW`), nội dung chat/forum bị admin xác nhận, fraud verification và lỗi vận hành do admin xác nhận. Report chưa được admin kết luận không làm tăng điểm.

Admin có thể ghi nhận lỗi đã xác minh bằng `POST /api/admin/mentors/{mentorUserId}/violations`. `sourceReferenceId` là bắt buộc để retry không tạo điểm trùng. API này không nhận các lỗi booking vì booking phải tự ghi nhận từ booking gốc.

Nếu quyết định bị xác định là sai, admin dùng `POST /api/admin/mentors/{mentorUserId}/violations/{violationId}/reverse` với lý do bắt buộc. Event cũ vẫn giữ cho audit nhưng không còn tính vào điểm active/lifetime và hệ thống tính lại thời gian khóa booking.

---

## 6. Thao Tác Dành Cho Mentee & Người Tham Gia (Mentee & Participant Actions)

### 6.1 Mentee Hủy Booking (`POST /api/me/bookings/{bookingId}/cancel`)
- **Endpoint**: `POST /api/me/bookings/{bookingId}/cancel`
- **Request Body**: `CancelBookingRequest` (`{ cancelReason: string }`)
- **Điều kiện**: Chỉ hiển thị khi `canCancel === true`.
- **Hạn mức**: Rate limit tối đa **3 request / giờ / user**.

---

### 6.2 Hoàn Thành, Xác Nhận, Báo Cáo Sự Cố & Đánh Giá

| Thao tác | Endpoint | Khi nào sử dụng |
|---|---|---|
| Complete (Dùng chung) | `POST /api/me/bookings/{bookingId}/complete` | Dùng cho UI chung: Mentor sẽ bấm hoàn thành, Mentee sẽ bấm xác nhận |
| Xác nhận (Confirm) | `POST /api/me/bookings/{bookingId}/confirm` | Mentee xác nhận buổi học đã diễn ra, kể cả khi mentor chưa bấm complete |
| Báo cáo sự cố (Issue) | `POST /api/me/bookings/{bookingId}/issue` | Người tham gia báo cáo vấn đề trong khung giờ cho phép sau buổi học |
| Phản hồi sự cố (Respond Issue) | `POST /api/me/bookings/{bookingId}/issue/respond` | Bên còn lại phản hồi khiếu nại (tối đa 1 lần phản hồi) |
| Đánh giá (Feedback) | `POST /api/bookings/{bookingId}/feedback` | Dành riêng cho mentee khi `canSubmitFeedback === true` |

```typescript
interface CompleteBookingRequest {
  completionNote?: string; // Tối đa 2000 ký tự
}

interface ConfirmBookingRequest {
  confirmationNote?: string; // Tối đa 2000 ký tự
}

interface SubmitBookingIssueRequest {
  issueType:
    | "MENTOR_NO_SHOW"      // Mentor không tham gia; chỉ mentee được gửi
    | "MENTEE_NO_SHOW"      // Mentee không tham gia; chỉ mentor được gửi
    | "QUALITY_ISSUE"       // Chất lượng buổi học không đảm bảo
    | "TECHNICAL_PROBLEM"   // Sự cố kỹ thuật/đường truyền
    | "OTHER";              // Lý do khác
  description: string;      // 1 - 2000 ký tự
}

interface RespondBookingIssueRequest {
  responseNote: string;     // 1 - 2000 ký tự
}

interface SubmitFeedbackRequest {
  rating: 1 | 2 | 3 | 4 | 5;             // Điểm đánh giá (1 đến 5 sao)
  satisfactionLevel?: 1 | 2 | 3 | 4 | 5; // Mức độ hài lòng
  comment?: string;                      // Nhận xét chi tiết
  wouldRecommend?: boolean;              // Có sẵn sàng giới thiệu mentor này không
  isPublic?: boolean;                    // Công khai đánh giá lên trang cá nhân mentor
}
```

> [!NOTE]
> Sự cố khiếu nại không tự động được đóng sau khi bên còn lại phản hồi. Nếu `bookingStatus === "UNDER_REVIEW"` hoặc `nextAction === "VIEW_ISSUE"`, hãy hiển thị chi tiết khiếu nại để người dùng theo dõi; **không tự đưa ra kết luận hoàn tiền trước khi có quyết định chính thức**.

> [!IMPORTANT]
> Deadline xác nhận hoặc báo vấn đề luôn là `selectedEndTime + 24 giờ`, không tính từ lúc mentor bấm complete. Nếu cả hai bên không thao tác, backend tự đóng booking và release tiền bình thường; mentor bị ghi nhận `COMPLETION_OVERDUE`.

Chính sách hủy booking đã thanh toán: hủy trước giờ học ít nhất 4 giờ được hoàn 100%; hủy trong vòng dưới 4 giờ chia `50% mentee / 35% mentor / 15% nền tảng`; từ đúng giờ bắt đầu không được hủy và trường hợp vắng mặt phải đi qua no-show flow.

---

## 7. Tích Hợp Thanh Toán, Chat & Lịch (Integrations)

- **Thanh toán**: Khi `canPay === true` (thường đi cùng `nextAction === "PAY_NOW"`), chuyển hướng người dùng sang luồng thanh toán trong [payment.md](payment.md). Mentee có tối đa 60 phút từ lúc mentor accept, nhưng FE luôn đếm ngược theo `actionDeadlineAt`. Sau khi PayOS chuyển hướng về, dùng API trạng thái/sync có chủ đích rồi tải lại booking; API GET danh sách booking không tự gọi PayOS.
- **Phòng Chat**: Trường `conversationId` có thể mang giá trị `null` khi booking chưa được xác nhận (`CONFIRMED`). Frontend **không tự ý tạo conversation**; chỉ mở khung chat khi backend đã trả về ID hợp lệ theo [realtime-chat-notification.md](realtime-chat-notification.md).
- **Tham gia buổi học**: Chỉ bật CTA khi `canJoin === true`; cửa sổ mặc định là từ 15 phút trước giờ bắt đầu đến 15 phút sau giờ kết thúc. Online mở `meetingLink`; offline hiển thị `location`.
- **Link Họp**: `meetingLink` có thể `null` lúc mới tạo. Frontend không tự tạo Google Meet link ở client. Nếu mentor đã liên kết Google Calendar, backend sẽ tự động đồng bộ và cập nhật link kèm mã trạng thái `calendarSyncStatus`. Lịch trong SkillSwap luôn là nguồn sự thật: lỗi hoặc trùng lịch Google không thể làm accept thất bại, hủy booking hay đổi payment. Khi sync lỗi, booking và thanh toán vẫn hợp lệ; hiển thị CTA cho mentor nhập link/địa điểm thủ công. Backend nhắc mentor trước 2 giờ và cả hai bên trước 30 phút nếu vẫn chưa có thông tin tham gia hợp lệ.

---

## 8. Xử Lý Lỗi & Cơ Chế Thử Lại An Toàn (Error Handling & Safe Retries)

| HTTP Status | Hướng xử lý cho Frontend |
|---|---|
| `400 Bad Request` | Hiển thị thông báo lỗi/validation. **Không tự động retry**. |
| `401 Unauthorized` | Thực hiện quy trình Refresh Token theo [identity.md](identity.md); nếu thất bại thì chuyển về trang Đăng nhập. |
| `403 Forbidden` | Người dùng không đúng role hoặc không phải người tham gia buổi học. Ẩn nút thao tác và tải lại trang. |
| `404 Not Found` | Booking, slot, dịch vụ hoặc candidate không còn tồn tại. Đóng modal và tải lại dữ liệu mới nhất. |
| `409 Conflict` | Slot vừa bị người khác đặt, booking đã đổi trạng thái hoặc Idempotency Key đang được xử lý. Tải lại dữ liệu trước khi cho người dùng thử lại. |
| `429 Too Many Requests` | Đọc trường `retryAfterSeconds` từ response, khóa nút và hiển thị đếm ngược thời gian chờ. Giới hạn tạo booking là **12 lần / 10 phút**; hủy booking là **3 lần / giờ**. |

> [!CAUTION]
> Với các mutation booking có hỗ trợ idempotency, FE gửi `Idempotency-Key` và chỉ retry sau lỗi mạng bằng **cùng key, cùng endpoint, cùng payload**. Backend cũng xử lý an toàn các lần gọi lặp của accept/reject/cancel/complete/confirm/issue khi trạng thái cuối đã trùng với yêu cầu trước đó.
