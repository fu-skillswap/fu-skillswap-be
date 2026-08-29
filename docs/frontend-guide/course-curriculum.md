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
