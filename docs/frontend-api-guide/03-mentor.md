# Mentor Service (`03-mentor.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Mentor (Mentor Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Mentor Service** quản lý toàn bộ vòng đời hoạt động của Mentor trên hệ thống SkillSwap: từ thiết lập hồ sơ chuyên môn (`MentorProfile`), quy trình nộp và duyệt minh chứng xác thực (`MentorVerificationRequest`), quản lý các gói dịch vụ hỗ trợ (`MentorService`), tài liệu học tập bảo mật (`Service Learning Resources`), chính sách đặt lịch (`MentorBookingPolicy`), đến hệ thống xuất bản bài viết chuyên môn (`Mentor Blog`) và quản lý lịch rảnh định kỳ (`Availability Templates`).

### Trách nhiệm chính của Service
- **Quản lý Hồ sơ Chuyên môn Mentor (`MentorProfile`)**: Thiết lập tiêu đề (headline), mô tả kinh nghiệm, điểm chuyên môn, mức hỗ trợ 1..4 (Foundation, Output Review, Direction), chủ đề hỗ trợ (Help Topics), dự án tiêu biểu (Featured Projects) và bằng cấp/thành tích (Achievements).
- **Quy trình Quy đổi & Xác thực Mentor (`MentorVerification`)**: Hỗ trợ quy trình 2 bước upload tài liệu minh chứng bảo mật trực tiếp lên S3/R2 storage via Presigned Upload URLs, gửi yêu cầu xét duyệt cho Admin, xử lý quy trình yêu cầu chỉnh sửa (Revision) và xem lịch sử sự kiện (Timeline).
- **Quản lý Dịch vụ Mentoring (`MentorService`)**: Tạo và quản lý gói 1-1 hoặc Group Session, thiết lập giá trị SCoin, thời lượng (durationMinutes - immutable), cờ hỗ trợ chat sau buổi học (`maintainPostSessionChat`) và cơ chế khóa lạc quan (Optimistic Locking via `version`).
- **Quản lý Tài liệu Học tập Bảo mật (`Service Learning Resources`)**: Upload 2 bước tài liệu đính kèm gói dịch vụ (PDF, DOCX, PPTX, TEXT, MARKDOWN, PNG, JPEG - tối đa 15 MiB), phân quyền truy cập (`BOOKED_MEMBERS` vs `AUTHENTICATED`) và cấp link tải bảo mật thời gian ngắn.
- **Trình diễn Hồ sơ Công khai (`Public Mentor Profile`)**: Cung cấp cấu trúc dữ liệu công khai 6 phần chuẩn mực (`identity` -> `mentoring` -> `services` -> `evidence` -> `reputation` -> `availability`) phục vụ hiển thị chi tiết Mentor cho Mentee.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Chuẩn hóa Danh tính & Năng lực Mentor**: Đảm bảo chỉ những Mentor đã được Admin xét duyệt minh chứng (`verifiedAt != null`, `mentorStatus = ACTIVE`) mới được phép kích hoạt gói dịch vụ và mở lịch nhận Booking từ Mentee.
2. **Bảo mật Tài liệu & Minh chứng Tuyệt đối**:
   - Sử dụng cơ chế Upload Intent 2 bước (Presigned URL) cho cả Verification Document và Service Learning Resource. Mã JavaScript ở Client không bao giờ nhận hoặc lưu `objectKey` hay storage bucket credential.
   - Link tải tài liệu bảo mật (`download-url`) chỉ được cấp phát theo phiên (short-lived) dựa trên quyền hạn đăng ký buổi học thành công của Mentee.
3. **Chống Race Condition trong Quản lý Dịch vụ (Optimistic Locking)**: Tất cả các thao tác cập nhật gói dịch vụ (`PUT`), bật/tắt dịch vụ (`PATCH /active`), và chỉnh sửa chính sách đặt lịch (`PATCH /booking-policy`) đều bắt buộc gửi kèm `version` (hoặc `expectedVersion`) để tránh ghi đè dữ liệu khi nhiều tab/người dùng cùng thao tác.
4. **Cam kết Rõ ràng về Trải nghiệm Chat Sau Buổi học**: Field `maintainPostSessionChat` công khai minh bạch cam kết hỗ trợ của Mentor (mặc định `false`: chat mở đến 24h sau buổi học; `true`: duy trì chat dài hạn khi kết thúc thành công).
5. **Đánh giá Uy tín Dựa trên Kết quả Thực tế**: Chỉ số `completedSessions` chỉ được tăng lên khi Mentee xác nhận hoàn tất buổi học thành công (`USER_CONFIRMED`). Mọi trường hợp tự động đóng session hay hủy lịch đều không làm tăng chỉ số này.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                    LUỒNG ĐĂNG KÝ & VẬN HÀNH MENTOR                                    |
+-------------------------------------------------------------------------------------------------------+

  Frontend (Browser)                  Backend (SkillSwap API)                 Storage (S3/R2) / Admin
          |                                     |                                         |
   1. Đã xong Hồ sơ Sinh viên (StudentProfileCompleted = true)                           |
          |                                     |                                         |
   2. PUT /api/me/mentor-profile -------------->|-- Lưu thông tin chuyên môn ------------>|
          |<-- 200 OK (MentorProfileResponse) --|                                         |
          |                                     |                                         |
   3. POST /api/me/mentor-verification/request ->|-- Khởi tạo Wizard Verification --------->|
          |<-- 200 OK (Draft Request) ----------|                                         |
          |                                     |                                         |
   4. POST /documents/upload-intents ---------->|-- Sinh Presigned Upload URL ------------>|
          |<-- 200 OK (uploadUrl, intentId) ----|                                         |
          |                                     |                                         |
   5. PUT bytes trực tiếp lên Storage ----------|---------------------------------------->|
          | (Dùng uploadUrl & requiredHeaders)  |                                         |
          |                                     |                                         |
   6. POST /documents (Confirm Intent) -------->|-- Xác nhận metadata tài liệu ---------->|
          |<-- 200 OK --------------------------|                                         |
          |                                     |                                         |
   7. POST /mentor-verification/submit --------->|-- Chuyển trạng thái PENDING_REVIEW ---->|
          |<-- 200 OK --------------------------|                                         |
          |                                     | (Admin kiểm tra & duyệt APPROVED)       |
   8. GET /api/me/onboarding-status ------------>|                                         |
          |<-- 200 OK { nextAction: "EXPLORE" } |                                         |
          |                                     |                                         |
   9. POST /api/me/mentor-services ------------>|-- Tạo gói dịch vụ mentoring ------------>|
          |<-- 200 OK (MentorServiceResponse) --|                                         |
          |                                     |                                         |
  10. Thiết lập Lịch rảnh & Sẵn sàng nhận Booking từ Mentee                               |
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Cấu trúc Hồ sơ Công khai 6 Phần (`Public Mentor Profile`)
API công khai `GET /api/mentors/{mentorUserId}` trả về đúng 6 section theo thứ tự hiển thị chuẩn trên Frontend:
1. `identity`: ID, tên hiển thị, avatar, headline, cờ `isVerified`, mốc `verifiedAt`.
2. `mentoring`: Bio, mô tả chuyên môn, danh sách `helpTopics`, ma trận mức hỗ trợ 1..4 (`foundation`, `outputReview`, `direction`).
3. `services`: Danh sách các gói dịch vụ đang hoạt động (`isActive = true`).
4. `evidence`: Bằng cấp học thuật (`education`), bảng điểm (`subjectResults`), dự án tiêu biểu (`featuredProjects`), thành tích (`achievements`), link Portfolio/Github, và bài viết chuyên môn (`authorityContent`).
5. `reputation`: Trạng thái đánh giá (`ratingState`: `NO_REVIEWS` vs `RATED`), điểm trung bình (`ratingAverage`), số lượt đánh giá (`reviewCount`), số buổi học hoàn tất (`completedSessions`).
6. `availability`: Cờ sẵn sàng (`isAvailable`), tạm ngưng (`suspendedUntil`), và cờ cho phép đặt lịch (`canRequestBooking`).

### 4.2 Cơ chế Upload Intent 2 Bước (Presigned Storage Upload)
- **Bước 1 (Request Intent)**: Client gọi API xin intent (Verification: `POST .../documents/upload-intents`, Resource: `POST .../resources/upload-url`) gửi `filename`, `contentType`, `sizeBytes`. Backend trả về `uploadIntentId`, `uploadUrl` (URL s3/r2 presigned) và `requiredHeaders` (hoặc `requiredContentType`).
- **Bước 2 (Direct Upload)**: Client dùng `fetch`/`axios` thực hiện request `PUT` trực tiếp file binary tới `uploadUrl` với đúng `headers`.
- **Bước 3 (Confirm Intent)**: Client gọi API xác nhận (Verification: `POST .../documents`, Resource: `POST .../resources`) truyền `uploadIntentId`. Backend kiểm tra file tồn tại trên storage và lưu metadata vào DB.

### 4.3 Khóa Lạc quan trong Quản lý Dịch vụ (`Optimistic Locking`)
- Mọi gói dịch vụ (`MentorService`) đều có trường `version` kiểu số nguyên.
- Khi gửi request cập nhật (`PUT /api/me/mentor-services/{id}`) hoặc đổi trạng thái (`PATCH .../active`), Frontend phải gửi đúng `version` hiện tại.
- Nếu `version` ở DB đã bị thay đổi bởi thao tác khác, Backend trả về lỗi `409 RESOURCE_CONFLICT`. Client phải tải lại danh sách dịch vụ để lấy `version` mới nhất.

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Rate Limit | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/me/mentor-profile` | Authenticated | Không giới hạn | Lấy hồ sơ chuyên môn mentor của caller | Màn hình Quản lý Hồ sơ Mentor |
| `PUT` | `/api/me/mentor-profile` | Authenticated | Không giới hạn | Tạo mới/Cập nhật hồ sơ chuyên môn mentor | Bấm "Lưu Hồ sơ Mentor" |
| `POST` | `/api/me/mentor-verification/request` | Authenticated | Không giới hạn | Khởi tạo/Khôi phục Wizard xác thực Mentor | Màn hình Xác thực Mentor |
| `GET` | `/api/me/mentor-verification` | Authenticated | Không giới hạn | Lấy chi tiết hồ sơ xác thực và danh sách tài liệu | Màn hình Wizard Verification |
| `POST` | `/api/me/mentor-verification/documents/upload-intents` | Authenticated | Không giới hạn | Xin Presigned Upload URL cho tài liệu xác thực | Khi chọn file đính kèm minh chứng |
| `POST` | `/api/me/mentor-verification/documents` | Authenticated | Không giới hạn | Xác nhận tài liệu xác thực sau khi upload S3/R2 | Ngay sau khi PUT file lên S3/R2 200 |
| `POST` | `/api/me/mentor-verification/submit` | Authenticated | Không giới hạn | Nộp hồ sơ xác thực cho Admin xét duyệt | Khi đã upload đủ minh chứng bắt buộc |
| `GET` | `/api/me/mentor-services` | Mentor Role | Không giới hạn | Lấy danh sách gói dịch vụ của Mentor | Trang Quản lý Dịch vụ Mentoring |
| `POST` | `/api/me/mentor-services` | Mentor Role | Không giới hạn | Tạo gói dịch vụ mới (gồm `durationMinutes`) | Form Tạo Gói Dịch vụ |
| `PUT` | `/api/me/mentor-services/{serviceId}` | Mentor Role | Không giới hạn | Cập nhật thông tin dịch vụ (`durationMinutes` immutable) | Form Sửa Gói Dịch vụ |
| `PATCH` | `/api/me/mentor-services/{serviceId}/active` | Mentor Role | Không giới hạn | Bật/tắt trạng thái hoạt động của dịch vụ | Nút Toggle Bật/Tắt Dịch vụ |
| `GET` | `/api/mentors/{mentorUserId}` | Public | Không giới hạn | Lấy thông tin công khai 6 phần của Mentor | Trang Chi tiết Mentor (Mentee view) |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `GET /api/mentors/{mentorUserId}`
- **Mục đích**: Lấy dữ liệu công khai chi tiết của Mentor phục vụ hiển thị cho Mentee trước khi đặt lịch.
- **Quyền truy cập**: Public (Không cần đăng nhập).
- **Response Body**:
```json
{
  "timestamp": "2026-08-04T09:00:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "identity": {
      "mentorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "displayName": "Nguyễn Văn A",
      "avatarUrl": "https://example.com/avatar.jpg",
      "headline": "Senior Spring Boot Engineer",
      "isVerified": true,
      "verifiedAt": "2026-07-20T08:30:00"
    },
    "mentoring": {
      "bio": "Kinh nghiệm 3 năm làm Microservices và AWS.",
      "expertiseDescription": "Hỗ trợ học viên làm đồ án tốt nghiệp và ôn phỏng vấn Java Backend.",
      "helpTopics": [],
      "supportLevels": { "foundation": 4, "outputReview": 3, "direction": 3 }
    },
    "services": [
      {
        "serviceId": "55555555-5555-5555-5555-555555555555",
        "title": "1-1 Mock Interview Java Backend",
        "description": "Luyện phỏng vấn 1-1 trực tiếp",
        "durationMinutes": 60,
        "isFree": false,
        "priceScoin": 100,
        "isActive": true,
        "maintainPostSessionChat": true
      }
    ],
    "evidence": {
      "education": {
        "campusName": "FPT University HCM",
        "programName": "Kỹ thuật phần mềm",
        "specializationName": "Web Development",
        "semester": 9,
        "alumni": true
      },
      "subjectResults": [],
      "featuredProjects": [],
      "achievements": [],
      "portfolioUrl": "https://mentor.dev",
      "githubUrl": "https://github.com/mentor",
      "authorityContent": { "publishedArticleCount": 2, "recentPublicArticles": [] }
    },
    "reputation": {
      "ratingState": "RATED",
      "ratingAverage": 4.9,
      "reviewCount": 12,
      "completedSessions": 15
    },
    "availability": {
      "isAvailable": true,
      "suspendedUntil": null,
      "canRequestBooking": true
    }
  }
}
```

---

### 6.2 `POST /api/me/mentor-verification/documents/upload-intents`
- **Mục đích**: Xin Presigned Upload URL để đẩy file minh chứng (Bằng cấp, Chứng chỉ, Bảng điểm) trực tiếp lên Cloud Storage.
- **Request Body**:
```json
{
  "filename": "bang_tot_nghiep_fpt.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 2048576
}
```
- **Response Body**:
```json
{
  "timestamp": "2026-08-04T09:01:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "uploadIntentId": "9fa85f64-5717-4562-b3fc-2c963f66afa9",
    "uploadUrl": "https://storage.skillswap.asia/verification-docs/temp-key?X-Amz-Algorithm=...",
    "expiresAt": "2026-08-04T09:16:00Z",
    "requiredHeaders": {
      "Content-Type": "application/pdf"
    }
  }
}
```

---

### 6.3 `POST /api/me/mentor-services`
- **Mục đích**: Tạo gói dịch vụ mentoring mới.
- **Request Body**:
```json
{
  "title": "Tư vấn Đồ án Tốt nghiệp SE",
  "description": "Hướng dẫn thiết kế kiến trúc phần mềm và code review",
  "expectedOutcome": "Hoàn thiện sơ đồ kiến trúc và tối ưu code base",
  "durationMinutes": 60,
  "isFree": false,
  "priceScoin": 150,
  "deliveryMode": "ONE_TO_ONE",
  "maintainPostSessionChat": true,
  "helpTopicIds": ["44444444-4444-4444-4444-444444444444"]
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Nộp Minh chứng & Xin Xác thực Mentor 2 Bước

```
Frontend (Verification Wizard)          Backend API                             Cloud Storage (S3/R2)
        |                                   |                                             |
   1. Chọn file PDF/Image minh chứng        |                                             |
        |                                   |                                             |
   2. POST /documents/upload-intents ------>|-- Kiểm tra định dạng & sizeBytes (<15MB) -->|
        | (filename, contentType, size)     |-- Sinh Presigned URL tạm thời (TTL 15m) ----|
        |<-- Trả uploadUrl & intentId ------|                                             |
        |                                   |                                             |
   3. PUT binary file ------------------------------------------------------------------->|
        | (Gửi đúng requiredHeaders)        |<-- S3/R2 xác nhận nhận file 200 OK ---------|
        |                                   |                                             |
   4. POST /documents (Confirm) ----------->|-- Kiểm tra file thực tế trên Storage ------->|
        | (uploadIntentId, documentType)    |-- Lưu metadata tài liệu vào DB ------------>|
        |<-- 200 OK (Cập nhật checklist) ---|                                             |
        |                                   |                                             |
   5. Bấm "Nộp Hồ sơ Xác thực"              |                                             |
   6. POST /mentor-verification/submit ---->|-- Kiểm tra đủ checklist bắt buộc ----------->|
        |<-- 200 OK (Status: PENDING_REVIEW)|-- Gửi Notification báo cho Admin ----------->|
```

---

## 8. State Machine (Ma trận Trạng thái Mentor Profile, Verification & Service)

### 8.1 Vòng đời Xác thực Mentor (`MentorVerificationStatus`)

```
             +-----------------------+
             |      NOT_STARTED      | (Chưa mở Wizard xác thực)
             +-----------------------+
                         |
           POST /mentor-verification/request
                         |
                         v
             +-----------------------+
             |         DRAFT         | (Đang upload tài liệu minh chứng)
             +-----------------------+
                         |
            POST /verification/submit
                         |
                         v
             +-----------------------+
             |    PENDING_REVIEW     | (Đang chờ Admin kiểm tra)
             +-----------------------+
              /          |          \
     Admin Approve   Admin Reject   Admin Require Revision
            /            |            \
           v             v             v
    +------------+ +------------+ +-----------------------+
    |  APPROVED  | |  REJECTED  | |    NEEDS_REVISION     |
    +------------+ +------------+ +-----------------------+
                                              |
                                     Sửa minh chứng & Nộp lại
                                              |
                                              v
                                   (Quay về PENDING_REVIEW)
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `BAD_REQUEST` | File upload vượt quá 15 MiB, sai định dạng (chỉ nhận PDF, PNG, JPG), hoặc chưa điền đủ trường bắt buộc. | Hiển thị thông báo lỗi file/form, giữ nguyên wizard state. |
| `400 BAD_REQUEST` | `INVALID_INPUT` | Thử thay đổi `durationMinutes` của gói dịch vụ đã tạo (trường này là Immutable). | Khóa ô input durationMinutes ở màn hình Edit Service. |
| `401 UNAUTHENTICATED` | `UNAUTHENTICATED` | Chưa đăng nhập hoặc Access Token hết hạn. | Chuyển luồng Refresh Token. |
| `403 FORBIDDEN` | `ACCESS_DENIED` | Người dùng chưa có vai trò `MENTOR` hoặc chưa được duyệt `APPROVED` thử tạo/bật dịch vụ. | Bật Modal thông báo "Bạn cần hoàn tất xác thực Mentor trước khi tạo dịch vụ". |
| `409 RESOURCE_CONFLICT` | `RESOURCE_CONFLICT` | Sai cờ khóa lạc quan `version` khi sửa dịch vụ hoặc chính sách đặt lịch. | Tải lại dữ liệu mới nhất từ server, giữ bản nháp của user và hỏi xác nhận đè. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Phân định Rõ ràng Giữa Role và Verification**:
   - Vai trò `MENTOR` trong danh sách `roles` của user quyết định quyền truy cập các menu quản trị dịch vụ.
   - Trạng thái `mentorVerificationStatus == "APPROVED"` và `verifiedAt != null` quyết định việc Mentor có được xuất hiện trên danh sách tìm kiếm công khai (Discovery) và nhận Booking hay không.
2. **Bảo mật File Minh chứng & Tài liệu Học tập**:
   - Nghiêm cấm upload file trực tiếp qua server backend ở luồng sản xuất. Bắt buộc qua cơ chế Upload Intent 2 bước.
   - Client tuyệt đối không tự tạo hoặc gửi `objectKey` hay storage bucket URL.
3. **Hiển thị Rating Minh bạch (`ratingState`)**:
   - Nếu Mentor chưa có đánh giá nào, Backend trả `ratingState = "NO_REVIEWS"` và `ratingAverage = null`. Frontend **nghiêm cấm** tự hiển thị điểm 5.0 giả tạo.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Kiểm tra `ratingState`: Nếu là `NO_REVIEWS`, render nhãn *"Mới tham gia, chưa có đánh giá"*. Nếu là `RATED`, render điểm `ratingAverage` cùng số sao.
- Tải danh mục `help-topics` và `mentor-profile-options` khi khởi tạo form cấu hình hồ sơ Mentor.
- Luôn gửi kèm `version` (hoặc `expectedVersion`) trong các request cập nhật dịch vụ hoặc chính sách đặt lịch.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** cho phép sửa trường `durationMinutes` khi edit gói dịch vụ đã tồn tại. Nếu muốn đổi thời lượng, yêu cầu Mentor tạo gói dịch vụ mới.
- **KHÔNG ĐƯỢC** dùng nút `DELETE` để xóa dịch vụ. Bắt buộc tắt dịch vụ bằng API `PATCH /api/me/mentor-services/{id}/active` (truyền `isActive = false`).
- **KHÔNG ĐƯỢC** dùng cờ `isAvailable` trên profile để thay thế cho danh sách khung giờ rảnh (`Availability Slots`).

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Upload Intent Hết Hạn (TTL 15 phút)**:
   - Nếu người dùng ngâm trang quá 15 phút trước khi bấm tải file lên S3/R2, request `PUT` binary sẽ bị S3/R2 từ chối (HTTP 403 Expired). Frontend phải bắt lỗi và tự động xin `uploadIntentId` mới mà không bắt user chọn lại file.
2. **Gợi ý Nhu cầu Chat Sau Buổi học (`maintainPostSessionChat`)**:
   - Khi Mentor đổi cờ `maintainPostSessionChat`, thay đổi này chỉ áp dụng cho các Booking đặt mới sau đó. Các Booking đã đặt trước đó vẫn giữ nguyên chính sách tại thời điểm đặt lịch.
3. **Cung cấp Lịch rảnh Nhóm (`Group Session Supply`)**:
   - Dịch vụ Group Session yêu cầu `deliveryMode = GROUP_SESSION`. Khi xuất bản một Group Session, tiêu đề, mô tả và giá SCoin sẽ bị đóng băng (Frozen).

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Academic Service**: Cung cấp bằng cấp/học kỳ của sinh viên phục vụ hiển thị phần `evidence.education`.
- **Booking Service**: Đọc gói dịch vụ `MentorService` và khung giờ rảnh `AvailabilitySlot` để khởi tạo đơn đặt lịch.
- **Chat & Conversation Service**: Tự động mở phòng chat nhóm hoặc phòng chat 1-1 dựa trên cờ `maintainPostSessionChat` và thời gian kết thúc buổi học.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Màn hình Quản lý Hồ sơ Chuyên môn Mentor (`/mentor/profile/edit`)
- **React Components**: `MentorProfileEditPage.tsx`, `SupportLevelSelector.tsx`, `HelpTopicTagSelect.tsx`
- **APIs Triggered**:
  1. `GET /api/catalog/help-topics` & `GET /api/catalog/mentor-profile-options` (Preload)
  2. `GET /api/me/mentor-profile` (Pre-fill dữ liệu)
  3. `PUT /api/me/mentor-profile` (Lưu thông tin)
- **Expected Behavior**: Render các mức hỗ trợ 1..4 từ options API. Sau khi lưu 200: Invalidate cache `['mentor-profile', 'me']` và thông báo thành công.

#### B. Màn hình Wizard Xác thực Mentor (`/mentor/verification`)
- **React Components**: `VerificationWizardPage.tsx`, `DocumentUploader.tsx`, `VerificationTimelineView.tsx`
- **APIs Triggered**:
  1. `GET /api/me/mentor-verification` (Lấy trạng thái hiện tại)
  2. `POST /api/me/mentor-verification/documents/upload-intents` (Khi chọn file)
  3. Direct `PUT` binary to S3/R2 Presigned URL
  4. `POST /api/me/mentor-verification/documents` (Xác nhận sau upload)
  5. `POST /api/me/mentor-verification/submit` (Nộp hồ sơ)
- **Expected Behavior**: Hiển thị danh sách checklist tài liệu đã upload. Nút "Nộp xét duyệt" chỉ sáng khi đã upload đủ minh chứng bắt buộc.

#### C. Màn hình Quản lý Gói Dịch vụ Mentoring (`/mentor/services`)
- **React Components**: `MentorServiceListPage.tsx`, `CreateServiceModal.tsx`, `EditServiceModal.tsx`
- **APIs Triggered**:
  1. `GET /api/me/mentor-services` (Lấy danh sách gói dịch vụ)
  2. `POST /api/me/mentor-services` (Tạo gói mới)
  3. `PUT /api/me/mentor-services/{id}` (Sửa thông tin - `durationMinutes` bị disable)
  4. `PATCH /api/me/mentor-services/{id}/active` (Bật/Tắt dịch vụ)
- **Expected Behavior**: Phân tách 2 tab Dịch vụ Đang bật (`Active`) và Đã tắt (`Inactive`). Khi bấm Toggle Active, gửi kèm `version` hiện tại.

---

### 14.2 Frontend Mentor State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |   PROFILE_INCOMPLETE  | (Chưa điền đủ Mentor Profile)
                       +-----------------------+
                                   |
                         PUT /me/mentor-profile
                                   |
                                   v
                       +-----------------------+
                       |   VERIFICATION_DRAFT  | (Chưa nộp minh chứng xác thực)
                       +-----------------------+
                                   |
                       POST /verification/submit
                                   |
                                   v
                       +-----------------------+
                       |    PENDING_REVIEW     | (Đang chờ Admin duyệt)
                       +-----------------------+
                                   |
                            Admin Approved
                                   |
                                   v
                       +-----------------------+
                       |    MENTOR_READY       | (Đã duyệt, được tạo dịch vụ & mở lịch)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | App Startup | Open Profile Form | Select File Verification | Save Service | Toggle Active | User Action |
| --- | --- | --- | --- | --- | --- | --- |
| `GET /api/catalog/help-topics` | ❌ KHÔNG | ✅ CÓ (Nếu chưa Cache) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /api/me/mentor-profile` | ❌ KHÔNG | ✅ CÓ (Pre-fill form) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `PUT /api/me/mentor-profile` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ Khi bấm "Lưu Hồ sơ" |
| `POST .../documents/upload-intents` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (Ngay khi chọn file) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `POST /api/me/mentor-services` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ✅ Khi bấm "Tạo Gói Dịch vụ" |
| `PATCH .../services/{id}/active` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ✅ Khi bấm công tắc Toggle Active |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Vượt Dung lượng File Upload (`HTTP 400`)
- **UI Component**: File Dropzone trong `DocumentUploader.tsx`.
- **Visual State**: Viền đỏ nhấp nháy + Thông báo dưới khung dropzone.
- **Error Message**: *"Kích thước tập tin vượt quá giới hạn cho phép (Tối đa 15 MiB). Vui lòng chọn tập tin nhỏ hơn."*

#### B. Lỗi Xung đột Khóa Lạc quan `version` (`HTTP 409`)
- **UI Component**: Modal Chỉnh sửa Dịch vụ (`EditServiceModal.tsx`).
- **Visual State**: Hiển thị Modal Cảnh báo Xung đột Dữ liệu.
- **Action Required**: *"Thông tin dịch vụ đã được cập nhật từ một phiên làm việc khác. Vui lòng tải lại dữ liệu mới nhất."* (Cung cấp nút "Tải lại Dữ liệu").

#### C. Lỗi Chưa Được Duyệt Mentor (`HTTP 403`)
- **UI Component**: Nút "Tạo Gói Dịch vụ Mới".
- **Visual State**: Nút bị Disable + Tooltip giải thích.
- **Tooltip Message**: *"Bạn cần hoàn tất quy trình xác thực Mentor và được Admin duyệt trước khi tạo gói dịch vụ."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['catalog', 'help-topics']` | 24 giờ (`24 * 60 * 60 * 1000`) | 48 giờ | `false` | Thay đổi phiên bản ứng dụng |
| `['mentor-profile', 'me']` | 10 phút | 60 phút | `false` | `PUT /api/me/mentor-profile` thành công |
| `['mentor-verification', 'me']` | 0 ms | 10 phút | `true` | `POST /verification/submit`, `POST /documents` thành công |
| `['mentor-services', 'me']` | 5 phút | 30 phút | `false` | `POST /services`, `PUT /services/{id}`, `PATCH /active` thành công |
| `['public-mentor', mentorId]` | 5 phút | 30 phút | `false` | Đặt lịch thành công, Đánh giá mới được duyệt |
