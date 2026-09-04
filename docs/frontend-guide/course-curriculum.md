# Course curriculum

## Mô hình hiển thị

Mỗi khóa học chỉ có một cây nội dung:

```text
Course
  └─ Chapter
       ├─ Video
       └─ PDF
```

`Lecture` và `Resource` không còn là khái niệm FE cần xử lý. Video và PDF đều là `material` thuộc trực tiếp một chapter.

## Đọc nội dung học

1. Gọi `GET /api/me/courses/{courseId}/curriculum` để dựng sidebar/cây học.
2. Mỗi material trả về `access`: `AVAILABLE` hoặc `LOCKED`.
3. Video sẵn sàng được mở qua `GET .../materials/{materialId}/playback`; PDF qua `GET .../materials/{materialId}/download`.
4. FE không tự đoán quyền truy cập từ giá khóa học. Luôn dùng `access` và lỗi trả về từ API.

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

- Tạo chapter: `POST /api/me/mentor/courses/{courseId}/chapters`.
- Sửa chapter: `PUT /api/me/mentor/courses/{courseId}/chapters/{chapterId}`.
- Đổi thứ tự chapter: `PUT .../chapters/order`.
- Tạo video: `POST .../chapters/{chapterId}/materials/video/upload-intent`.
- Tạo PDF: `POST .../chapters/{chapterId}/materials/pdf/upload-intent`.
- Sau khi upload PDF thành công, gọi `POST .../materials/{materialId}/confirm-pdf-upload` với đúng `objectKey` được cấp.
- Sửa/xóa material và đổi thứ tự material dùng các endpoint `/materials/{materialId}` và `/materials/order`.

Khi reorder, FE phải gửi đủ danh sách ID đang có trong container, mỗi ID đúng một lần. `expectedContainerVersion`/`expectedVersion` lấy từ curriculum để ngăn người dùng vô tình ghi đè thay đổi của người khác.

## Progress

Với video có thời lượng đã xác định, FE gửi số giây đã xem qua `PUT /api/me/courses/{courseId}/materials/{materialId}/progress`.

- Từ 90% trở lên, material được ghi nhận hoàn thành.
- Progress tổng khóa học xuất hiện trong `curriculum.progress`.
- PDF không có API “đã đọc”; không tự coi một lần tải là hoàn thành.

## Lưu ý upload PDF

- Chỉ nhận PDF, tối đa 25 MB.
- Upload URL có thời hạn. Nếu hết hạn hoặc upload không hợp lệ, khởi tạo intent mới.
- FE chỉ hiển thị material cho học viên khi `status = READY`; các trạng thái upload/processing chỉ mentor cần thấy.
