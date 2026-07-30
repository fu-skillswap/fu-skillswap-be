# Chat & Notification

## Contract
- REST/database la source of truth. STOMP chi la delivery hint.
- Conversation duoc tao khi booking tro thanh effective; FE khong tao thread truc tiep.
- `sequence` la thu tu va read cursor duy nhat. `createdAt` chi de hien thi.
- Chat history duoc giu lai. Quyen gui, upload va download la derived permission tu booking policy va safety controls.

## Conversation APIs
Tat ca endpoint deu can bearer token va caller phai la participant.

| Method | Endpoint | Response | Note |
| --- | --- | --- | --- |
| GET | `/api/me/conversations?cursor=&limit=` | `CursorPageResponse<ConversationResponse>` | Inbox, newest activity first. `limit` mac dinh 20, toi da 50. |
| GET | `/api/me/conversations/{conversationId}` | `ConversationResponse` | Detail metadata, khong kem message page. |
| GET | `/api/me/conversations/{conversationId}/messages?beforeSequence=&afterSequence=&limit=` | `List<MessageResponse>` | Initial page newest-first; FE sap xep lai sequence tang dan de render. |
| PATCH | `/api/me/conversations/{conversationId}/read` | `ConversationReadResponse` | Body `{ "lastReadSequence": 128 }`; cursor chi tang. |
| GET | `/api/me/conversations/unread-count` | `{ "totalUnreadCount": 3 }` | Tong message chua doc. |
| POST | `/api/me/conversations/{conversationId}/block` | `ConversationBlockResponse` | Chan hai chieu, van doc history. |
| DELETE | `/api/me/conversations/{conversationId}/block` | `ConversationBlockResponse` | Chi go block do caller tao. |
| POST | `/api/me/conversations/{conversationId}/reports` | `ChatReportResponse` | Report participant con lai. |

### Message synchronization
- Khong gui `beforeSequence` va `afterSequence`: lay initial newest-first page.
- `beforeSequence`: lay message cu hon.
- `afterSequence`: lay message moi hon de repair reconnect/gap.
- Gui ca hai: `400 CHAT_MESSAGE_CURSOR_INVALID`.
- FE dedupe theo `messageId`, sap xep nghiem ngat theo `sequence`, va sync lai bang `afterSequence` sau reconnect/gap.

### `ConversationResponse`
| Field | Meaning |
| --- | --- |
| `id`, `type`, `status` | Conversation identity/state. Khong co `sourceType` hay `sourceId`. |
| `otherUserId`, `otherUserName`, `otherUserAvatarUrl` | Participant con lai. |
| `lastMessageContent`, `lastMessageAt`, `createdAt` | Inbox preview va activity time. |
| `unreadCount`, `myLastReadSequence`, `otherLastReadSequence` | Read state theo sequence. |
| `messagingAccess`, `readOnlyReason`, `messagingWindowEndsAt`, `postSessionChatPermanent` | Booking-derived chat policy. |
| `canSendMessages`, `canUploadAttachments`, `canDownloadAttachments` | Permission hien tai; FE khong tu map raw booking/payment status. |

`messagingWindowEndsAt` la `null` khi permanent entitlement. Read-only reason co the la `ADMIN_LOCKED`, `ACCOUNT_RESTRICTED`, `UNDER_REVIEW`, `PARTICIPANT_BLOCKED`, `NO_EFFECTIVE_BOOKING` hoac `CHAT_WINDOW_EXPIRED`.

## Message APIs
| Method | Endpoint | Body | Response |
| --- | --- | --- | --- |
| POST | `/api/me/conversations/{conversationId}/messages` | `SendMessageRequest` | `MessageResponse` (`201`) |
| PATCH | `/api/me/conversations/{conversationId}/messages/{messageId}` | `{ "content": "...", "expectedVersion": 0 }` | `MessageResponse` |
| DELETE | `/api/me/conversations/{conversationId}/messages/{messageId}` | `{ "expectedVersion": 0 }` | tombstoned `MessageResponse` |

### Send request
```json
{
  "clientMessageId": "019f7234-aaaa-bbbb-cccc-1234567890ab",
  "content": "Em gui CV de anh review.",
  "replyToMessageId": "019f7234-aaaa-bbbb-cccc-1234567890ac",
  "attachmentIntentIds": ["019f7234-aaaa-bbbb-cccc-1234567890ad"]
}
```

- Khong dung `Idempotency-Key` cho message.
- Cung `clientMessageId` va canonical payload se replay message cu. Cung ID nhung payload khac tra `409 CHAT_CLIENT_MESSAGE_CONFLICT`.
- `replyToMessageId` phai thuoc cung conversation.
- Chi sender duoc edit/delete text message `ACTIVE` trong 15 phut. System message khong the sua/xoa.
- `expectedVersion` la field `version` cua `MessageResponse` gan nhat.
- Delete an content, giu tombstone/sequence va revoke attachment access ngay.

### `MessageResponse`
Bao gom `id`, `sequence`, `conversationId`, sender metadata, `messageType`, `content`, `state`, `version`, `editedAt`, `deletedAt`, `attachments`, `createdAt`, `isMine` va `isReadByOther`.

`isReadByOther` chi co y nghia voi text message cua caller; UI khong duoc dien giai no nhu delivery receipt cua socket.

## Private attachments
| Method | Endpoint | Response |
| --- | --- | --- |
| POST | `/api/me/conversations/{conversationId}/attachment-upload-intents` | `{ uploadIntentId, uploadUrl, expiresAt, requiredContentType }` (`201`) |
| POST | `/api/me/chat-attachments/{attachmentId}/download-url` | `{ downloadUrl, expiresAt }` |

Upload body:
```json
{
  "filename": "cv.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 1048576
}
```

- Allowed: PNG, JPEG, PDF, DOCX. Max 10 MiB/file, 5 files/message, 50 MiB/user/day.
- Backend owns storage key; client khong gui/nhan object key.
- PNG/JPEG co the inline qua private URL. PDF/DOCX luon la attachment download.
- Attachment response state: `ACTIVE`, `EXPIRED`, `REVOKED`, `TAKEN_DOWN`.
- Upload intent state la internal lifecycle rieng: `PENDING_UPLOAD`, `CONFIRMED`, `EXPIRED`, `REJECTED`.
- URL moi khong duoc cap sau 90 ngay. Metadata van hien thi; physical cleanup chi xay ra sau 7 ngay grace va khong vuot dispute/admin hold.

## Notification APIs
| Method | Endpoint | Response |
| --- | --- | --- |
| GET | `/api/me/notifications?unreadOnly=&cursor=&limit=` | `CursorPageResponse<NotificationResponse>` |
| GET | `/api/me/notifications/unread-count` | `UnreadCountResponse` |
| PATCH | `/api/me/notifications/{id}/read` | `ApiResponse<Void>` |
| PATCH | `/api/me/notifications/read-all` | `ApiResponse<Void>` |

`CHAT_UNREAD` duoc dedupe theo recipient/conversation: message moi update mot notification dang active, khong gui email cho tung message. Khi user read den latest sequence, notification duoc clear.

## Realtime
STOMP endpoint: `/ws-stomp`.

Subscribe:
```text
/user/queue/chat/messages
/user/queue/chat/inbox
/user/queue/chat/unread
/user/queue/chat/typing
/user/queue/notifications/items
/user/queue/notifications/badge
```

Client chi duoc send typing ephemeral den `/app/chat/typing`. Typing la participant-authorized, rate-limited va khong persist/outbox.

Durable chat events hien tai thong bao message/inbox/unread qua outbox sau DB commit. RabbitMQ/STOMP unavailable khong duoc phep lam mat message; FE phai REST-sync sau reconnect.

## Moderation
- User report/block qua conversation endpoints.
- Admin dung `GET /api/admin/chat-reports?status=&page=&size=`, `PATCH /api/admin/chat-reports/{reportId}`, va `PATCH /api/admin/chat-reports/conversations/{conversationId}/lock`.
- Admin/account restriction overrides booking-derived access. `UNDER_REVIEW` la temporary two-way read-only.

## FE recovery
- `400 CHAT_MESSAGE_CURSOR_INVALID`: chi gui mot cursor direction.
- `403 CHAT_CONVERSATION_READ_ONLY` hoac `CHAT_CONVERSATION_LOCKED`: disable send/upload va refetch conversation state.
- `409 CHAT_CLIENT_MESSAGE_CONFLICT`: giu local draft; chi tao `clientMessageId` moi khi user thuc su sua noi dung.
- `409 CHAT_MESSAGE_VERSION_CONFLICT`: refetch thread truoc khi retry edit/delete.
- `404` attachment download: coi nhu unavailable, khong leak object/resource state.
