# Calendar

## Mục tiêu
File này mô tả Google Calendar connect/disconnect/status để FE biết khi nào mentor đã sẵn sàng cho lịch tự động và sync hội họp.

## API inventory
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/google-calendar/status` | Authenticated | any logged-in user | - | `GoogleCalendarStatusResponse` | - | Trạng thái calendar hiện tại |
| POST | `/api/me/google-calendar/connect` | Authenticated | any logged-in user | `GoogleCalendarConnectRequest` | `GoogleCalendarStatusResponse` | - | Kết nối calendar bằng authorization code flow |
| POST | `/api/me/google-calendar/disconnect` | Authenticated | any logged-in user | - | `GoogleCalendarStatusResponse` | - | Ngắt kết nối calendar hiện tại |

## Call order chuẩn
### Connect flow
1. FE lấy authorization code từ Google OAuth flow.
2. FE gọi `POST /api/me/google-calendar/connect` với:
   - `authorizationCode`
   - `redirectUri`
   - `codeVerifier`
3. Backend trao đổi token với Google và lưu connection.
4. FE dùng response để update UI ngay.
5. Nếu cần, FE gọi lại `/api/auth/me` hoặc `/api/me/google-calendar/status` để sync màn hình.

### Disconnect flow
1. FE gọi `POST /api/me/google-calendar/disconnect`.
2. Backend thu hồi mapping connection hiện tại.
3. FE refresh trạng thái bằng `/api/me/google-calendar/status` hoặc `/api/auth/me`.

### Khi nào cần gọi status
- ngay sau login/refresh nếu cần render badge calendar
- khi mở settings mentor
- trước khi bật UI phụ thuộc sync calendar
- sau connect/disconnect

## Ý nghĩa field quan trọng
### `GoogleCalendarStatusResponse`
- `connected`
  - đã kết nối hay chưa
- `syncEnabled`
  - backend có thể sync tự động hay không
- `email`
  - email Google đang liên kết
- `grantedScopes`
  - scopes đã cấp
- `needsReconnect`
  - cần reconnect do token/scope lỗi
- `lastSyncStatus`
  - trạng thái sync gần nhất
- `lastSyncAt`
  - thời điểm sync gần nhất
- `lastSyncErrorCode`
  - mã lỗi sync gần nhất
- `lastSyncErrorMessage`
  - message sync gần nhất

## FE phải làm
- Hiển thị banner reconnect nếu `needsReconnect = true`.
- Chỉ dùng `syncEnabled = true` để bật tính năng phụ thuộc sync tự động.
- Sau connect/disconnect, refresh trạng thái ngay.

## FE không được làm
- Không dùng auth file để suy luận calendar connection.
- Không coi `connected = true` là token sync sẽ luôn thành công.
- Không hiển thị tính năng phụ thuộc calendar nếu backend báo cần reconnect.
- Không coi disconnect là lỗi đăng nhập.
- Không dùng timestamp của Google Calendar để thay thế slot/booking schedule contract. Booking/slot API dùng UTC `Instant`; provider event timestamps có thể giữ precision riêng.

## Response JSON example
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "connected": true,
    "syncEnabled": true,
    "email": "mentor@fpt.edu.vn",
    "grantedScopes": [
      "https://www.googleapis.com/auth/calendar"
    ],
    "needsReconnect": false,
    "lastSyncStatus": "SYNCED",
    "lastSyncAt": "2026-07-13T09:55:00",
    "lastSyncErrorCode": null,
    "lastSyncErrorMessage": null
  }
}
```

## UI mapping
- Settings screen:
  - badge connected / reconnect / disconnected
- Mentor profile setup:
  - chỉ hiển thị CTA connect khi user cần đồng bộ lịch
- Booking helper UI:
  - nếu `syncEnabled=true` thì cho phép flow tự tạo meeting/sync calendar

## API success/error behavior
- `GET /status`
  - success: render trạng thái hiện tại
- `POST /connect`
  - success: bật badge connected và sync-enabled nếu đủ scope
  - 400: thiếu authorization code / verifier / redirectUri
  - 409: callback đã hết hạn hoặc token mismatch
- `POST /disconnect`
  - success: tắt badge và reset UI liên quan
  - 401: về login nếu session hỏng

## Ghi chú cho AI Agent và FE dev
- `needsReconnect=true` phải được render rõ hơn `connected=true`.
- `lastSyncErrorCode` và `lastSyncErrorMessage` chỉ để debug và hiển thị hỗ trợ.
- Không dùng field auth me để thay thế `google-calendar/status`.

## Group-session reservation intervals
Group sessions are created from an active parent availability slot and a duration-aligned candidate. Publishing a group session does not split, deactivate or edit the parent slot. Its `[scheduledStartAt, scheduledEndAt)` interval is treated as reserved when the backend generates 1:1 candidates.

Residual non-overlapping time in the same parent slot remains selectable. Adjacent intervals do not overlap. Group seats share that reserved interval, so individual seat bookings never create another mentor-calendar reservation. They still lock the learner calendar while their payment hold or confirmed booking is active. The group-session lifecycle is `DRAFT -> OPEN -> IN_PROGRESS -> COMPLETED`; registration is separately `OPEN` or `CLOSED`.

Phase 3 creates one shared Session at group-session publish time. The mentor may prepare one meeting link before registration; confirmed attendees see the same meeting context. No attendee-specific Google Calendar event is provisioned.
