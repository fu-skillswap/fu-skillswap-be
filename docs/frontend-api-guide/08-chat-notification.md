# Chat & Notification Service (`08-chat-notification.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Trò chuyện và Thông báo Thời gian Thực (Chat & Notification Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Chat & Notification Service** quản lý hệ thống nhắn tin trực tiếp 1-1 / nhóm (`Conversation`), tài liệu đính kèm riêng tư (`Private Chat Attachments`), truyền tin thời gian thực STOMP over WebSocket (`/ws-stomp`), và trung tâm thông báo đa kênh (`Notification Center`) trên SkillSwap.

### Trách nhiệm chính của Service
- **Quản lý Cuộc hội thoại Tự động (`Auto-Created Conversations`)**: Đội ngũ Frontend không tự tạo conversation thủ công. Backend tự động sinh cuộc hội thoại khi đơn đặt lịch đạt trạng thái `CONFIRMED`.
- **Quản lý Con trỏ Đọc & Thứ tự Tin nhắn Nghiêm ngặt (`Sequence-Based Ordering`)**: Sử dụng trường `sequence` (số nguyên tăng dần đồng nhất per conversation) làm nguồn sự thật duy nhất cho thứ tự hiển thị và con trỏ đã đọc (`lastReadSequence`). Trường `createdAt` chỉ dùng để hiển thị giao diện.
- **Tải File Đính kèm 2 Bước Bảo mật (`Private Attachment Intents`)**: Xin Presigned Upload Intent cho các định dạng cho phép (`PNG`, `JPEG`, `PDF`, `DOCX`, tối đa 10 MiB/file, 5 file/message, 50 MiB/ngày). Link tải file bảo mật (`download-url`) được cấp thời gian ngắn qua API.
- **Đồng bộ Thời gian thực STOMP & Phục hồi Khi Ngắt mạng (`STOMP & Gap Repair`)**: Đẩy tin nhắn mới, thông báo và hiệu ứng gõ phím (typing) qua WebSocket (`/ws-stomp`). Khi mất kết nối, Frontend tự động dùng `afterSequence` để tải bù các tin nhắn bị thiếu.
- **Trung tâm Thông báo (`Notification Center`)**: Quản lý danh sách thông báo phân trang cursor (`/api/me/notifications`), đếm số thông báo chưa đọc (`unread-count`), đánh dấu đã đọc một hoặc tất cả (`read-all`).

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **REST/Database làm Nguồn Sự Thật Duy nhất**: STOMP WebSocket chỉ đóng vai trò là đường truyền nhanh (delivery hint). Nếu kết nối WebSocket bị đứt, ứng dụng vẫn hoạt động bình thường qua REST APIs.
2. **Quyền Hạn Chat Dựa trên Đơn Đặt Lịch (Booking-Derived Permissions)**: Các cờ `canSendMessages`, `canUploadAttachments`, `canDownloadAttachments` được Backend tính toán động dựa vào trạng thái booking, snapshot chính sách chat, và cờ khóa bảo mật. Frontend không tự tính các quyền này từ UI.
3. **Tránh Trùng Tin nhắn Khi Mạng Chập Chờn (`clientMessageId`)**: Frontend truyền `clientMessageId` (UUIDv4) cho mỗi request gửi tin nhắn. Nếu client retry do timeout, Backend nhận biết và trả lại đúng tin nhắn cũ đã lưu mà không nhân đôi message.
4. **Bảo mật Tài liệu Nhạy cảm**: File đính kèm chat không lưu public. Quyền tải đính kèm bị thu hồi ngay lập tức nếu tin nhắn bị xóa (`DELETE`), conversation bị block, hoặc quá mốc 90 ngày.
5. **Gộp Thông báo Thông minh (Chat Notification Deduplication)**: Nhiều tin nhắn mới từ cùng một cuộc hội thoại chỉ tạo 1 thông báo active (`CHAT_UNREAD`). Khi người dùng đọc tin nhắn đến `sequence` mới nhất, thông báo chat tương ứng tự động được làm sạch.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                    LUỒNG TRÒ CHUYỆN VÀ ĐỒNG BỘ REALTIME                               |
+-------------------------------------------------------------------------------------------------------+

  Frontend (Chat Box)                  Backend (SkillSwap API)                 STOMP WebSocket / Outbox
          |                                     |                                         |
   1. Booking chuyển sang CONFIRMED ------------>|-- Backend tự tạo Conversation ---------->|
          |                                     |                                         |
   2. GET /api/me/conversations ---------------->|-- Trả danh sách Inbox & permissions ----|
          |<-- 200 OK (CursorPageResponse) -----|                                         |
          |                                     |                                         |
   3. Kết nối WebSocket /ws-stomp & Subscribe ->|---------------------------------------->|
      (/user/queue/chat/messages, unread, etc)  |                                         |
          |                                     |                                         |
   4. GET /conversations/{id}/messages --------->|-- Trả 30 tin nhắn mới nhất (newest-first)
          |<-- 200 OK (Sort theo sequence asc)--|                                         |
          |                                     |                                         |
   5. User gõ phím -> Send STOMP Ephemeral ----->|---------------------------------------->| (Bắn /queue/chat/typing)
          |                                     |                                         |
   6. User gửi tin nhắn Text + File             |                                         |
          |-- POST /attachment-upload-intents ->|-- Sinh uploadUrl (S3/R2) --------------->|
          |-- Direct PUT file binary sang S3 -->|                                         |
          |-- POST /conversations/{id}/messages |                                         |
          |   (clientMessageId, content, intent)|-- Lưu DB & cấp sequence mới ------------>|
          |<-- 201 Created (MessageResponse) ---|-- Outbox Worker đẩy STOMP Message ------>|
          |                                     |<========================================|
   7. Người còn lại nhận STOMP Realtime Message |                                         |
   8. PATCH /conversations/{id}/read ---------->|-- Advance lastReadSequence ------------->|
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Mô hình Phân trang Con trỏ Sequence (`Sequence Window Pagination`)
- API `/api/me/conversations/{id}/messages` hỗ trợ 2 tham số con trỏ:
  - KHÔNG truyền `beforeSequence` và `afterSequence`: Lấy trang 30 tin nhắn mới nhất (Initial page).
  - `beforeSequence = X`: Lấy các tin nhắn cũ hơn sequence `X` (Dùng khi người dùng cuộn lên trên để xem lịch sử).
  - `afterSequence = Y`: Lấy các tin nhắn mới hơn sequence `Y` (Dùng khi vừa reconnect WebSocket để lấy bù tin nhắn bị sót).
  - **Lưu ý**: Truyền đồng thời cả `beforeSequence` và `afterSequence` sẽ bị Backend từ chối với lỗi `400 CHAT_MESSAGE_CURSOR_INVALID`.

### 4.2 Lý do Chuyển Chế độ Chỉ Đọc (`readOnlyReason`)
Khi cờ `canSendMessages = false`, trường `readOnlyReason` sẽ trả về lý do cụ thể để Frontend hiển thị Banner giải thích:
- `NO_EFFECTIVE_BOOKING`: Chưa có đơn đặt lịch hợp lệ.
- `CHAT_WINDOW_EXPIRED`: Đã quá thời hạn 24h sau buổi học (đối với gói không bật post-session chat).
- `UNDER_REVIEW`: Buổi học đang bị khiếu nại tranh chấp.
- `PARTICIPANT_BLOCKED`: Một trong hai người dùng đã bấm Chặn.
- `ADMIN_LOCKED` / `ACCOUNT_RESTRICTED`: Quản trị viên khóa phòng chat hoặc tài khoản bị hạn chế.

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- |
| `GET` | `/api/me/conversations` | Participant | Lấy danh sách hộp thư thoại Inbox (phân trang Cursor) | Màn hình Hộp thư Khung Chat |
| `GET` | `/api/me/conversations/{conversationId}` | Participant | Lấy thông tin metadata và cờ quyền hạn của phòng chat | Màn hình Khung Chat |
| `GET` | `/api/me/conversations/{conversationId}/messages` | Participant | Lấy danh sách tin nhắn theo cửa sổ `sequence` | Mở khung chat / Cuộn xem lịch sử / Reconnect |
| `POST` | `/api/me/conversations/{conversationId}/messages` | Participant | Gửi tin nhắn mới (hỗ trợ `clientMessageId` & đính kèm) | Bấm nút Gửi tin nhắn |
| `PATCH` | `/api/me/conversations/{conversationId}/messages/{messageId}` | Sender | Chỉnh sửa nội dung tin nhắn Text (trong vòng 15 phút) | Bấm "Sửa tin nhắn" |
| `DELETE` | `/api/me/conversations/{conversationId}/messages/{messageId}` | Sender | Xóa tin nhắn (tạo Tombstone, thu hồi quyền file) | Bấm "Xóa tin nhắn" |
| `PATCH` | `/api/me/conversations/{conversationId}/read` | Participant | Cập nhật con trỏ `lastReadSequence` của caller | Khi người dùng xem tin nhắn mới |
| `GET` | `/api/me/conversations/unread-count` | Participant | Lấy tổng số tin nhắn chưa đọc toàn bộ Inbox | Badge đếm chưa đọc trên Navbar |
| `POST` | `/api/me/conversations/{conversationId}/attachment-upload-intents` | Participant | Xin Presigned URL upload file đính kèm chat | Khi chọn file đính kèm trong chat |
| `POST` | `/api/me/chat-attachments/{attachmentId}/download-url` | Entitled | Xin link tải bảo mật cho file đính kèm | Khi bấm xem/tải file đính kèm |
| `GET` | `/api/me/notifications` | Authenticated | Lấy danh sách thông báo hệ thống (Cursor Page) | Center Thông báo |
| `GET` | `/api/me/notifications/unread-count` | Authenticated | Lấy đếm số thông báo chưa đọc | Badge quả chuông trên Navbar |
| `PATCH` | `/api/me/notifications/{id}/read` | Authenticated | Đánh dấu 1 thông báo là đã đọc | Bấm vào 1 thông báo |
| `PATCH` | `/api/me/notifications/read-all` | Authenticated | Đánh dấu tất cả thông báo là đã đọc | Bấm "Đánh dấu tất cả đã đọc" |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `POST /api/me/conversations/{conversationId}/messages`

#### Purpose
Gửi tin nhắn văn bản hoặc tin nhắn có đính kèm file vào phòng chat.

#### Request Body (`SendMessageRequest`)
```json
{
  "clientMessageId": "019f7234-aaaa-bbbb-cccc-1234567890ab",
  "content": "Em chào anh, em vừa tải tài liệu CV lên nhờ anh xem giúp ạ.",
  "replyToMessageId": null,
  "attachmentIntentIds": ["019f7234-aaaa-bbbb-cccc-1234567890ad"]
}
```

#### Response Body (`MessageResponse`)
```json
{
  "timestamp": "2026-08-04T10:00:00Z",
  "status": 201,
  "code": "SUCCESS",
  "message": "Created",
  "data": {
    "id": "019f7234-aaaa-bbbb-cccc-1234567890ab",
    "sequence": 129,
    "conversationId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
    "senderId": "019f6234-aaaa-bbbb-cccc-1234567890ac",
    "senderName": "Nguyễn Văn A",
    "messageType": "TEXT",
    "content": "Em chào anh, em vừa tải tài liệu CV lên nhờ anh xem giúp ạ.",
    "state": "ACTIVE",
    "version": 0,
    "createdAt": "2026-08-04T10:00:00Z",
    "isMine": true,
    "isReadByOther": false,
    "attachments": [
      {
        "attachmentId": "019f7234-aaaa-bbbb-cccc-1234567890ad",
        "filename": "CV_NguyenVanA.pdf",
        "sizeBytes": 1048576,
        "contentType": "application/pdf",
        "state": "ACTIVE"
      }
    ]
  }
}
```

---

### 6.2 `PATCH /api/me/conversations/{conversationId}/read`

#### Purpose
Cập nhật vị trí tin nhắn cuối cùng đã đọc của người dùng (`lastReadSequence`).

#### Request Body
```json
{
  "lastReadSequence": 129
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Phục hồi Tin nhắn Khi Reconnect WebSocket (`Gap Repair`)

```
Frontend (Chat Window)                 STOMP Broker / Outbox                   Backend REST API
        |                                       |                                     |
   1. Mất kết nối Internet (Disconnected) ------X                                     |
   2. Người dùng bên kia gửi 3 tin nhắn mới     |-- Lưu DB (Sequence: 130, 131, 132)  |
   3. Internet có lại (Reconnected) ------------>|                                     |
   4. Frontend lấy sequence lớn nhất hiện có (129)|                                     |
        |---------------------------------------------------------------------------->|
        | GET /api/me/conversations/{id}/messages?afterSequence=129                  |
        |<----------------------------------------------------------------------------|
        | Trả về 3 tin nhắn thiếu: [Seq 130, Seq 131, Seq 132]                       |
   5. Dedupe theo messageId & Append vào UI Chat                                     |
```

---

## 8. State Machine (Ma trận Trạng thái Messaging, Read Cursor & Notification)

### 8.1 Ma trận Quyền hạn Phòng Chat (`MessagingAccessState`)

```
                                  +-----------------------+
                                  |    FULL_ACCESS        | (Đang trong session / có cờ chat permanent)
                                  +-----------------------+
                                   /          |          \
                 Booking hết hạn  /           |           \ Có khiếu nại Dispute /
                 hoặc quá 24h    /            |            \ Bị Admin khóa
                                v             v             v
                    +-------------------+ +-------------------+ +-----------------------+
                    | CHAT_EXPIRED      | |   UNDER_REVIEW    | |     ADMIN_LOCKED      |
                    +-------------------+ +-------------------+ +-----------------------+
                    (Chuyển Read-Only)    (Khóa chat 2 chiều)  (Khóa toàn bộ truy cập)
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `CHAT_MESSAGE_CURSOR_INVALID` | Truyền đồng thời cả `beforeSequence` và `afterSequence`. | Chỉ truyền 1 hướng con trỏ duy nhất. |
| `403 FORBIDDEN` | `CHAT_CONVERSATION_READ_ONLY` | Phòng chat đã hết hạn 24h hoặc bị khóa chuyển sang Read-Only. | Hiển thị Banner nguyên nhân `readOnlyReason`, khóa khung nhập tin nhắn. |
| `409 RESOURCE_CONFLICT` | `CHAT_CLIENT_MESSAGE_CONFLICT` | Gửi lại `clientMessageId` cũ nhưng nội dung `content` bị khác. | Giữ nguyên bản nháp local, sinh `clientMessageId` mới nếu user có sửa chữ. |
| `409 RESOURCE_CONFLICT` | `CHAT_MESSAGE_VERSION_CONFLICT` | Sai trường `expectedVersion` khi sửa/xóa tin nhắn. | Refetch lại tin nhắn trước khi retry. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Kiểm soát Quyền Hạn Đơn Hàng (Booking Ownership)**: Quyền gửi tin nhắn, upload file và download đính kèm do Backend tính toán tự động dựa trên hợp đồng đặt lịch. Client tuyệt đối không tự sửa các cờ này.
2. **Bảo mật Tệp Đính kèm Chat**: Tất cả các file đính kèm (`attachments`) chỉ được tải thông qua API xin link tải bảo mật thời gian ngắn `POST /api/me/chat-attachments/{attachmentId}/download-url`. Không thể tải trực tiếp file bằng URL tĩnh.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Sắp xếp danh sách tin nhắn theo trường `sequence` tăng dần để hiển thị khung chat.
- Tiến hành Dedupe tin nhắn theo `messageId` khi nhận từ cả REST API và STOMP WebSocket.
- Tự động gọi `GET .../messages?afterSequence={maxSeq}` sau khi kết nối lại WebSocket thành công để lấy bù tin nhắn bị sót.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** tự tạo Conversation thủ công ở Client.
- **KHÔNG ĐƯỢC** dùng `createdAt` để sắp xếp thứ tự tin nhắn.
- **KHÔNG ĐƯỢC** truyền header `Idempotency-Key` cho API gửi tin nhắn (dùng `clientMessageId` trong body).

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **File Đính kèm Quá 90 Ngày**:
   - Sau 90 ngày, Backend dừng cấp link tải mới (`download-url`). Metadata file vẫn hiển thị nhưng nút Tải về bị làm mờ (Disabled) kèm nhãn *"Tệp đính kèm đã hết hạn lưu trữ"*.
2. **Xóa Tin nhắn Đã Gửi (`Message Tombstone`)**:
   - Khi xóa tin nhắn, nội dung tin nhắn chuyển thành nhãn *"Tin nhắn này đã bị xóa"*, đồng thời quyền tải các file đính kèm thuộc tin nhắn đó lập tức bị thu hồi.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Booking Service**: Tự động tạo phòng chat khi Booking chuyển `CONFIRMED`, cung cấp cờ snapshot chat.
- **Notification Service**: Đẩy thông báo quả chuông và badge đếm chưa đọc qua STOMP WebSocket.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Khung Chat Trực tiếp (`ChatWindow.tsx`)
- **React Components**: `ChatWindow.tsx`, `MessageList.tsx`, `ChatInputBar.tsx`, `AttachmentPreview.tsx`
- **APIs Triggered**:
  1. `GET /api/me/conversations/{id}/messages` (Khi mở chat hoặc cuộn lên)
  2. `POST /api/me/conversations/{id}/messages` (Khi bấm gửi)
  3. `PATCH /api/me/conversations/{id}/read` (Khi xem tin mới)
- **Expected Behavior**: Sort tin nhắn theo `sequence` tăng dần. Disable khung chat nếu `canSendMessages = false` và hiển thị lý do từ `readOnlyReason`.

#### B. Hộp thư Inbox (`ConversationInbox.tsx`)
- **React Components**: `ConversationInbox.tsx`, `ConversationItemCard.tsx`, `UnreadBadge.tsx`
- **APIs Triggered**:
  1. `GET /api/me/conversations` (Tải danh sách phòng chat)
  2. STOMP Subscription `/user/queue/chat/inbox` (Nhận update realtime)
- **Expected Behavior**: Đưa phòng chat có tin nhắn mới nhất lên đầu danh sách. Hiển thị badge số tin chưa đọc.

---

### 14.2 Frontend Chat State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |    FETCHING_INBOX     | (Tải danh sách phòng chat)
                       +-----------------------+
                                   |
                          Chọn 1 Conversation
                                   |
                                   v
                       +-----------------------+
                       |    LOADING_MESSAGES   | (Tải tin nhắn ban đầu)
                       +-----------------------+
                                   |
                     Kết nối WebSocket & Render UI
                                   |
                                   v
                       +-----------------------+
                       |     CHAT_ACTIVE       | (Sẵn sàng chat realtime)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Inbox | Open Chat Window | Send Text Message | Reconnect Socket | User Action |
| --- | --- | --- | --- | --- | --- |
| `GET /api/me/conversations` | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET .../messages` | ❌ KHÔNG | ✅ CÓ (Initial page) | ❌ KHÔNG | ✅ CÓ (`afterSequence`) | ❌ KHÔNG |
| `POST .../messages` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ✅ Bấm "Gửi" |
| `PATCH .../read` | ❌ KHÔNG | ✅ CÓ (Khi xem tin) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Khung Chat Chuyển Sang Read-Only (`HTTP 403`)
- **UI Component**: `ReadOnlyChatBanner.tsx` trên khung chat.
- **Visual State**: Khung nhập tin nhắn bị khóa (Disabled) + Banner màu vàng.
- **Message**: *"Cuộc trò chuyện đã chuyển sang chế độ chỉ đọc do thời hạn hỗ trợ của buổi học đã kết thúc."*

#### B. Lỗi File Đính kèm Vượt Dung lượng (`HTTP 400`)
- **UI Component**: Modal Báo Lỗi Upload File.
- **Message**: *"Kích thước tập tin vượt quá giới hạn 10 MiB hoặc không đúng định dạng hỗ trợ (PNG, JPEG, PDF, DOCX)."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['conversations', 'inbox']` | 1 phút | 10 phút | `true` | Nhận STOMP event inbox mới |
| `['messages', conversationId]` | 0 ms | 15 phút | `false` | Gửi tin nhắn mới hoặc nhận STOMP message |
| `['notifications', 'unread-count']` | 1 phút | 10 phút | `true` | Nhận STOMP badge event |
