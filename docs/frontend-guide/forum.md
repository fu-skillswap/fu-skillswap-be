# Frontend Integration Guide — Forum Module

> **Quy tắc ảnh & CDN**: Frontend sử dụng trực tiếp các URL hình ảnh nhận được từ backend trong `imageUrls` hoặc `authorAvatarUrl`. Tuyệt đối không tự ghép nối prefix CDN, không tự trích xuất key/ID và không cần bất kỳ biến môi trường `.env` nào về CDN.

---

## 1. Chuẩn Envelope & Phân quyền Truy cập (Architecture & Security)

### 1.1 Chuẩn Response Envelope (`ApiResponse<T>`)
Tất cả các API của hệ thống đều trả về theo cấu trúc bọc chuẩn:

```typescript
interface ApiResponse<T> {
  timestamp: string;  // Thời gian server xử lý (ISO string)
  status: number;     // HTTP Status Code (200, 201, 400, 401, 403, 404, 429, 500)
  code: string;       // Mã nghiệp vụ (vd: "SUCCESS_0200", "FORUM_4201", "VAL_3001")
  message: string;    // Thông điệp thân thiện từ server
  data: T;            // Dữ liệu payload thực tế
}
```

> [!TIP]
> **Lưu ý bóc tách dữ liệu cho FE**:
> Nếu dùng `axios` mặc định: Payload dữ liệu thực tế sẽ nằm ở `response.data.data`.
> Nếu dự án đã có `apiClient` cài sẵn interceptor `return res.data`: Bạn truy cập trực tiếp qua `res.data`.

---

### 1.2 Phân quyền Người dùng (Access Control Matrix)

| Nhóm API | Khách chưa đăng nhập | Role `MENTEE` / `MENTOR` | Role `ADMIN` / `SYSTEM_ADMIN` |
|---|---|---|---|
| `GET /api/forum/topics` |  Cho phép |  Cho phép |  Cho phép |
| `GET /api/forum/posts` (Duyệt chung) |  Cho phép |  Cho phép |  Cho phép |
| `GET /api/forum/posts?mine=true` | ❌ 401 Unauthorized |  Cho phép | ❌ 403 Forbidden |
| `GET /api/forum/feed` (Newsfeed ngành) | ❌ 401 Unauthorized |  Cho phép | ❌ 403 Forbidden |
| `GET /api/forum/posts/{postId}` |  Cho phép |  Cho phép |  Cho phép |
| `GET /api/forum/posts/{postId}/comments` |  Cho phép |  Cho phép |  Cho phép |
| Tạo / Sửa / Xóa bài viết | ❌ 401 Unauthorized |  Cho phép (Chính chủ) | ❌ 403 Forbidden |
| Tạo / Sửa / Xóa bình luận | ❌ 401 Unauthorized |  Cho phép (Chính chủ) | ❌ 403 Forbidden |
| Thả / Bỏ Reaction (Like) | ❌ 401 Unauthorized |  Cho phép | ❌ 403 Forbidden |
| Gửi Báo cáo vi phạm (Report) | ❌ 401 Unauthorized |  Cho phép | ❌ 403 Forbidden |

> [!IMPORTANT]
> **Lưu ý đặc biệt cho Role ADMIN / SYSTEM_ADMIN**:
> Backend chặn hoàn toàn quyền tương tác cộng đồng của Admin tại nhóm client `/api/forum/*` (`!hasRole('ADMIN') and !hasRole('SYSTEM_ADMIN')`). Quản trị viên chỉ xem và xử lý báo cáo tại `/api/admin/forum/*`.

---

### 1.3 Cơ chế Phân trang Cursor (`CursorPageResponse<T>`)

Danh sách bài viết, newsfeed và bình luận sử dụng **Cursor-Based Pagination** tối ưu cho Infinity Scroll:

```typescript
interface CursorPageResponse<T> {
  items: T[];                // Mảng các item của trang hiện tại
  nextCursor: string | null; // Chuỗi token opaque dùng để gọi trang tiếp (null = đã hết dữ liệu)
  prevCursor: string | null; // Chuỗi token trang trước (nếu có)
  hasNext: boolean;          // true nếu còn dữ liệu ở trang sau
  hasPrev: boolean;          // true nếu có dữ liệu ở trang trước
  limit: number;             // Số lượng item trên mỗi trang (Default: 20, Max: 50)
}
```

#### 📌 3 Quy tắc bắt buộc khi xử lý Cursor:
1. `cursor` là một chuỗi mã hóa an toàn (Opaque Base64Url Token). **FE KHÔNG decode, KHÔNG parse và KHÔNG tự tạo chuỗi này**.
2. Để tải trang tiếp theo: Lấy nguyên văn `nextCursor` nhận được từ response và truyền vào query param `?cursor=${nextCursor}`.
3. Khi `hasNext === false` hoặc `nextCursor === null`: FE **dừng gọi API** và ẩn nút "Tải thêm" hoặc hủy trigger Infinite Scroll.

---

## 2. Danh mục Chủ đề Thảo luận (Forum Topics)

Trước khi hiển thị bộ lọc hoặc form đăng bài, FE gọi API này để lấy danh sách topic hợp lệ.

### 2.1 Lấy danh sách Topics công khai (`GET /api/forum/topics`)
- **Endpoint**: `GET /api/forum/topics`
- **Quyền truy cập**: Public (Không cần token)
- **Response**: `ApiResponse<ForumTopicResponse[]>`

```typescript
interface ForumTopicResponse {
  id: string;                                          // UUID của topic (Dùng để truyền vào forumTopicId khi tạo post/lọc)
  code: "QUESTION" | "SHARING" | "SEARCH" | "REVIEW"; // Mã định danh topic
  nameVi: string;                                      // Tên tiếng Việt (vd: "Hỏi đáp kiến thức")
  nameEn: string;                                      // Tên tiếng Anh (vd: "Q&A")
  displayOrder: number;                                // Thứ tự hiển thị tăng dần
}
```

---

## 3. Bảng tin & Quản lý Bài viết (Posts & Feed APIs)

### 3.1 TypeScript Type: `ForumPostResponse`

```typescript
interface ForumPostResponse {
  postId: string;                     // UUID của bài viết
  authorUserId: string;               // UUID của tác giả
  authorFullName: string;             // Họ tên tác giả
  authorAvatarUrl: string;            // Avatar tác giả
  authorProgram?: {                   // Ngành học của tác giả (nếu có)
    id: string;
    code: string;
    nameVi: string;
    nameEn: string;
  } | null;
  forumTopic: ForumTopicResponse;     // Chủ đề bài viết
  title: string;                      // Tiêu đề bài viết
  content: string;                    // Nội dung chi tiết
  status: "PUBLISHED" | "HIDDEN" | "DELETED"; // Trạng thái
  commentCount: number;               // Tổng số bình luận (gồm cả comment gốc & reply)
  reactionCount: number;              // Tổng số lượt Like
  reportCount: number;                // Số lượt báo cáo (FE người dùng không cần quan tâm)
  lastActivityAt: string;             // Thời điểm có tương tác mới nhất (ISO format)
  reactedByCurrentUser: boolean;     // true nếu user hiện tại đã bấm Like
  myReactionType: "LIKE" | null;      // "LIKE" hoặc null
  createdAt: string;                  // Thời gian tạo (ISO string)
  updatedAt: string;                  // Thời gian cập nhật (ISO string)
  imageUrls: string[];                // Mảng tối đa 4 URL ảnh đính kèm
}
```

---

### 3.2 Lấy Newsfeed ưu tiên theo ngành học (`GET /api/forum/feed`)
Thuật toán backend sẽ ưu tiên hiển thị bài viết thuộc cùng ngành học (Academic Program) của người dùng lên đầu, sau đó đến các bài viết chung.

- **Endpoint**: `GET /api/forum/feed`
- **Header**: `Authorization: Bearer <accessToken>`
- **Query Params**:
  - `cursor` (string, optional): `nextCursor` từ response trước
  - `limit` (number, optional): Mặc định `20`, tối đa `50`
- **Response**: `ApiResponse<CursorPageResponse<ForumPostResponse>>`

---

### 3.3 Tìm kiếm & Lọc bài viết (`GET /api/forum/posts`)
Dùng cho màn hình Tìm kiếm / Lọc bài viết theo Topic, từ khóa, hoặc xem bài viết của chính tôi.

- **Endpoint**: `GET /api/forum/posts`
- **Header**: `Authorization: Bearer <accessToken>` (Bắt buộc nếu `mine=true`, optional nếu duyệt public)
- **Query Params**:
  - `cursor` (string, optional): `nextCursor` từ response trước
  - `limit` (number, optional): Mặc định `20`, tối đa `50`
  - `keyword` (string, optional): Từ khóa tìm kiếm trong tiêu đề hoặc nội dung
  - `forumTopicId` (string, optional): UUID của topic
  - `mine` (boolean, optional): `true` để chỉ lấy bài viết do chính tôi đăng (Mặc định `false`)
- **Response**: `ApiResponse<CursorPageResponse<ForumPostResponse>>`

---

### 3.4 Xem chi tiết bài viết (`GET /api/forum/posts/{postId}`)
- **Endpoint**: `GET /api/forum/posts/{postId}`
- **Header**: `Authorization: Bearer <accessToken>` (Optional)
- **Response**: `ApiResponse<ForumPostResponse>`

---

### 3.5 Tạo bài viết mới (`POST /api/forum/posts`)
- **Endpoint**: `POST /api/forum/posts`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`ForumPostUpsertRequest`)**:

```typescript
interface ForumPostUpsertRequest {
  title: string;        // Bắt buộc. Không quá 200 ký tự
  content: string;      // Bắt buộc. Không quá 5000 ký tự
  forumTopicId: string; // Bắt buộc. UUID topic lấy từ GET /api/forum/topics
  imageUrls?: string[]; // Tùy chọn. Tối đa 4 URL ảnh, mỗi URL không quá 2000 ký tự
}
```

#### ⚠️ Quy định kiểm duyệt & Chống spam khi tạo bài:
1. **Kiểm tra từ ngữ cấm**: Nếu tiêu đề/nội dung chứa từ cấm, backend trả `400 Bad Request` (`code: "FORUM_4201"`).
2. **Chặn bài viết trùng lặp**: Người dùng không được đăng bài viết có cùng tiêu đề và nội dung trong vòng 15 phút (`429 TOO_MANY_REQUESTS`).
3. **Giới hạn tốc độ (Rate Limit)**: Tối đa **5 bài viết / 10 phút**. Nếu vượt quá sẽ nhận lỗi `429 TOO_MANY_REQUESTS`.

---

### 3.6 Cập nhật bài viết (`PUT /api/forum/posts/{postId}`)
- **Endpoint**: `PUT /api/forum/posts/{postId}`
- **Header**: `Authorization: Bearer <accessToken>` (Chỉ chính tác giả)
- **Request Body**: `ForumPostUpsertRequest`
- **Response**: `ApiResponse<ForumPostResponse>`

---

### 3.7 Xóa bài viết (`DELETE /api/forum/posts/{postId}`)
- **Endpoint**: `DELETE /api/forum/posts/{postId}`
- **Header**: `Authorization: Bearer <accessToken>` (Chỉ chính tác giả)
- **Response**: `ApiResponse<ForumPostResponse>` (Bài viết được xóa mềm khỏi hệ thống)

---

## 4. Quản lý Bình luận & Phản hồi (Comments & 1-Level Threads)

### 4.1 TypeScript Type: `ForumCommentResponse`

```typescript
interface ForumCommentResponse {
  commentId: string;                  // UUID của bình luận
  postId: string;                     // UUID bài viết cha
  authorUserId: string;               // UUID tác giả bình luận
  authorFullName: string;             // Tên tác giả
  authorAvatarUrl: string;            // Avatar tác giả
  authorRole: "MENTEE" | "MENTOR";    // Vai trò tác giả
  content: string;                    // Nội dung bình luận
  status: "VISIBLE" | "HIDDEN" | "DELETED";
  reportCount: number;
  reactionCount: number;              // Số lượt Like của bình luận
  reactedByCurrentUser: boolean;     // true nếu user hiện tại đã Like bình luận này
  replyToCommentId?: string | null;   // UUID bình luận gốc nếu đây là reply (null nếu là comment gốc)
  replyToUserId?: string | null;      // UUID người được trả lời (Backend tự tính)
  replyToUserName?: string | null;    // Tên người được trả lời (Backend tự tính)
  createdAt: string;                  // Thời gian tạo (ISO string)
  updatedAt: string;                  // Thời gian cập nhật (ISO string)
  imageUrls: string[];                // Mảng tối đa 1 URL ảnh đính kèm
}
```

---

### 4.2 Lấy danh sách bình luận (`GET /api/forum/posts/{postId}/comments`)
Backend trả về danh sách toàn bộ bình luận xếp theo thứ tự **từ cũ nhất đến mới nhất** (Oldest First).

- **Endpoint**: `GET /api/forum/posts/{postId}/comments`
- **Header**: `Authorization: Bearer <accessToken>` (Optional)
- **Query Params**: `cursor`, `limit` (Default: 20, Max: 50)
- **Response**: `ApiResponse<CursorPageResponse<ForumCommentResponse>>`

---

### 4.3 Tạo bình luận / Trả lời bình luận (`POST /api/forum/posts/{postId}/comments`)
- **Endpoint**: `POST /api/forum/posts/{postId}/comments`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`ForumCommentUpsertRequest`)**:

```typescript
interface ForumCommentUpsertRequest {
  content: string;            // Bắt buộc. Tối đa 2000 ký tự
  imageUrls?: string[];       // Tùy chọn. Tối đa 1 URL ảnh
  replyToCommentId?: string;  // Tùy chọn. UUID của bình luận GỐC nếu là trả lời (truyền null nếu comment mới)
}
```

> [!IMPORTANT]
> **QUY TẮC BÌNH LUẬN 1 CẤP (1-LEVEL THREADING)**:
> - Backend SkillSwap áp dụng cấu trúc luồng thảo luận **1 cấp**: `Bình luận gốc ➔ Các câu trả lời (Replies)`.
> - **Tuyệt đối KHÔNG reply lồng trong reply**: Nếu người dùng bấm trả lời một bình luận con (reply), Frontend vẫn phải truyền `replyToCommentId` là **ID của bình luận gốc cha (Root Comment)**. Nếu truyền ID của một reply, backend sẽ từ chối với lỗi `400 Bad Request` (`"Chỉ hỗ trợ trả lời bình luận 1 cấp"`).

#### ⚠️ Quy định kiểm duyệt & Chống spam khi bình luận:
1. **Kiểm tra từ ngữ cấm**: Trả về `400 Bad Request` (`code: "FORUM_4201"`) nếu chứa từ cấm.
2. **Chặn bình luận trùng lặp**: Người dùng không được gửi 2 bình luận có nội dung y hệt nhau vào cùng 1 bài viết trong vòng 5 phút (`429 TOO_MANY_REQUESTS`).
3. **Giới hạn tốc độ (Rate Limit)**: Tối đa **20 bình luận / 10 phút**.

---

### 4.4 Cập nhật bình luận (`PUT /api/forum/comments/{commentId}`)
- **Endpoint**: `PUT /api/forum/comments/{commentId}`
- **Header**: `Authorization: Bearer <accessToken>` (Chỉ chính tác giả)
- **Request Body**: `ForumCommentUpsertRequest`
- **Response**: `ApiResponse<ForumCommentResponse>`

---

### 4.5 Xóa bình luận (`DELETE /api/forum/comments/{commentId}`)
- **Endpoint**: `DELETE /api/forum/comments/{commentId}`
- **Header**: `Authorization: Bearer <accessToken>` (Chỉ chính tác giả)
- **Response**: `ApiResponse<ForumCommentResponse>`
- **Cơ chế Cascade**: Nếu xóa bình luận gốc, backend sẽ tự động xóa tất cả các câu trả lời con trực thuộc và trừ tương ứng vào `commentCount` của bài viết.

---

## 5. Thả Cảm xúc / Reaction (Like APIs)

Hệ thống hỗ trợ cơ chế thả và thu hồi Like tức thì.

### 5.1 Thả Like cho bài viết (`PUT /api/forum/posts/{postId}/reaction`)
- **Endpoint**: `PUT /api/forum/posts/{postId}/reaction`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body**: `{ "reactionType": "LIKE" }`
- **Response**: `ApiResponse<ForumPostResponse>` (Trả về post với `reactedByCurrentUser = true` và `reactionCount` tăng 1).

### 5.2 Bỏ Like bài viết (`DELETE /api/forum/posts/{postId}/reaction`)
- **Endpoint**: `DELETE /api/forum/posts/{postId}/reaction`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<ForumPostResponse>` (Trả về post với `reactedByCurrentUser = false` và `reactionCount` giảm 1).

### 5.3 Thả Like cho bình luận (`PUT /api/forum/comments/{commentId}/reaction`)
- **Endpoint**: `PUT /api/forum/comments/{commentId}/reaction`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body**: `{ "reactionType": "LIKE" }`
- **Response**: `ApiResponse<ForumCommentResponse>`

### 5.4 Bỏ Like bình luận (`DELETE /api/forum/comments/{commentId}/reaction`)
- **Endpoint**: `DELETE /api/forum/comments/{commentId}/reaction`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<ForumCommentResponse>`

> [!NOTE]
> **Rate limit reaction**: Tối đa **60 lượt Like/Bỏ like trong vòng 10 phút**.

---

## 6. Báo cáo Vi phạm (Reporting System)

### 6.1 Gửi báo cáo bài viết hoặc bình luận (`POST /api/forum/reports`)
- **Endpoint**: `POST /api/forum/reports`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`ForumReportCreateRequest`)**:

```typescript
interface ForumReportCreateRequest {
  targetType: "POST" | "COMMENT";     // Bắt buộc. Loại đối tượng báo cáo
  targetId: string;                   // Bắt buộc. UUID của Post hoặc Comment
  reasonType:                         // Bắt buộc. Lý do vi phạm
    | "SPAM"                          // Spam, quảng cáo rác
    | "OFF_TOPIC"                      // Lạc đề, không liên quan
    | "HARASSMENT"                     // Quấy rối, công kích cá nhân
    | "MISLEADING"                     // Thông tin sai lệch, lừa đảo
    | "OTHER";                         // Lý do khác
  description?: string;               // Tùy chọn. Mô tả chi tiết (Tối đa 1000 ký tự)
}
```

- **Response**: `ApiResponse<ForumReportResponse>`
- **Rate limit**: Tối đa **10 lượt báo cáo / 30 phút**.

---

## 7. Bảng Mã Lỗi Chi Tiết & Hướng dẫn Xử lý UX (Error Codes Reference)

| HTTP Status | Error Code | Key i18n | Ý nghĩa & Hướng xử lý cho Frontend |
|---|---|---|---|
| `400` | `FORUM_4201` | `error.forum.content_prohibited` | **Nội dung chứa từ ngữ vi phạm tiêu chuẩn cộng đồng**. Hiển thị Modal/Toast nhắc nhở người dùng chỉnh sửa từ ngữ lịch sự. |
| `400` | `VAL_3001` | `error.val.invalid_input` | **Dữ liệu không hợp lệ** (Tiêu đề > 200 ký tự, nội dung > 5000 ký tự, quá 4 ảnh, topicId rỗng,...). Hiển thị lỗi đỏ dưới input. |
| `400` | `BAD_REQUEST` | `error.sys.bad_request` | **Thao tác không hợp lệ** (Ví dụ: reply lồng trong reply, tương tác với post đã xóa,...). Hiển thị thông báo `message` từ response. |
| `401` | `AUTH_1001` | `error.auth.unauthenticated` | **Chưa đăng nhập**. Mở modal đăng nhập hoặc chuyển hướng sang trang Login. |
| `403` | `AUTH_1002` | `error.auth.unauthorized` | **Không có quyền**. (Ví dụ: sửa/xóa bài viết của người khác, hoặc tài khoản Admin cố tình gọi API forum client). |
| `404` | `SYS_0003` | `error.sys.not_found` | **Không tìm thấy dữ liệu** (Bài viết hoặc bình luận đã bị tác giả xóa). Hiển thị giao diện 404 / Quay về feed. |
| `409` | `SYS_0007` | `error.sys.conflict` | **Xung đột dữ liệu** (Bài viết đang ở trạng thái ẩn nên không thể sửa). |
| `429` | `TOO_MANY_REQUESTS` | `error.sys.rate_limit` | **Spam lặp lại hoặc thao tác quá nhanh**. Hiển thị toast thông báo người dùng tạm nghỉ vài phút. |

---

## 8. Hướng dẫn Dựng Tree Bình luận 1 Cấp (Frontend Utility Helper)

Vì API trả về mảng phẳng các bình luận theo thời gian (`createdAt ASC`), FE sử dụng hàm tiện ích sau để nhóm thành danh sách `Root Comment ➔ Replies`:

```typescript
export interface ThreadedComment extends ForumCommentResponse {
  replies: ForumCommentResponse[];
}

/**
 * Gom nhóm mảng comment phẳng thành cây 1 cấp (Root Comments + Replies)
 */
export function buildCommentTree(comments: ForumCommentResponse[]): ThreadedComment[] {
  const rootComments: ThreadedComment[] = [];
  const replyMap = new Map<string, ForumCommentResponse[]>();

  // Bước 1: Phân loại root comment và reply
  for (const c of comments) {
    if (c.replyToCommentId) {
      const existing = replyMap.get(c.replyToCommentId) || [];
      existing.push(c);
      replyMap.set(c.replyToCommentId, existing);
    } else {
      rootComments.push({ ...c, replies: [] });
    }
  }

  // Bước 2: Gán replies vào root comment tương ứng
  for (const root of rootComments) {
    root.replies = replyMap.get(root.commentId) || [];
  }

  return rootComments;
}
```

---

## 9. Code Mẫu Thực Chiến Next.js (React Component Hoàn Chỉnh)

Dưới đây là component mẫu tích hợp đầy đủ: Lọc theo Topic, Infinite Scroll Feed với Cursor Pagination, Optimistic Like Update và Comment Form 1-Level.

```tsx
import React, { useState, useEffect, useCallback } from 'react';
import { apiClient } from '@/lib/api-client';

// 1. Interfaces
interface ForumTopic {
  id: string;
  code: string;
  nameVi: string;
  nameEn: string;
}

interface ForumPost {
  postId: string;
  title: string;
  content: string;
  authorFullName: string;
  authorAvatarUrl: string;
  forumTopic: ForumTopic;
  reactionCount: number;
  commentCount: number;
  reactedByCurrentUser: boolean;
  imageUrls: string[];
  createdAt: string;
}

interface CursorPageResponse<T> {
  items: T[];
  nextCursor: string | null;
  hasNext: boolean;
}

export const ForumFeedPage: React.FC = () => {
  const [topics, setTopics] = useState<ForumTopic[]>([]);
  const [selectedTopicId, setSelectedTopicId] = useState<string | null>(null);
  const [posts, setPosts] = useState<ForumPost[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(false);

  // 2. Tải danh sách Topics
  useEffect(() => {
    async function loadTopics() {
      try {
        const res: any = await apiClient.get('/api/forum/topics');
        // Unpack: res.data.data hoặc res.data tùy cấu hình axios
        const topicList: ForumTopic[] = res.data?.data || res.data;
        setTopics(topicList);
      } catch (err) {
        console.error('Không thể tải danh sách topic:', err);
      }
    }
    loadTopics();
  }, []);

  // 3. Tải danh sách bài viết theo Cursor
  const fetchPosts = useCallback(
    async (cursorParam?: string | null, topicId?: string | null, isRefresh = false) => {
      if (loading) return;
      setLoading(true);

      try {
        const res: any = await apiClient.get('/api/forum/posts', {
          params: {
            limit: 10,
            cursor: cursorParam || undefined,
            forumTopicId: topicId || undefined,
          },
        });

        const cursorData: CursorPageResponse<ForumPost> = res.data?.data || res.data;

        setPosts((prev) => (isRefresh || !cursorParam ? cursorData.items : [...prev, ...cursorData.items]));
        setNextCursor(cursorData.nextCursor);
        setHasNext(cursorData.hasNext);
      } catch (err) {
        console.error('Lỗi khi tải bài viết:', err);
      } finally {
        setLoading(false);
      }
    },
    [loading]
  );

  // Load lại khi thay đổi Topic
  useEffect(() => {
    fetchPosts(null, selectedTopicId, true);
  }, [selectedTopicId]);

  // 4. Xử lý Tải thêm (Infinite Scroll / Load More)
  const handleLoadMore = () => {
    if (hasNext && nextCursor && !loading) {
      fetchPosts(nextCursor, selectedTopicId, false);
    }
  };

  // 5. Xử lý Thả / Bỏ Like (Optimistic UI Update)
  const handleToggleLike = async (postId: string, currentLiked: boolean) => {
    // Cập nhật UI ngay lập tức
    setPosts((prev) =>
      prev.map((p) =>
        p.postId === postId
          ? {
              ...p,
              reactedByCurrentUser: !currentLiked,
              reactionCount: currentLiked ? p.reactionCount - 1 : p.reactionCount + 1,
            }
          : p
      )
    );

    try {
      if (currentLiked) {
        await apiClient.delete(`/api/forum/posts/${postId}/reaction`);
      } else {
        await apiClient.put(`/api/forum/posts/${postId}/reaction`, { reactionType: 'LIKE' });
      }
    } catch (err: any) {
      // Revert lại trạng thái nếu gọi API lỗi
      console.error('Lỗi thả reaction:', err);
      setPosts((prev) =>
        prev.map((p) =>
          p.postId === postId
            ? {
                ...p,
                reactedByCurrentUser: currentLiked,
                reactionCount: currentLiked ? p.reactionCount : p.reactionCount,
              }
            : p
        )
      );
    }
  };

  return (
    <div className="max-w-4xl mx-auto p-4 space-y-6">
      {/* Topic Filter Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-2 border-b">
        <button
          onClick={() => setSelectedTopicId(null)}
          className={`px-4 py-2 rounded-full text-sm font-medium ${
            selectedTopicId === null ? 'bg-primary text-white' : 'bg-gray-100 text-gray-700'
          }`}
        >
          Tất cả chủ đề
        </button>
        {topics.map((topic) => (
          <button
            key={topic.id}
            onClick={() => setSelectedTopicId(topic.id)}
            className={`px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap ${
              selectedTopicId === topic.id ? 'bg-primary text-white' : 'bg-gray-100 text-gray-700'
            }`}
          >
            {topic.nameVi}
          </button>
        ))}
      </div>

      {/* Feed List */}
      <div className="space-y-4">
        {posts.map((post) => (
          <article key={post.postId} className="bg-white border rounded-xl p-5 shadow-sm space-y-3">
            {/* Header tác giả */}
            <div className="flex items-center gap-3">
              <img
                src={post.authorAvatarUrl || '/default-avatar.png'}
                alt={post.authorFullName}
                className="w-10 h-10 rounded-full object-cover"
              />
              <div>
                <h4 className="font-semibold text-gray-900">{post.authorFullName}</h4>
                <div className="flex items-center gap-2 text-xs text-gray-500">
                  <span>{post.forumTopic.nameVi}</span>
                  <span>•</span>
                  <span>{new Date(post.createdAt).toLocaleDateString('vi-VN')}</span>
                </div>
              </div>
            </div>

            {/* Nội dung bài viết */}
            <h3 className="text-lg font-bold text-gray-900">{post.title}</h3>
            <p className="text-gray-700 whitespace-pre-line">{post.content}</p>

            {/* Ảnh đính kèm (nếu có) */}
            {post.imageUrls && post.imageUrls.length > 0 && (
              <div className="grid grid-cols-2 gap-2 pt-2">
                {post.imageUrls.map((url, idx) => (
                  <img
                    key={idx}
                    src={url}
                    alt={`Attachment ${idx + 1}`}
                    className="rounded-lg object-cover w-full h-48 border"
                  />
                ))}
              </div>
            )}

            {/* Thao tác Post */}
            <div className="flex items-center gap-6 pt-3 border-t text-sm font-medium text-gray-600">
              <button
                onClick={() => handleToggleLike(post.postId, post.reactedByCurrentUser)}
                className={`flex items-center gap-1.5 transition ${
                  post.reactedByCurrentUser ? 'text-red-500 font-bold' : 'hover:text-gray-900'
                }`}
              >
                {post.reactedByCurrentUser ? '❤️ Đã thích' : '🤍 Thích'} ({post.reactionCount})
              </button>

              <button className="flex items-center gap-1.5 hover:text-gray-900">
                💬 {post.commentCount} bình luận
              </button>
            </div>
          </article>
        ))}
      </div>

      {/* Nút tải thêm */}
      {hasNext && (
        <div className="text-center pt-4">
          <button
            onClick={handleLoadMore}
            disabled={loading}
            className="px-6 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-800 font-medium rounded-lg disabled:opacity-50"
          >
            {loading ? 'Đang tải thêm...' : 'Tải thêm bài viết'}
          </button>
        </div>
      )}
    </div>
  );
};
```
