# API Document Addendum - Forum Post Cursor List

Tài liệu này chỉ chuẩn hóa 2 endpoint post list đang chuyển sang cursor pagination.

## Quy ước chung

- `cursor` là chuỗi mờ `opaque string`.
- Frontend không được decode, sửa, hoặc tự tạo cursor.
- Frontend chỉ được lấy `nextCursor` từ response trước đó rồi truyền lại nguyên giá trị.
- `nextCursor = null` nghĩa là đã hết dữ liệu.
- `prevCursor` hiện chưa dùng cho 2 endpoint này nên luôn có thể là `null`.
- Mỗi response list trả theo envelope `ApiResponse<CursorPageResponse<ForumPostResponse>>`.

## 1. GET `/api/forum/posts`

### Mục đích

Lấy danh sách bài viết forum cho user đã đăng nhập, sắp xếp theo hoạt động mới nhất trước.

### Query params

| Param | Kiểu | Mặc định | Bắt buộc | Mô tả |
|---|---|---:|---|---|
| `cursor` | string | null | Không | Cursor mờ để lấy window tiếp theo. Chỉ dùng lại `nextCursor` từ response trước đó. |
| `limit` | integer | 20 | Không | Số lượng item mỗi lần lấy. Tối đa 50. |
| `keyword` | string | null | Không | Tìm theo title/content/tên tác giả. |
| `helpTopicId` | UUID | null | Không | Lọc theo help topic. |
| `mine` | boolean | false | Không | Chỉ lấy bài viết của chính user hiện tại. |

### Response

```json
{
  "timestamp": "2026-07-08 15:40:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "items": [
      {
        "postId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a1001",
        "authorUserId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a2001",
        "authorFullName": "Nguyen Van A",
        "authorAvatarUrl": "https://cdn.skillswap.asia/avatar/a.jpg",
        "helpTopic": {
          "id": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a3001",
          "code": "HELP_PROJECT_REVIEW",
          "nameVi": "Góp ý dự án/case study",
          "nameEn": "Project or case study review"
        },
        "title": "Xin góp ý slide milestone",
        "content": "Mọi người review giúp mình flow thuyết trình với.",
        "status": "PUBLISHED",
        "commentCount": 3,
        "reactionCount": 5,
        "reportCount": 0,
        "lastActivityAt": "2026-07-08T14:30:00",
        "reactedByCurrentUser": false,
        "myReactionType": null,
        "createdAt": "2026-07-08T10:00:00",
        "updatedAt": "2026-07-08T10:00:00"
      }
    ],
    "nextCursor": "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I",
    "prevCursor": null,
    "hasNext": true,
    "hasPrev": false,
    "limit": 20
  }
}
```

## 2. GET `/api/admin/forum/posts`

### Mục đích

Lấy danh sách bài viết forum cho admin với bộ lọc quản trị.

### Query params

| Param | Kiểu | Mặc định | Bắt buộc | Mô tả |
|---|---|---:|---|---|
| `cursor` | string | null | Không | Cursor mờ để lấy window tiếp theo. Chỉ dùng lại `nextCursor` từ response trước đó. |
| `limit` | integer | 20 | Không | Số lượng item mỗi lần lấy. Tối đa 50. |
| `keyword` | string | null | Không | Tìm theo title/content/tên tác giả. |
| `helpTopicId` | UUID | null | Không | Lọc theo help topic. |
| `authorId` | UUID | null | Không | Lọc theo tác giả. |
| `status` | enum | null | Không | Lọc theo trạng thái post. |

### Response

```json
{
  "timestamp": "2026-07-08 15:40:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "items": [
      {
        "postId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a1001",
        "authorUserId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a2001",
        "authorFullName": "Nguyen Van A",
        "authorAvatarUrl": "https://cdn.skillswap.asia/avatar/a.jpg",
        "helpTopic": {
          "id": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a3001",
          "code": "HELP_PROJECT_REVIEW",
          "nameVi": "Góp ý dự án/case study",
          "nameEn": "Project or case study review"
        },
        "title": "Xin góp ý slide milestone",
        "content": "Mọi người review giúp mình flow thuyết trình với.",
        "status": "PUBLISHED",
        "commentCount": 3,
        "reactionCount": 5,
        "reportCount": 0,
        "lastActivityAt": "2026-07-08T14:30:00",
        "reactedByCurrentUser": false,
        "myReactionType": null,
        "createdAt": "2026-07-08T10:00:00",
        "updatedAt": "2026-07-08T10:00:00"
      }
    ],
    "nextCursor": "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I",
    "prevCursor": null,
    "hasNext": true,
    "hasPrev": false,
    "limit": 20
  }
}
```

## Lưu ý cho Frontend

- Không được coi `cursor` là payload có thể parse.
- Không được tạo cursor mới từ `lastActivityAt` hoặc `postId`.
- Muốn load trang kế tiếp thì chỉ truyền nguyên `nextCursor` của response trước đó.
- Khi `nextCursor = null`, dừng infinite scroll.

# API Document Addendum - Academic / Catalog / Onboarding

## Quy ước chung

- Các API danh mục học thuật và catalog đều là master data ổn định.
- Các endpoint này trả `Cache-Control: public, max-age=86400`.
- Frontend nên cache cục bộ và chỉ gọi lại khi cần refresh thủ công.
- `help topic` là catalog nghiệp vụ cho mentor profile / mentor service / discovery, không phải tag kỹ thuật.
- `student profile completed` là tín hiệu nền cho onboarding, không thay thế trạng thái tài khoản hay role.

## 1. GET `/api/campuses`

### Mục đích

Lấy danh sách cơ sở học tập đang hoạt động của FPT University để đổ dropdown trong form academic profile.

### Response

- `ApiResponse<List<CampusResponse>>`
- Header:
  - `Cache-Control: public, max-age=86400`

## 2. GET `/api/academic-programs`

### Mục đích

Lấy danh sách ngành học đang hoạt động để user chọn trước khi chọn specialization.

### Response

- `ApiResponse<List<AcademicProgramResponse>>`
- Header:
  - `Cache-Control: public, max-age=86400`

## 3. GET `/api/specializations`

### Mục đích

Lấy toàn bộ specialization đang hoạt động để FE dựng form nhanh hoặc cache sẵn data.

### Response

- `ApiResponse<List<SpecializationResponse>>`
- Header:
  - `Cache-Control: public, max-age=86400`

## 4. GET `/api/academic-programs/{programId}/specializations`

### Mục đích

Lấy specialization theo ngành học đã chọn.

### Rule

- `programId` phải tồn tại và đang active.
- Nếu `programId` không hợp lệ hoặc inactive, backend trả `404`.
- `Cache-Control: public, max-age=86400`

## 5. GET `/api/catalog/help-topics`

### Mục đích

Lấy danh sách help topics hiện hành để dùng cho mentor profile, mentor service, discovery và các form liên quan.

### Response

- `ApiResponse<List<HelpTopicResponse>>`
- Header:
  - `Cache-Control: public, max-age=86400`

## 6. GET `/api/catalog/mentor-profile-options`

### Mục đích

Lấy label mức support 1..4 cho mentor profile để FE không fix cứng wording.

### Response

- `ApiResponse<MentorProfileOptionsResponse>`
- Header:
  - `Cache-Control: public, max-age=86400`

## 7. PUT `/api/me/student-profile`

### Rule quan trọng

- `campusId`, `programId`, `specializationId` phải hợp lệ.
- `specializationId` phải thuộc đúng `programId`.
- Sai relation phải trả `400 Bad Request`, không để DB văng `500`.
- FE nên coi đây là bước validate trước khi đi tiếp onboarding.

## 8. GET `/api/me/onboarding-status`

### Output chính

- `studentProfileCompleted`
- `mentorProfileCompleted`
- `mentoringNeedsCompleted`
- `mentorVerificationStatus`
- `roles`
- `nextRecommendedAction`

### Ghi chú

- FE dùng response này để điều hướng tiếp theo trong onboarding.
- Không tự suy diễn trạng thái khác ngoài `nextRecommendedAction`.
