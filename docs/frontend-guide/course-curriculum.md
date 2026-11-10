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

## Đọc nội dung học

1. Gọi `GET /api/me/courses/{courseId}/curriculum` để dựng sidebar/cây học.
2. Mỗi material trả về `access`: `AVAILABLE` hoặc `LOCKED`.
3. **Bảo mật nội dung video (Anti-leak):**
   - Đối với bài học Video YouTube (`storageProviderType = "YOUTUBE"`), `youtubeVideoId` và `embedUrl` **chỉ xuất hiện trong response khi `access == "AVAILABLE"`** (học viên đã mua khóa học, hoặc bài học xem thử `previewable = true`, hoặc chính mentor sở hữu khóa học).
   - Nếu bài học bị khóa (`access == "LOCKED"`), backend ẩn hoàn toàn link YouTube (`youtubeVideoId = null`, `embedUrl = null`) nhằm chống việc xem lậu qua Network tab / DevTools.
4. Khi bài học có `access == "AVAILABLE"` và `status == "READY"`, FE có thể phát trực tiếp bằng `embedUrl` từ curriculum hoặc gọi `GET /api/me/courses/{courseId}/materials/{materialId}/playback` để nhận URL nhúng iframe.
5. PDF sẵn sàng được tải qua `GET .../materials/{materialId}/download`.
6. FE không tự đoán quyền truy cập từ giá khóa học. Luôn dùng `access` và lỗi trả về từ API.

## Luồng quyền truy cập và giao tiếp khóa học

Hiện backend không có endpoint public riêng để tạo enrollment. FE không được tự tạo request giả hoặc coi việc mở curriculum là đã đăng ký. Enrollment hợp lệ được tạo bởi luồng nghiệp vụ hiện có; khi cần kiểm tra quyền, FE dùng dữ liệu trả về từ API khóa học/curriculum và các cờ quyền trong response.

Luồng FE thực tế:

1. User đăng nhập và mở course detail.
2. Gọi `GET /api/me/courses/{courseId}/curriculum`. Mentor của khóa học xem toàn bộ cây; user chưa có enrollment hợp lệ vẫn có thể xem phần đã công bố, nhưng material bị khóa trả `access=LOCKED`.
3. Chỉ gọi playback/download khi material có `access=AVAILABLE` và `status=READY`. Nếu bị khóa, hiển thị `lockedReason`/`userActionMessage`; không tự suy đoán quyền từ giá hoặc trạng thái enrollment.
4. Người học có enrollment `ACTIVE` hoặc `COMPLETED` gọi `GET /api/courses/{courseId}/announcements` để đọc thông báo. Mentor của khóa học cũng đọc được. Không có thông báo là `data.content=[]`.
5. Khi người học mở chat, gọi `POST /api/courses/{courseId}/chat`. Backend trả conversation cũ hoặc tạo conversation mới nếu enrollment đang `ACTIVE` và mentor của khóa học còn active, có role mentor và ownership hợp lệ.
6. Dùng `conversationId` nhận được để tải messages và subscribe các queue chat đã mô tả trong guide realtime.

Các lỗi FE cần xử lý:

| Mã | Ý nghĩa | FE nên làm |
|---|---|---|
| `401` | Chưa đăng nhập hoặc access token không hợp lệ | Refresh session hoặc đưa user về login. |
| `403` | User chưa có quyền đọc announcement, mở material/chat; hoặc mentor không còn đủ điều kiện | Hiển thị trạng thái không có quyền, không retry liên tục. |
| `404` | Không tìm thấy course/material | Hiển thị resource not found và quay lại danh sách. |
| `409` | Xung đột tạo course conversation đồng thời hoặc dữ liệu curriculum đã đổi | Tải lại dữ liệu mới nhất rồi thử lại một lần. |

Enrollment `PENDING_PAYMENT`, `CANCELLED`, `REFUNDED` hoặc `PAYMENT_EXPIRED` không đáp ứng điều kiện mở direct course chat. Announcement read cũng yêu cầu enrollment `ACTIVE`/`COMPLETED`; các điều kiện này do backend kiểm tra.

## Mentor quản lý curriculum

### 1. Quản lý Chapter
- Tạo chapter: `POST /api/me/mentor/courses/{courseId}/chapters`
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
  - Backend tự trích xuất `youtubeVideoId`, sinh `thumbnailUrl` tự động và đặt trạng thái bài học thành `READY` ngay lập tức. Mentor không cần chờ upload hay xử lý video.
- **Sửa thông tin video**:
  `PUT /api/me/mentor/courses/{courseId}/materials/{materialId}`
  Cho phép gửi kèm `youtubeUrl` mới và `durationSeconds` để cập nhật video.

*(Lưu ý: Các API hạ tầng upload video stream trực tiếp như Bunny/R2 upload intent đã được ẩn trên Swagger để tránh nhầm lẫn cho Frontend).*

### 3. Quản lý PDF
- Khởi tạo upload: `POST /api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/pdf/upload-intent`
- Sau khi upload PDF lên URL presigned thành công, gọi `POST /api/me/mentor/courses/{courseId}/materials/{materialId}/confirm-pdf-upload` với đúng `objectKey` được cấp.

### 4. Đổi thứ tự & Xóa
- Đổi thứ tự material: `PUT /api/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/order`
- Xóa material: `DELETE /api/me/mentor/courses/{courseId}/materials/{materialId}`

Khi reorder, FE phải gửi đủ danh sách ID đang có trong container, mỗi ID đúng một lần. `expectedContainerVersion`/`expectedVersion` lấy từ curriculum để ngăn người dùng vô tình ghi đè thay đổi của người khác.

## Phát video YouTube trên Frontend

Khi học viên mở bài học có `access == "AVAILABLE"` và `storageProviderType == "YOUTUBE"`, FE có thể nhúng trực tiếp qua thẻ `<iframe>`:

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

Với video có thời lượng đã xác định, FE gửi số giây đã xem qua `PUT /api/me/courses/{courseId}/materials/{materialId}/progress`.

- Từ 90% trở lên, material được ghi nhận hoàn thành.
- Progress tổng khóa học xuất hiện trong `curriculum.progress`.
- PDF không có API “đã đọc”; không tự coi một lần tải là hoàn thành.

## Lưu ý upload PDF

- Chỉ nhận PDF, tối đa 25 MB.
- Upload URL có thời hạn. Nếu hết hạn hoặc upload không hợp lệ, khởi tạo intent mới.
- FE chỉ hiển thị material cho học viên khi `status = READY`; các trạng thái upload/processing chỉ mentor cần thấy.
