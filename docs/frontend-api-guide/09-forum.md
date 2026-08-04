# Forum Service (`09-forum.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Diễn đàn Học tập (Forum Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Forum Service** quản lý toàn bộ hệ thống diễn đàn trao đổi học thuật nội bộ SkillSwap: tạo bài viết (`Forum Post`), bình luận nhiều cấp (`Forum Comment`), phản hồi cảm xúc (`Forum Reaction`), tìm kiếm bài viết theo chủ đề hỗ trợ (`Help Topic`), thuật toán ưu tiên ngành học (`Program-First Newsfeed`), kiểm duyệt nội dung vi phạm tự động (`Prohibited Phrase Engine`), và hệ thống báo cáo vi phạm (`Forum Moderation`).

### Trách nhiệm chính của Service
- **Thuật toán Newsfeed Ưu tiên Ngành học (`Program-First Feed`)**: API `GET /api/forum/feed` tự động phát hiện ngành học (`StudentProfile`) của người dùng hiện tại, ưu tiên đẩy các bài viết thuộc cùng ngành học lên đầu theo thứ tự hoạt động mới nhất (`lastActivityAt`), sau đó mới đến bài viết của các ngành học khác (Global Fallback).
- **Phân trang Con trỏ Opaque Cursor (`Cursor Pagination`)**: Tất cả các danh sách bài viết và bình luận đều sử dụng phân trang con trỏ `cursor` mã hóa opaque để đảm bảo hiệu năng cao và không bị trùng lặp bài viết khi có bình luận mới.
- **Dựng Cây Bình luận Nhiều Cấp (`Reply Hierarchy`)**: Dựa trên các trường `replyToCommentId`, `replyToUserId`, và `replyToUserName` để Frontend dựng giao diện bình luận trả lời đè cấp (nested reply).
- **Lọc Nội dung Vi phạm Tự động (`Prohibited Content Engine`)**: Tự động đối soát tiêu đề và nội dung bài viết/bình luận với danh sách từ cấm do Admin quản lý. Nếu phát hiện vi phạm, Backend từ chối với lỗi `400 FORUM_CONTENT_PROHIBITED`.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Gợi ý Nội dung Phù hợp Ngành học**: Sinh viên ngành Software Engineering sẽ mặc định nhìn thấy các câu hỏi liên quan đến CNTT trước tiên, tạo cộng đồng học tập cá nhân hóa theo đúng chương trình học FPTU.
2. **Lưu vết Ngành học Tác giả theo Thời điểm (`Author Program Snapshot`)**: Trường `authorProgram` được đóng băng ngay tại thời điểm tạo bài viết. Khi sinh viên chuyển ngành hoặc thay đổi thông tin cá nhân, thông tin ngành học trên các bài viết cũ giữ nguyên gốc.
3. **Kiểm duyệt Tự động Ngăn chặn Từ Cấm**: Admin cấu hình danh sách từ cấm, hệ thống tự động chuẩn hóa Unicode, xóa khoảng trắng thừa và kiểm tra trước khi lưu vào DB.
4. **Cập nhật Thứ tự Hoạt động Động (`lastActivityAt Sort`)**: Mỗi khi có bình luận mới hoặc phản hồi mới, thời gian `lastActivityAt` của bài viết được cập nhật, đẩy bài viết lên trên trong newsfeed.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                  LUỒNG TRAO ĐỔI VÀ ĐĂNG BÀI TRÊN DIỄN ĐÀN                             |
+-------------------------------------------------------------------------------------------------------+

  Mentee / Mentor (Frontend)             Backend (SkillSwap API)                Prohibited Phrase Engine
          |                                     |                                         |
   1. Mở trang Bảng tin Forum                   |                                         |
          |-- GET /api/forum/feed ------------->|-- Nhận diện ngành học của User -------->|
          |<-- 200 OK (Program-First Posts) ----|                                         |
          |                                     |                                         |
   2. Nhập Form Đăng bài mới                    |                                         |
          |-- POST /api/forum/posts ----------->|-- Clean Plain-text & Check Từ cấm ----->|
          |   (title, content, helpTopicId)     |<-- 400 FORUM_CONTENT_PROHIBITED (nếu vi phạm)
          |<-- 200 OK (ForumPostResponse) ------|                                         |
          |                                     |                                         |
   3. Xem Chi tiết Bài viết & Bình luận        |                                         |
          |-- GET /api/forum/posts/{id} ------->|                                         |
          |-- GET /posts/{id}/comments -------->|-- Trả danh sách Comment (Cursor asc) ->|
          |<-- 200 OK (ForumCommentResponse) ---|                                         |
          |                                     |                                         |
   4. Thả Cảm xúc & Trả lời Bình luận          |                                         |
          |-- PUT /posts/{id}/reaction -------->|-- Cập nhật reactionCount -------------->|
          |-- POST /posts/{id}/comments ------->|-- Lưu comment kèm replyToCommentId ---->|
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Thuật toán Newsfeed Ngành học (`Program-First Algorithm`)
- Không cần truyền `programId` trên Query Param. Backend tự lấy `program` trong `StudentProfile` active của user hiện tại.
- Cấu trúc trả về:
  1. Các bài viết có `authorProgram.id == currentProgram.id`, sắp xếp `lastActivityAt` giảm dần.
  2. Các bài viết thuộc ngành học khác hoặc bài viết không có thông tin ngành học (Global fallback).

### 4.2 Cấu trúc Cây Bình luận (`Reply Hierarchy`)
- Dữ liệu bình luận được trả về theo danh sách phẳng (Flat list) phân trang cursor để tải nhanh.
- Frontend dựa vào các thuộc tính sau để render thụt lùi (Indent) hoặc gắn nhãn trả lời:
  - `replyToCommentId`: ID của bình luận cha.
  - `replyToUserId`: ID của người được trả lời.
  - `replyToUserName`: Tên hiển thị của người được trả lời.

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- |
| `GET` | `/api/forum/feed` | User Role | Lấy bảng tin tin tức ưu tiên cùng ngành học | Trang chủ Forum / Newsfeed |
| `GET` | `/api/forum/posts` | User Role | Tìm kiếm và lọc bài viết toàn diễn đàn | Thanh tìm kiếm Forum / Filter Topic |
| `GET` | `/api/forum/posts/{postId}` | User Role | Lấy thông tin chi tiết của 1 bài viết | Màn hình Chi tiết Bài viết |
| `POST` | `/api/forum/posts` | User Role | Tạo bài viết forum mới | Bấm nút "Đăng bài" |
| `PUT` | `/api/forum/posts/{postId}` | Owner | Chỉnh sửa bài viết của tôi | Bấm "Sửa bài viết" |
| `DELETE` | `/api/forum/posts/{postId}` | Owner | Xóa mềm bài viết của tôi | Bấm "Xóa bài viết" |
| `GET` | `/api/forum/posts/{postId}/comments` | User Role | Lấy danh sách bình luận (phân trang Cursor asc) | Màn hình Chi tiết Bài viết |
| `POST` | `/api/forum/posts/{postId}/comments` | User Role | Gửi bình luận mới hoặc trả lời bình luận khác | Bấm "Gửi bình luận" |
| `PUT` | `/api/forum/comments/{commentId}` | Owner | Chỉnh sửa bình luận của tôi | Bấm "Sửa bình luận" |
| `DELETE` | `/api/forum/comments/{commentId}` | Owner | Xóa mềm bình luận của tôi | Bấm "Xóa bình luận" |
| `PUT` | `/api/forum/posts/{postId}/reaction` | User Role | Thả/Đổi cảm xúc cho bài viết (`LIKE`, `HEART`, `HELPFUL`) | Bấm nút Thả tim / Like bài |
| `DELETE` | `/api/forum/posts/{postId}/reaction` | User Role | Gỡ bỏ cảm xúc khỏi bài viết | Bấm bỏ Thả tim |
| `PUT` | `/api/forum/comments/{commentId}/reaction` | User Role | Thả/Đổi cảm xúc cho bình luận | Bấm Thả tim bình luận |
| `POST` | `/api/forum/reports` | User Role | Báo cáo bài viết hoặc bình luận vi phạm | Bấm "Báo cáo vi phạm" |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `POST /api/forum/posts`

#### Purpose
Tạo bài viết mới trên diễn đàn.

#### Request Body (`ForumPostUpsertRequest`)
```json
{
  "title": "Xin tài liệu ôn thi môn Software Architecture (PRN231)",
  "content": "Chào mọi người, mình đang chuẩn bị thi cuối kỳ môn PRN231. Bạn nào có slide hoặc đề ôn tập cho mình xin với ạ!",
  "helpTopicId": "44444444-4444-4444-4444-444444444444",
  "imageUrls": ["https://cdn.skillswap.asia/forum/img1.jpg"]
}
```

#### Response Body (`ForumPostResponse`)
```json
{
  "timestamp": "2026-08-04T10:05:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "authorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "authorFullName": "Nguyễn Văn A",
    "authorAvatarUrl": "https://cdn.skillswap.asia/avatar/a.jpg",
    "authorProgram": {
      "id": "55555555-5555-5555-5555-555555555555",
      "code": "CNTT",
      "nameVi": "Công nghệ thông tin",
      "nameEn": "Information Technology"
    },
    "helpTopic": {
      "id": "44444444-4444-4444-4444-444444444444",
      "code": "SPRING_BOOT",
      "nameVi": "Spring Boot",
      "nameEn": "Spring Boot"
    },
    "title": "Xin tài liệu ôn thi môn Software Architecture (PRN231)",
    "content": "Chào mọi người, mình đang chuẩn bị thi cuối kỳ môn PRN231...",
    "status": "PUBLISHED",
    "commentCount": 0,
    "reactionCount": 0,
    "reportCount": 0,
    "lastActivityAt": "2026-08-04T10:05:00Z",
    "reactedByCurrentUser": false,
    "myReactionType": null,
    "createdAt": "2026-08-04T10:05:00Z",
    "updatedAt": "2026-08-04T10:05:00Z",
    "imageUrls": ["https://cdn.skillswap.asia/forum/img1.jpg"]
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Đăng bài & Trả lời Bình luận Nhiều Cấp

```
User A (Author)                      Backend API                            User B (Commenter)
       |                                  |                                          |
  1. Tạo Bài viết mới ------------------->|-- Validate Từ cấm & Lưu DB ------------->|
       |<-- Trả ForumPostResponse --------|                                          |
       |                                  | 2. User B xem bài viết                   |
       |                                  |<-- GET /api/forum/posts/{id}/comments ---|
       |                                  | 3. User B gửi bình luận gốc              |
       |                                  |<-- POST /posts/{id}/comments ------------|
       |                                  |    (replyToCommentId = null)             |
       | 4. User A trả lời bình luận B    |                                          |
       |-- POST /posts/{id}/comments ---->|-- Lưu comment với replyToCommentId=B --->|
       |<-- Trả ForumCommentResponse -----|                                          |
```

---

## 8. State Machine (Ma trận Trạng thái Post, Comment, Reaction & Report)

### 8.1 Trạng thái Bài viết & Bình luận (`ForumEntityStatus`)

```
             +-----------------------+
             |  PUBLISHED / VISIBLE  | (Hiển thị công khai trên Forum)
             +-----------------------+
                         |
           +-------------+-------------+
           |                           |
      Admin khóa bài /            Tác giả bấm Xóa
    Report được duyệt                  |
           |                           v
           v               +-----------------------+
+-----------------------+  |     DELETED_USER      |
|    HIDDEN_MODERATED   |  +-----------------------+
+-----------------------+  (Xóa mềm - Ẩn khỏi Feed)
(Ẩn do vi phạm quy định)
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `FORUM_CONTENT_PROHIBITED` | Tiêu đề hoặc nội dung chứa từ cấm trong danh sách kiểm duyệt. | Giữ nguyên bản nháp form, hiển thị thông báo yêu cầu người dùng chỉnh sửa ngôn từ. |
| `403 FORBIDDEN` | `FORBIDDEN` | Cố tình sửa/xóa bài viết hoặc bình luận của người khác. | Ẩn các nút Sửa/Xóa trên giao diện đối với bài viết không thuộc về user. |
| `409 RESOURCE_CONFLICT` | `ALREADY_REPORTED` | Người dùng đã gửi báo cáo vi phạm cho bài viết/bình luận này trước đó. | Hiển thị Toast thông báo *"Bạn đã báo cáo nội dung này trước đó"*. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Chỉ Người Sở hữa Mới Được Sửa/Xóa**: API `PUT` và `DELETE` bài viết/bình luận bắt buộc kiểm tra `authorUserId == currentUser.id`.
2. **Không Tiết lộ Từ Cấm Trong Thông báo Lỗi**: Thông báo lỗi `FORUM_CONTENT_PROHIBITED` chỉ trả về thông báo chung, không trả về cụm từ cấm khớp để tránh người dùng tìm cách lách luật.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Sử dụng đúng opaque string `nextCursor` từ response trước đó để tải các trang tiếp theo.
- Tự động gọi refetch danh sách bình luận khi người dùng gửi bình luận mới thành công.
- Render đầy đủ các thuộc tính `authorProgram` để làm nổi bật ngành học của tác giả.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** tự decode hoặc tự tạo chuỗi `cursor`.
- **KHÔNG ĐƯỢC** dùng `reactionCount` để tự ẩn các bài viết có ít tương tác.
- **KHÔNG ĐƯỢC** tự ý xóa comment con trên Client khi comment cha bị xóa; bắt buộc phải reload lại thread từ Backend.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Thay đổi Ngành học của Người dùng**:
   - Khi người dùng cập nhật ngành học mới trong Student Profile, các bài viết cũ của họ vẫn giữ nguyên `authorProgram` cũ (Snapshot).
2. **Bình luận Cha bị Xóa**:
   - Khi một bình luận gốc bị xóa, Backend cập nhật trạng thái `DELETED_USER`. Các bình luận con (`replyToCommentId`) vẫn được giữ nguyên và hiển thị dưới dạng trả lời cho một bình luận đã bị ẩn.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **User Profile Service**: Cung cấp thông tin ngành học `StudentProfile` để Backend thực hiện thuật toán ưu tiên `Program-First Feed`.
- **Catalog Service**: Cung cấp danh sách chủ đề trợ giúp `HelpTopic` để phân loại bài viết.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Trang Bảng tin Forum (`ForumNewsfeedPage.tsx`)
- **React Components**: `ForumNewsfeedPage.tsx`, `ProgramFeedTab.tsx`, `PostCardItem.tsx`, `HelpTopicFilter.tsx`
- **APIs Triggered**:
  1. `GET /api/forum/feed` (Khi mở tab Bảng tin ưu tiên ngành học)
  2. `GET /api/forum/posts` (Khi gõ từ khóa tìm kiếm hoặc lọc theo Topic)
- **Expected Behavior**: Bài viết cùng ngành học hiển thị badge ngành nổi bật. Hiển thị nút "Tải thêm" khi `hasNext = true`.

#### B. Màn hình Chi tiết Bài viết & Thread (`PostDetailPage.tsx`)
- **React Components**: `PostDetailPage.tsx`, `PostDetailHeader.tsx`, `CommentThreadList.tsx`, `ReplyCommentInput.tsx`
- **APIs Triggered**:
  1. `GET /api/forum/posts/{postId}` (Mount trang)
  2. `GET /api/forum/posts/{postId}/comments` (Tải danh sách bình luận)
  3. `POST /api/forum/posts/{postId}/comments` (Khi gửi bình luận)
- **Expected Behavior**: Render cây bình luận lùi vào dựa vào `replyToCommentId`.

---

### 14.2 Frontend Forum State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |     LOADING_FEED      | (Tải tin tức Program-First)
                       +-----------------------+
                                   |
                          Chọn 1 bài viết
                                   |
                                   v
                       +-----------------------+
                       |    VIEWING_POST_THREAD| (Xem chi tiết & danh sách comment)
                       +-----------------------+
                                   |
                      Gửi comment / Thả reaction
                                   |
                                   v
                       +-----------------------+
                       |    REFRESHING_THREAD  | (Tải lại thread bình luận)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Forum Home | Search / Filter Topic | Open Post Detail | Submit Comment | User Action |
| --- | --- | --- | --- | --- | --- |
| `GET /api/forum/feed` | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /api/forum/posts` | ❌ KHÔNG | ✅ CÓ (Khi gõ từ khóa) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /api/forum/posts/{id}` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG |
| `POST .../comments` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ✅ Bấm "Gửi" |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Bài viết Chứa Từ Cấm Vi phạm (`HTTP 400`)
- **UI Component**: Modal Cảnh báo Nội dung (`ContentWarningModal.tsx`).
- **Visual State**: Viền đỏ cảnh báo trên Form đăng bài.
- **Message**: *"Bài viết của bạn chứa ngôn từ không phù hợp với tiêu chuẩn cộng đồng SkillSwap. Vui lòng chỉnh sửa lại nội dung."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['forum', 'feed']` | 2 phút | 15 phút | `true` | Tạo/Sửa/Xóa bài viết thành công |
| `['forum', 'post', postId]` | 1 phút | 10 phút | `true` | Thả reaction hoặc gửi comment mới |
| `['forum', 'comments', postId]` | 0 ms | 10 phút | `false` | Gửi/Sửa/Xóa bình luận thành công |
