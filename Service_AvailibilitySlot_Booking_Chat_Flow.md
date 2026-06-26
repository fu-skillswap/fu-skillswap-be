# Service + Availability Slot + Booking + Chat Flow

Tai lieu nay la checklist FE cho cac flow:
- Mentor Service
- Mentor Availability Slot / Candidate Segment
- Booking
- Chat / Conversation
- Notification
- WebSocket realtime

Muc tieu:
- bam dung contract BE hien tai
- khong doan business rule
- REST la source of truth
- raw WebSocket chi dung de push realtime

---

## 1. Nguyen tac tong

- REST la source of truth cho:
  - mentor services
  - mentor detail / discovery
  - availability slots
  - candidate segments
  - booking create / list / detail / actions
  - conversation list / message history / send message
  - notification list / unread count / mark read
- Raw WebSocket chi dung de push outbound:
  - `AUTH_OK`
  - `ERROR`
  - `PING`
  - `PONG`
  - `CHAT_MESSAGE_CREATED`
  - `NEW_NOTIFICATION`
- FE khong gui booking command qua WebSocket.
- FE khong gui chat message qua WebSocket o phase hien tai.
- `?token=` chi dung cho `/ws` va chi dung **access token**.

---

## 2. Flow 1 - Mentor Service CRUD

### API hien co

#### GET `/api/me/mentor-services`
- Auth: `bearerAuth`, role `MENTOR`
- Query param:
  - `active=true|false|all`
- Mac dinh: `all`
- Response: `List<MentorServiceResponse>`

#### GET `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`

#### POST `/api/me/mentor-services`
- Auth: `bearerAuth`, role `MENTOR`

Request body:
```json
{
  "title": "Review CV Backend Intern",
  "description": "Review CV, mock interview va dinh huong hoc backend.",
  "expectedOutcome": "Ban co CV ro rang hon va biet can cai thien diem nao.",
  "durationMinutes": 30,
  "isFree": true,
  "priceScoin": 0,
  "helpTopicIds": ["uuid-topic-1", "uuid-topic-2"]
}
```

#### PUT `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Body: nhu `POST`

#### PATCH `/api/me/mentor-services/{serviceId}/active`
- Auth: `bearerAuth`, role `MENTOR`

```json
{
  "active": true
}
```

#### DELETE `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Xoa mem service

### FE checklist

- [ ] User phai co role `MENTOR`
- [ ] Load danh sach bang `GET /api/me/mentor-services?active=all`
- [ ] Support filter UI `true | false | all`
- [ ] Create / update / active / delete deu phai refetch hoac update local state theo response
- [ ] Khong tu doan field `currency` nua, BE hien tai dung `priceScoin`

### Rule FE phai nho

- `durationMinutes` phai dung option app ho tro
- `priceScoin >= 0`
- `isFree = true` thi `priceScoin = 0`
- `isFree = false` thi `priceScoin > 0`
- `403` thuong la user khong co role mentor
- `409` thuong la mentor profile / verification / business state chua hop le

---

## 3. Flow 2 - Mentor Discovery va Availability Slot

### API hien co

#### GET `/api/mentors/recommendations`
- Auth: `bearerAuth`
- Query param:
  - `limit` (mac dinh `12`)

#### GET `/api/mentors`
- Auth: `bearerAuth`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `keyword`
  - `tagIds`
  - `campusId`
  - `specializationId`
  - `teachingMode`
- Ghi chu:
  - `sortBy=relevance` la default cua discovery
  - `isAvailable` khong phai query param public chinh thuc cua endpoint nay

#### GET `/api/mentors/{mentorUserId}`
- Auth: `bearerAuth`

#### GET `/api/mentors/{mentorUserId}/availability`
- Auth: `bearerAuth`
- Query params:
  - `fromDate`
  - `toDate`

#### GET `/api/mentors/{mentorUserId}/availability-slots`
- Auth: `bearerAuth`
- Alias ro nghia hon cho FE moi, data giong endpoint availability o tren

#### GET `/api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId=...`
- Auth: `bearerAuth`

### FE checklist

- [ ] Load mentor detail truoc khi cho booking
- [ ] Load parent availability slots bang 1 trong 2 endpoint:
  - `GET /api/mentors/{mentorUserId}/availability`
  - `GET /api/mentors/{mentorUserId}/availability-slots`
- [ ] User phai chon:
  - mentor
  - service
  - parent slot
  - exact candidate segment
- [ ] Sau khi chon slot + service, goi candidate API de lay exact segment
- [ ] Khong coi parent slot la unit dat lich cuoi cung

### Rule FE phai nho

- Backend chi cho xem availability trong pham vi:
  - tu thu 2 tuan hien tai
  - den chu nhat tuan sau
  - theo timezone `Asia/Ho_Chi_Minh`
- Slot hien thi chua phai slot da giu cho mentee
- Unit canh tranh that su la exact candidate segment
- Candidate co the mat hieu luc giua luc user xem va luc submit

### Response FE can dung

`MentorDiscoveryCardResponse`:
- `mentorUserId`
- `displayName`
- `avatarUrl`
- `headline`
- `expertiseDescription`
- `supportingSubjects`
- `isAvailable`
- `ratingAverage`
- `reviewCount`
- `completedSessions`
- `teachingMode`
- `verifiedAt`
- `campusId`
- `campusName`
- `programId`
- `programName`
- `specializationId`
- `specializationName`
- `helpTopicTags`

`MentorDiscoveryDetailResponse`:
- `mentorUserId`
- `displayName`
- `avatarUrl`
- `headline`
- `bio`
- `expertiseDescription`
- `supportingSubjects`
- `isAvailable`
- `bookingSuspendedUntil`
- `ratingAverage`
- `reviewCount`
- `completedSessions`
- `teachingMode`
- `defaultSessionDuration`
- `verifiedAt`
- `campusId`
- `campusName`
- `programId`
- `programName`
- `specializationId`
- `specializationName`
- `semester`
- `alumni`
- `portfolioUrl`
- `linkedinUrl`
- `githubUrl`
- `helpTopicTags`
- `services`

`MentorAvailabilitySlotResponse`:
- `slotId`
- `startTime`
- `endTime`
- `timezone`
- `durationMinutes`
- `teachingMode`
- `pendingRequestCount`
- `maxPendingRequests`
- `remainingRequestSlots`
- `services`

`ServiceSlotCandidatesResponse`:
- `slotId`
- `serviceId`
- `serviceDurationMinutes`
- `candidateServiceSlots`
  - `startTime`
  - `endTime`
  - `pendingCount`
  - `remainingPendingQuota`
  - `isSelectable`
  - `reasonIfBlocked`

---

## 4. Flow 3 - Mentor Availability Slot Management

### API hien co

#### PUT `/api/me/availability-slots/{slotId}/services`
- Auth: `bearerAuth`, role `MENTOR`
- Body:
```json
{
  "serviceIds": [
    "uuid-service-1",
    "uuid-service-2"
  ]
}
```

### FE checklist

- [ ] Man nay chi dung cho mentor
- [ ] Chon lai danh sach service gan voi tung slot
- [ ] Sau khi update, refetch lai slot hoac mentor detail

### Rule FE phai nho

- Slot phai thuoc mentor hien tai
- Service phai hop le cua mentor hien tai
- Backend co the tra `409` neu slot het hieu luc hoac service khong con dung duoc

> Ghi chu: public FE hien tai khong co API `/api/mentor/availability-rules`. Phan rule generator nay chi ton tai o service noi bo, khong phai public contract.

---

## 5. Flow 4 - Create Booking

### API hien co

#### POST `/api/bookings`
- Auth: `bearerAuth`
- Body:
```json
{
  "availabilitySlotId": "uuid-slot",
  "serviceId": "uuid-service",
  "selectedStartTime": "2026-06-27T19:00:00",
  "selectedEndTime": "2026-06-27T19:30:00",
  "learningGoalTitle": "Can review CV backend",
  "learningGoalDescription": "Muon duoc goi y de ung tuyen intern."
}
```

### FE checklist

- [ ] User da login
- [ ] User da chon mentor
- [ ] User da chon service
- [ ] User da chon parent slot
- [ ] User da chon exact candidate segment
- [ ] User da nhap learning goal
- [ ] Sau khi tao thanh cong:
  - refetch `GET /api/me/bookings?role=MENTEE`
  - hoac di sang booking detail

### Rule FE phai nho

- `ADMIN` va `SYSTEM_ADMIN` khong duoc tao booking
- `MENTOR` van co the tao booking nhu mot mentee hop le neu business cho phep
- Booking ban dau la `PENDING`
- Backend tu kiem tra:
  - student profile da hoan tat chua
  - quota pending co vuot khong
  - segment co bi overlap / full queue khong
  - service co thuoc mentor va slot khong
  - mentor co active / available / discoverable khong

### Response FE can dung

- `bookingId`
- `status`
- `selectedStartTime`
- `selectedEndTime`
- `serviceTitle`
- `mentorDisplayName`
- `meetingPlatform`
- `meetingLink`

---

## 6. Flow 5 - Booking List / Detail

### API hien co

#### GET `/api/me/bookings`
- Auth: `bearerAuth`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `status`
  - `role`
- FE dung:
  - `role=MENTEE` cho man mentee
  - `role=MENTOR` cho man mentor

#### GET `/api/me/bookings/{bookingId}`
- Auth: `bearerAuth`

### FE checklist

- [ ] Dung list role MENTEE / MENTOR dung nganh
- [ ] Render action button theo `status` that su backend tra ve
- [ ] Khong hardcode state machine o FE

---

## 7. Flow 6 - Mentor Accept / Reject / Meeting Link

### API hien co

#### POST `/api/mentor/bookings/{bookingId}/accept`
- Auth: `bearerAuth`, role `MENTOR`
- Body co the rong hoac:
```json
{
  "mentorResponseNote": "Hen em toi Google Meet"
}
```

#### POST `/api/mentor/bookings/{bookingId}/reject`
- Auth: `bearerAuth`, role `MENTOR`
- Body:
```json
{
  "rejectReason": "Khung gio nay anh ban",
  "mentorResponseNote": "Em co the dat lich khac"
}
```

#### POST `/api/mentor/bookings/{bookingId}/cancel`
- Auth: `bearerAuth`, role `MENTOR`
- Body:
```json
{
  "cancelReason": "Anh co viec dot xuat"
}
```

Rule:
- booking phai dang `ACCEPTED`
- mentee duoc refund `100%` ve vi Scoin
- mentor van bi penalty:
  - `< 6h`: suspend 3 ngay
  - `>= 6h` va `< 12h`: +0.5 penalty points

#### POST `/api/mentor/bookings/{bookingId}/complete`
- Auth: `bearerAuth`, role `MENTOR`

#### PATCH `/api/mentor/bookings/{bookingId}/meeting-link`
- Auth: `bearerAuth`, role `MENTOR`
- Body:
```json
{
  "meetingPlatform": "GOOGLE_MEET",
  "meetingLink": "https://meet.google.com/abc-defg-hij",
  "location": "Thu vien FPTU HCM - tang 2"
}
```

### FE checklist

- [ ] Neu booking `PENDING` thi cho `Accept` / `Reject`
- [ ] Sau accept:
  - refetch booking detail
  - mo chat
  - cho phep cap nhat meeting link
- [ ] Accept thanh cong co the auto reject cac pending overlap cung segment

### Rule FE phai nho

- `meetingLink` phai hop le `http/https`
- `meetingPlatform` bat buoc khi luu meeting info
- `cancelReason` / `rejectReason` bat buoc khi action tuong ung

---

## 8. Flow 7 - Cancel / Complete / Confirm / Issue

### API hien co

#### Mentee cancel
`POST /api/me/bookings/{bookingId}/cancel`

Body:
```json
{
  "cancelReason": "Em khong sap xep kip lich"
}
```

Rule:
- booking `PENDING`: huy tu do
- booking `ACCEPTED`:
  - truoc `6h`: refund `100%` ve vi Scoin mentee
  - sau `6h`: mat `50%`
    - `35%` vao vi mentor
    - `15%` la commission app

### Reschedule workflow

#### API hien co

`POST /api/me/bookings/{bookingId}/reschedule-requests`

`GET /api/me/bookings/{bookingId}/reschedule-requests`

`POST /api/me/bookings/reschedule-requests/{requestId}/accept`

`POST /api/me/bookings/reschedule-requests/{requestId}/reject`

`POST /api/mentor/bookings/{bookingId}/reschedule-requests`

`POST /api/mentor/bookings/reschedule-requests/{requestId}/accept`

`POST /api/mentor/bookings/reschedule-requests/{requestId}/reject`

`GET /api/admin/bookings/{bookingId}/reschedule-requests`

`POST /api/admin/bookings/reschedule-requests/{requestId}/force-approve`

`POST /api/admin/bookings/reschedule-requests/{requestId}/force-reject`

#### Rule FE phai nho

- Chi booking `ACCEPTED` moi duoc reschedule
- `PENDING` khong duoc reschedule
- Moi booking chi duoc reschedule thanh cong toi da `1` lan
- Chi duoc tao request neu con it nhat `6h` truoc gio hoc cu
- Request da tao se het han tai moc `currentStartTime - 2h`
- Mentee tao request thi chi mentor moi duoc accept/reject
- Mentor tao request thi chi mentee moi duoc accept/reject
- Nguoi tao request khong duoc tu accept/reject request cua minh
- Admin khong tu tao request; admin chi `force-approve` hoac `force-reject` request da ton tai
- Khong doi service
- Khong doi payment
- Khong giu slot tam
- Luc accept moi validate lai slot moi
- Neu slot moi fail thi booking giu lich cu
- Neu accept thanh cong vao segment moi thi chi auto reject cac `PENDING` overlap segment moi

#### Payload tao request

```json
{
  "proposedSlotId": "uuid",
  "proposedSelectedStartTime": "2026-06-30T19:00:00",
  "proposedSelectedEndTime": "2026-06-30T20:00:00",
  "reason": "Em bi trung lich hoc, muon doi sang toi thu 3"
}
```

#### Payload accept/reject/force

```json
{
  "reason": "Dong y doi lich"
}
```

#### Mentor complete alias
`POST /api/me/bookings/{bookingId}/complete`

#### Participant confirm
`POST /api/me/bookings/{bookingId}/confirm`

Body:
```json
{
  "confirmationNote": "Buoi mentoring di dung ke hoach."
}
```

#### Participant issue
`POST /api/me/bookings/{bookingId}/issue`

Body:
```json
{
  "issueType": "NO_SHOW_OR_QUALITY_OR_OTHER",
  "description": "Mentor khong tham gia dung gio hen.",
  "wantsAdminReview": true
}
```

### Rule FE phai nho

- cancel / complete / confirm / issue deu la state machine action
- issue chi hop le voi participant hop le cua booking
- issue chi duoc gui sau khi session ket thuc va trong cua so backend cho phep
- du FE an button, backend van co the tra `409`

---

## 9. Flow 8 - Chat

### API hien co

#### GET `/api/me/conversations`
- Auth: `bearerAuth`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`

#### GET `/api/me/conversations/{conversationId}/messages`
- Auth: `bearerAuth`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`

#### POST `/api/me/conversations/{conversationId}/messages`
- Auth: `bearerAuth`
- Body:
```json
{
  "content": "Hello anh, em da vao phong hop"
}
```

### FE checklist

- [ ] Chi mo chat khi booking da tao conversation
- [ ] Load inbox bang `GET /api/me/conversations`
- [ ] Load lich su bang `GET /api/me/conversations/{conversationId}/messages`
- [ ] Gui tin nhan bang REST
- [ ] Sau khi gui thanh cong:
  - append message vao UI
  - dedupe neu WebSocket push cung message do ve

### Rule FE phai nho

- message history hien tai tra newest-first
- `MessageResponse.isMine` co san
- `CHAT_MESSAGE_CREATED` tu WebSocket khong co `isMine`
- FE so sanh `senderId` voi current user

---

## 10. Flow 9 - Notification

### API hien co

#### GET `/api/me/notifications`
- Auth: `bearerAuth`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `unreadOnly` (mac dinh `false`)

#### GET `/api/me/notifications/unread-count`
- Auth: `bearerAuth`

#### PATCH `/api/me/notifications/{id}/read`
- Auth: `bearerAuth`

#### PATCH `/api/me/notifications/read-all`
- Auth: `bearerAuth`

### FE checklist

- [ ] Load list notification bang REST
- [ ] Load unread count bang REST
- [ ] Mark read / read-all bang REST
- [ ] Khi nhan `NEW_NOTIFICATION` qua WebSocket:
  - update badge local
  - neu muon thi prepend item vao dropdown
  - van co the refetch REST khi mo notification center

### Rule FE phai nho

- notification DB la source of truth
- WebSocket chi push event moi
- reconnect xong phai refetch unread count va page dau neu can

---

## 11. Flow 10 - Payment / Wallet / Payout

### 11.1 Payment checkout cho booking

#### API hien co

##### POST `/api/me/payment-orders/checkout`
- Auth: `bearerAuth`
- Body:
```json
{
  "bookingId": "uuid-booking",
  "couponCode": "WELCOME10"
}
```

##### GET `/api/me/payment-orders/{bookingId}`
- Auth: `bearerAuth`
- Muc dich: poll trang thai payment order theo booking

### FE checklist

- [ ] Chi checkout khi booking da den luong thanh toan
- [ ] Sau checkout thanh cong:
  - redirect toi `checkoutUrl` hoac `paymentLink`
  - luu tam `orderCode` / `paymentOrderId` neu can theo doi
- [ ] Neu can theo doi trang thai:
  - poll `GET /api/me/payment-orders/{bookingId}`
  - hoac cho webhook PayOS cap nhat backend truoc

### Rule FE phai nho

- `checkoutUrl` la hosted payment link do PayOS tra ve
- Webhook PayOS la nguon chot `PAID` that su
- FE khong tu set paid state chi vi redirect thanh cong
- So tien noi bo hien tai dung SCoin

### 11.2 Webhook PayOS

#### API hien co

##### POST `/api/payments/webhook/payos`
- Auth: Public
- Muc dich: nhan webhook PayOS, verify chu ky, xu ly idempotent

### FE checklist

- [ ] FE khong goi webhook nay
- [ ] FE chi can biet backend se tu cap nhat trang thai payment
- [ ] Neu trang thai chua cap nhat ngay:
  - poll lai payment order
  - hoac refetch booking detail / wallet sau mot khoang ngan

### 11.3 Wallet

#### API hien co

##### GET `/api/me/credit-wallet`
- Auth: `bearerAuth`, role `MENTEE`

##### GET `/api/me/mentor-wallet`
- Auth: `bearerAuth`, role `MENTOR`

### FE checklist

- [ ] Dung `GET /api/me/credit-wallet` cho mentee
- [ ] Dung `GET /api/me/mentor-wallet` cho mentor
- [ ] Hien thi:
  - so du available
  - 15 giao dich gan nhat
- [ ] Sau cac action lien quan tien:
  - booking paid
  - refund
  - settlement release
  - payout
  - refetch lai wallet

### Rule FE phai nho

- `availableScoin` la so du hien tai
- `recentTransactions` chi lay 15 item de UI nhe
- giao dich co the la:
  - `CAMPAIGN_BONUS`
  - `COUPON_BONUS`
  - `REFUND`
  - `MANUAL`
  - `PAYMENT_RESERVATION`
  - `COMMISSION`
  - `PAID_OUT`

### 11.4 Mentor payout profile / payout request

#### API hien co

##### POST `/api/mentor/payout-profiles`
- Auth: `bearerAuth`, role `MENTOR`

##### PUT `/api/mentor/payout-profiles/{payoutProfileId}`
- Auth: `bearerAuth`, role `MENTOR`

##### GET `/api/mentor/payout-profiles`
- Auth: `bearerAuth`, role `MENTOR`

##### POST `/api/mentor/payout-requests`
- Auth: `bearerAuth`, role `MENTOR`
- Body:
```json
{
  "amountScoin": 100,
  "payoutProfileId": "uuid-payout-profile",
  "note": "Rut settlement thang nay"
}
```

##### GET `/api/mentor/payout-requests`
- Auth: `bearerAuth`, role `MENTOR`

##### Admin payout actions
- `POST /api/admin/payout-requests/{payoutRequestId}/approve`
- `POST /api/admin/payout-requests/{payoutRequestId}/reject`
- `POST /api/admin/payout-requests/{payoutRequestId}/mark-paid`

### FE checklist

- [ ] Mentor can tao va quan ly payout profile truoc
- [ ] Mentor chi tao payout request khi con settlement balance hop le
- [ ] Admin moi duoc duyet / tu choi / mark paid
- [ ] Sau moi action payout:
  - refetch payout requests
  - neu can thi refetch mentor wallet

### Rule FE phai nho

- payout la flow mentor-only
- payout request khong phai wallet balance raw
- admin co the cap nhat trang thai payout theo quy trinh van hanh

---

## 12. WebSocket checklist cho FE

### Endpoint

```ts
new WebSocket(`wss://api.skillswap.asia/ws?token=${accessToken}`)
```

### Local

```ts
new WebSocket(`ws://localhost:8080/ws?token=${accessToken}`)
```

### FE phai lam

- [ ] Bo SockJS client
- [ ] Bo STOMP client
- [ ] Tao raw WebSocket wrapper
- [ ] Chi dung access token
- [ ] Khong bao gio dung refresh token cho `/ws`
- [ ] Sau reconnect phai resync qua REST:
  - unread count
  - notification page dau
  - conversation dang mo neu can

### Message types hien co

#### AUTH_OK
```json
{
  "type": "AUTH_OK",
  "payload": {
    "userId": "uuid"
  },
  "timestamp": "2026-06-26T10:00:00"
}
```

#### PING
```json
{
  "type": "PING"
}
```

#### PONG
```json
{
  "type": "PONG",
  "timestamp": "2026-06-26T10:00:00"
}
```

#### CHAT_MESSAGE_CREATED
- payload la `ChatMessageEvent`

#### NEW_NOTIFICATION
- payload la `NotificationResponse`

#### ERROR
```json
{
  "type": "ERROR",
  "payload": {
    "code": "WS_4000",
    "message": "Loai realtime message khong duoc ho tro"
  },
  "timestamp": "2026-06-26T10:00:00"
}
```

### FE smoke checklist cho WebSocket

- [ ] Connect thanh cong thi nhan `AUTH_OK`
- [ ] Gui `PING` thi nhan `PONG`
- [ ] Gui chat bang REST thi user nhan `CHAT_MESSAGE_CREATED`
- [ ] Trigger booking accept / reject / cancel / notification thi user nhan `NEW_NOTIFICATION`
- [ ] Restart backend thi FE reconnect duoc
- [ ] Sau reconnect FE refetch REST de dong bo

### Boundary can nho

- WebSocket hien tai la outbound-only practical realtime
- Khong gui business command lon qua socket
- Khong dua history qua socket
- Khong xem WebSocket la source of truth

---

## 13. Quick checklist theo thu tu FE nen lam

1. Mentor services CRUD
2. Mentor detail + availability slots + candidates
3. Create booking
4. Booking list + booking detail
5. Mentor accept / reject / meeting link
6. Booking cancel / complete / confirm / issue
7. Payment checkout + payment status poll
8. Wallet mentee / mentor
9. Payout profile / payout request
10. Chat REST flow
11. Notification REST flow
12. Raw WebSocket wrapper
13. Chat push + notification push
14. Reconnect + REST resync

---

## 14. Quick troubleshooting

### Tao booking bi `409`
Check:
- segment da day 3 pending chua
- mentee da co 5 pending chua
- mentee da co accepted booking overlap chua
- service co thuoc mentor va slot khong
- mentor co con active / available / discoverable khong

### Chat gui thanh cong nhung bi duplicate
Check:
- FE da append message tu REST response
- sau do WebSocket push cung message do ve
- can dedupe theo `messageId`

### WebSocket khong connect duoc
Check:
- dung `wss://` tren site `https://`
- URL co phai `/ws?token=...` khong
- token co phai access token con han khong
- backend/nginx da mo `/ws` chua

### Notification badge khong khop
Check:
- FE da cap nhat local state tu `NEW_NOTIFICATION` chua
- sau reconnect da refetch unread count chua
