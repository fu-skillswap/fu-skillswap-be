# Frontend Guide - Booking Mentoring 1:1

> Guide nay chi mo ta booking mentoring `ONE_TO_ONE`. Khong dung cho group session hay learning course.

> **Envelope chung:** Tat ca response nam trong `ApiResponse<T>`. Xem [identity.md](identity.md) de xu ly access token, refresh token, validation va `429 Retry-After`.

> **Cac guide lien quan:** Tim mentor va chon candidate nam trong [mentor-discovery.md](mentor-discovery.md). Mentor quan ly service va lich ranh nam trong [mentor-service.md](mentor-service.md). Thanh toan sau khi mentor accept nam trong [payment.md](payment.md). Chat chi mo khi booking da du dieu kien, xem [realtime-chat-notification.md](realtime-chat-notification.md).

## 1. Phan quyen va quy tac thoi gian

| Thao tac | Role duoc phep |
| --- | --- |
| Quote, tao booking, xem booking cua minh | `MENTEE` hoac `MENTOR` |
| Accept, reject, huy voi tu cach mentor, cap nhat meeting, complete voi tu cach mentor | `MENTOR` va phai la mentor cua booking |
| Cancel voi tu cach mentee, confirm, issue, feedback | Participant dung cua booking |
| Tao booking hoac thao tac booking truc tiep | `ADMIN`, `SYSTEM_ADMIN` bi chan |

Mentor co the dat lich voi mentor khac nhu mot mentee. Backend luon kiem tra ownership cua booking; FE khong gui `mentorUserId` de tu cap quyen.

### 1.1 Quy tac timestamp

- `startAt` khi quote va create booking la `Instant` UTC, vi du `2026-06-30T12:00:00Z`.
- Gia tri nay phai lay tu `candidateServiceSlots[].startTime` cua API candidate, khong tu tinh tu gio preview.
- Cac timestamp trong `BookingResponse` va reschedule request la `LocalDateTime` theo gio nghiep vu `Asia/Ho_Chi_Minh`. Khong tu them hau to `Z` khi parse/hien thi cac gia tri nay.

## 2. Luong booking 1:1

```text
Mentee chon mentor, service va exact candidate
-> quote (khong giu cho)
-> create booking: PENDING / REQUESTED
-> mentor accept
   -> service free: booking da CONFIRMED
   -> service co phi: WAITING_PAYMENT
      -> mentee checkout va provider xac nhan thanh cong
      -> CONFIRMED
-> buoi hoc ket thuc
-> mentor complete
-> mentee confirm hoac bao issue
-> COMPLETED hoac UNDER_REVIEW
```

`bookingStatus`, `paymentStatus`, `displayState`, `nextAction` va cac co `can*` trong response la source of truth cho UI. Khong tu suy dien action tu `status`, `sessionId` hay `sessionStatus`: day la field legacy.

### 2.1 Trang thai de hien thi

| Field | Gia tri quan trong | FE can hien thi |
| --- | --- | --- |
| `bookingStatus` | `REQUESTED` | Dang cho mentor phan hoi |
| `bookingStatus` | `WAITING_PAYMENT` | Mentee can thanh toan |
| `bookingStatus` | `CONFIRMED` | Lich da duoc chot |
| `bookingStatus` | `REJECTED_BY_MENTOR` | Mentor tu choi |
| `bookingStatus` | `CANCELED_BY_MENTEE`, `CANCELED_BY_MENTOR`, `REQUEST_EXPIRED` | Booking da dung/het han |
| `bookingStatus` | `UNDER_REVIEW` | Dang xu ly issue |
| `bookingStatus` | `COMPLETED` | Booking ket thuc |
| `paymentStatus` | `NOT_REQUIRED`, `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `REFUNDED` | Trang thai thanh toan tach rieng voi booking |
| `displayState` | `PAYMENT_REQUIRED`, `MENTOR_ACTION_REQUIRED`, `WAITING_CONFIRMATION`, `FEEDBACK_REQUIRED`, ... | Nhom UI backend da tinh theo role va thoi diem |
| `nextAction` | `PAY_NOW`, `ACCEPT_OR_REJECT`, `COMPLETE_SESSION`, `CONFIRM_SESSION`, `LEAVE_FEEDBACK`, `VIEW_ISSUE`, `NONE` | CTA uu tien cua man hinh |

Dung `actionDeadlineAt` khi co gia tri de hien thi countdown. Dung `canCancel`, `canComplete`, `canReschedule`, `canSubmitFeedback` de bat/tat nut. Van phai xu ly `409` vi du lieu co the thay doi giua luc render va luc user bam nut.

## 3. Mentee tao booking

### 3.1 Tai lai candidate sau login

1. Tu public preview, chi luu `mentorUserId`, service du kien va gio du kien trong state client.
2. Sau khi dang nhap, goi availability authenticated va candidate theo [mentor-discovery.md](mentor-discovery.md).
3. Chon candidate co `isSelectable = true`.
4. Goi quote ngay truoc modal xac nhan.
5. Tao booking voi cung `slotId`, `serviceId`, `startAt` cua candidate vua tai.

Khong dung `availability-preview` de create booking: preview khong tra `slotId`, quota hay booking conflict hien tai.

### 3.2 Quote truoc khi dat

`POST /api/bookings/quote` yeu cau Bearer token (`MENTEE` hoac `MENTOR`). Quote chi la uoc tinh, khong giu slot.

```ts
interface BookingQuoteRequest {
  slotId: string;
  serviceId: string;
  startAt: string; // ISO Instant UTC tu candidate
}

interface BookingQuoteResponse {
  slotId: string;
  serviceId: string;
  serviceTitle: string;
  durationMinutes: number;
  scheduledStartAt: string; // LocalDateTime
  scheduledEndAt: string; // LocalDateTime
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
    mentorCancellationRefundPercent: number;
    mentorNoShowRefundPercent: number;
  };
  isEstimate: boolean;
  disclaimer: string | null;
}
```

Hien thi `pricing.estimatedPayableScoin` va `disclaimer` dung nhu backend tra ve. Khong tu tinh phu phi, campaign hay refund policy o FE.

### 3.3 Create booking

`POST /api/bookings` yeu cau:

- Bearer token.
- Header **bat buoc** `Idempotency-Key: <uuid-hoac-chuoi-duy-nhat>`.
- Cung mot key chi dung lai voi dung `POST /api/bookings` va dung payload. Key duoc luu de replay response thanh cong; dung lai voi payload khac se bi `409`.

```ts
interface CreateBookingRequest {
  slotId: string;
  serviceId: string;
  startAt: string; // ISO Instant UTC, whole minute, lay tu candidate
  learningGoalTitle: string; // 1-200 ky tu
  learningGoalDescription?: string; // Toi da 2000 ky tu
}
```

Vi du:

```ts
await apiClient.post(
  "/api/bookings",
  {
    slotId,
    serviceId,
    startAt: selectedCandidate.startTime,
    learningGoalTitle: "Review lo trinh Spring Boot",
    learningGoalDescription: "Can goi y project va cach chuan bi intern backend.",
  },
  { headers: { "Idempotency-Key": crypto.randomUUID() } },
);
```

Neu request timeout, retry bang **cung key va cung payload**. Neu user sua muc tieu hoc hoac chon candidate khac, tao key moi. Khong retry mu khi nhan `409`; tai lai availability/candidate truoc.

## 4. Danh sach va detail booking

### 4.1 Danh sach cua user hien tai

`GET /api/me/bookings?role=MENTEE|MENTOR&status=<BookingStatus>&page=0&size=10&sortBy=selectedStartTime&direction=DESC`

- `role` mac dinh `MENTEE`.
- `status` la filter theo persisted `BookingStatus`, vi du `PENDING`, `ACCEPTED_AWAITING_PAYMENT`, `PAID`, `REJECTED`, `EXPIRED`, `CANCELLED_BY_MENTEE`, `CANCELLED_BY_MENTOR`, `AWAITING_MENTOR_COMPLETION`, `AWAITING_MENTEE_CONFIRMATION`, `COMPLETED`, `AUTO_CLOSED`, `UNDER_REVIEW`, `NO_SHOW`.
- Khong co query `fromDate` hay `toDate` trong API hien tai.
- Khi khong loc `status`, backend tra dashboard window tu 7 ngay truoc den 7 ngay sau va sap xep theo uu tien action. Khong dung endpoint nay de hien thi lich su toan bo theo thang.

### 4.2 Detail

`GET /api/me/bookings/{bookingId}` chi cho mentee hoac mentor cua booking. `403` nghia la booking khong thuoc user hien tai.

FE co the dung cung `BookingResponse` cho list va detail. Cac field can dung tren UI:

```ts
interface BookingResponse {
  bookingId: string;
  actualSessionId: string | null;
  actualSessionStatus: string | null;
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
  canComplete: boolean;
  canReschedule: boolean;
  canSubmitFeedback: boolean;
  cancellationRefundPolicy: {
    earlyMenteeCancellationDeadlineMinutes: number;
    earlyMenteeRefundPercent: number;
    lateMenteeRefundPercent: number;
    mentorCancellationRefundPercent: number;
    mentorNoShowRefundPercent: number;
  };
  displayState: string;
  nextAction: string | null;
  actionDeadlineAt: string | null;
}
```

Khong dung `sessionId`, `sessionStatus` hay `status` trong response moi. Chung la alias legacy con lai de tuong thich nguoc.

## 5. Thao tac mentor

Tat ca endpoint o phan nay can role `MENTOR` va backend tu kiem tra booking co thuoc mentor hien tai hay khong.

### 5.1 Accept hoac reject request

| Thao tac | Endpoint | Request |
| --- | --- | --- |
| Accept | `POST /api/mentor/bookings/{bookingId}/accept` | `{ mentorResponseNote?: string }`, toi da 2000 ky tu |
| Reject | `POST /api/mentor/bookings/{bookingId}/reject` | `{ rejectReason: string, mentorResponseNote?: string }`, moi field toi da 2000 ky tu |

Chi hien thi accept/reject khi `nextAction = "ACCEPT_OR_REJECT"` hoac booking dang `REQUESTED` theo response moi tai lai.

Accept slot se auto reject cac request pending xung dot. Voi service co phi, accept chi dua booking sang `WAITING_PAYMENT`; **khong** mo chat/session truoc khi payment thanh cong. Voi service free, booking co the duoc confirmed ngay.

### 5.2 Huy booking

`POST /api/mentor/bookings/{bookingId}/cancel`

```ts
interface CancelBookingRequest {
  cancelReason: string; // 1-1000 ky tu
}
```

Chi hien thi nut khi `canCancel = true`. Backend ap dung refund, penalty va suspension theo thoi diem thuc te; hien thi policy tu `cancellationRefundPolicy` trong response thay vi hard-code. Endpoint bi rate limit `3 request / gio / user`.

### 5.3 Complete va meeting details

| Thao tac | Endpoint | Request |
| --- | --- | --- |
| Mentor complete | `POST /api/mentor/bookings/{bookingId}/complete` | `{ completionNote?: string }`, toi da 2000 ky tu |
| Luu meeting | `PATCH /api/mentor/bookings/{bookingId}/meeting-link` | Xem schema ben duoi |

```ts
interface SaveMeetingLinkRequest {
  meetingPlatform:
    | "GOOGLE_MEET" | "ZOOM" | "MICROSOFT_TEAMS" | "DISCORD" | "OFFLINE" | "OTHER";
  meetingLink: string; // Bat buoc, toi da 1000 ky tu
  location?: string; // Toi da 500 ky tu
}
```

Neu `googleCalendarManaged = true`, khong hien thi form ghi de meeting link. Calendar duoc dong bo bat dong bo; `calendarSyncStatus` va thong tin loi chi de hien thi trang thai, khong phai dieu kien de mentee tham gia booking.

## 6. Thao tac mentee va participant

### 6.1 Mentee huy booking

`POST /api/me/bookings/{bookingId}/cancel` dung cung `CancelBookingRequest`. Chi hien thi khi `canCancel = true`. Endpoint dung chung rate limit `3 request / gio / user`.

### 6.2 Reschedule

Chi hien thi action khi `canReschedule = true`. Hien tai moi booking co toi da mot lan reschedule va request phai duoc tao truoc gio hoc it nhat 6 gio. Participant con lai phai phan hoi truoc moc 2 gio truoc gio hoc cu.

| Actor | Tao request | Accept/reject |
| --- | --- | --- |
| Mentee | `POST /api/me/bookings/{bookingId}/reschedule-requests` | `POST /api/me/bookings/reschedule-requests/{requestId}/accept` hoac `/reject` |
| Mentor | `POST /api/mentor/bookings/{bookingId}/reschedule-requests` | `POST /api/mentor/bookings/reschedule-requests/{requestId}/accept` hoac `/reject` |

```ts
interface CreateBookingRescheduleRequest {
  proposedSlotId: string;
  proposedSelectedStartTime: string; // LocalDateTime theo Asia/Ho_Chi_Minh
  proposedSelectedEndTime: string; // LocalDateTime theo Asia/Ho_Chi_Minh
  reason: string; // 1-1000 ky tu
}

interface RespondBookingRescheduleRequest {
  reason: string; // 1-1000 ky tu, bat buoc ca khi accept va reject
}

interface BookingRescheduleRequestResponse {
  rescheduleRequestId: string;
  bookingId: string;
  currentSlotId: string;
  proposedSlotId: string;
  previousSelectedStartTime: string;
  previousSelectedEndTime: string;
  proposedSelectedStartTime: string;
  proposedSelectedEndTime: string;
  requesterRole: "MENTEE" | "MENTOR" | "ADMIN";
  requestedByUserId: string;
  responderRole: "MENTEE" | "MENTOR" | "ADMIN" | null;
  respondedByUserId: string | null;
  status: "PENDING" | "ACCEPTED" | "REJECTED" | "EXPIRED";
  requestReason: string;
  responseNote: string | null;
  adminOverride: boolean;
  requestedAt: string;
  respondedAt: string | null;
  expiredAt: string | null;
}
```

Sau accept/reject, tai lai booking detail va reschedule history. Khong tu doi gio tren UI truoc khi backend accept thanh cong.

### 6.3 Complete, confirm, issue va feedback

| Thao tac | Endpoint | Khi nao dung |
| --- | --- | --- |
| Complete alias | `POST /api/me/bookings/{bookingId}/complete` | Chi dung khi UI chung cho participant; mentor se complete, participant con lai se confirm |
| Confirm | `POST /api/me/bookings/{bookingId}/confirm` | Participant con lai xac nhan sau khi mentor complete |
| Submit issue | `POST /api/me/bookings/{bookingId}/issue` | Participant bao van de trong cua so sau buoi hoc |
| Respond issue | `POST /api/me/bookings/{bookingId}/issue/respond` | Chi counterparty, toi da mot phan hoi |
| Feedback | `POST /api/bookings/{bookingId}/feedback` | Chi mentee, khi `canSubmitFeedback = true` |

```ts
interface CompleteBookingRequest {
  completionNote?: string; // Toi da 2000 ky tu
}

interface ConfirmBookingRequest {
  confirmationNote?: string; // Toi da 2000 ky tu
}

interface SubmitBookingIssueRequest {
  issueType:
    | "MENTOR_NO_SHOW" | "MENTEE_NO_SHOW" | "QUALITY_ISSUE"
    | "TECHNICAL_PROBLEM" | "OTHER";
  description: string; // 1-2000 ky tu
}

interface RespondBookingIssueRequest {
  responseNote: string; // 1-2000 ky tu
}

interface SubmitFeedbackRequest {
  rating: 1 | 2 | 3 | 4 | 5;
  satisfactionLevel?: 1 | 2 | 3 | 4 | 5;
  comment?: string;
  wouldRecommend?: boolean;
  isPublic?: boolean;
}
```

Issue khong tu duoc giai quyet sau khi counterparty response. Neu `bookingStatus = "UNDER_REVIEW"` hoac `nextAction = "VIEW_ISSUE"`, hien thi trang thai va thong tin issue hien co; khong tu ket luan refund cho user.

## 7. Payment, chat va calendar

- Khi `nextAction = "PAY_NOW"`, chuyen user sang flow checkout trong [payment.md](payment.md). Sau khi quay lai tu PayOS, poll/truy van payment theo payment guide, sau do tai lai booking detail.
- `conversationId` co the `null` khi booking chua confirmed. Khong tao conversation o FE; dung chat guide khi backend da tra conversation hop le.
- `meetingLink` co the `null`. Khong tu tao Google Meet link o FE. Neu mentor co Google Calendar, backend dong bo qua flow rieng va cap nhat `calendarSyncStatus`.

## 8. Loi va retry an toan

| HTTP | FE can lam |
| --- | --- |
| `400` | Hien validation/message. Khong retry tu dong. |
| `401` | Theo refresh flow trong [identity.md](identity.md); neu fail thi quay ve login. |
| `403` | User khong dung role/khong phai participant. An action va tai lai profile neu role vua doi. |
| `404` | Booking, slot, service, candidate hoac reschedule request khong con hop le. Dong modal va tai lai du lieu lien quan. |
| `409` | Slot/candidate vua bi xu ly, booking da doi state, reschedule het han hoac idempotency key dang dung. Tai lai detail/availability truoc khi cho user thu lai. |
| `429` | Doc `retryAfterSeconds`, khoa nut va hien thi thoi gian cho. Create booking la `12 / 10 phut`; cancel la `3 / gio` tren moi user. |

Khong retry mu mutation. Rieng create booking co the retry bang cung `Idempotency-Key` va cung payload neu FE khong nhan duoc response.
