# Forum

## Mục tiêu
File này mô tả forum user flow và admin moderation flow.
FE phải hiểu:
- post list/comment list dùng cursor semantics
- reply hierarchy phải render từ field replyTo*
- same DTO có thể được tái dùng cho user/admin, không được tự giả định thiếu field

## API inventory
### User forum
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/forum/feed` | Authenticated | mentee/mentor | `cursor`, `limit` | `CursorPageResponse<ForumPostResponse>` | - | Same-program posts first, then newest global fallback. |
| GET | `/api/forum/posts` | Authenticated | mentee/mentor | `cursor`, `limit`, `keyword`, `helpTopicId`, `mine` | `CursorPageResponse<ForumPostResponse>` | - | Search/filter toàn Forum |
| GET | `/api/forum/posts/{postId}` | Authenticated | mentee/mentor | path `postId` | `ForumPostResponse` | - | Chi tiết bài post |
| POST | `/api/forum/posts` | Authenticated | user | create post request | `ForumPostResponse` | - | Tạo bài post |
| PUT | `/api/forum/posts/{postId}` | Authenticated | owner | update post request | `ForumPostResponse` | - | Sửa bài post |
| DELETE | `/api/forum/posts/{postId}` | Authenticated | owner | - | `ForumPostResponse` | - | Xóa mềm bài post |
| POST | `/api/forum/posts/{postId}/comments` | Authenticated | user | create comment request | `ForumCommentResponse` | - | Tạo comment |
| GET | `/api/forum/posts/{postId}/comments` | Public or Authenticated | - | cursor request theo runtime | `CursorPageResponse<ForumCommentResponse>` | - | Comment list hiện dùng cursor |
| PUT | `/api/forum/comments/{commentId}` | Authenticated | owner | update comment request | `ForumCommentResponse` | - | Sửa comment |
| DELETE | `/api/forum/comments/{commentId}` | Authenticated | owner | - | `Void` | - | Xóa comment |
| PUT/DELETE | `/api/forum/posts/{postId}/reaction` | Authenticated | user | `ForumReactionRequest` for PUT | `ForumPostResponse` | - | Set/remove reaction |
| PUT/DELETE | `/api/forum/comments/{commentId}/reaction` | Authenticated | user | `ForumReactionRequest` for PUT | `ForumCommentResponse` | - | Set/remove reaction comment |
| POST | `/api/forum/posts/{postId}/reports` | Authenticated | user | report request | `ForumReportResponse` | - | Report post |
| POST | `/api/forum/comments/{commentId}/reports` | Authenticated | user | report request | `ForumReportResponse` | - | Report comment |

### Admin moderation
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/admin/forum/reports` | Admin/system admin | admin | filter request | `PageResponse<ForumReportResponse>` | - | Queue báo cáo |
| GET | `/api/admin/forum/reports/{reportId}` | Admin/system admin | admin | path `reportId` | `ForumReportResponse` | - | Report detail |
| POST | `/api/admin/forum/reports/{reportId}/resolve` | Admin/system admin | admin | moderation action request | `ForumReportResponse` | - | Resolve report |
| GET | `/api/admin/forum/posts` | Admin/system admin | admin | filter request | `PageResponse<ForumPostResponse>` | - | Danh sách post admin |
| GET | `/api/admin/forum/comments` | Admin/system admin | admin | filter request | `CursorPageResponse<ForumCommentResponse>` | - | Danh sách comment admin |
| POST | `/api/admin/forum/posts/{postId}/restore` | Admin/system admin | admin | - | `ForumPostResponse` | - | Restore post hidden |
| POST | `/api/admin/forum/comments/{commentId}/restore` | Admin/system admin | admin | - | `ForumCommentResponse` | - | Restore comment hidden |

### Admin prohibited phrases
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/admin/forum/prohibited-phrases?isActive=&cursor=&limit=` | Admin/system admin | admin | query params | `CursorPageResponse<ForumProhibitedPhraseResponse>` | `isActive` omitted returns active and inactive rules. |
| GET | `/api/admin/forum/prohibited-phrases/{ruleId}` | Admin/system admin | admin | path `ruleId` | `ForumProhibitedPhraseResponse` | Detail for an existing rule. |
| POST | `/api/admin/forum/prohibited-phrases` | Admin/system admin | admin | `{ "phrase": "..." }` | `ForumProhibitedPhraseResponse` | Creates an active rule. |
| PUT | `/api/admin/forum/prohibited-phrases/{ruleId}` | Admin/system admin | admin | `{ "phrase": "...", "expectedVersion": 0 }` | `ForumProhibitedPhraseResponse` | Updates the phrase with optimistic version protection. |
| PATCH | `/api/admin/forum/prohibited-phrases/{ruleId}/active` | Admin/system admin | admin | `{ "isActive": false, "expectedVersion": 1 }` | `ForumProhibitedPhraseResponse` | Deactivates/reactivates a rule; no hard delete. |

## Call order chuẩn
### User browse
1. FE load `/api/forum/feed` cho home newsfeed; backend tự lấy ngành hiện tại của viewer.
2. FE load `/api/forum/posts` khi cần search/filter toàn Forum.
3. FE click vào detail.
4. Nếu cần, FE load comment cursor page cho thread.
5. FE chỉ load thêm page/cursor khi user kéo thêm.

### Program-first newsfeed
- `GET /api/forum/feed` không nhận `programId`; backend lấy program hiện tại từ `StudentProfile` của viewer.
- Post cùng program được trả hết trước, newest-first theo `lastActivityAt`; sau đó mới đến post mới nhất của mọi program khác và post không có program snapshot.
- Nếu viewer chưa có active program hoặc program đó không có post, response là global newest-first fallback.
- `authorProgram` là snapshot tại lúc tạo post. Đổi academic profile hoặc sửa post không thay đổi snapshot này.
- Cursor bind vào program của viewer. Nếu program profile đổi giữa hai lần tải, FE bỏ cursor cũ và tải lại trang đầu.

### User action
1. Tạo post/comment/reaction/report bằng đúng endpoint.
2. Sau action, refresh detail/thread.
3. Nếu có nested reply, FE dùng `replyToCommentId`, `replyToUserId`, `replyToUserName` để dựng cây.

### Admin moderation
1. FE admin load queue reports.
2. FE mở report detail.
3. FE resolve bằng action phù hợp.
4. FE refresh lại queue và target post/comment sau resolve.

### Prohibited phrase administration
1. Admin loads the cursor list and keeps the server `version` for every editable rule.
2. Admin creates, updates, or activates/deactivates a phrase through the endpoints above.
3. The server normalizes Unicode/case/whitespace and rejects a duplicate normalized phrase.
4. Changes apply only to subsequent Forum create/update requests; existing posts/comments are not rescanned.

## Ý nghĩa field quan trọng
### `ForumPostResponse`
- `authorProgram`
  - snapshot ngành học của tác giả khi tạo post; có thể `null` với tài khoản không có academic program.
- `status`
  - `PUBLISHED` hoặc `HIDDEN`
- `commentCount`, `reactionCount`, `reportCount`
  - thống kê hiển thị
- `lastActivityAt`
  - sort mới nhất hoạt động
- `reactedByCurrentUser`, `myReactionType`
  - trạng thái tương tác của user hiện tại
- `imageUrls`
  - ảnh đính kèm

### `ForumCommentResponse`
- `status`
  - `VISIBLE` hoặc `HIDDEN`
- `replyToCommentId`, `replyToUserId`, `replyToUserName`
  - dữ liệu reply hierarchy
- `reactedByCurrentUser`, `reactionCount`
  - trạng thái tương tác

### `ForumReportResponse`
- `targetType`
  - `POST` hoặc `COMMENT`
- `reasonType`
  - `SPAM`, `OFF_TOPIC`, `HARASSMENT`, `MISLEADING`, `OTHER`
- `status`
  - `OPEN`, `RESOLVED_NO_ACTION`, `RESOLVED_ACTION_TAKEN`, `DISMISSED`

### `ForumProhibitedPhraseResponse`
- `phrase`
  - exact admin-managed phrase; never exposed to regular Forum users.
- `isActive`
  - only active rules are evaluated when a post or comment is created or updated.
- `version`
  - FE must send this value on update/activate mutations.

## FE phải làm
- Dùng cursor response đúng nghĩa nếu backend đã trả cursor.
- Refresh thread sau mỗi mutation lớn.
- Dùng same DTO cho user/admin theo đúng field backend trả, không tự cắt field.

## FE không được làm
- Không tự suy diễn parent/child comment chỉ từ content.
- Không giả định xóa/hide parent sẽ tự xử lý cây comment ở FE; luôn refresh lại thread.
- Không dùng page/size cho comment nếu endpoint đã trả cursor.
- Không dùng report status để ẩn post/comment nếu chưa có resolve response mới.

## FE anti-patterns
- Không load toàn bộ comment rồi sắp xếp/filter client-side.
- Không giả định admin response thiếu metadata.
- Không dùng reaction count để thay thế content quality.

## Response JSON example
### Post detail
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "postId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "authorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "authorFullName": "Nguyễn Văn A",
    "authorAvatarUrl": "https://example.com/avatar.jpg",
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
    "title": "Cách học Spring Boot hiệu quả",
    "content": "Nội dung bài viết...",
    "status": "PUBLISHED",
    "commentCount": 12,
    "reactionCount": 20,
    "reportCount": 0,
    "lastActivityAt": "2026-07-13T09:55:00",
    "reactedByCurrentUser": false,
    "myReactionType": null,
    "createdAt": "2026-07-13T08:00:00",
    "updatedAt": "2026-07-13T09:00:00",
    "imageUrls": ["https://example.com/pic1.jpg"]
  }
}
```

### Comment detail
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "commentId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    "postId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "authorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "authorFullName": "Nguyễn Văn A",
    "authorAvatarUrl": "https://example.com/avatar.jpg",
    "authorRole": "MENTOR",
    "content": "Rất hữu ích",
    "status": "VISIBLE",
    "reportCount": 0,
    "reactionCount": 3,
    "reactedByCurrentUser": true,
    "replyToCommentId": null,
    "replyToUserId": null,
    "replyToUserName": null,
    "createdAt": "2026-07-13T09:50:00",
    "updatedAt": "2026-07-13T09:50:00",
    "imageUrls": []
  }
}
```

## UI mapping
- Post list:
  - card title/excerpt/help topic
- Post detail:
  - content, images, reaction count, comment count
- Comment thread:
  - reply hierarchy từ `replyTo*`
- Admin moderation screen:
  - report queue, target snapshot, resolve action

## API success/error behavior
- post/comment create/update/delete
  - success: refresh detail/thread
  - 403: không phải owner / không có quyền
  - 409: item đã bị đổi trạng thái
- report create
  - success: refresh report state
  - 409: đã report trước đó
- admin resolve
  - success: refresh queue và target entity
  - 400: action không phù hợp target/status
- post/comment create or update
  - `400 FORUM_CONTENT_PROHIBITED`: keep the local draft, ask the writer to revise it, and do not auto-retry.
- prohibited phrase create/update
  - `409 FORUM_PROHIBITED_PHRASE_DUPLICATE`: show that an equivalent normalized rule already exists.
  - `409 RESOURCE_CONFLICT`: refetch the rule before the admin submits another edit.

## Ghi chú cho AI Agent và FE dev
- `imageUrls`, `replyTo*`, `reactedByCurrentUser` là field FE phải render đủ.
- Khi parent comment bị hide/delete, FE phải reload thread thay vì tự dựng cây từ cache cũ.
- Forum validates post `title + content` and comment `content` after plain-text cleanup. The generic user error never identifies the matching phrase or rule.
