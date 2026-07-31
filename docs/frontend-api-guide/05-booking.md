# Booking

## Mục tiêu
File này mô tả toàn bộ booking lifecycle, payment dependency, completion, issue/dispute và các action mentor/mentee phải gọi đúng thứ tự.

## API inventory
### Booking của mentee
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/bookings/quote` | Authenticated | mentee-capable | `BookingQuoteRequest` | `BookingQuoteResponse` | - | Read-only validation, estimate and deadlines; no booking/hold/reservation. |
| POST | `/api/bookings` | Authenticated | mentee-capable | `CreateBookingRequest` | `BookingResponse` | - | Tạo booking request mới |
| GET | `/api/me/bookings` | Authenticated | participant | `BookingListRequest` | `PageResponse<BookingResponse>` | `status`, `sessionStatus` legacy | Danh sách booking của tôi |
| GET | `/api/me/bookings/{bookingId}` | Authenticated | participant | path `bookingId` | `BookingResponse` | legacy fields present | Booking detail |
| POST | `/api/me/bookings/{bookingId}/cancel` | Authenticated | mentee | `CancelBookingRequest` | `BookingResponse` | - | Hủy booking của mentee |
| POST | `/api/me/bookings/{bookingId}/reschedule-requests` | Authenticated | mentee | `CreateBookingRescheduleRequest` | `BookingRescheduleRequestResponse` | - | Đề xuất đổi lịch |
| GET | `/api/me/bookings/{bookingId}/reschedule-requests` | Authenticated | participant | - | `BookingRescheduleRequestResponse[]` | - | Lịch sử đổi lịch |
| POST | `/api/me/bookings/reschedule-requests/{requestId}/accept` | Authenticated | participant | `RespondBookingRescheduleRequest` | `BookingRescheduleRequestResponse` | - | Accept reschedule |
| POST | `/api/me/bookings/reschedule-requests/{requestId}/reject` | Authenticated | participant | `RespondBookingRescheduleRequest` | `BookingRescheduleRequestResponse` | - | Reject reschedule |
| POST | `/api/me/bookings/{bookingId}/confirm` | Authenticated | mentee | `ConfirmBookingRequest` | `BookingResponse` | - | Mentee xác nhận sau session |
| POST | `/api/me/bookings/{bookingId}/complete` | Authenticated | mentee or legacy unified action | `CompleteBookingRequest` | `BookingResponse` | - | FE chỉ dùng khi màn/flow cần entrypoint chung |
| POST | `/api/me/bookings/{bookingId}/issue` | Authenticated | participant | `SubmitBookingIssueRequest` | `BookingIssueResponse` | - | Báo issue/dispute |
| POST | `/api/me/bookings/{bookingId}/issue/respond` | Authenticated | counterparty | `RespondBookingIssueRequest` | `BookingIssueResponse` | - | Counterparty phản hồi issue 1 lần |

### Booking của mentor
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/mentor/bookings/{bookingId}/accept` | Authenticated | mentor | `AcceptBookingRequest` | `BookingResponse` | - | Chấp nhận request |
| POST | `/api/mentor/bookings/{bookingId}/reject` | Authenticated | mentor | `RejectBookingRequest` | `BookingResponse` | - | Từ chối request |
| POST | `/api/mentor/bookings/{bookingId}/cancel` | Authenticated | mentor | `CancelBookingRequest` | `BookingResponse` | - | Hủy booking đã accept |
| POST | `/api/mentor/bookings/{bookingId}/reschedule-requests` | Authenticated | mentor | `CreateBookingRescheduleRequest` | `BookingRescheduleRequestResponse` | - | Mentor đề xuất đổi lịch |
| POST | `/api/mentor/bookings/reschedule-requests/{requestId}/accept` | Authenticated | mentor | `RespondBookingRescheduleRequest` | `BookingRescheduleRequestResponse` | - | Mentor accept đổi lịch |
| POST | `/api/mentor/bookings/reschedule-requests/{requestId}/reject` | Authenticated | mentor | `RespondBookingRescheduleRequest` | `BookingRescheduleRequestResponse` | - | Mentor reject đổi lịch |
| POST | `/api/mentor/bookings/{bookingId}/complete` | Authenticated | mentor | `CompleteBookingRequest` | `BookingResponse` | - | Mentor mark complete |
| PATCH | `/api/mentor/bookings/{bookingId}/meeting-link` | Authenticated | mentor | `SaveMeetingLinkRequest` | `BookingResponse` | - | Lưu/cập nhật meeting link |

### Pricing và checkout
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/mentor-services/{serviceId}/pricing-preview` | Authenticated | active user | path `serviceId` | `ServicePricingPreviewResponse` | Campaign-aware browse estimate only; no coupon, wallet, hold, or reservation. |
| POST | `/api/me/bookings/{bookingId}/checkout-preview` | Authenticated | booking mentee | `PaymentCheckoutPreviewRequest` | `PaymentCheckoutPreviewResponse` | Only while `ACCEPTED_AWAITING_PAYMENT`; estimate only, including optional coupon and current wallet/campaign value. |

### Availability slot
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/me/availability-slots` | Authenticated | mentor | `CreateAvailabilitySlotRequest` | `MentorManagedAvailabilitySlotResponse` | - | Tạo slot rảnh |
| GET | `/api/me/availability-slots` | Authenticated | mentor | query `fromDate`, `toDate` | `MentorManagedAvailabilitySlotResponse[]` | - | Danh sách slot của mentor |
| PUT | `/api/me/availability-slots/{slotId}` | Authenticated | mentor | `UpdateAvailabilitySlotRequest` | `MentorManagedAvailabilitySlotResponse` | - | Sửa slot |
| POST | `/api/me/availability-slots/{slotId}/deactivate` | Authenticated | mentor | `DeactivateAvailabilitySlotRequest` + `Idempotency-Key` | `MentorManagedAvailabilitySlotResponse` | - | Deactivate terminal; không có reactivate |

## Call order chuẩn
### Tạo booking
1. FE mở mentor detail để xem service và availability.
2. FE chọn parent availability slot.
3. FE chọn service trong slot.
4. FE chọn exact `startAt` theo candidate hợp lệ. `endAt` là derived data của backend, không gửi khi create booking.
5. FE có thể gọi `POST /api/bookings/quote` với cùng `slotId`, `serviceId`, `startAt` để lấy kiểm tra read-only, `pendingExpireAt`, pricing estimate và policy hoàn/hủy.
6. Quote không giữ slot, không tạo booking, không reserve campaign/coupon/wallet và không phải giá cuối cùng. FE vẫn phải xử lý candidate stale khi submit.
7. FE tạo một `Idempotency-Key` opaque mới và gọi `POST /api/bookings`.
8. FE giữ `bookingId` để đi tiếp sang payment hoặc chờ mentor action.

### Mentor phản hồi
- `accept`
  - booking đi sang chờ payment hoặc confirmed nếu service free
- `reject`
  - booking kết thúc ở nhánh từ chối
- `cancel`
  - chỉ dùng khi booking đã accept hoặc đã ở trạng thái phù hợp backend

### Payment và confirm
- Nếu service có phí:
  1. Sau khi mentor accept, FE gọi `POST /api/me/bookings/{bookingId}/checkout-preview` với `couponCode` tùy chọn để hiển thị `couponDiscountScoin`, campaign credit, wallet credit, `finalPayableScoin` và `paymentDeadlineAt`.
  2. Checkout preview chỉ là estimate: không tạo payment order/link, không reserve coupon/campaign/wallet và không gọi payment provider.
  3. FE checkout payment; checkout thật tính lại toàn bộ số tiền một cách authoritative.
  4. FE chờ webhook/provider sync. Booking chuyển sang confirmed khi payment hoàn tất.
- Nếu service free:
  - booking có thể đi thẳng sang confirmed sau accept.

### Sau session
1. Mentor gọi `POST /api/mentor/bookings/{bookingId}/complete`.
2. Mentee gọi `POST /api/me/bookings/{bookingId}/confirm`.
3. Nếu có vấn đề, participant gọi `POST /api/me/bookings/{bookingId}/issue`.
4. Counterparty phản hồi issue bằng `issue/respond`.

### Feedback
`POST /api/bookings/{bookingId}/feedback` chi danh cho mentee cua booking co `completionOutcome=USER_CONFIRMED`. Moi booking chi co mot feedback. Auto-close, no-show va admin-resolved outcome khong mo feedback.

## Booking chat snapshot
`BookingResponse.maintainPostSessionChatSnapshot` là snapshot của service policy tại lúc tạo booking. FE không được đọc lại policy hiện tại của service để suy luận quyền chat cho booking cũ.

- Booking effective tạo hoặc liên kết vào một direct conversation dài hạn của cặp mentor/mentee và sinh đúng một system message `BOOKING_CONFIRMED`.
- Snapshot `true` chỉ cấp chat dài hạn sau `USER_CONFIRMED` hoặc `AUTO_CLOSED`.
- Refund, cancel, reject, expire hoặc no-show không tự cấp quyền dài hạn; một refund không thu hồi quyền dài hạn do booking hợp lệ trước đó cấp.
- Khi booking `UNDER_REVIEW`, chat chuyển read-only tạm thời cho cả hai bên đến khi dispute được resolve.
- `BookingResponse.maintainPostSessionChatSnapshot` is canonical for that booking; service changes never rewrite an existing booking's chat promise.

## Canonical state model
### FE phải đọc `bookingStatus`
- `REQUESTED`
- `WAITING_PAYMENT`
- `CONFIRMED`
- `REJECTED_BY_MENTOR`
- `CANCELED_BY_MENTEE`
- `CANCELED_BY_MENTOR`
- `REQUEST_EXPIRED`
- `PAYMENT_EXPIRED`
- `UNDER_REVIEW`
- `COMPLETED`

### FE phải đọc `paymentStatus`
- `NOT_REQUIRED`
- `PENDING`
- `PAID`
- `FAILED`
- `EXPIRED`
- `REFUNDED`

### FE phải đọc `settlementStatus`
- `HELD`
- `RELEASED`
- `REFUNDED`

### FE phải đọc `completionOutcome`
- `USER_CONFIRMED`
- `AUTO_CLOSED`
- `UNDER_REVIEW`
- `NO_SHOW_MENTEE`
- `NO_SHOW_MENTOR`

## Mapping legacy -> canonical
| Persisted `BookingStatus` | Public `BookingLifecycleStatus` |
| --- | --- |
| `PENDING` | `REQUESTED` |
| `ACCEPTED_AWAITING_PAYMENT` | `WAITING_PAYMENT` |
| `PAID`, `ACCEPTED`, `AWAITING_MENTOR_COMPLETION`, `AWAITING_MENTEE_CONFIRMATION` | `CONFIRMED` |
| `REJECTED` | `REJECTED_BY_MENTOR` |
| `EXPIRED` | `REQUEST_EXPIRED` |
| `CANCELLED_BY_MENTEE` | `CANCELED_BY_MENTEE` |
| `CANCELLED_BY_MENTOR` | `CANCELED_BY_MENTOR` |
| `UNDER_REVIEW` | `UNDER_REVIEW` |
| `COMPLETED`, `AUTO_CLOSED`, `NO_SHOW` | `COMPLETED` |

## Ý nghĩa field quan trọng
### `BookingResponse`
- `bookingStatus`
  - canonical booking state
- `paymentStatus`
  - trạng thái payment riêng
- `settlementStatus`
  - trạng thái settlement riêng
- `completionOutcome`
  - kết quả sau session
- `releasedAt`, `refundedAt`, `refundedScoin`, `refundReason`
  - settlement summary cho FE hiển thị
- `canCancel`, `canComplete`, `canReschedule`, `canSubmitFeedback`
  - UI hint, không phải source of truth duy nhất
- `status`, `sessionStatus`
  - legacy/deprecated

### Request DTO
- `CreateBookingRequest`
  - `slotId`, `serviceId`, minute-aligned UTC `startAt`, learning goal. Backend derive `scheduledEndAt` từ immutable service duration.
- `AcceptBookingRequest`
  - note tùy chọn của mentor
- `RejectBookingRequest`
  - lý do reject
- `CancelBookingRequest`
  - lý do hủy
- `CompleteBookingRequest`
  - note hoàn thành
- `ConfirmBookingRequest`
  - note xác nhận của mentee
- `SubmitBookingIssueRequest`
  - loại issue + mô tả
- `RespondBookingIssueRequest`
  - phản hồi của counterparty
- `CreateBookingRescheduleRequest`
  - đề xuất lịch mới
- `RespondBookingRescheduleRequest`
  - lý do accept/reject
- `SaveMeetingLinkRequest`
  - meeting link/location

## FE phải làm
- Chỉ dùng `bookingStatus` + `paymentStatus` + `settlementStatus` + `completionOutcome` để render flow.
- Dùng `canCancel`, `canComplete`, `canReschedule`, `canSubmitFeedback` như UI hint.
- Gọi booking detail sau mỗi action quan trọng để refresh canonical state.
- Đọc lại state trước khi cho user bấm action tiếp theo.
- Giữ nguyên cùng `Idempotency-Key` và body khi retry create sau network timeout. Không dùng lại key cho booking khác.

## FE không được làm
- Không suy ra completed chỉ từ `PAID`.
- Không dùng legacy `status/sessionStatus` để build flow mới.
- Không cho review nếu outcome không phải `USER_CONFIRMED`.
- Không auto-close ở FE; auto-close là backend/scheduler responsibility.
- Không suy diễn settlement release từ payment success một mình.
- Không gửi legacy `availabilitySlotId`, `selectedStartTime` hoặc `selectedEndTime` trong create request.

## Availability management reset
### UTC và date range
- Slot boundaries và booking scheduled boundaries là UTC `Instant`, bắt buộc đúng phút (`seconds=0`, `nanoseconds=0`). Actual session/payment/provider timestamps không có rule này.
- Mọi overlap dùng `[startAt, endAt)`: segment liền kề không overlap.
- `GET /api/me/availability-slots` bắt buộc có cả `fromDate` và `toDate`; range là ngày local inclusive trong timezone policy mentor, server đổi sang `[fromDate 00:00, toDate + 1 day 00:00)` UTC. Range tối đa xem ở `/api/me/mentor-scheduling-constraints`.

### Slot mutation
- `CreateAvailabilitySlotRequest` nhận `startAt`, `endAt`, `note`, `serviceIds` non-empty. `serviceIds` là set: thứ tự/duplicate không có nghĩa nghiệp vụ.
- `UpdateAvailabilitySlotRequest` là full replacement và bắt buộc `expectedVersion`. Note-only được phép; đổi time hoặc gỡ service có booking locking bị block.
- Khi chỉ có pending bị ảnh hưởng, server trả 409 với lý do `SLOT_HAS_PENDING_BOOKINGS`; FE phải reload slot, hiển thị xác nhận, rồi retry với `rejectPendingBookings=true` sau khi backend hỗ trợ confirmation token.
- `POST .../deactivate` yêu cầu `expectedVersion`. Slot inactive vẫn thấy ở management list nhưng không thể reactivate; request mới vào slot inactive bị conflict.

### Candidate field mapping
- `isSelectable=true`: FE có thể submit `startAt` vào create booking.
- `blockReason`: `PAST_OR_STARTED`, `OUTSIDE_BOOKING_POLICY`, `LOCKING_BOOKING_OVERLAP`, `PENDING_QUOTA_REACHED`. Đây là hint read-side; submit vẫn phải xử lý conflict latest từ backend.
- Khi `serviceId` bị omit ở reader slot list, UI chỉ nhận service còn ít nhất một candidate selectable; không hiển thị service không thể book trong parent slot đó.

## Response JSON example
### Booking detail
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "bookingId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "sessionId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "sessionStatus": "PAID",
    "actualSessionId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "actualSessionStatus": "PAID",
    "bookingStatus": "CONFIRMED",
    "paymentStatus": "PAID",
    "settlementStatus": "HELD",
    "completionOutcome": null,
    "releasedAt": null,
    "refundedAt": null,
    "refundedScoin": null,
    "refundReason": null,
    "canCancel": false,
    "canComplete": true,
    "canReschedule": false,
    "canSubmitFeedback": false
  }
}
```

### Issue response
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "issueId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
    "bookingId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "issueType": "MENTOR_NO_SHOW",
    "status": "UNDER_REVIEW",
    "respondedAt": null
  }
}
```

## UI mapping
- Booking request form:
  - dùng `CreateBookingRequest`
- Booking list:
  - map từ `bookingStatus`, `paymentStatus`, `settlementStatus`
- Payment banner:
  - chỉ show khi `bookingStatus=WAITING_PAYMENT`
- Completion action:
  - mentor/mentee action buttons hiển thị theo `canComplete`, `canSubmitFeedback`
- Issue screen:
  - hiển thị riêng cho dispute/report flow, không trộn vào cancel flow

## Payment redirect flow
### Khi nào redirect
- Chỉ redirect sang payment provider khi backend trả `PaymentCheckoutResponse.checkoutUrl`.
- Chỉ redirect cho booking cần thanh toán, tức booking đang ở nhánh `WAITING_PAYMENT`.

### Flow chuẩn
1. FE gọi checkout API.
2. Backend trả `checkoutUrl`, `expiresAt`, `remainingPayableScoin/Vnd`.
3. FE redirect user sang `checkoutUrl`.
4. Sau khi user quay lại app, FE gọi booking detail hoặc payment order detail để refresh.
5. FE không tự set booking thành confirmed từ URL return.

### Khi nào không redirect
- Booking free.
- Booking đã hết hạn.
- Booking bị conflict hoặc bị backend từ chối checkout.

## Permission matrix
| Action | Mentee | Mentor | Điều kiện |
| --- | --- | --- | --- |
| Create booking | ✅ | ❌ | chỉ user đang book mentor |
| Accept booking | ❌ | ✅ | booking phải thuộc mentor |
| Reject booking | ❌ | ✅ | booking phải thuộc mentor |
| Cancel booking | ✅ | ✅ | chỉ ở trạng thái cho phép backend |
| Complete booking | ✅ | ✅ | theo role và trạng thái session |
| Confirm booking | ✅ | ❌ | chỉ mentee sau session |
| Report issue | ✅ | ✅ | đúng issue type và đúng role reporter |
| Respond issue | ✅ | ✅ | chỉ counterparty của người report |
| Checkout payment | ✅ | ❌ | booking đang chờ thanh toán |

## API success/error behavior
- `POST /api/bookings/quote` và `POST /api/me/bookings/{bookingId}/checkout-preview`
  - success: render price/deadline as estimate only; submit/checkout vẫn có thể thay đổi vì campaign, coupon, wallet và availability được revalidated.
  - 400/409: refetch candidate hoặc booking canonical before allowing a new attempt; never treat a preview as a reservation.
- `POST /api/bookings`
  - success: giữ `bookingId`, refresh detail và chờ mentor/payment
  - 409: slot/service không còn hợp lệ, phải chọn lại
- `accept/reject`
  - success: refresh booking detail
  - 409: booking đã bị xử lý bởi người khác trước đó
- `checkout`
  - success: redirect `checkoutUrl`
  - 409/400: booking không còn ở trạng thái chờ thanh toán
- `complete/confirm`
  - success: refresh detail và chờ settlement auto/manual
  - 409: session đã bị chốt theo trạng thái khác
- `issue/respond`
  - success: refresh issue detail + booking detail
  - 400: sai vai trò responder hoặc issue type không hợp lệ

## Ghi chú cho AI Agent và FE dev
- `BookingStatus` là persisted internal state, không phải field FE tự invent.
- `bookingStatus`, `paymentStatus`, `settlementStatus`, `completionOutcome` mới là canonical contract cho UI.
- `AUTO_CLOSED` là outcome, không phải trạng thái FE tự set.

## Display Guidance
`BookingResponse` also has read-only UI guidance: `displayState`, `nextAction`, `actionDeadlineAt`. It reduces duplicated FE mapping but never replaces canonical lifecycle fields.

`pendingExpireAt` is the mentor-response SLA for a pending booking. For a pending booking, it is `min(createdAt + 12h, scheduledStartAt - 3h)` and is also returned as `actionDeadlineAt`. The server expires the request after that deadline and notifies both parties. A payment-required booking uses `min(acceptedAt + 6h, scheduledStartAt - 1h)` as its payment deadline and `actionDeadlineAt`.

`cancellationRefundPolicy` is a read model of the current platform policy, not a per-service promise. It exposes the early cancellation threshold, early/late mentee refund percentages, mentor-cancellation refund percentage and mentor-no-show refund percentage.

| `displayState` | Typical FE treatment |
| --- | --- |
| `PENDING_MENTOR_RESPONSE` | Mentee waits for mentor. |
| `PAYMENT_REQUIRED` | Show checkout only when `nextAction=PAY_NOW`. |
| `MENTOR_ACTION_REQUIRED` | Mentor accepts/rejects or marks the completed session. |
| `UPCOMING`, `IN_SESSION` | Show schedule and meeting entry. |
| `WAITING_CONFIRMATION` | Mentee confirms before `actionDeadlineAt`. |
| `UNDER_REVIEW` | Show issue status; no settlement inference. |
| `FEEDBACK_REQUIRED` | Mentee can call `POST /api/bookings/{bookingId}/feedback`. |
| `COMPLETED`, `CANCELED_OR_EXPIRED` | Terminal presentation. |

Feedback is accepted only after `completionOutcome=USER_CONFIRMED`. Auto-close, no-show and admin-resolved outcomes do not unlock feedback.

## Group-session seat booking
Group sessions are available only while `APPLICATION_GROUP_SESSIONS_ENABLED=true`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/group-sessions?mentorUserId=&serviceId=&from=&cursor=&limit=` | Public upcoming sessions with only snapshot offer, schedule and seat availability. |
| GET | `/api/group-sessions/{groupSessionId}` | Public session detail; never exposes attendee data. |
| POST | `/api/group-sessions/{groupSessionId}/bookings` | Authenticated learner creates one seat booking with learning-goal fields. |

`POST /api/group-sessions/{groupSessionId}/bookings` atomically creates the booking and increments `reservedSeatCount`. For a paid session its internal status is `ACCEPTED_AWAITING_PAYMENT`; the booking itself is the seat hold and checkout creates the payment order later. For a free session the internal status is `PAID`, while the response remains `bookingStatus=CONFIRMED` and `paymentStatus=NOT_REQUIRED`.

The response is the existing `BookingResponse` with `bookingType=GROUP_SESSION`, `groupSessionId` and a compact `groupSession` summary (ID, snapshot service title, schedule and registration close). Group payment deadline is `min(acceptedAt + 6h, registrationClosesAt)`. Checkout, payment webhooks, coupon, campaign, wallet, expiry and refund all stay booking-ID based.

One learner can have one active seat per group session. Pending payment holds, paid seats and other locking bookings participate in learner calendar overlap checks. On payment expiry or learner cancellation the seat is released exactly once. Mentor cancellation releases all seats and fully refunds paid attendees. A late successful webhook after cancellation is compensated and never restores a seat.

Group seats do not use mentor accept/reject or rescheduling. Phase 3 adds one shared session/conversation per GroupSession: meeting details and chat appear only after the seat is confirmed. Mentor submits attendance after the event; `PRESENT` seats enter a 24-hour learner confirmation window, while `MENTEE_NO_SHOW` uses the existing issue path. Feedback remains available only after `USER_CONFIRMED`.

Published group-session intervals remain excluded from 1:1 candidate selection. FE must refetch candidates after a `409` conflict rather than assuming a parent availability slot is fully unavailable.

## Service-resource entitlement
Tài liệu `BOOKED_MEMBERS` không được mở chỉ vì booking tồn tại. Backend dùng booking-owned policy: booking ở canonical `CONFIRMED` hoặc `COMPLETED` mới cấp quyền. Service inactive không thu hồi quyền lịch sử; resource bị mentor xóa mềm thì quyền download bị thu hồi ngay.

## Premium Blog entitlement
`BOOKED_MEMBERS` Blog follows the same booking-owned boundary as service resources, but is read through `GET /api/me/blog/library?serviceId=`. Blog never reads raw booking states. FE must not show premium posts in generic feed/search/trending, and must treat a `404` from a premium detail as non-enumerating access denial.
