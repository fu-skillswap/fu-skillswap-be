# Service + AvailibilitySlot + Booking + Chat Flow

Tai lieu nay la checklist cho FE implement cac flow Service, Availability Slot, Booking, Notification va Chat dua tren BE hien co.

Muc tieu:
- bam dung API contract hien tai
- khong doan them business rule
- tach ro cai nao la source of truth qua REST, cai nao la realtime push qua WebSocket

---

## 1. Nguyen tac tong

- REST van la source of truth cho:
  - mentor services
  - mentor availability slots va candidate segments
  - booking list / detail / actions
  - conversation list
  - message history
  - send chat message
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

---

## 2. Flow 1 - Mentor Service

### FE checklist

- [ ] Chac chan user da co role `MENTOR`
- [ ] Goi `GET /api/me/mentor-services?active=all` de load danh sach ban dau
- [ ] Ho tro filter UI:
  - `active=true`
  - `active=false`
  - `active=all`
- [ ] Tao service bang `POST /api/me/mentor-services`
- [ ] Sua service bang `PUT /api/me/mentor-services/{serviceId}`
- [ ] Bat/tat service bang `PATCH /api/me/mentor-services/{serviceId}/active`
- [ ] Xoa mem service bang `DELETE /api/me/mentor-services/{serviceId}`
- [ ] Sau moi action create/update/active/delete:
  - cap nhat state local tu response
  - hoac refetch lai list

### API dung

#### 1. Load danh sach service cua toi
`GET /api/me/mentor-services?active=all`

#### 2. Load chi tiet service
`GET /api/me/mentor-services/{serviceId}`

#### 3. Tao service
`POST /api/me/mentor-services`

Request body toi thieu:

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

#### 4. Sua service
`PUT /api/me/mentor-services/{serviceId}`

#### 5. Bat/tat service
`PATCH /api/me/mentor-services/{serviceId}/active`

```json
{
  "active": true
}
```

#### 6. Xoa mem service
`DELETE /api/me/mentor-services/{serviceId}`

### Rule FE phai nho

- `durationMinutes` phai dung option ma app ho tro.
- `priceScoin >= 0`
- neu `isFree = true` thi `priceScoin = 0`
- neu `isFree = false` thi `priceScoin > 0`
- neu backend tra `403` thi user hien tai khong duoc phep quan ly service
- neu backend tra `409` thi thuong la mentor profile / verification / business state chua hop le

---

## 3. Flow 2 - Availability Slot Discovery Side

Day la phan mentee xem slot public cua mentor va chon candidate segment.

### FE checklist

- [ ] Load mentor detail truoc bang `GET /api/mentors/{mentorUserId}`
- [ ] Load slot public bang:
  - `GET /api/mentors/{mentorUserId}/availability`
  - hoac alias ro nghia hon: `GET /api/mentors/{mentorUserId}/availability-slots`
- [ ] User chon 1 parent slot
- [ ] User chon 1 service gan voi slot do
- [ ] Goi `GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId=...`
- [ ] Hien thi exact candidate segments de user chon
- [ ] Chi tao booking khi user da chon:
  - mentor
  - service
  - slot
  - exact candidate segment

### API dung

#### 1. Load mentor detail
`GET /api/mentors/{mentorUserId}`

#### 2. Load parent slots
`GET /api/mentors/{mentorUserId}/availability-slots`

#### 3. Load candidate segments
`GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId={serviceId}`

### Rule FE phai nho

- Parent slot chua phai cho duoc dat ngay.
- Don vi canh tranh that su la exact candidate segment.
- Candidate co the bi day hang doi, bi overlap, hoac bien mat giua luc user dang xem va luc user submit.
- FE phai chap nhan backend co the tra `409 Conflict` khi tao booking du UI van dang thay slot.

---

## 4. Flow 3 - Mentor Availability Slot Management Side

Hien tai BE co endpoint de mentor thay danh sach service gan tren tung slot cu the.

### FE checklist

- [ ] Khi mentor mo man hinh quan ly slot, FE can co danh sach service cua mentor
- [ ] Cho mentor chon lai serviceIds gan voi slot
- [ ] Goi `PUT /api/me/availability-slots/{slotId}/services`

### API dung

`PUT /api/me/availability-slots/{slotId}/services`

Request body:

```json
{
  "serviceIds": [
    "uuid-service-1",
    "uuid-service-2"
  ]
}
```

### Rule FE phai nho

- Slot phai thuoc mentor hien tai
- Service phai la service hop le cua mentor hien tai
- Backend co the tra `409` neu slot het hieu luc hoac service khong con dung duoc

---

## 5. Flow 4 - Create Booking

### FE checklist

- [ ] User da login
- [ ] User da chon exact candidate segment
- [ ] User da nhap learning goal
- [ ] Goi `POST /api/bookings`
- [ ] Sau khi tao thanh cong:
  - dua user ve booking detail
  - hoac refetch list `GET /api/me/bookings?role=MENTEE`
- [ ] Handle tot `409 Conflict` vi day la case rat thuong gap

### API dung

`POST /api/bookings`

Request body:

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

### Rule FE phai nho

- `ADMIN` va `SYSTEM_ADMIN` khong duoc tao booking
- `MENTOR` van co the booking mentor khac nhu mentee
- Booking tao ra ban dau la `PENDING`
- Backend tu kiem tra:
  - user co completed student profile chua
  - mentee co vuot quota pending khong
  - segment co day 3 pending chua
  - mentee co accepted booking overlap khong
  - service co dung gan voi slot khong
  - mentor co dang active / available / discoverable khong

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

### FE checklist

- [ ] Dung `GET /api/me/bookings?role=MENTEE` cho man hinh booking cua mentee
- [ ] Dung `GET /api/me/bookings?role=MENTOR` cho man hinh booking cua mentor
- [ ] Co filter theo `status` neu can
- [ ] Mo booking detail bang `GET /api/me/bookings/{bookingId}`

### API dung

#### 1. Booking list
`GET /api/me/bookings?role=MENTEE&page=0&size=20`

`GET /api/me/bookings?role=MENTOR&page=0&size=20`

#### 2. Booking detail
`GET /api/me/bookings/{bookingId}`

### Rule FE phai nho

- `role` la query param quan trong
- mot user co the xem duoi goc `MENTEE` hoac `MENTOR`
- FE phai render action button theo `status` that su backend tra ve, khong hardcode theo suy doan UI

---

## 7. Flow 6 - Mentor Accept / Reject / Meeting Link

### FE checklist

- [ ] Mentor load booking detail
- [ ] Neu `status = PENDING` thi cho hien `Accept` / `Reject`
- [ ] Accept: goi `POST /api/mentor/bookings/{bookingId}/accept`
- [ ] Reject: goi `POST /api/mentor/bookings/{bookingId}/reject`
- [ ] Sau accept:
  - refetch booking detail
  - mo/enable khu vuc chat
  - enable form meeting link
- [ ] Luu meeting link bang `PATCH /api/mentor/bookings/{bookingId}/meeting-link`

### API dung

#### Accept
`POST /api/mentor/bookings/{bookingId}/accept`

Body co the rong hoac:

```json
{
  "mentorResponseNote": "Hen em toi Google Meet"
}
```

#### Reject
`POST /api/mentor/bookings/{bookingId}/reject`

```json
{
  "rejectReason": "Khung gio nay anh ban",
  "mentorResponseNote": "Em co the dat lich khac"
}
```

#### Save meeting link
`PATCH /api/mentor/bookings/{bookingId}/meeting-link`

```json
{
  "meetingPlatform": "GOOGLE_MEET",
  "meetingLink": "https://meet.google.com/abc-defg-hij"
}
```

### Rule FE phai nho

- accept se auto reject cac pending booking overlap cua segment vua duoc chot
- sau accept, backend tao conversation
- chi khi booking o state hop le moi duoc cap nhat meeting link

---

## 8. Flow 7 - Cancel / Complete / Confirm / Issue

### FE checklist

- [ ] Mentee cancel bang `POST /api/me/bookings/{bookingId}/cancel`
- [ ] Mentor cancel bang `POST /api/mentor/bookings/{bookingId}/cancel`
- [ ] Mentor complete bang `POST /api/mentor/bookings/{bookingId}/complete`
- [ ] Participant con lai confirm bang `POST /api/me/bookings/{bookingId}/confirm`
- [ ] Neu co van de sau session, participant goi `POST /api/me/bookings/{bookingId}/issue`

### API dung

#### Mentee cancel
`POST /api/me/bookings/{bookingId}/cancel`

```json
{
  "cancelReason": "Em khong sap xep kip lich"
}
```

#### Mentor cancel
`POST /api/mentor/bookings/{bookingId}/cancel`

```json
{
  "cancelReason": "Anh co viec dot xuat"
}
```

#### Mentor complete
`POST /api/mentor/bookings/{bookingId}/complete`

#### Participant confirm
`POST /api/me/bookings/{bookingId}/confirm`

#### Participant issue
`POST /api/me/bookings/{bookingId}/issue`

```json
{
  "issueType": "NO_SHOW_OR_QUALITY_OR_OTHER",
  "description": "Mo ta van de",
  "wantsAdminReview": true
}
```

### Rule FE phai nho

- cancel / complete / confirm / issue deu la action theo state machine, FE phai an button neu state khong hop le
- du FE co an button, backend van co the tra `409`
- issue flow khong phai mo luc nao cung duoc, backend check participant + time window

---

## 9. Flow 8 - Chat

### FE checklist

- [ ] Chi mo chat khi booking da tao conversation
- [ ] Load conversation list bang `GET /api/me/conversations`
- [ ] Load lich su bang `GET /api/me/conversations/{conversationId}/messages`
- [ ] Gui tin nhan bang REST:
  - `POST /api/me/conversations/{conversationId}/messages`
- [ ] Sau khi gui thanh cong:
  - them message vao UI
  - dedupe khi WebSocket push cung message do ve

### API dung

#### Conversation list
`GET /api/me/conversations?page=0&size=20`

#### Message history
`GET /api/me/conversations/{conversationId}/messages?page=0&size=20`

#### Send message
`POST /api/me/conversations/{conversationId}/messages`

```json
{
  "content": "Hello anh, em da vao phong hop"
}
```

### Rule FE phai nho

- BE hien tai tra messages theo thu tu moi nhat truoc
- FE nen reverse danh sach neu muon render tu cu den moi
- `MessageResponse` co `isMine`
- `CHAT_MESSAGE_CREATED` tu WebSocket khong co `isMine`, FE tu so sanh `senderId` voi current user

---

## 10. Flow 9 - Notification

### FE checklist

- [ ] Load list bang `GET /api/me/notifications`
- [ ] Load unread count bang `GET /api/me/notifications/unread-count`
- [ ] Mark read bang `PATCH /api/me/notifications/{id}/read`
- [ ] Mark all bang `PATCH /api/me/notifications/read-all`
- [ ] Khi nhan `NEW_NOTIFICATION` qua WebSocket:
  - update badge local
  - prepend item vao dropdown neu muon
  - nhung van co the refetch REST khi user mo notification center

### API dung

#### Notification list
`GET /api/me/notifications?unreadOnly=false&page=0&size=20&sort=createdAt,desc`

#### Unread count
`GET /api/me/notifications/unread-count`

#### Mark one as read
`PATCH /api/me/notifications/{id}/read`

#### Mark all as read
`PATCH /api/me/notifications/read-all`

### Rule FE phai nho

- notification DB van la source of truth
- WebSocket chi push su kien moi
- reconnect xong phai refetch unread count va page dau neu can

---

## 11. WebSocket checklist cho FE hien tai

### FE phai lam

- [ ] Bo SockJS client
- [ ] Bo STOMP client
- [ ] Tao 1 wrapper raw WebSocket
- [ ] Connect bang:

```ts
new WebSocket(`wss://api.skillswap.asia/ws?token=${accessToken}`)
```

- [ ] Neu local:

```ts
new WebSocket(`ws://localhost:8080/ws?token=${accessToken}`)
```

- [ ] Khong bao gio dung refresh token cho `/ws`
- [ ] Sau reconnect phai resync qua REST:
  - unread count
  - notification page dau
  - conversation dang mo neu can

### Message types hien tai

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

payload la `ChatMessageEvent`

#### NEW_NOTIFICATION

payload la `NotificationResponse`

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
- [ ] Trigger booking accept / reject / cancel / verification... tao notification thi user nhan `NEW_NOTIFICATION`
- [ ] Restart backend thi FE reconnect duoc
- [ ] Sau reconnect FE refetch REST de dong bo phan missed event

### Dinh nghia boundary can nho

- WebSocket hien tai la outbound-only practical realtime
- Khong gui business command lon qua socket
- Khong dua history qua socket
- Khong xem WebSocket la source of truth

---

## 12. Thu tu FE nen implement

1. Mentor services CRUD
2. Mentor detail + availability slots + candidates
3. Create booking
4. Booking list + booking detail
5. Mentor accept/reject/meeting link
6. Chat REST flow
7. Notification REST flow
8. Raw WebSocket wrapper
9. Chat push + notification push
10. Reconnect + REST resync

---

## 13. Quick troubleshooting cho FE

### Neu tao booking bi `409`

Check:
- segment da day 3 pending chua
- mentee da co 5 pending chua
- mentee da co accepted booking overlap chua
- service co con gan voi slot khong
- mentor co con active / available khong

### Neu chat gui thanh cong nhung UI bi duplicate

Check:
- FE da append message tu REST response
- sau do WebSocket push cung message
- can dedupe theo `messageId`

### Neu WebSocket khong connect duoc

Check:
- FE co dang dung `wss://` tren site `https://` khong
- URL co phai `/ws?token=...` khong
- token co phai access token con han khong
- backend/nginx da mo `/ws` chua
- DevTools con thay `/ws/info` hoac `/xhr_send` khong

### Neu notification badge khong khop

Check:
- FE da cap nhat local state tu `NEW_NOTIFICATION` chua
- sau reconnect da refetch unread count chua

