# Frontend Integration Guide — Realtime WebSocket (STOMP), Chat & Thông Báo

Tài liệu này hướng dẫn chi tiết cho các lập trình viên Frontend (FE) cách tích hợp với hệ thống **Realtime WebSocket (STOMP Protocol)**, luồng **Trò chuyện trực tiếp (Direct Chat)** và luồng **Thông báo thời gian thực (Notifications & Badges)** của SkillSwap Backend.

---

## 1. Giới Thiệu Công Nghệ: WebSocket + STOMP Relay

### 1.1 Tổng Quan Về Công Nghệ
* **WebSocket**: Giao thức truyền thông hai chiều song công (Full-duplex) qua một kết nối TCP duy nhất kéo dài (Persistent Connection). Khác với HTTP truyền thống (Client gửi Request $\rightarrow$ Server trả Response rồi đóng kết nối), WebSocket cho phép Server chủ động đẩy dữ liệu (Server Push) xuống Client ngay tức thì với độ trễ tính bằng mili-giây.
* **STOMP (Simple Text Oriented Messaging Protocol)**: Là giao thức tầng ứng dụng (Application Layer Protocol) chạy trên nền WebSocket (tương tự như HTTP chạy trên nền TCP). STOMP định nghĩa cấu trúc khung truyền tin chuẩn hóa bao gồm các lệnh: `CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, `UNSUBSCRIBE`, `DISCONNECT` cùng với các Header và Body.

```text
+-----------------------------------------------------------------------+
|                 Frontend Web Application (React / Next.js)            |
|   +---------------------------------------------------------------+   |
|   |         STOMP Client Library (@stomp/stompjs)                 |   |
|   +---------------------------------------------------------------+   |
+-----------------------------------|-----------------------------------+
                                    | Giao thức STOMP Frames (Pub/Sub)
+-----------------------------------v-----------------------------------+
|                     WebSocket Connection (WSS)                        |
+-----------------------------------|-----------------------------------+
                                    | Handshake & STOMP Auth
+-----------------------------------v-----------------------------------+
|                     Backend Spring Boot (SkillSwap)                   |
|  - StompConnectAuthChannelInterceptor (Xác thực JWT Token)            |
|  - RealtimeFanoutService (Định tuyến kênh theo User ID)               |
+-----------------------------------------------------------------------+
```

### 1.2 Ý Nghĩa & Lý Do Áp Dụng Trong SkillSwap
1. **Trải nghiệm tức thời (Instant UX)**: Người dùng nhận được tin nhắn chat, thông báo mời học, chấp nhận lịch, cập nhật thanh toán ngay lập tức mà không cần bấm F5 hoặc chờ chu kỳ Polling.
2. **Tiết kiệm tài nguyên mạng & Server**: Loại bỏ hoàn toàn cơ chế HTTP Polling liên tục (Polling định kỳ gây lãng phí băng thông và vắt kiệt CPU Server khi có nhiều người dùng đồng thời).
3. **Mô hình Pub/Sub bảo mật theo User**: Hệ thống sử dụng tiền tố `/user/queue/...` giúp cô lập hoàn toàn luồng tin của từng người dùng, ngăn chặn việc nghe lén tin nhắn của người khác.

### 1.3 Đánh Giá Ưu Điểm & Thách Thức (Pros & Cons)

| Tiêu chí | Ưu điểm (Pros) | Thách thức (Cons) | Giải pháp xử lý ở Frontend |
| :--- | :--- | :--- | :--- |
| **Độ trễ (Latency)** | Cực thấp (< 50ms), trải nghiệm mượt mà | Không có | Tối ưu render UI với animation nhẹ nhàng |
| **Kết nối (Connection)** | Tiết kiệm băng thông, duy trì 1 socket duy nhất | Kết nối Stateful: Mạng chập chờn sẽ bị ngắt | Cấu hình Heartbeat tự phát hiện đứt mạng và tự Reconnect |
| **Độ tin cậy dữ liệu** | Pub/Sub rõ ràng theo từng topic/queue | Có nguy cơ mất gói tin nếu rớt mạng đúng lúc Server bắn tin | Kết hợp REST API lấy snapshot ban đầu + cơ chế Gap Repair bằng `afterSequence` |
| **Xác thực (Auth)** | Xác thực JWT trực tiếp trong CONNECT frame | Access Token hết hạn làm đứt kết nối | Lắng nghe sự kiện ngắt kết nối, gọi Refresh Token lấy token mới và kích hoạt Reconnect |

---

## 2. Cấu Hình Kết Nối Phía Frontend

### 2.1 Cài Đặt Thư Viện Khuyến Nghị
Khuyến nghị sử dụng thư viện chính thống `@stomp/stompjs` (không cần SockJS vì các trình duyệt hiện đại đều hỗ trợ Native WebSocket):
```bash
npm install @stomp/stompjs
# hoặc
yarn add @stomp/stompjs
```

### 2.2 Thông Số Kết Nối Backend
* **WebSocket Endpoint URL**:
  - Môi trường Local / Dev: `ws://localhost:8080/ws-stomp`
  - Môi trường Production: `wss://api.skillswap.asia/ws-stomp`
* **Xác thực (Authentication)**:
  - Khi gửi frame `CONNECT`, bắt buộc truyền Access Token trong `connectHeaders`:
    - Header: `Authorization: Bearer <ACCESS_TOKEN>` (hoặc `X-Access-Token: <ACCESS_TOKEN>`).

### 2.3 Mã Nguồn Mẫu Khởi Tạo STOMP Client (TypeScript / React)

```typescript
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

class RealtimeService {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();

  public connect(
    accessToken: string,
    onConnected?: () => void,
    onTokenExpired?: () => Promise<string | null>
  ) {
    if (this.client && this.client.active) {
      return;
    }

    const wsUrl = process.env.NEXT_PUBLIC_WS_URL || 'wss://api.skillswap.asia/ws-stomp';

    this.client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      heartbeatIncoming: 10000, // Nhận heartbeat từ server mỗi 10s
      heartbeatOutgoing: 10000, // Gửi heartbeat lên server mỗi 10s
      reconnectDelay: 5000,     // Tự động kết nối lại sau 5s nếu mất kết nối
      debug: (str) => {
        if (process.env.NODE_ENV === 'development') {
          console.log('[STOMP Debug]:', str);
        }
      },
    });

    this.client.onConnect = (frame) => {
      console.log('✅ STOMP Connected:', frame);
      if (onConnected) onConnected();
    };

    this.client.onStompError = async (frame) => {
      console.error('❌ STOMP Error:', frame.headers['message'], frame.body);
      // Xử lý khi Token hết hạn
      if (frame.headers['message']?.includes('token') && onTokenExpired) {
        const newToken = await onTokenExpired();
        if (newToken && this.client) {
          this.client.connectHeaders = { Authorization: `Bearer ${newToken}` };
        }
      }
    };

    this.client.onWebSocketClose = () => {
      console.warn('⚠️ WebSocket connection closed');
    };

    this.client.activate();
  }

  public subscribe<T>(destination: string, callback: (payload: T) => void): string {
    if (!this.client || !this.client.connected) {
      console.warn('Cannot subscribe, STOMP client is not connected:', destination);
      return '';
    }

    const sub = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const parsed: T = JSON.parse(message.body);
        callback(parsed);
      } catch (err) {
        console.error('Failed to parse STOMP message:', err);
      }
    });

    const subId = `${destination}_${Date.now()}`;
    this.subscriptions.set(subId, sub);
    return subId;
  }

  public unsubscribe(subId: string) {
    const sub = this.subscriptions.get(subId);
    if (sub) {
      sub.unsubscribe();
      this.subscriptions.delete(subId);
    }
  }

  public publish(destination: string, body: object) {
    if (this.client && this.client.connected) {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  }

  public disconnect() {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.client.deactivate();
      this.client = null;
    }
  }
}

export const realtimeService = new RealtimeService();
```

---

## 3. Danh Sách Kênh Realtime (STOMP Destinations)

> **QUY TẮC BẢO MẬT CỦA BACKEND**: Tất cả các kênh subscribe của Client bắt buộc phải bắt đầu bằng `/user/` (Backend sẽ tự động map đích danh tới User ID của phiên đăng nhập qua `Principal.name`).

```text
                      BACKEND REALTIME CHANNELS
                                  │
    ┌─────────────────────────────┼─────────────────────────────┐
    │                             │                             │
    ▼                             ▼                             ▼
[NOTIFICATIONS]                [CHAT]                       [BOOKING]
/user/queue/notifications/    /user/queue/chat/messages     /user/queue/bookings/status
  - items (Chi tiết noti)     /user/queue/chat/inbox
  - badge (Số đếm đỏ)         /user/queue/chat/unread
                              /user/queue/chat/typing
```

### Bảng Tổng Hợp Kênh STOMP

| Destination | Loại | Payload Type | Mục đích sử dụng ở Frontend |
| :--- | :---: | :--- | :--- |
| `/user/queue/notifications/items` | `SUBSCRIBE` | `NotificationResponse` | Nhận thông báo mới $\rightarrow$ hiện Toast/Popup và chèn vào đầu danh sách thông báo. |
| `/user/queue/notifications/badge` | `SUBSCRIBE` | `NotificationBadgePayload` | Cập nhật số đếm badge đỏ trên biểu tượng quả chuông ở Header/Navbar. |
| `/user/queue/chat/messages` | `SUBSCRIBE` | `ChatMessageEvent` | Nhận tin nhắn mới trong cuộc hội thoại $\rightarrow$ render vào khung chat đang mở. |
| `/user/queue/chat/inbox` | `SUBSCRIBE` | `ConversationResponse` | Cập nhật danh sách cuộc hội thoại bên Sidebar (preview tin nhắn cuối, thời gian). |
| `/user/queue/chat/unread` | `SUBSCRIBE` | `ChatUnreadPayload` | Cập nhật tổng số tin nhắn chưa đọc trên Tab Chat/Icon Chat toàn trang. |
| `/user/queue/chat/typing` | `SUBSCRIBE` | `TypingPayload` | Nhận sự kiện đối phương đang gõ $\rightarrow$ hiển thị hiệu ứng "..." (Typing Indicator). |
| `/app/chat/typing` | `SEND` | `ChatTypingRequest` | Frontend gửi lên khi người dùng đang gõ chữ vào ô nhập tin nhắn. |
| `/user/queue/bookings/status` | `SUBSCRIBE` | `BookingStatusUpdatedEvent` | Nhận cập nhật trạng thái Booking tức thời $\rightarrow$ cập nhật UI không cần reload. |

---

### 3.1 Ví dụ payload realtime và cách FE xử lý

Các ví dụ dưới đây mô tả payload đang được backend phát. Destination là contract hiện tại,
không thay đổi theo màn hình.

#### Chat: tin nhắn mới

Subscribe `/user/queue/chat/messages`. Event có `messageId` ổn định và `sequence` tăng dần
theo conversation:

```json
{
  "conversationId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
  "messageId": "019f8234-aaaa-bbbb-cccc-1234567890ab",
  "sequence": 129,
  "senderId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
  "senderName": "Nguyen Van B",
  "messageType": "TEXT",
  "content": "Chào bạn, mình đã xem tài liệu.",
  "createdAt": "2026-09-04T03:21:00Z",
  "conversationType": "DIRECT",
  "isSelf": false,
  "unreadCount": 3
}
```

FE đối chiếu event này với message REST theo `messageId` (không dùng nội dung làm khóa).
Nếu `messageId` đã có trong state thì bỏ qua lần nhận trùng; nếu sequence bị hở, gọi REST
với `afterSequence=<sequence-cuối-đã-biết>` để bù tin.

#### Chat: inbox, unread và typing

`/user/queue/chat/inbox` gửi lại summary conversation để cập nhật preview/sidebar. Ví dụ:

```json
{
  "id": "019f5234-aaaa-bbbb-cccc-1234567890ab",
  "type": "DIRECT",
  "status": "ACTIVE",
  "otherUserId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
  "otherUserName": "Nguyen Van B",
  "lastMessageContent": "Chào bạn, mình đã xem tài liệu.",
  "lastMessageAt": "2026-09-04T03:21:00Z",
  "unreadCount": 3,
  "contextType": "BOOKING"
}
```

`/user/queue/chat/unread` có payload `{ "totalUnreadCount": 3 }` để cập nhật badge Chat.
`/user/queue/chat/typing` có payload:

```json
{
  "conversationId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
  "senderId": "019f6234-aaaa-bbbb-cccc-1234567890ab",
  "typing": true
}
```

FE nên debounce typing (ví dụ khoảng 1–1,5 giây), gửi `typing: false` khi dừng gõ và không
hiển thị typing event như một message.

#### Notification: lịch sử REST và event realtime là hai mục đích khác nhau

REST `GET /api/me/notifications` dùng để tải lịch sử; WebSocket chỉ giúp UI phản hồi ngay.
Subscribe `/user/queue/notifications/items` để nhận notification mới:

```json
{
  "notificationId": "019f9234-aaaa-bbbb-cccc-1234567890ab",
  "type": "BOOKING_ACCEPTED",
  "title": "Mentor đã nhận lịch",
  "message": "Nguyen Van B đã chấp nhận lịch mentoring của bạn.",
  "relatedEntityType": "BOOKING",
  "relatedEntityId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
  "deepLink": "/bookings/019f4234-aaaa-bbbb-cccc-1234567890ab",
  "actionType": "VIEW_BOOKING",
  "read": false,
  "readAt": null,
  "createdAt": "2026-09-04T03:22:00Z",
  "unreadCount": 4,
  "realtimeEventKind": "CREATED"
}
```

Subscribe `/user/queue/notifications/badge` để cập nhật số badge:

```json
{
  "unreadCount": 4,
  "eventKind": "CREATED"
}
```

Khi nhận event mới, FE có thể hiện popup, cập nhật badge và chèn event vào đầu danh sách
tạm thời. Khi mở notification center hoặc sau reconnect, gọi lại REST để lấy snapshot chính
thức. `READ` và `READ_ALL` cũng có thể tạo badge event; không dùng event badge làm lịch sử.

#### Reconnect và at-least-once delivery

Mô hình realtime là at-least-once, không phải exactly-once. Sau disconnect:

1. Reconnect STOMP bằng access token còn hiệu lực.
2. Subscribe lại toàn bộ queue cần dùng.
3. Gọi REST notification history và chat history/snapshot.
4. Với từng conversation đang mở, gọi `afterSequence` từ sequence cuối đã lưu.
5. Merge theo `messageId`, sort theo `sequence` và bỏ qua event trùng.

Vì vậy FE không được tăng badge hoặc thêm message mù quáng mỗi lần nhận frame; cần áp dụng
deduplication theo khóa ổn định rồi mới cập nhật UI.

---

## 4. Chi Tiết Từng Luồng Nghiệp Vụ & Tích Hợp REST API

---

### 4.1 Luồng Thông Báo (Notifications)

```text
[Vào Web/F5] ────────► Gọi REST API (GET /api/me/notifications/unread-count)
                                │
[Mở Socket]  ────────► Subscribe: /user/queue/notifications/badge & items
                                │
[Có Noti Mới] ───────► Nhận STOMP payload ──► Render Toast & Tăng Badge
```

#### A. Data Models TypeScript

```typescript
export interface NotificationResponse {
  notificationId: string;
  type: string; // e.g. "BOOKING_ACCEPTED", "BOOKING_ISSUE_RESOLVED", "MENTOR_VERIFICATION_APPROVED"
  title: string;
  message: string;
  relatedEntityType: "BOOKING" | "CONVERSATION" | "FORUM_POST" | "BLOG_POST" | "MENTOR_VERIFICATION";
  relatedEntityId: string;
  deepLink: string; // e.g. "/bookings/019f4234-aaaa-bbbb-cccc-1234567890ab"
  actionType: string; // e.g. "VIEW_BOOKING", "OPEN_CHAT", "VIEW_DETAIL"
  read: boolean;
  readAt: string | null;
  createdAt: string;
  unreadCount?: number | null;
  realtimeEventKind?: string | null;
}

export interface NotificationBadgePayload {
  unreadCount: number;
  eventKind: "NOTIFICATION_CREATED" | "READ" | "READ_ALL";
}
```

#### B. Các REST API Phối Hợp

1. **Lấy danh sách thông báo (Phân trang Cursor cho Infinite Scroll)**:
   - `GET /api/me/notifications?cursor=<nextCursor>&limit=20&unreadOnly=false`
   - *Lưu ý*: `nextCursor` là chuỗi Opaque Base64 do backend trả về, FE không tự giải mã hoặc tự tạo chuỗi này.
2. **Lấy số lượng thông báo chưa đọc (Khởi tạo ban đầu)**:
   - `GET /api/me/notifications/unread-count` $\rightarrow$ Trả về `{ "status": 200, "data": { "unreadCount": 5 } }`.
3. **Đánh dấu 1 thông báo đã đọc**:
   - `PATCH /api/me/notifications/{id}/read`
4. **Đánh dấu đọc tất cả thông báo**:
   - `PATCH /api/me/notifications/read-all`

---

### 4.2 Luồng Chat & Trò Chuyện Trực Tiếp (Direct Chat)

```text
[Mở Trang Chat] ───────► Gọi REST GET /api/me/conversations (Danh sách Inbox)
                               │
[Chọn Cuộc Chat] ──────► Gọi REST GET /api/me/conversations/{id}/messages (Lịch sử)
                               │
[Subscribe Socket] ────► Lắng nghe: /user/queue/chat/messages & typing
                               │
[Gửi Tin Nhắn] ────────► Gọi REST POST /api/me/conversations/{id}/messages
                               │
[Backend Fanout] ──────► Nhận STOMP payload trên cả 2 máy ──► Nối tin nhắn vào khung chat
```

#### A. Data Models TypeScript

```typescript
export interface ChatMessageEvent {
  conversationId: string;
  messageId: string;
  sequence: number; // Định danh thứ tự nghiêm ngặt (dùng để sort và gap repair)
  senderId: string;
  senderName: string;
  messageType: "TEXT" | "IMAGE" | "FILE" | "SYSTEM";
  content: string;
  createdAt: string;
  conversationType: "DIRECT" | "GROUP";
  isSelf: boolean | null;
  unreadCount?: number | null;
}

export interface ConversationResponse {
  id: string;
  type: "DIRECT";
  status: "ACTIVE" | "BLOCKED";
  otherUserId: string;
  otherUserName: string;
  otherUserAvatarUrl: string;
  lastMessageContent: string;
  lastMessageAt: string;
  createdAt: string;
  unreadCount: number;
}

export interface TypingPayload {
  conversationId: string;
  senderId: string;
  typing: boolean;
}
```

`messageId` là khóa deduplication ổn định. Rabbit và WebSocket dùng mô hình at-least-once:
nếu server dừng sau khi ghi vào socket nhưng trước khi ghi nhận delivery, event có thể được
phát lại. FE bỏ qua `messageId` đã render và dùng sequence window để bù gap sau reconnect.

#### B. Các REST API Phối Hợp

1. **Lấy danh sách cuộc hội thoại của tôi**:
   - `GET /api/me/conversations?cursor=<nextCursor>&limit=20`
2. **Lấy tin nhắn trong cuộc hội thoại (Theo Sequence Window)**:
   - `GET /api/me/conversations/{conversationId}/messages?beforeSequence=100&limit=30` (Cuộn lên để tải tin nhắn cũ).
   - `GET /api/me/conversations/{conversationId}/messages?afterSequence=150&limit=50` (Tải bù tin nhắn mới sau khi mất mạng).
   - *Lưu ý*: FE phải sắp xếp tin nhắn tăng dần theo trường `sequence` trước khi render.
3. **Gửi tin nhắn**:
   - `POST /api/me/conversations/{conversationId}/messages`
   - Request Body:
      ```json
      {
        "clientMessageId": "019f7234-aaaa-bbbb-cccc-1234567890ab",
        "content": "Chào bạn, mình đã xem qua tài liệu.",
        "replyToMessageId": null,
        "attachmentIntentIds": []
      }
      ```
      `clientMessageId` do FE tạo và giữ nguyên khi retry. Người gửi, message type và sequence
      do backend xác định; FE không gửi `senderId` hoặc tự tạo sequence.
4. **Đánh dấu cuộc hội thoại đã đọc (Read Receipt)**:
   - `PATCH /api/me/conversations/{conversationId}/read`
   - Request Body:
     ```json
     {
       "lastReadSequence": 150
     }
     ```
5. **Gửi Trạng Thái Đang Gõ (Typing Indicator via WebSocket STOMP)**:
   - Khi user gõ vào ô nhập tin nhắn:
     ```typescript
     realtimeService.publish('/app/chat/typing', {
       conversationId: activeConversationId,
       typing: true,
     });
     ```
   - Nên dùng `debounce` (ví dụ sau 1.5s không gõ nữa thì gửi `typing: false`).
6. **Upload File / Ảnh Đính Kèm Trong Chat**:
   - **Bước 1**: Gọi `POST /api/me/conversations/{conversationId}/attachment-upload-intents` để lấy intent ID và URL upload tạm thời.
   - **Bước 2**: Upload binary file lên URL nhận được.
   - **Bước 3**: Gọi `POST /api/me/conversations/{conversationId}/messages` với
     `attachmentIntentIds: ["<UPLOAD_INTENT_ID>"]` và một `clientMessageId` mới.
   - `uploadUrl` chỉ dùng để upload, không dùng để mở file hoặc render ảnh. URL này tạm thời; không lưu vào local storage và không tự dùng lại khi đã hết hạn.
   - Attachment chat là private. Khi user bấm xem/tải, gọi `POST /api/me/chat-attachments/{attachmentId}/download-url`, rồi dùng `downloadUrl` trước `expiresAt`. Không tự ghép CDN URL từ tên file, `storageKey` hoặc `objectKey`; không lưu `downloadUrl` lâu dài.

---

### 4.3 Luồng Cập Nhật Trạng Thái Booking (Booking Status)

#### A. Data Model TypeScript

```typescript
export interface BookingStatusUpdatedEvent {
  bookingId: string;
  oldStatus: string;
  newStatus: string; // "ACCEPTED_AWAITING_PAYMENT", "PAID", "AWAITING_MENTOR_COMPLETION", "COMPLETED", "CANCELLED_BY_MENTOR", "UNDER_REVIEW", etc.
  updatedAt: string;
}
```

#### B. Cách Xử Lý Ở Frontend
- Subscribe kênh `/user/queue/bookings/status`.
- Khi nhận event:
  - Nếu user đang ở trang chi tiết booking có `bookingId` trùng khớp: Cập nhật state badge trạng thái và các nút action (ví dụ: nút "Tham gia phòng học", nút "Xác nhận hài lòng", nút "Báo sự cố").
  - Nếu user đang ở trang danh sách booking: Refresh lại item tương ứng trong danh sách.

---

## 5. Xử Lý Các Tình Huống Ngoại Lệ & Best Practices

### 5.1 Cơ Chế Bù Tin Nhắn Khi Mất Mạng (Reconnect Gap Repair)
Khi thiết bị người dùng bị mất kết nối mạng (hoặc gập nắp laptop) trong vài phút rồi kết nối lại:
1. Lưu lại `lastSequence` của tin nhắn mới nhất trong Redux / Zustand / React State.
2. Khi STOMP `onConnect` kích hoạt lại:
   ```typescript
   async function repairChatGaps(conversationId: string, lastSequence: number) {
     const res = await api.get(
       `/api/me/conversations/${conversationId}/messages?afterSequence=${lastSequence}&limit=50`
     );
     if (res.data && res.data.length > 0) {
       // Nối các tin nhắn còn thiếu vào state và sort lại theo sequence
       appendAndSortMessages(res.data);
     }
   }
   ```

### 5.2 Xử Lý Hết Hạn Access Token (JWT Token Refresh)
* Khi Access Token hết hạn, Server sẽ từ chối kết nối hoặc ngắt WebSocket kèm lỗi.
* FE cần bắt sự kiện lỗi trong `onStompError`, gọi API Refresh Token để lấy token mới, gán lại vào `client.connectHeaders` và gọi `activate()`.

### 5.3 Quản Lý Vòng Đời Subscription (Tránh Memory Leak)
* **Kênh Global** (Cấp Layout / App level): Subscribe một lần khi đăng nhập và chỉ unsubscribe khi đăng xuất:
  - `/user/queue/notifications/badge`
  - `/user/queue/notifications/items`
  - `/user/queue/chat/unread`
  - `/user/queue/chat/inbox`
  - `/user/queue/bookings/status`
* **Kênh Local** (Cấp Component / Màn hình cụ thể): Subscribe khi mở Component và **bắt buộc unsubscribe** trong cleanup function (ví dụ: `useEffect return` trong React):
  - `/user/queue/chat/messages`
  - `/user/queue/chat/typing`
