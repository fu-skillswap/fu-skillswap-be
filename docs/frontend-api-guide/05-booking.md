# Booking Service (`05-booking.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Đặt lịch Mentoring (Booking Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Booking Service** quản lý toàn bộ vòng đời đặt lịch học giữa Mentee và Mentor: từ tính toán giá và kiểm tra điều kiện (`Booking Quote`), tạo yêu cầu đặt lịch (`Create Booking`), xử lý phản hồi từ Mentor (Accept/Reject), tích hợp thanh toán (`Checkout Preview`), thực thi session, xác nhận hoàn thành (`Confirm & Complete`), đổi lịch (`Reschedule`), xử lý tranh chấp (`Issue & Dispute`), đến lưu snapshot cờ chat và phân quyền tài liệu học tập.

### Trách nhiệm chính của Service
- **Tính toán Báo giá & Điều kiện (`Booking Quote`)**: Kiểm tra thời gian `startAt`, tính toán hạn chót xử lý SLA (`pendingExpireAt`), báo giá ước tính và chính sách hủy lịch trước khi tạo đơn. API này hoàn toàn read-only, không giữ chỗ hay giữ coupon.
- **Tạo Đơn Đặt lịch (`Create Booking`)**: Nhận `slotId`, `serviceId`, `startAt` (UTC Instant) và `learningGoal`. Backend tự động tính `scheduledEndAt` dựa trên thời lượng cố định của dịch vụ. Yêu cầu HTTP Header `Idempotency-Key` để chống trùng đơn.
- **Quản lý Phản hồi từ Mentor (`Accept / Reject / Cancel`)**: Mentor chấp nhận hoặc từ chối yêu cầu. Nếu chấp nhận gói có phí, đơn chuyển sang trạng thái chờ thanh toán (`WAITING_PAYMENT`).
- **Xem trước Thanh toán (`Checkout Preview`)**: Áp dụng mã giảm giá (`couponCode`), tính toán số SCoin thực trả (`finalPayableScoin`), hạn chót thanh toán (`paymentDeadlineAt`) và trả link thanh toán.
- **Xác nhận Hoàn thành & Tranh chấp (`Completion & Dispute`)**: Mentor đánh dấu hoàn tất (`Complete`), Mentee xác nhận hài lòng (`Confirm`), hoặc một trong hai bên báo cáo sự cố (`Submit Issue`) đưa đơn vào trạng thái kiểm tra (`UNDER_REVIEW`).

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Chuẩn hóa Vòng đời Đặt lịch (Canonical State Machine)**: Tách biệt rõ ràng 4 nhóm trạng thái độc lập (`bookingStatus`, `paymentStatus`, `settlementStatus`, `completionOutcome`) giúp Frontend render chính xác giao diện theo từng giai đoạn.
2. **Chống Tạo Đơn Trùng Lặp (Idempotency Control)**: Bắt buộc gửi HTTP Header `Idempotency-Key` khi khởi tạo Booking. Nếu gặp sự cố mạng retry, Backend trả lại đúng Booking cũ đã tạo thay vì sinh đơn mới.
3. **Bảo đảm Thời gian Phục vụ SLA (Automatic Expiration)**:
   - Hạn chót Mentor duyệt đơn (`pendingExpireAt`): `min(createdAt + 12h, scheduledStartAt - 3h)`. Đơn quá hạn tự chuyển `REQUEST_EXPIRED`.
   - Hạn chót Mentee thanh toán (`paymentDeadlineAt`): `min(acceptedAt + 6h, scheduledStartAt - 1h)`. Đơn quá hạn tự chuyển `PAYMENT_EXPIRED`.
4. **Bảo lưu Cam kết Chat bằng Snapshot Policy**: Snapshot cờ `maintainPostSessionChatSnapshot` được lưu nguyên bản tại thời điểm tạo đơn. Mọi thay đổi về chính sách dịch vụ của Mentor sau đó không làm ảnh hưởng đến quyền lợi chat của đơn hàng cũ.
5. **Chỉ Mở Đánh giá Khi Mentee Xác nhận Hợp lệ**: API gửi Đánh giá (`POST /api/bookings/{bookingId}/feedback`) chỉ mở khi `completionOutcome == USER_CONFIRMED`. Các trường hợp tự động đóng (`AUTO_CLOSED`) hoặc báo lỗi (`NO_SHOW`) không thể mở form đánh giá.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                    LUỒNG ĐẶT LỊCH & THỰC THI SESSION                                  |
+-------------------------------------------------------------------------------------------------------+

  Mentee (Frontend)                    Backend (SkillSwap API)                 Mentor (Frontend)
          |                                     |                                         |
   1. Chọn Service & Candidate Segment          |                                         |
          |                                     |                                         |
   2. POST /api/bookings/quote ---------------->|-- Validate điều kiện & SLA ------------>|
          |<-- 200 OK (BookingQuoteResponse) ---|                                         |
          |                                     |                                         |
   3. POST /api/bookings (Idempotency-Key) ---->|-- Tạo Booking (Status: REQUESTED) ------>|
          |<-- 200 OK (BookingResponse) --------|---------------------------------------->| (Báo Notification)
          |                                     |                                         |
          |                                     | 4. Mentor chọn "Đồng ý"                 |
          |                                     |<-- POST /mentor/bookings/{id}/accept ---|
   5. Báo Notification "Đã chấp nhận" <--------| (Status: WAITING_PAYMENT)               |
          |                                     |                                         |
   6. POST /me/bookings/{id}/checkout-preview ->|-- Tính toán Coupon & SCoin Ví ---------->|
          |<-- 200 OK (finalPayableScoin) ------|                                         |
          |                                     |                                         |
   7. Thanh toán SCoin / Ví VNPay ------------->|-- Trừ SCoin & Khóa tiền Settlement --->|
          |<-- 200 OK (Status: CONFIRMED) ------|---------------------------------------->|
          |                                     |                                         |
   8. Tham gia Buổi học qua Google Meet         | 9. Mentor bấm "Hoàn thành Session"       |
          |<------------------------------------|<-- POST /mentor/bookings/{id}/complete ---|
          |                                     |                                         |
  10. Mentee bấm "Xác nhận Buổi học"            |                                         |
          |-- POST /me/bookings/{id}/confirm -->|-- Giải ngân SCoin cho Mentor (RELEASE) ->|
          |<-- 200 OK (Outcome: USER_CONFIRMED) |                                         |
          |                                     |                                         |
  11. Mở Form Đánh giá (Feedback)               |                                         |
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Bốn Ma trận Trạng thái Chuẩn (Canonical State Model)
Frontend bắt buộc phải kết hợp 4 trường dữ liệu sau để hiển thị đúng UI:
1. `bookingStatus` (Trạng thái Vòng đời Đặt lịch):
   - `REQUESTED`: Mới gửi yêu cầu, chờ Mentor duyệt.
   - `WAITING_PAYMENT`: Mentor đã duyệt, chờ Mentee thanh toán.
   - `CONFIRMED`: Đã thanh toán / Service miễn phí, sẵn sàng tham gia.
   - `REJECTED_BY_MENTOR`: Mentor từ chối yêu cầu.
   - `CANCELED_BY_MENTEE` / `CANCELED_BY_MENTOR`: Một trong 2 bên hủy lịch.
   - `REQUEST_EXPIRED`: Hết hạn chờ Mentor duyệt (Quá 12h hoặc 3h trước buổi học).
   - `PAYMENT_EXPIRED`: Hết hạn chờ Mentee thanh toán (Quá 6h hoặc 1h trước buổi học).
   - `UNDER_REVIEW`: Đang khiếu nại tranh chấp (Issue / Dispute).
   - `COMPLETED`: Đã hoàn tất toàn bộ quy trình.
2. `paymentStatus` (Trạng thái Thanh toán): `NOT_REQUIRED`, `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `REFUNDED`.
3. `settlementStatus` (Trạng thái Tiền ví): `HELD` (Backend tạm giữ tiền trên hệ thống), `RELEASED` (Đã giải ngân cho Mentor), `REFUNDED` (Đã hoàn tiền lại cho Mentee).
4. `completionOutcome` (Kết quả Buổi học): `USER_CONFIRMED`, `AUTO_CLOSED`, `UNDER_REVIEW`, `NO_SHOW_MENTEE`, `NO_SHOW_MENTOR`.

### 4.2 Giao diện Hướng dẫn Hiển thị Đơn giản (`Display Guidance`)
Backend hỗ trợ sẵn 2 trường gợi ý UI:
- `displayState`: `PENDING_MENTOR_RESPONSE`, `PAYMENT_REQUIRED`, `MENTOR_ACTION_REQUIRED`, `UPCOMING`, `IN_SESSION`, `WAITING_CONFIRMATION`, `UNDER_REVIEW`, `FEEDBACK_REQUIRED`, `COMPLETED`, `CANCELED_OR_EXPIRED`.
- `nextAction`: Mã gợi ý nút bấm chính (ví dụ: `PAY_NOW`, `CONFIRM_SESSION`, `LEAVE_FEEDBACK`).

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Header Bắt buộc | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/bookings/quote` | Authenticated | Bearer | Báo giá ước tính, kiểm tra SLA & chính sách hoàn hủy (Read-only) | Trước khi submit form đặt lịch |
| `POST` | `/api/bookings` | Authenticated | `Idempotency-Key` | Khởi tạo yêu cầu đặt lịch mới | Bấm "Gửi yêu cầu Đặt lịch" |
| `GET` | `/api/me/bookings` | Authenticated | Bearer | Lấy danh sách lịch học của tôi (có lọc status/role) | Trang Lịch học của tôi |
| `GET` | `/api/me/bookings/{bookingId}` | Authenticated | Bearer | Lấy thông tin chi tiết canonical của 1 đơn đặt lịch | Màn hình Chi tiết Đơn đặt lịch |
| `POST` | `/api/mentor/bookings/{bookingId}/accept` | Mentor Role | Bearer | Mentor chấp nhận yêu cầu đặt lịch | Mentor bấm "Chấp nhận" |
| `POST` | `/api/mentor/bookings/{bookingId}/reject` | Mentor Role | Bearer | Mentor từ chối yêu cầu đặt lịch | Mentor bấm "Từ chối" |
| `POST` | `/api/me/bookings/{bookingId}/checkout-preview` | Mentee Role | Bearer | Tính toán mã giảm giá, giá SCoin thực trả & hạn thanh toán | Màn hình Thanh toán Checkout |
| `POST` | `/api/me/bookings/{bookingId}/cancel` | Mentee Role | Bearer | Mentee hủy đơn đặt lịch | Mentee bấm "Hủy đơn" |
| `POST` | `/api/mentor/bookings/{bookingId}/cancel` | Mentor Role | Bearer | Mentor hủy đơn đặt lịch đã nhận | Mentor bấm "Hủy đơn" |
| `POST` | `/api/mentor/bookings/{bookingId}/complete` | Mentor Role | Bearer | Mentor đánh dấu đã hoàn thành buổi học | Mentor bấm "Hoàn tất Session" |
| `POST` | `/api/me/bookings/{bookingId}/confirm` | Mentee Role | Bearer | Mentee xác nhận hài lòng sau buổi học | Mentee bấm "Xác nhận Học xong" |
| `POST` | `/api/me/bookings/{bookingId}/issue` | Participant | Bearer | Báo cáo sự cố/tranh chấp buổi học (Mentor/Mentee Vắng mặt) | Bấm "Báo cáo Sự cố" |
| `POST` | `/api/bookings/{bookingId}/feedback` | Mentee Role | Bearer | Gửi đánh giá sao & nhận xét dịch vụ | Bấm "Gửi Đánh giá" |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `POST /api/bookings`

#### Purpose
Khởi tạo đơn đặt lịch mới từ Mentee gửi tới Mentor.

#### Request Headers
- `Idempotency-Key` (`String`, Bắt buộc): Chuỗi UUID ngẫu nhiên duy nhất cho mỗi giao dịch tạo đơn.

#### Request Body (`CreateBookingRequest`)
```json
{
  "slotId": "6fa85f64-5717-4562-b3fc-2c963f66afa6",
  "serviceId": "55555555-5555-5555-5555-555555555555",
  "startAt": "2026-08-05T08:00:00Z",
  "learningGoal": "Muốn tư vấn kiến trúc Microservices cho đồ án Spring Boot"
}
```

#### Response Body (`BookingResponse`)
```json
{
  "timestamp": "2026-08-04T09:40:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "bookingId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "bookingStatus": "REQUESTED",
    "paymentStatus": "PENDING",
    "settlementStatus": "HELD",
    "completionOutcome": null,
    "displayState": "PENDING_MENTOR_RESPONSE",
    "nextAction": "WAIT_FOR_MENTOR",
    "actionDeadlineAt": "2026-08-04T21:40:00Z",
    "maintainPostSessionChatSnapshot": true,
    "canCancel": true,
    "canComplete": false,
    "canReschedule": false,
    "canSubmitFeedback": false
  }
}
```

---

### 6.2 `POST /api/me/bookings/{bookingId}/confirm`

#### Purpose
Mentee xác nhận đã hoàn tất buổi học thành công. Hệ thống tiến hành giải ngân SCoin cho Mentor và kích hoạt quyền đánh giá (Feedback).

#### Response Body (`BookingResponse`)
```json
{
  "timestamp": "2026-08-05T09:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "bookingId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "bookingStatus": "COMPLETED",
    "paymentStatus": "PAID",
    "settlementStatus": "RELEASED",
    "completionOutcome": "USER_CONFIRMED",
    "displayState": "FEEDBACK_REQUIRED",
    "nextAction": "LEAVE_FEEDBACK",
    "releasedAt": "2026-08-05T09:30:00Z",
    "canCancel": false,
    "canComplete": false,
    "canReschedule": false,
    "canSubmitFeedback": true
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Thanh toán Đơn hàng Đã Chấp nhận (`Checkout Flow`)

```
Mentee (Frontend)                    Backend API                             Payment Gateway (VNPay/SCoin)
        |                                     |                                             |
   1. Nhận thông báo Mentor đã Accept         |                                             |
   2. Mở màn hình Checkout                    |                                             |
        |------------------------------------>|-- POST /me/bookings/{id}/checkout-preview --|
        |                                     |   (?couponCode=SUMMER2026)                  |
        |<-- Trả finalPayableScoin & Deadline |                                             |
   3. Bấm "Thanh toán bằng SCoin / Ví"        |                                             |
        |------------------------------------>|-- Khởi tạo Giao dịch Thanh toán ----------->|
        |<-- Trả checkoutUrl (nếu có VNPay) --|                                             |
   4. Trình duyệt chuyển hướng checkoutUrl ---->|-------------------------------------------->|
        |<-- Callback kết quả thanh toán -----|                                             |
   5. Backend cập nhật bookingStatus = CONFIRMED, paymentStatus = PAID                      |
   6. Frontend reload Booking Detail để cập nhật giao diện "Sẵn sàng tham gia"              |
```

---

## 8. State Machine (Ma trận Trạng thái Booking, Payment, Settlement & Outcome)

### 8.1 Ma trận Chuyển đổi Trạng thái Đơn Đặt lịch (`BookingLifecycleStatus`)

```
                                  +-----------------------+
                                  |       REQUESTED       | (Mentee gửi yêu cầu)
                                  +-----------------------+
                                   /          |          \
                 Mentor Reject /  /           |           \ Mentee Cancel /
                 Quá hạn 12h/3h  /            |            \ Hết hạn Chờ
                                v             v             v
                    +-------------------+ +-------------------+ +-----------------------+
                    | REJECTED_BY_MENTOR| |  WAITING_PAYMENT  | |  CANCELED_BY_MENTEE   |
                    +-------------------+ +-------------------+ +-----------------------+
                                              |          \
                                        Thanh toán    Thanh toán quá hạn 6h/1h
                                        thành công        \
                                              |            v
                                              v   +-----------------------+
                                  +-------------------+ |    PAYMENT_EXPIRED    |
                                  |     CONFIRMED     | +-----------------------+
                                  +-------------------+
                                   /          |          \
                     Báo báo Issue/           |           \ Mentor Cancel /
                     Tranh chấp              |            \ Hủy lịch
                           /                  v            v
                          v       +-------------------+ +-----------------------+
              +-----------------+ |     COMPLETED     | |   CANCELED_BY_MENTOR  |
              |  UNDER_REVIEW   | +-------------------+ +-----------------------+
              +-----------------+ (USER_CONFIRMED / AUTO_CLOSED)
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `INVALID_INPUT` | Minute-aligned startAt sai (không đúng 00 giây), hoặc learningGoal trống. | Báo lỗi form, yêu cầu nhập đúng thông tin. |
| `401 UNAUTHENTICATED` | `UNAUTHENTICATED` | Chưa xác thực tài khoản người dùng. | Kích hoạt luồng refresh token. |
| `409 RESOURCE_CONFLICT` | `RESOURCE_CONFLICT` | Segment bị trùng với booking khác, hoặc đơn đã đổi trạng thái bởi bên kia. | Reload lại Booking Detail để cập nhật UI mới nhất. |
| `409 RESOURCE_CONFLICT` | `AVAILABILITY_TEMPLATE_OCCURRENCE_UNAVAILABLE` | Lịch rảnh định kỳ của Mentor vừa bị sửa/hủy. | Yêu cầu user chọn lại phân đoạn thời gian khác. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Chống Thao tác Trái Quyền (Role & Ownership Enforcer)**:
   - Mentee không thể gọi các API `accept` / `reject` / `complete` của Mentor.
   - Mentor không thể gọi API `confirm` của Mentee.
   - Chỉ người dùng tham gia vào đơn (`participant`) mới được phép truy vấn thông tin chi tiết.
2. **Đảm bảo An toàn Giao dịch (Idempotency)**:
   - Header `Idempotency-Key` được bắt buộc với API `POST /api/bookings`. Nếu client retry do gián đoạn mạng, Backend cam kết không tạo trùng đơn.
3. **Hiển thị Link Google Meet Bảo mật**:
   - Link họp mặt (`meetingLink`) chỉ hiển thị khi `bookingStatus == CONFIRMED` và thời gian buổi học sắp diễn ra.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Sử dụng các cờ trợ lý UI (`canCancel`, `canComplete`, `canReschedule`, `canSubmitFeedback`) để bật/tắt các nút bấm hành động trên giao diện.
- Gọi lại `GET /api/me/bookings/{bookingId}` sau mỗi thao tác quan trọng để đảm bảo dữ liệu đồng bộ tuyệt đối với Backend.
- Sinh `Idempotency-Key` mới (chuỗi UUIDv4) trước mỗi lần người dùng bấm nút submit đặt lịch.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** tự suy đoán đơn đã hoàn thành chỉ dựa vào cờ `paymentStatus == PAID`.
- **KHÔNG ĐƯỢC** dùng các trường cũ (`status`, `sessionStatus`) để xây dựng luồng xử lý mới.
- **KHÔNG ĐƯỢC** tự động chốt trạng thái `AUTO_CLOSED` ở Client; việc này thuộc trách nhiệm của Scheduler Backend.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Hủy Lịch Đột xuất & Chính sách Hoàn tiền (`Cancellation Policy`)**:
   - Nếu Mentee hủy sớm (trước mốc quy định): Hoàn tiền lại theo tỷ lệ phần trăm cấu hình (`cancellationRefundPolicy`).
   - Nếu Mentor hủy lịch hoặc vắng mặt (`NO_SHOW_MENTOR`): Mentee luôn được hoàn lại **100%** số SCoin đã trả.
2. **Tranh chấp/Khiếu nại Buổi học (`UNDER_REVIEW`)**:
   - Khi có báo cáo sự cố (`Submit Issue`), đơn chuyển thành `UNDER_REVIEW`, tính năng chat chuyển sang chế độ Chỉ đọc (Read-only), và tiến trình giải ngân tiền ví bị tạm dừng cho đến khi Admin xử lý xong.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Mentor Service**: Đọc thông tin gói dịch vụ `MentorService` và cờ chat snapshot `maintainPostSessionChatSnapshot`.
- **Payment & Wallet Service**: Thực thi giao dịch trừ SCoin khi thanh toán và hoàn SCoin khi hủy đơn.
- **Notification Service**: Gửi thông báo đẩy (Push/Email) khi có yêu cầu mới, chấp nhận, hủy đơn, hoặc nhắc nhở buổi học.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Form Đặt Lịch Mentoring (`BookingRequestModal.tsx`)
- **React Components**: `BookingRequestModal.tsx`, `QuoteSummaryBox.tsx`, `LearningGoalInput.tsx`
- **APIs Triggered**:
  1. `POST /api/bookings/quote` (Tính toán báo giá ước tính & xem SLA)
  2. `POST /api/bookings` (Khi bấm "Gửi yêu cầu", truyền `Idempotency-Key`)
- **Expected Behavior**: Hiển thị bảng tóm tắt giá và thời gian hạn chót duyệt đơn. Khi tạo đơn 200 OK: Chuyển hướng sang màn hình Chi tiết Đơn hàng.

#### B. Màn hình Chi tiết Đơn Đặt Lịch (`BookingDetailPage.tsx`)
- **React Components**: `BookingDetailPage.tsx`, `BookingStatusBadge.tsx`, `ActionControlPanel.tsx`, `MeetingLinkBox.tsx`
- **APIs Triggered**:
  1. `GET /api/me/bookings/{bookingId}` (Khi mount trang)
  2. `POST /api/mentor/bookings/{id}/accept` hoặc `reject` (Thao tác của Mentor)
  3. `POST /api/me/bookings/{id}/confirm` (Thao tác của Mentee sau session)
- **Expected Behavior**: Hiển thị badge trạng thái chuẩn canonical. Render các nút bấm hành động tương ứng với cờ `canCancel`, `canComplete`, `canSubmitFeedback`.

#### C. Màn hình Thanh toán Checkout (`BookingCheckoutPage.tsx`)
- **React Components**: `BookingCheckoutPage.tsx`, `CouponInput.tsx`, `ScoinPayButton.tsx`
- **APIs Triggered**:
  1. `POST /api/me/bookings/{bookingId}/checkout-preview` (Khi nhập mã giảm giá)
  2. Executing payment -> Redirect gateway
- **Expected Behavior**: Hiển thị countdown timer đếm ngược hạn thanh toán (`paymentDeadlineAt`). Sau khi thanh toán thành công: Reload lại Booking Detail.

---

### 14.2 Frontend Booking State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |    DRAFT_REQUEST      | (User chọn slot & service)
                       +-----------------------+
                                   |
                          POST /api/bookings
                                   |
                                   v
                       +-----------------------+
                       |  WAITING_ACCEPTANCE   | (Chờ Mentor duyệt)
                       +-----------------------+
                                   |
                            Mentor Accept
                                   |
                                   v
                       +-----------------------+
                       |    WAITING_PAYMENT    | (Chờ Mentee thanh toán SCoin)
                       +-----------------------+
                                   |
                           Thanh toán 200 OK
                                   |
                                   v
                       +-----------------------+
                       |       CONFIRMED       | (Sẵn sàng tham gia buổi học)
                       +-----------------------+
                                   |
                           Session Hoàn tất
                                   |
                                   v
                       +-----------------------+
                       |       COMPLETED       | (Mentee xác nhận -> Mở Feedback)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Booking Modal | Fill Learning Goal | Submit Booking | Mentor Accept | Mentee Confirm | User Action |
| --- | --- | --- | --- | --- | --- | --- |
| `POST /api/bookings/quote` | ✅ CÓ (Read-only) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `POST /api/bookings` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (`Idempotency-Key`) | ❌ KHÔNG | ❌ KHÔNG | ✅ Bấm "Gửi Yêu cầu" |
| `GET /api/me/bookings/{id}` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (Sau Accept) | ✅ CÓ (Sau Confirm) | ❌ KHÔNG |
| `POST .../checkout-preview` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ Khi mở màn Checkout |
| `POST .../confirm` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ✅ Bấm "Xác nhận Học xong" |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Xung đột Khung giờ / Quá hạn SLA (`HTTP 409`)
- **UI Component**: Modal Báo lỗi Đặt lịch (`BookingConflictModal.tsx`).
- **Toast Message**: *"Khung giờ hoặc gói dịch vụ này vừa thay đổi trạng thái. Vui lòng chọn lại khung giờ rảnh mới."*
- **Action**: Nút "Tải lại Lịch rảnh".

#### B. Lỗi Đơn hàng Hết hạn Thanh toán (`PAYMENT_EXPIRED`)
- **UI Component**: Banner thông báo trên `BookingDetailPage.tsx`.
- **Visual State**: Banner màu xám cảnh báo hết hạn.
- **Message**: *"Thời gian chờ thanh toán cho buổi học này đã hết hạn. Đơn đặt lịch đã tự động hủy."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['booking', 'quote', slotId, serviceId]` | 0 ms | 2 phút | `false` | Thay đổi lựa chọn ô giờ/gói dịch vụ |
| `['booking', 'detail', bookingId]` | 0 ms | 15 phút | `true` | `accept`, `reject`, `confirm`, `cancel`, `checkout` thành công |
| `['my-bookings', filterState]` | 1 phút | 10 phút | `true` | Thực hiện bất kỳ mutation booking nào |
| `['checkout-preview', bookingId]` | 0 ms | 5 phút | `false` | Nhập mã coupon mới |
