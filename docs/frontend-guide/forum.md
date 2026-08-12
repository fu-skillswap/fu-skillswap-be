# Frontend Integration Guide — Forum Module

---

## 1. Nguyên tắc Chung & Quy tắc Phân quyền (Security & General Rules)

### 1.1 Phân quyền Truy cập (Access Control)
- **Role `MENTEE` & `MENTOR`**: Đều có quyền đọc newsfeed, tạo bài viết, sửa/xóa bài viết của mình, bình luận, thả reaction và báo cáo nội dung vi phạm.
- **Role `ADMIN` & `SYSTEM_ADMIN`**: **Bị chặn hoàn toàn** ở nhóm API client `/api/forum/*` (`!hasRole('ADMIN') and !hasRole('SYSTEM_ADMIN')`). Quản trị viên chỉ làm việc ở các API quản trị riêng thuộc `/api/admin/forum/*`.

### 1.2 Nguyên tắc Phân trang Cursor (`CursorPageResponse<T>`)
Dữ liệu danh sách bài viết và bình luận được trả về theo dạng **Cursor-Based Pagination** tối ưu cho trải nghiệm cuộn vô tận (Infinite Scroll):

```typescript
interface CursorPageResponse<T> {
  items: T[];              // Danh sách các bản ghi trong window hiện tại
  nextCursor: string | null; // Chuỗi opaque cursor để lấy trang tiếp theo (null = đã hết dữ liệu)
  prevCursor: string | null; // Chuỗi opaque cursor để lấy trang trước đó
  hasNext: boolean;        // true nếu còn dữ liệu ở trang tiếp theo
  hasPrev: boolean;        // true nếu còn dữ liệu ở trang trước
  limit: number;           // Kích thước trang thực tế được backend resolve (Mặc định 20, max 50)
}
```

> [!IMPORTANT]
> **QUY TẮC BẮT BUỘC DÀNH CHO FRONTEND**:
> 1. Chuỗi `cursor` là một chuỗi mã hóa an toàn (Opaque Base64Url Token). **FE tuyệt đối KHÔNG decode, KHÔNG chỉnh sửa và KHÔNG tự tạo chuỗi cursor**.
> 2. Khi gọi trang tiếp theo, FE chỉ cần lấy nguyên văn chuỗi `nextCursor` nhận được từ response trước đó và truyền vào query param `?cursor=...`.
> 3. Khi `nextCursor = null` hoặc `hasNext = false`, FE dừng gửi request lấy thêm dữ liệu.

### 1.3 Kiểm duyệt Từ ngữ Vi phạm (Prohibited Phrase Moderation)
Backend tích hợp sẵn engine kiểm tra từ cấm. Nếu nội dung bài viết (`title`, `content`) hoặc bình luận chứa cụm từ vi phạm tiêu chuẩn cộng đồng, backend sẽ từ chối tạo/cập nhật và trả về HTTP `400 Bad Request` kèm mã lỗi:
- `status`: `400`
- `code`: `"FORUM_4201"` (`FORUM_CONTENT_PROHIBITED`)
- `message`: `"Nội dung chứa cụm từ không được phép"`

---

## 2. Bảng tin & Quản lý Bài viết (Newsfeed & Posts APIs)

### 2.1 Lấy Newsfeed Ưu tiên Ngành học (`GET /api/forum/feed`)
Trả về danh sách bài viết trên Forum được thuật toán sắp xếp ưu tiên theo ngành học (Academic Program) của sinh viên hiện tại.

- **Endpoint**: `GET /api/forum/feed`
- **Header**: `Authorization: Bearer <accessToken>`
- **Query Parameters**:
  - `cursor` (string, optional): Chuỗi `nextCursor` từ response trước
  - `limit` (number, optional): Số bài viết mỗi trang (Default: `20`, Max: `50`)
- **Response**: `ApiResponse<CursorPageResponse<ForumPostResponse>>`

### 2.2 Tìm kiếm & Lọc Bài viết Forum (`GET /api/forum/posts`)
Dùng cho màn hình Duyệt / Tìm kiếm bài viết theo chủ đề hoặc từ khóa.

- **Endpoint**: `GET /api/forum/posts`
- **Header**: `Authorization: Bearer <accessToken>`
- **Query Parameters**:
  - `cursor` (string, optional): Chuỗi `nextCursor` từ response trước
  - `limit` (number, optional): Mặc định `20`
  - `keyword` (string, optional): Từ khóa tìm kiếm trong tiêu đề hoặc nội dung
  - `helpTopicId` (string, optional): UUID của chủ đề hỗ trợ (Help Topic)
  - `mine` (boolean, optional): Đặt `true` nếu chỉ muốn lấy danh sách bài viết của chính tôi (Mặc định: `false`)

**Response Payload (`ForumPostResponse`)**:
```typescript
interface ForumPostResponse {
  postId: string;                     // UUID của bài viết
  authorUserId: string;               // UUID tác giả
  authorFullName: string;             // Tên hiển thị tác giả
  authorAvatarUrl: string;            // Avatar tác giả
  authorProgram?: {                   // Ngành học của tác giả
    id: string;
    code: string;
    nameVi: string;
    nameEn: string;
  } | null;
  helpTopic: {                        // Chủ đề hỗ trợ của bài viết
    id: string;
    code: string;
    nameVi: string;
    nameEn: string;
  };
  title: string;                      // Tiêu đề bài viết
  content: string;                    // Nội dung chi tiết
  status: "PUBLISHED" | "HIDDEN" | "DELETED"; // Trạng thái bài viết
  commentCount: number;               // Tổng số bình luận
  reactionCount: number;              // Tổng số lượt thả Like
  reportCount: number;                // Số lượt báo cáo (dùng cho quản trị)
  lastActivityAt: string;             // Thời điểm có bình luận / tương tác mới nhất (ISO format)
  reactedByCurrentUser: boolean;     // true nếu user hiện tại đã thả Like bài viết này
  myReactionType: "LIKE" | null;      // Loại reaction của user hiện tại (hoặc null)
  createdAt: string;
  updatedAt: string;
  imageUrls: string[];                // Mảng chứa tối đa 4 URL hình ảnh đính kèm
}
```

### 2.3 Xem Chi tiết Bài viết (`GET /api/forum/posts/{postId}`)
- **Endpoint**: `GET /api/forum/posts/{postId}`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<ForumPostResponse>`

### 2.4 Tạo Bài viết mới (`POST /api/forum/posts`)
- **Endpoint**: `POST /api/forum/posts`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`ForumPostUpsertRequest`)**:

```typescript
interface ForumPostUpsertRequest {
  title: string;        // Bắt buộc. Tối đa 200 ký tự
  content: string;      // Bắt buộc. Tối đa 5000 ký tự
  helpTopicId: string;  // Bắt buộc. UUID của Help Topic
  imageUrls?: string[]; // Optional. Tối đa 4 ảnh, mỗi URL tối đa 2000 ký tự
}
```

#### Quy tắc Validation cho FE:
- `title`: Không được để trống, không quá 200 ký tự.
- `content`: Không được để trống, không quá 5000 ký tự.
- `helpTopicId`: Không được null.
- `imageUrls`: Tối đa 4 phần tử trong mảng.

### 2.5 Cập nhật Bài viết của tôi (`PUT /api/forum/posts/{postId}`)
- **Endpoint**: `PUT /api/forum/posts/{postId}`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body**: `ForumPostUpsertRequest` (Chỉ chính tác giả mới được cập nhật).

### 2.6 Xóa Bài viết của tôi (`DELETE /api/forum/posts/{postId}`)
- **Endpoint**: `DELETE /api/forum/posts/{postId}`
- **Header**: `Authorization: Bearer <accessToken>`
- Thực hiện xóa mềm (Soft delete) bài viết khỏi forum.

---

## 3. Quản lý Bình luận & Phản hồi lồng nhau (Comments & Replies)

### 3.1 Lấy danh sách Bình luận của Bài viết (`GET /api/forum/posts/{postId}/comments`)
Trả về danh sách các bình luận xếp theo thứ tự thời gian **từ cũ nhất đến mới nhất** (Oldest First).

- **Endpoint**: `GET /api/forum/posts/{postId}/comments`
- **Header**: `Authorization: Bearer <accessToken>`
- **Query Params**: `cursor`, `limit` (Default: 20)
- **Response**: `ApiResponse<CursorPageResponse<ForumCommentResponse>>`

**Response Payload (`ForumCommentResponse`)**:
```typescript
interface ForumCommentResponse {
  commentId: string;                  // UUID của bình luận
  postId: string;                     // UUID của bài viết cha
  authorUserId: string;               // UUID tác giả bình luận
  authorFullName: string;             // Tên tác giả
  authorAvatarUrl: string;            // Avatar tác giả
  authorRole: "MENTEE" | "MENTOR";    // Vai trò tác giả
  content: string;                    // Nội dung bình luận
  status: "PUBLISHED" | "HIDDEN" | "DELETED";
  reportCount: number;
  reactionCount: number;              // Số lượt Like của bình luận
  reactedByCurrentUser: boolean;     // true nếu user hiện tại đã Like bình luận này
  replyToCommentId?: string | null;   // UUID bình luận được phản hồi (null nếu là comment gốc)
  replyToUserId?: string | null;      // UUID người được trả lời
  replyToUserName?: string | null;    // Tên người được trả lời
  createdAt: string;
  updatedAt: string;
  imageUrls: string[];                // Tối đa 1 ảnh đính kèm
}
```

### 3.2 Tạo Bình luận / Phản hồi mới (`POST /api/forum/posts/{postId}/comments`)
- **Endpoint**: `POST /api/forum/posts/{postId}/comments`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`ForumCommentUpsertRequest`)**:

```typescript
interface ForumCommentUpsertRequest {
  content: string;            // Bắt buộc. Tối đa 2000 ký tự
  imageUrls?: string[];       // Optional. Tối đa 1 ảnh
  replyToCommentId?: string;  // Optional. Truyền UUID của comment gốc nếu là reply, truyền null nếu là comment mới
}
```

### 3.3 Cập nhật Bình luận (`PUT /api/forum/comments/{commentId}`)
- **Endpoint**: `PUT /api/forum/comments/{commentId}`
- **Request Body**: `ForumCommentUpsertRequest`

### 3.4 Xóa Bình luận (`DELETE /api/forum/comments/{commentId}`)
- **Endpoint**: `DELETE /api/forum/comments/{commentId}`

---

## 4. Thả Cảm xúc / Reactions (Like APIs)

### 4.1 Thả / Cập nhật Reaction cho Bài viết (`PUT /api/forum/posts/{postId}/reaction`)
- **Endpoint**: `PUT /api/forum/posts/{postId}/reaction`
- **Request Body**:
```typescript
interface ForumReactionRequest {
  reactionType: "LIKE"; // Bắt buộc
}
```
- **Response**: Trả về `ForumPostResponse` đã cập nhật lại `reactionCount` và `reactedByCurrentUser = true`.

### 4.2 Bỏ Reaction khỏi Bài viết (`DELETE /api/forum/posts/{postId}/reaction`)
- **Endpoint**: `DELETE /api/forum/posts/{postId}/reaction`
- **Response**: Trả về `ForumPostResponse` với `reactedByCurrentUser = false`.

### 4.3 Thả / Cập nhật Reaction cho Bình luận (`PUT /api/forum/comments/{commentId}/reaction`)
- **Endpoint**: `PUT /api/forum/comments/{commentId}/reaction`
- **Request Body**: `{ reactionType: "LIKE" }`

### 4.4 Bỏ Reaction khỏi Bình luận (`DELETE /api/forum/comments/{commentId}/reaction`)
- **Endpoint**: `DELETE /api/forum/comments/{commentId}/reaction`

---

## 5. Báo cáo Vi phạm (Reporting System)

Cho phép người dùng báo cáo bài viết hoặc bình luận có nội dung rác, quấy rối hoặc thông tin sai lệch.

### 5.1 Tạo Báo cáo Vi phạm (`POST /api/forum/reports`)
- **Endpoint**: `POST /api/forum/reports`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`ForumReportCreateRequest`)**:

```typescript
interface ForumReportCreateRequest {
  targetType: "POST" | "COMMENT";     // Bắt buộc. Đối tượng bị báo cáo
  targetId: string;                   // Bắt buộc. UUID của Post hoặc Comment
  reasonType:                         // Bắt buộc. Lý do báo cáo
    | "SPAM" 
    | "OFF_TOPIC" 
    | "HARASSMENT" 
    | "MISLEADING" 
    | "OTHER";
  description?: string;               // Mô tả chi tiết (Tối đa 1000 ký tự)
}
```

---

## 6. Bảng Mã Lỗi Chi Tiết (Error Codes Reference)

| HTTP Status | Error Code | Key | Hướng xử lý cho Frontend |
|---|---|---|---|
| `400` | `FORUM_4201` | `error.forum.content_prohibited` | Content/Title chứa cụm từ vi phạm. Hiển thị thông báo yêu cầu người dùng chỉnh sửa từ ngữ. |
| `400` | `VAL_3001` | `error.val.invalid_input` | Tiêu đề quá 200 ký tự, nội dung quá 5000 ký tự, hoặc đính kèm quá số lượng ảnh cho phép. |
| `401` | `AUTH_1001` | `error.auth.unauthenticated` | Chưa đăng nhập. Redirect sang trang Login. |
| `403` | `AUTH_1002` | `error.auth.unauthorized` | Không phải tác giả của bài viết/bình luận nên không có quyền sửa/xóa. |
| `404` | `SYS_0003` | `error.sys.not_found` | Không tìm thấy bài viết, bình luận hoặc help topic tương ứng. |
| `409` | `SYS_0007` | `error.sys.conflict` | Xung đột dữ liệu trạng thái. |

---

## 7. Ví dụ Code Tích hợp Next.js (Infinite Scroll Feed Component)

Component React sử dụng `apiClient` và Cursor Pagination để tải thêm bài viết khi cuộn trang:

```typescript
import React, { useState, useEffect, useCallback } from 'react';
import { apiClient } from '@/lib/api-client';

interface Post {
  postId: string;
  title: string;
  content: string;
  authorFullName: string;
  authorAvatarUrl: string;
  reactionCount: number;
  commentCount: number;
  reactedByCurrentUser: boolean;
}

export const ForumInfiniteFeed: React.FC = () => {
  const [posts, setPosts] = useState<Post[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(false);

  const fetchPosts = useCallback(async (cursorParam?: string | null) => {
    if (loading) return;
    setLoading(true);

    try {
      const res: any = await apiClient.get('/api/forum/feed', {
        params: {
          limit: 10,
          cursor: cursorParam || undefined,
        },
      });

      const cursorData = res.data; // CursorPageResponse<ForumPostResponse>
      setPosts((prev) => (cursorParam ? [...prev, ...cursorData.items] : cursorData.items));
      setNextCursor(cursorData.nextCursor);
      setHasNext(cursorData.hasNext);
    } catch (err) {
      console.error('Lỗi khi tải bảng tin forum:', err);
    } finally {
      setLoading(false);
    }
  }, [loading]);

  useEffect(() => {
    fetchPosts(null); // Load lần đầu
  }, []);

  const handleLoadMore = () => {
    if (hasNext && nextCursor) {
      fetchPosts(nextCursor);
    }
  };

  const handleToggleLike = async (postId: string, isLiked: boolean) => {
    try {
      if (isLiked) {
        await apiClient.delete(`/api/forum/posts/${postId}/reaction`);
      } else {
        await apiClient.put(`/api/forum/posts/${postId}/reaction`, { reactionType: 'LIKE' });
      }

      setPosts((prev) =>
        prev.map((p) =>
          p.postId === postId
            ? {
                ...p,
                reactedByCurrentUser: !isLiked,
                reactionCount: isLiked ? p.reactionCount - 1 : p.reactionCount + 1,
              }
            : p
        )
      );
    } catch (err) {
      console.error('Lỗi khi thả reaction:', err);
    }
  };

  return (
    <div className="forum-feed-container">
      {posts.map((post) => (
        <article key={post.postId} className="post-card">
          <div className="author-info">
            <img src={post.authorAvatarUrl} alt={post.authorFullName} />
            <span>{post.authorFullName}</span>
          </div>
          <h3>{post.title}</h3>
          <p>{post.content}</p>
          <div className="post-actions">
            <button onClick={() => handleToggleLike(post.postId, post.reactedByCurrentUser)}>
              {post.reactedByCurrentUser ? '❤️ Thích' : '🤍 Thích'} ({post.reactionCount})
            </button>
            <span>💬 {post.commentCount} bình luận</span>
          </div>
        </article>
      ))}

      {hasNext && (
        <button onClick={handleLoadMore} disabled={loading}>
          {loading ? 'Đang tải thêm...' : 'Tải thêm bài viết'}
        </button>
      )}
    </div>
  );
};
```
