# Blog Service (`10-blog.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Bài viết Chuyên môn và Thư viện Tri thức Mentor (Blog Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Blog Service** quản lý toàn bộ hệ thống bài viết chuyên môn, kiến thức chuẩn SEO, tạo uy tín cho Mentor, chuyển đổi người đọc thành học viên đặt lịch (`Conversion Funnel`), và Thư viện Tri thức Độc quyền dành riêng cho học viên đã đặt lịch (`Booked Members Premium Library`).

### Trách nhiệm chính của Service
- **Phân quyền Đọc Theo Quyền Hạn (`Audience Visibility Engine`)**: Quản lý 3 cấp độ quyền đọc độc lập: `PUBLIC` (mọi người), `AUTHENTICATED` (chỉ user đã đăng nhập), và `BOOKED_MEMBERS` (chỉ học viên đã mua gói dịch vụ `MentorService` gắn kèm).
- **Quy trình Soạn thảo & Xuất bản Bài viết Mentor (`Mentor Authoring Flow`)**: Cung cấp bộ công cụ viết bài định dạng Markdown cho Mentor (`/api/me/blog/posts`), quản lý hình ảnh minh họa bảo mật via Asset Upload Intent (`coverAssetId`), và kiểm soát xung đột phiên bản bằng khóa lạc quan `expectedVersion`.
- **Chuyển đổi Đặt lịch Từ Bài viết (`Author Conversion Telemetry`)**: Mỗi bài viết của Mentor trả về thông tin `authorConversion` (gợi ý gói dịch vụ, giá tiền, và nút CTA "Đặt lịch ngay") kèm hệ thống đo lường hiệu quả (`view`, `cta-click`, `booking-started`).
- **Thư viện Kiến thức Độc quyền (`Booked Members Library`)**: API `GET /api/me/blog/library` trả về danh sách bài viết chuyên sâu mở khóa tự động ngay khi Mentee đặt lịch thành công với Mentor.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Phễu Chuyển đổi Uy tín (Trust & Conversion Funnel)**:
   - Bài viết chuyên môn của Mentor -> Xây dựng uy tín học thuật -> Người đọc bấm CTA "Đặt lịch" -> Tạo đơn Booking -> Mở khóa Thư viện Tri thức Chuyên sâu.
2. **Bảo vệ Tri thức Độc quyền (`BOOKED_MEMBERS`)**: Các bài viết nâng cao mang nhãn `BOOKED_MEMBERS` bị ẩn hoàn toàn khỏi danh sách tìm kiếm, newsfeed, trending và related public. Chỉ người dùng có đơn đặt lịch thành công (`CONFIRMED`) với gói dịch vụ liên kết mới có quyền đọc.
3. **An toàn Biên tập Bằng Khóa Phiên bản (`Optimistic Locking`)**: Mọi thao tác cập nhật, xuất bản (`publish`), hoặc lưu trữ (`archive`) đều yêu cầu gửi trường `expectedVersion`. Tránh trường hợp Mentor mở 2 tab trình duyệt ghi đè đè dữ liệu của nhau.
4. **Đo lường Hiệu quả Nội dung Bất đồng bộ (Best-Effort Telemetry)**: Các API ghi nhận lượt xem (`recordView`), bấm CTA (`recordAuthorCtaClick`) chạy dưới dạng non-blocking. Nếu mạng chập chờn, hành vi đọc bài viết của người dùng vẫn không bị gián đoạn.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                  LUỒNG ĐỌC BÀI VIẾT VÀ CHUYỂN ĐỔI ĐẶT LỊCH                             |
+-------------------------------------------------------------------------------------------------------+

  Reader / Student (Frontend)           Backend (SkillSwap API)                Mentor / Author (Frontend)
          |                                     |                                         |
   1. Đọc Bài viết Public qua Slug              |                                         |
          |-- GET /api/blog/posts/{slug} ------>|-- Trả BlogPostReaderDetailResponse ---->|
          |<-- 200 OK (Nội dung Markdown, CTA) -|                                         |
          |                                     |                                         |
   2. Ghi nhận Telemetry Lượt xem               |                                         |
          |-- POST /posts/{postId}/view ------->|-- Ghi nhận lượt xem không trùng lặp --->|
          |                                     |                                         |
   3. Bấm Nút "Đặt lịch với Mentor này"         |                                         |
          |-- POST /posts/{id}/author-cta-click |                                         |
          |-- POST /posts/{id}/booking-started >|-- Ghi nhận phễu chuyển đổi ----------->|
          |-- Chuyển sang Modal Booking --------|                                         |
          |                                     |                                         |
   4. [Sau khi Thanh toán Booking thành công]   | 5. Mentor đăng bài viết Độc quyền       |
          |                                     |<-- POST /me/blog/posts -----------------|
          |                                     |    (visibility: BOOKED_MEMBERS)         |
   6. Mở Thư viện Tri thức (/me/blog/library)   |                                         |
          |-- GET /me/blog/library?serviceId= ->|-- Mở khóa Bài viết Chuyên sâu ---------->|
          |<-- 200 OK (Xem Bài viết Độc quyền)--|                                         |
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Ba Cấp độ Quyền Đọc Bài viết (`BlogPostVisibility`)
- `PUBLIC`: Hiển thị công khai cho tất cả mọi người (cả khách ẩn danh và người dùng đã đăng nhập).
- `AUTHENTICATED`: Chỉ hiển thị cho người dùng đã đăng nhập vào hệ thống (ẩn khỏi Google Search / Khách chưa login).
- `BOOKED_MEMBERS`: Bài viết độc quyền. Ẩn hoàn toàn khỏi tìm kiếm, danh mục và tin tức công khai. Chỉ hiển thị trong Thư viện Tri thức cá nhân `/api/me/blog/library` của Mentee đã đặt lịch thành công gói dịch vụ `entitledServiceIds`.

### 4.2 Trạng thái Biên tập Bài viết (`BlogPostStatus`)
- `DRAFT`: Bản nháp cá nhân của Mentor / Admin.
- `PUBLISHED`: Đã xuất bản và sẵn sàng phục vụ người đọc theo cấp độ visibility.
- `ARCHIVED`: Đã lưu trữ. Bài viết bị ẩn khỏi mọi giao diện khám phá của người đọc.

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- |
| `GET` | `/api/blog/posts` | Public | Lấy danh sách bài viết public (phân trang Cursor, lọc category/tag/keyword) | Màn hình Trang chủ Blog |
| `GET` | `/api/blog/posts/{slug}` | Public | Lấy chi tiết bài viết theo đường dẫn Slug (chuẩn Markdown & SEO) | Màn hình Đọc bài viết |
| `GET` | `/api/blog/featured` | Public | Lấy danh sách bài viết nổi bật do Admin chọn | Carousel bài viết nổi bật |
| `GET` | `/api/blog/trending` | Public | Lấy danh sách bài viết xu hướng (đọc nhiều) | Sidebar Bài viết HOT |
| `GET` | `/api/blog/posts/{slug}/related` | Public | Lấy danh sách bài viết liên quan theo chủ đề | Cuối bài viết đang đọc |
| `GET` | `/api/me/blog/library` | Authenticated | Lấy danh sách bài viết độc quyền dành cho học viên đã mua gói | Trang Thư viện Tri thức của tôi |
| `GET` | `/api/me/blog/bookmarks` | Authenticated | Lấy danh sách bài viết đã lưu cá nhân | Trang Bài viết Đã lưu |
| `GET` | `/api/me/blog/feed` | Authenticated | Lấy tin tức bài viết từ các Mentor/Chủ đề đang theo dõi | Trang Feed tin tức cá nhân |
| `GET` | `/api/me/blog/posts` | Mentor Role | Lấy danh sách bài viết soạn thảo của Mentor | Dashboard Quản lý Bài viết Mentor |
| `POST` | `/api/me/blog/posts` | Mentor Role | Tạo bản nháp bài viết mới | Bấm "Viết bài mới" |
| `PUT` | `/api/me/blog/posts/{postId}` | Mentor Role | Cập nhật nội dung bản nháp (yêu cầu `expectedVersion`) | Bấm "Lưu bản nháp" / Auto-save |
| `POST` | `/api/me/blog/posts/{postId}/publish` | Mentor Role | Xuất bản bài viết (yêu cầu `expectedVersion`) | Bấm "Xuất bản bài viết" |
| `POST` | `/api/me/blog/posts/{postId}/archive` | Mentor Role | Lưu trữ bài viết (yêu cầu `expectedVersion`) | Bấm "Gỡ bài viết" |
| `PUT/DELETE` | `/api/blog/posts/{postId}/like` | Authenticated | Thả / Bỏ Thích bài viết (Idempotent) | Bấm nút Thích |
| `PUT/DELETE` | `/api/blog/posts/{postId}/bookmark` | Authenticated | Lưu / Bỏ Lưu bài viết vào thư viện cá nhân | Bấm nút Bookmark |
| `PUT/DELETE` | `/api/blog/mentors/{mentorId}/follow` | Authenticated | Theo dõi / Bỏ theo dõi bài viết của Mentor | Bấm Follow Mentor |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `POST /api/me/blog/posts`

#### Purpose
Mentor tạo bản nháp bài viết mới.

#### Request Body (`MentorBlogPostCreateRequest`)
```json
{
  "title": "Hướng dẫn Xây dựng Microservices Thực chiến với Spring Boot 3",
  "excerpt": "Tổng hợp kinh nghiệm thiết kế kiến trúc Microservices và xử lý transaction bất đồng bộ.",
  "contentMarkdown": "# Khái niệm Cốt lõi\n\nTrong kiến trúc Microservices...",
  "coverAssetId": "018f3abf-0a22-7152-9748-6cf000c47b6e",
  "visibility": "PUBLIC",
  "categoryIds": ["018f3abf-0a22-7152-9748-6cf000c47b6f"],
  "tagIds": ["018f3abf-0a22-7152-9748-6cf000c47b70"],
  "entitledServiceIds": []
}
```

#### Response Body (`MentorBlogPostDetailResponse`)
```json
{
  "timestamp": "2026-08-04T10:10:00Z",
  "status": 201,
  "code": "SUCCESS",
  "message": "Created",
  "data": {
    "postId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "title": "Hướng dẫn Xây dựng Microservices Thực chiến với Spring Boot 3",
    "slug": "huong-dan-xay-dung-microservices-thuc-chien-voi-spring-boot-3",
    "excerpt": "Tổng hợp kinh nghiệm thiết kế kiến trúc Microservices...",
    "contentMarkdown": "# Khái niệm Cốt lõi\n\nTrong kiến trúc Microservices...",
    "status": "DRAFT",
    "visibility": "PUBLIC",
    "version": 0,
    "createdAt": "2026-08-04T10:10:00Z",
    "updatedAt": "2026-08-04T10:10:00Z"
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Đăng bài Độc quyền & Mở khóa Thư viện Cho Học viên

```
Mentor (Author)                      Backend API                             Mentee (Student)
       |                                  |                                          |
  1. Tạo bài viết độc quyền              |                                          |
     POST /api/me/blog/posts ------------>|                                          |
     { visibility: "BOOKED_MEMBERS",      |                                          |
       entitledServiceIds: [serviceA] }   |                                          |
  2. Bấm Xuất bản                         |                                          |
     POST /me/blog/posts/{id}/publish --->|-- Lưu status = PUBLISHED -------------->|
       |                                  |                                          |
       |                                  | 3. Mentee hoàn tất đặt lịch gói serviceA |
       |                                  |-- Booking -> CONFIRMED ----------------->|
       |                                  |                                          |
       |                                  | 4. Mentee vào Thư viện cá nhân           |
       |                                  |<-- GET /api/me/blog/library?serviceId= --|
       |                                  |-- Trả bài viết độc quyền đã mở khóa ---->|
```

---

## 8. State Machine (Ma trận Trạng thái Bài viết, Phân quyền Quyền đọc & Engagement)

### 8.1 Ma trận Trạng thái Bài viết (`BlogPostStatus`)

```
             +-----------------------+
             |         DRAFT         | (Bản nháp đang soạn thảo)
             +-----------------------+
                         |
           POST /me/blog/posts/{id}/publish
                         |
                         v
             +-----------------------+
             |       PUBLISHED       | (Bài viết đã xuất bản công khai/độc quyền)
             +-----------------------+
                         |
           POST /me/blog/posts/{id}/archive
                         |
                         v
             +-----------------------+
             |       ARCHIVED        | (Bài viết gỡ xuống / Lưu trữ)
             +-----------------------+
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `BLOG_INVALID_ENTITLED_SERVICE` | Tạo bài viết `BOOKED_MEMBERS` nhưng không truyền gói dịch vụ `entitledServiceIds`. | Bắt buộc chọn ít nhất 1 gói dịch vụ đính kèm trên form. |
| `404 NOT_FOUND` | `NOT_FOUND` | Bài viết không tồn tại, bị ẩn, hoặc bị gỡ bỏ (Backend trả `404` chung cho bảo mật). | Hiển thị màn hình Lỗi 404 "Không tìm thấy bài viết". |
| `409 RESOURCE_CONFLICT` | `BLOG_POST_VERSION_CONFLICT` | Sai trường `expectedVersion` khi Mentor lưu/xuất bản bài viết. | Giữ nguyên văn bản trên editor, tải lại thông tin bài viết và yêu cầu lưu lại. |
| `409 RESOURCE_CONFLICT` | `BLOG_FOLLOW_LIMIT_REACHED` | Đã theo dõi vượt quá số lượng tối đa (20 Mentor / 20 Danh mục). | Báo lỗi, yêu cầu bỏ theo dõi một mục cũ trước khi thêm mới. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Bảo vệ Nội dung Độc quyền ở Cấp độ Database**: Bài viết mang nhãn `BOOKED_MEMBERS` bắt buộc phải qua khâu kiểm tra quyền sở hữu đơn đặt lịch `CONFIRMED`. Trả về `404 NOT_FOUND` nếu người dùng cố tình nhập slug bài viết độc quyền mà chưa mua gói.
2. **Không Truyền trực tiếp Object Key Storage**: Khi tải ảnh đại diện bài viết (`coverAssetId`), Frontend xin upload intent và truyền `assetId`. Không bao giờ truyền chuỗi đường dẫn lưu trữ S3/R2 thô.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Sử dụng trình soạn thảo Markdown chuẩn (như SimpleMDE / ByteMD) để tạo nội dung `contentMarkdown`.
- Gửi trường `expectedVersion` chính xác trong mỗi lệnh chỉnh sửa hoặc thay đổi trạng thái bài viết của Mentor.
- Thực hiện các request đo lường telemetry (`view`, `author-cta-click`, `booking-started`) dưới dạng bất đồng bộ non-blocking (`fire-and-forget`).

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** lưu trữ định dạng HTML thô hoặc JSON thô của editor vào Database.
- **KHÔNG ĐƯỢC** đưa các bài viết `BOOKED_MEMBERS` vào giao diện khám phá tin tức công khai.
- **KHÔNG ĐƯỢC** tự tăng giá trị `version` ở Client; bắt buộc phải lấy từ response của Server.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Gói Dịch vụ Của Mentor Bị Tắt (`Service Deactivated`)**:
   - Khi Mentor tắt hoặc ngưng bán một gói dịch vụ, các học viên đã mua gói đó trong quá khứ vẫn tiếp tục có quyền đọc các bài viết `BOOKED_MEMBERS` thuộc gói đó trong Thư viện Tri thức cá nhân.
2. **Xung đột Phiên bản Biên tập (`Version Conflict`)**:
   - Khi nhận lỗi `409 BLOG_POST_VERSION_CONFLICT`, Frontend tuyệt đối không được xóa nội dung người dùng vừa gõ. Phải giữ nguyên văn bản nháp ở Client, hiển thị thông báo cảnh báo và hỗ trợ nút "So sánh & Lưu đè".

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Mentor Service**: Kết nối gói dịch vụ `MentorService` để phân quyền bài viết độc quyền `BOOKED_MEMBERS`.
- **Booking Service**: Chuyển hướng người đọc từ nút CTA bài viết sang luồng Đặt lịch và mở khóa Thư viện Tri thức.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Trang Đọc Bài Viết (`BlogPostDetailPage.tsx`)
- **React Components**: `BlogPostDetailPage.tsx`, `MarkdownRenderer.tsx`, `MentorAuthorCtaBox.tsx`, `RelatedPostsSlider.tsx`
- **APIs Triggered**:
  1. `GET /api/blog/posts/{slug}` (Mount trang)
  2. `POST /api/blog/posts/{postId}/view` (Ghi nhận lượt xem)
  3. `GET /api/blog/posts/{slug}/related` (Tải bài viết liên quan)
- **Expected Behavior**: Render nội dung Markdown đẹp mắt. Hiển thị thẻ CTA "Đặt lịch với Mentor" nổi bật ở cuối bài viết.

#### B. Trình Soạn Thảo Bài Viết Mentor (`MentorBlogEditorPage.tsx`)
- **React Components**: `MentorBlogEditorPage.tsx`, `MarkdownEditor.tsx`, `CoverImageUploader.tsx`, `VisibilitySelector.tsx`
- **APIs Triggered**:
  1. `POST /api/me/blog/posts` (Khi tạo bản nháp)
  2. `PUT /api/me/blog/posts/{postId}` (Khi bấm Lưu nháp, gửi `expectedVersion`)
  3. `POST /api/me/blog/posts/{postId}/publish` (Khi bấm Xuất bản)
- **Expected Behavior**: Quản lý `version` nghiêm ngặt. Khi xuất bản thành công: Chuyển hướng về Dashboard quản lý bài viết.

#### C. Màn hình Thư viện Tri thức Học viên (`BookedLibraryPage.tsx`)
- **React Components**: `BookedLibraryPage.tsx`, `ServiceFilterTabs.tsx`, `PremiumArticleCard.tsx`
- **APIs Triggered**:
  1. `GET /api/me/blog/library?serviceId=` (Tải danh sách bài viết độc quyền đã mở khóa)
- **Expected Behavior**: Hiển thị danh sách bài viết chuyên sâu mở khóa theo từng gói dịch vụ đã mua.

---

### 14.2 Frontend Blog State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |    FETCHING_ARTICLE   | (Tải chi tiết bài viết theo Slug)
                       +-----------------------+
                                   |
                          Tải bài viết 200 OK
                                   |
                                   v
                       +-----------------------+
                       |    READING_ARTICLE    | (Đọc bài viết & Bắn Telemetry View)
                       +-----------------------+
                                   |
                     Bấm CTA "Đặt lịch với Mentor"
                                   |
                                   v
                       +-----------------------+
                       |  REDIRECT_TO_BOOKING  | (Mở Modal Đặt lịch Mentoring)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Blog Post | Click Author CTA | Save Draft | Publish Post | User Action |
| --- | --- | --- | --- | --- | --- |
| `GET /api/blog/posts/{slug}` | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `POST .../view` | ✅ CÓ (Bất đồng bộ) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `POST .../author-cta-click` | ❌ KHÔNG | ✅ CÓ (Non-blocking) | ❌ KHÔNG | ❌ KHÔNG | ✅ Bấm "Đặt lịch ngay" |
| `PUT /me/blog/posts/{id}` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (`expectedVersion`) | ❌ KHÔNG | ✅ Bấm "Lưu bản nháp" |
| `POST .../publish` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (`expectedVersion`) | ✅ Bấm "Xuất bản" |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Xung đột Phiên bản Trình soạn thảo (`HTTP 409`)
- **UI Component**: Modal Xử lý Xung đột Phiên bản (`VersionConflictModal.tsx`).
- **Visual State**: Cảnh báo màu cam trên Trình soạn thảo.
- **Message**: *"Bài viết đã được chỉnh sửa ở một phiên khác. Bạn có muốn tải lại phiên bản mới nhất từ máy chủ không?"*
- **Action**: Nút "Tải lại dữ liệu" và "Giữ nguyên bản nháp hiện tại".

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['blog', 'detail', slug]` | 5 phút | 30 phút | `true` | Khi tác giả chỉnh sửa bài viết |
| `['blog', 'posts', filters]` | 2 phút | 15 phút | `true` | Thay đổi bộ lọc hoặc danh mục |
| `['blog', 'library']` | 5 phút | 30 phút | `true` | Đặt lịch mới thành công |
| `['blog', 'mentor-drafts']` | 0 ms | 10 phút | `false` | Tạo/Sửa/Lưu trữ bài viết |
