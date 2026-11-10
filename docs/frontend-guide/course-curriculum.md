# Course curriculum

## Mô hình hiển thị

Mỗi khóa học chỉ có một cây nội dung:

```text
Course
  └─ Chapter
       ├─ Video (YouTube embed)
       └─ PDF
```

`Lecture` và `Resource` không còn là khái niệm FE cần xử lý. Video và PDF đều là `material` thuộc trực tiếp một chapter.

## Đọc nội dung học & Kiểm tra quyền truy cập

1. Gọi `GET /api/me/courses/{courseId}/curriculum` để dựng sidebar/cây học.
2. Mỗi material trong `/curriculum` trả về `access`: `"AVAILABLE"` hoặc `"LOCKED"` (không chứa `lockedReason` hay `userActionMessage`).
   - Nếu cần danh sách phẳng có sẵn `available` (`boolean`), `lockedReason`, `userActionMessage`, `retryable`, gọi `GET /api/me/courses/{courseId}/materials`.
3. **Bảo mật nội dung video (Anti-leak):**
   - Đối với bài học Video YouTube (`storageProviderType == "YOUTUBE"`), `youtubeVideoId` và `embedUrl` **chỉ xuất hiện trong response khi `access == "AVAILABLE"`** (học viên đã mua khóa học, hoặc bài học xem thử `previewable == true`, hoặc chính mentor sở hữu khóa học).
   - Nếu bài học bị khóa (`access == "LOCKED"`), backend ẩn hoàn toàn link YouTube (`youtubeVideoId = null`, `embedUrl = null`) nhằm chống việc xem lậu qua Network tab / DevTools.
4. Khi bài học có `access == "AVAILABLE"` và `status == "READY"`:
   - FE có thể dùng trực tiếp `material.embedUrl` từ `/curriculum`, hoặc gọi `GET /api/me/courses/{courseId}/materials/{materialId}/playback` để lấy thông tin phát video (`playbackUrl`, `embedUrl`, `youtubeVideoId`, `storageProviderType`, `thumbnailUrl`, `durationSeconds`, `expiresAt`).
5. PDF sẵn sàng được tải qua `GET /api/me/courses/{courseId}/materials/{materialId}/download`.
6. FE không tự đoán quyền truy cập từ giá khóa học. Luôn dùng `access` (ở `/curriculum`) hoặc `available` (ở `/materials`) và lỗi trả về từ API.

### Mẫu JSON `GET /api/me/courses/{courseId}/curriculum`

```json
{
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "courseId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
    "progress": {
      "overallPercentage": 50,
      "lastStudiedMaterialId": "019f3234-aaaa-bbbb-cccc-1234567890ab"
    },
    "chapters": [
      {
        "chapterId": "019f2234-aaaa-bbbb-cccc-1234567890ab",
        "title": "Chương 1: Spring Boot cơ bản",
        "description": "Các khái niệm nền tảng",
        "sortOrder": 1,
        "published": true,
        "version": 0,
        "materials": [
          {
            "materialId": "019f3234-aaaa-bbbb-cccc-1234567890ab",
            "title": "Bài 1: Giới thiệu Spring Boot (Xem thử)",
            "type": "VIDEO",
            "sortOrder": 1,
            "previewable": true,
            "published": true,
            "status": "READY",
            "durationSeconds": 600,
            "thumbnailUrl": "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            "access": "AVAILABLE",
            "progressPercentage": 100,
            "completed": true,
            "version": 0,
            "storageProviderType": "YOUTUBE",
            "youtubeVideoId": "dQw4w9WgXcQ",
            "embedUrl": "https://www.youtube.com/embed/dQw4w9WgXcQ"
          },
          {
            "materialId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
            "title": "Bài 2: Bảo mật JWT (Chưa mua)",
            "type": "VIDEO",
            "sortOrder": 2,
            "previewable": false,
            "published": true,
            "status": "READY",
            "durationSeconds": 480,
            "thumbnailUrl": "https://img.youtube.com/vi/jNQXAC9IVRw/hqdefault.jpg",
            "access": "LOCKED",
            "progressPercentage": null,
            "completed": false,
            "version": 0,
            "storageProviderType": "YOUTUBE",
            "youtubeVideoId": null,
            "embedUrl": null
          }
        ]
      }
    ]
  }
}
```

## Đăng ký khóa học, Thông báo & Chat khóa học

1. **Đăng ký khóa học (Enrollment):**
   - Người học đã đăng nhập gọi `POST /api/courses/{courseId}/enroll` (không cần body).
   - Backend tự báo giá, thu credit từ ví, tạo enrollment `ACTIVE` và trả `201 Created` với `{ enrollmentId, courseId, status, enrolledAt, completedAt }`.
   - Sau khi enroll thành công, FE tải lại `/curriculum` để các bài học chuyển từ `LOCKED` sang `AVAILABLE`.
2. **Mở course detail & học bài:**
   - Gọi `GET /api/me/courses/{courseId}/curriculum`. Mentor của khóa học xem toàn bộ cây; user chưa có enrollment hợp lệ vẫn xem được các bài đã công bố, bài chưa mua có `access == "LOCKED"`.
   - Chỉ gọi `playback` / `download` khi bài học có `access == "AVAILABLE"` và `status == "READY"`. Nếu chưa có quyền mà vẫn gọi sẽ nhận `403`.
3. **Thông báo khóa học:**
   - Người học có enrollment `ACTIVE` hoặc `COMPLETED` (và Mentor sở hữu khóa học) gọi `GET /api/courses/{courseId}/announcements` để đọc thông báo. Không có thông báo sẽ trả `data.content = []`.
4. **Chat khóa học:**
   - Khi người học mở chat, gọi `POST /api/courses/{courseId}/chat`. Backend trả conversation cũ hoặc tạo mới nếu enrollment đang `ACTIVE` và mentor của khóa học còn active, có role mentor và ownership hợp lệ.
   - Dùng `conversationId` nhận được để tải messages và subscribe các queue chat đã mô tả trong guide realtime.

Các lỗi FE cần xử lý:

| Mã | Ý nghĩa | FE nên làm |
|---|---|---|
| `400` | Dữ liệu không hợp lệ (link YouTube sai định dạng, hoặc gửi `/progress` khi `durationSeconds` chưa có) | Hiển thị thông báo validate cho người dùng. |
| `401` | Chưa đăng nhập hoặc access token không hợp lệ | Refresh session hoặc đưa user về login. |
| `403` | User chưa có quyền đọc announcement, mở material/chat; hoặc không phải mentor sở hữu | Hiển thị trạng thái không có quyền, không retry liên tục. |
| `404` | Không tìm thấy course/chapter/material | Hiển thị resource not found và quay lại danh sách. |
| `409` | Trùng `sortOrder` material, đã enroll trước đó, hoặc `expectedVersion` đã cũ | Tải lại `/curriculum` mới nhất rồi thử lại. |
| `422` | Không đủ credit khi gọi `POST /api/courses/{courseId}/enroll` | Hướng dẫn người học nạp thêm credit. |

Enrollment `PENDING_PAYMENT`, `CANCELLED`, `REFUNDED` hoặc `PAYMENT_EXPIRED` không đáp ứng điều kiện mở direct course chat hay đọc announcement; các điều kiện này do backend kiểm tra.

## Mentor quản lý curriculum

### 1. Quản lý Chapter
- Tạo chapter: `POST /api/me/mentor/courses/{courseId}/chapters` $\rightarrow$ Trả `200 OK`, `data` là `UUID` của chapter vừa tạo.
- Sửa chapter: `PUT /api/me/mentor/courses/{courseId}/chapters/{chapterId}`
- Đổi thứ tự chapter: `PUT /api/me/mentor/courses/{courseId}/chapters/order`
- Xóa chapter rỗng: `DELETE /api/me/mentor/courses/{courseId}/chapters/{chapterId}`

### 2. Quản lý Video (YouTube Embed)
Mentor tự tải video lên kênh YouTube cá nhân ở chế độ **Không công khai (Unlisted)** và bật **Cho phép nhúng (Allow embedding)**, sau đó dán link vào website:

- **Tạo video bài học**:
  ```http
  POST /api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/youtube
  Content-Type: application/json

  {
    "title": "Bài 1: Giới thiệu Spring Boot",
    "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "durationSeconds": 600,
    "sortOrder": 1,
    "previewable": false,
    "published": true
  }
  ```
  - Trả về `201 Created` với `data` chứa `materialId`, `title`, `playbackUrl`, `embedUrl`, `youtubeVideoId`, `storageProviderType = "YOUTUBE"`, `thumbnailUrl`, `durationSeconds`, `expiresAt = null`.
  - Backend tự trích xuất `youtubeVideoId`, sinh `thumbnailUrl` tự động và đặt trạng thái bài học thành `READY` ngay lập tức.
  - **Lưu ý:** `durationSeconds` là optional trong schema tạo, nhưng nếu muốn tính tiến độ hoàn thành (`PUT .../progress`) cho bài học thì cần truyền `durationSeconds > 0`.

- **Sửa thông tin bài học / đổi link video**:
  ```http
  PUT /api/me/mentor/courses/{courseId}/materials/{materialId}
  Content-Type: application/json

  {
    "title": "Bài 1: Giới thiệu Spring Boot (Cập nhật)",
    "previewable": false,
    "published": true,
    "expectedVersion": 0,
    "youtubeUrl": "https://youtu.be/9bZkp7q19f0",
    "durationSeconds": 252
  }
  ```
  - **Lưu ý quan trọng:** 4 trường `title`, `previewable`, `published`, `expectedVersion` là **bắt buộc** trong mọi request sửa (kể cả khi chỉ muốn đổi `youtubeUrl` hay `durationSeconds`). FE cần truyền lại giá trị hiện tại từ `/curriculum` nếu không thay đổi.

*(Lưu ý: Các API hạ tầng upload/stream video cũ như Bunny `upload-intent`, R2 `r2-upload-intent`, `confirm-video-upload`, internal streaming và Bunny webhook đã được ẩn khỏi Swagger để tránh nhầm lẫn).*

### 3. Quản lý PDF
- Khởi tạo upload: `POST /api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/pdf/upload-intent`
- Sau khi upload PDF lên URL presigned thành công, gọi `POST /api/me/mentor/courses/{courseId}/materials/{materialId}/confirm-pdf-upload` với đúng `objectKey` được cấp.

### 4. Đổi thứ tự & Xóa
- Đổi thứ tự material: `PUT /api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/order`
- Xóa material: `DELETE /api/me/mentor/courses/{courseId}/materials/{materialId}`

Khi reorder, FE phải gửi đủ danh sách ID đang có trong container, mỗi ID đúng một lần. `expectedContainerVersion`/`expectedVersion` lấy từ `/curriculum` để ngăn ghi đè thay đổi của người khác.

## Phát video YouTube trên Frontend

Khi học viên mở bài học có `access == "AVAILABLE"` và `storageProviderType == "YOUTUBE"`, FE nhúng trực tiếp qua thẻ `<iframe>`:

```tsx
<div className="relative w-full aspect-video">
  <iframe
    className="w-full h-full rounded-xl shadow-lg"
    src={material.embedUrl}
    title={material.title}
    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
    allowFullScreen
  />
</div>
```

## Progress

- Chỉ gọi `PUT /api/me/courses/{courseId}/materials/{materialId}/progress` với `{ "watchedSeconds": number }` khi bài học có **`durationSeconds > 0`**. Nếu `durationSeconds` bị `null` hoặc `<= 0`, backend sẽ trả `400 Bad Request`.
- Từ **90%** trở lên (`watchedSeconds / durationSeconds >= 0.9`), material được ghi nhận hoàn thành (`completed = true`).
- Progress tổng khóa học xuất hiện trong `curriculum.progress` (`overallPercentage`, `lastStudiedMaterialId`).
- PDF không có API “đã đọc”; không tự coi một lần tải là hoàn thành.

## Lưu ý upload PDF

- Chỉ nhận PDF, tối đa 25 MB.
- Upload URL có thời hạn. Nếu hết hạn hoặc upload không hợp lệ, khởi tạo intent mới.
- FE chỉ hiển thị material cho học viên khi `status = READY`; các trạng thái upload/processing chỉ mentor cần thấy.
