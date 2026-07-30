# Chat & Notification

## Mục tiêu
File này mô tả:
- conversation list
- message list
- send message
- unread count / mark read
- notification inbox
- STOMP realtime contract

Nguyên tắc chốt:
- REST là source of truth
- STOMP là realtime hint
- FE phải resync bằng REST sau reconnect

## API inventory
### Conversations
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/conversations` | Authenticated | participant | `BasePageRequest`/cursor request theo runtime | `PageResponse<ConversationResponse>` | - | Danh sách hội thoại |
| GET | `/api/me/conversations/{conversationId}` | Authenticated | participant | path `conversationId` | `ConversationResponse` | - | Chi tiết conversation |
| GET | `/api/me/conversations/{conversationId}/messages` | Authenticated | participant | `BasePageRequest`/cursor request theo runtime | `PageResponse<MessageResponse>` | - | Danh sách message |
| POST | `/api/me/conversations/{conversationId}/messages` | Authenticated | participant có `canSendMessages` | `{clientMessageId, content, replyToMessageId?, attachmentIntentIds?}` | `MessageResponse` | - | Gửi message, không dùng `Idempotency-Key` |
| POST | `/api/me/conversations/{conversationId}/block` | Authenticated | participant | - | `ConversationBlockResponse` | - | Khóa chat/file hai chiều, vẫn đọc lịch sử |
| DELETE | `/api/me/conversations/{conversationId}/block` | Authenticated | blocker | - | `ConversationBlockResponse` | - | Chỉ gỡ block do user hiện tại tạo |
| POST | `/api/me/conversations/{conversationId}/reports` | Authenticated | participant | `{reasonType, description?}` | `ChatReportResponse` | - | Tạo report moderation cho participant còn lại |
| GET | `/api/me/conversations/unread-count` | Authenticated | participant | - | `UnreadCountResponse` | - | Số hội thoại chưa đọc |
| PATCH | `/api/me/conversations/{conversationId}/read` | Authenticated | participant | - | `Void` | - | Mark read |

### Notifications
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/notifications` | Authenticated | any logged-in user | `PageRequest`/cursor request theo runtime | `PageResponse<NotificationResponse>` | - | Inbox notification |
| GET | `/api/me/notifications/unread-count` | Authenticated | any logged-in user | - | `UnreadCountResponse` | - | Badge count |
| PATCH | `/api/me/notifications/{id}/read` | Authenticated | any logged-in user | - | `Void` | - | Mark one read |
| PATCH | `/api/me/notifications/read-all` | Authenticated | any logged-in user | - | `Void` | - | Mark all read |

## Call order chuẩn
### Chat
1. FE load unread count trước.
2. FE load conversation list.
3. Khi user mở 1 conversation, FE load message list.
4. Khi user gửi message, FE gọi REST send message.
5. Nếu socket reconnect, FE gọi lại REST để sync state mới nhất.

### Notification
1. FE load unread badge.
2. FE load inbox list.
3. Khi user click notification, FE mark read rồi điều hướng deep link.
4. Nếu nhận realtime push, FE vẫn phải refresh list/badge nếu cần consistency.

## STOMP contract
### Endpoint và destination
- Endpoint chính: `/ws-stomp`
- Chat destinations:
  - `/user/queue/chat/messages`
  - `/user/queue/chat/inbox`
  - `/user/queue/chat/unread`
- Notification destinations:
  - `/user/queue/notifications/items`
  - `/user/queue/notifications/badge`

### Quy tắc FE
- Subscribe sau khi đã có auth hợp lệ.
- Sort lại message theo `sequence`; timestamp chỉ để hiển thị.
- Dedup theo `messageId`.
- Với notification, dedup theo `notificationId`.
- Nếu FE thấy lệch giữa push và REST, ưu tiên REST.

## Ý nghĩa field quan trọng
### `ConversationResponse`
- `status`
  - trạng thái conversation
- `otherUserId`, `otherUserName`, `otherUserAvatarUrl`
  - thông tin đối tượng còn lại
- `lastMessageContent`, `lastMessageAt`
  - dùng để sort list
- `unreadCount`
  - số message chưa đọc trong conversation
- `messagingAccess`, `canSendMessages`, `canUploadAttachments`, `canDownloadAttachments`
  - quyền dẫn xuất từ `BookingChatAccessPolicy`; FE không tự suy luận bằng raw booking status.
- `messagingWindowEndsAt`
  - null khi quyền chat là dài hạn; khi có giá trị, FE hiển thị thời hạn chat temporary.
- `readOnlyReason`
  - enum machine-readable như `UNDER_REVIEW`, `PARTICIPANT_BLOCKED`, `CHAT_WINDOW_EXPIRED`, `NO_EFFECTIVE_BOOKING`, `ADMIN_LOCKED`.

### Safety controls
- `POST /{conversationId}/block` giữ text history nhưng lập tức đặt conversation ở `READ_ONLY` cho cả hai participant: không gửi message, upload hoặc xin URL download attachment mới.
- `DELETE /{conversationId}/block` chỉ xóa block do caller tạo; nếu participant còn lại hoặc admin vẫn khóa, quyền chat không tự mở.
- `POST /{conversationId}/reports` cho phép tối đa một report `OPEN` trên mỗi reporter/conversation. Sau khi admin resolve, user có thể report sự cố mới. Report không tự thay đổi booking, payment hoặc account status.
- Admin dùng `GET /api/admin/chat-reports?status=` và `PATCH /api/admin/chat-reports/{reportId}`. `RESOLVED_LOCKED` khóa conversation hai chiều; `RESOLVED_NO_ACTION` chỉ đóng report. `PATCH /api/admin/chat-reports/conversations/{conversationId}/lock` cho phép admin mở/khóa lại có audit; mở khóa vẫn tôn trọng participant block và booking access.

### `MessageResponse`
- `messageType`
  - loại message
- `content`
  - nội dung message
- `createdAt`
  - chỉ dùng để hiển thị; `sequence` là ordering/cursor source of truth
- `isMine`
  - message của user hiện tại hay không
- `clientMessageId`
  - FE tạo UUID cho mỗi lần gửi và giữ nguyên khi retry. Cùng ID + cùng canonical payload replay message cũ; cùng ID + payload khác trả `409 CHAT_CLIENT_MESSAGE_CONFLICT`.

### `NotificationResponse`
- `type`
  - loại notification
- `title`, `message`
  - nội dung hiển thị
- `relatedEntityType`, `relatedEntityId`
  - entity liên quan
- `deepLink`
  - route FE nên mở
- `actionType`
  - kiểu CTA
- `read`, `readAt`
  - trạng thái đọc
- `unreadCount`
  - badge count
- `realtimeEventKind`
  - loại realtime event đã sinh notification

## FE không được làm
- Không coi websocket là nguồn sự thật cuối cùng.
- Không gửi `Idempotency-Key` cho message. Dùng `clientMessageId`.
- Không render message theo thứ tự nhận gói mạng.
- Không quên refresh unread count sau reconnect.
- Không đánh dấu đã đọc chỉ ở local state mà không gọi API.
- Không dùng notification để thay thế audit hay activity log.

## FE anti-patterns
- Không mở chat thread bằng dữ liệu socket chưa sync.
- Không coi notification badge đã đủ để render list full.
- Không rely vào `lastMessageContent` nếu đã có detail message list.

## Response JSON example
### Conversation list item
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "items": [
      {
        "id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
        "type": "DIRECT",
        "status": "ACTIVE",
        "otherUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "otherUserName": "Nguyễn Văn A",
        "otherUserAvatarUrl": "https://example.com/avatar.jpg",
        "lastMessageContent": "See you tomorrow",
        "lastMessageAt": "2026-07-13T09:55:00",
        "createdAt": "2026-07-13T08:00:00",
        "unreadCount": 2
      }
    ]
  }
}
```

### Notification
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "items": [
      {
        "notificationId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
        "type": "BOOKING_ACCEPTED",
        "title": "Mentor đã chấp nhận booking",
        "message": "Booking của bạn đã được chấp nhận.",
        "relatedEntityType": "BOOKING",
        "relatedEntityId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "deepLink": "/bookings/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "actionType": "OPEN_DETAIL",
        "read": false,
        "readAt": null,
        "createdAt": "2026-07-13T09:55:00",
        "unreadCount": 5,
        "realtimeEventKind": "BOOKING_STATUS_CHANGED"
      }
    ]
  }
}
```

## UI mapping
- Conversation list:
  - dùng `otherUserName`, `lastMessageAt`, `unreadCount`
- Message thread:
  - bubble theo `isMine`
  - sort theo `createdAt`
- Notification inbox:
  - badge `unreadCount`
  - deep link để mở đúng màn liên quan
- Realtime badge:
  - chỉ là signal, list vẫn phải refresh REST

## API success/error behavior
- conversation/message read/send
  - success: update local UI, sau đó refresh list/thread
  - 404: conversation không còn hợp lệ
  - 409: conversation đã thay đổi trạng thái
- notification read/read-all
  - success: update badge + inbox
  - 404: notification không còn tồn tại/đã được xử lý

## Ghi chú cho AI Agent và FE dev
- REST là nguồn sự thật cuối cùng.
- STOMP có thể tới lệch thứ tự, nên FE phải sort lại theo `createdAt`.
- Dedup theo `messageId` / `notificationId` khi cần.

## Booking Engagement Notifications
Booking reminders are created in-app at 24 hours, 2 hours and 15 minutes before an eligible confirmed session. A feedback prompt is sent once after a mentee-confirmed session. These are server-deduped; FE only renders the normal notification inbox and deep-links to the booking detail.

## Everyday Booking Chat Contract
Chat is a persistent direct mentor/mentee relationship. REST is canonical; STOMP only accelerates delivery.

### Conversation and message APIs
```text
GET   /api/me/conversations?cursor=&limit=
GET   /api/me/conversations/{conversationId}
GET   /api/me/conversations/{conversationId}/messages?beforeSequence=&afterSequence=&limit=
PATCH /api/me/conversations/{conversationId}/read
POST  /api/me/conversations/{conversationId}/messages
PATCH /api/me/conversations/{conversationId}/messages/{messageId}
DELETE /api/me/conversations/{conversationId}/messages/{messageId}
GET   /api/me/conversations/unread-count
```

- `sequence` is the sole message ordering and read-cursor source. `createdAt` is display-only.
- No sequence parameter returns the newest-first initial page. `beforeSequence` fetches older messages; `afterSequence` repairs reconnect gaps. Sending both is `400 CHAT_MESSAGE_CURSOR_INVALID`.
- Read request is `{ "lastReadSequence": 128 }`; response returns `conversationId`, `myLastReadSequence`, `otherLastReadSequence`, and `unreadCount`.
- Send request requires `{ clientMessageId, content?, replyToMessageId?, attachmentIntentIds? }`. Same client ID and canonical payload replays; changed payload returns `409 CHAT_CLIENT_MESSAGE_CONFLICT`.
- Text messages can be edited or tombstoned by their sender within 15 minutes with `expectedVersion`. System messages cannot be changed.

`ConversationResponse` returns direct counterpart metadata, last message, read sequences, unread count, `latestBooking`, and derived `messagingAccess`, `readOnlyReason`, `messagingWindowEndsAt`, `postSessionChatPermanent`, `canSendMessages`, `canUploadAttachments`, `canDownloadAttachments`. It does not expose legacy `sourceType/sourceId`.

### Private attachments
```text
POST /api/me/conversations/{conversationId}/attachment-upload-intents
POST /api/me/chat-attachments/{attachmentId}/download-url
```

- Upload request: `filename`, `contentType`, `sizeBytes`; response: `uploadIntentId`, `uploadUrl`, `expiresAt`, `requiredContentType`.
- Allowed: PNG, JPEG, PDF, DOCX. Limits: 10 MiB/file, five files/message, 50 MiB/user/day.
- Client never receives or sends object keys. PNG/JPEG can inline through private URL; PDF/DOCX are attachment download only.
- Attachment state: `ACTIVE`, `EXPIRED`, `REVOKED`, `TAKEN_DOWN`. New URLs stop after 90 days; physical cleanup is eligible after a seven-day grace period unless a dispute/admin hold exists.

### Realtime
Subscribe to `/user/queue/chat/messages`, `/user/queue/chat/inbox`, `/user/queue/chat/unread`, and `/user/queue/chat/typing`. Client may send ephemeral typing only to `/app/chat/typing`. Dedupe by event/message ID, order by `sequence`, and call the `afterSequence` REST API after reconnect or a gap.

`CHAT_UNREAD` is one unread notification per recipient/conversation. It updates on new message, sends no per-message email, and is cleared once the recipient reads through the current sequence.

## Attachment retention
- Chat attachment là private participant content, không phải Blog asset hoặc Mentor Service Resource.
- Allowed types: `PNG`, `JPEG`, `PDF`, `DOCX`; tối đa 10 MiB/file, 5 file/message và 50 MiB/user/day.
- Image có thể inline qua URL private ngắn hạn. PDF/DOCX luôn download attachment, không preview server-side.
- Attachment hết quyền cấp URL sau 90 ngày. Object bị xóa vật lý sau thêm 7 ngày grace, trừ khi dispute/admin hold còn hiệu lực.

## File storage capability
FE gọi `GET /api/files/capabilities` sau khi có auth để biết runtime hiện có object storage hay không. Response chỉ gồm boolean `privateFileStorageAvailable`, `chatAttachmentsAvailable`, `mentorServiceResourcesAvailable`, `blogAssetUploadsAvailable`; không chứa bucket, endpoint hoặc object key. Khi một capability là `false`, ẩn thao tác file tương ứng thay vì thử upload rồi đoán lỗi.
- Xóa message thu hồi access ngay, nhưng không được vượt qua hold/retention audit.
