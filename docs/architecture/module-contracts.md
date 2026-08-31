# Hợp đồng kiến trúc Spring Modulith — Danh mục Port và Module Contracts

Tài liệu này xác định ranh giới kiến trúc (architectural boundaries), hợp đồng API công khai (`NamedInterface("api")`) tại `modules.<owner>.port`, các consumer hợp lệ, mô hình tương tác (command/query/event), kỳ vọng transaction và ràng buộc DTO/snapshot cho 13 business module của SkillSwap.

---

## Nguyên tắc bất biến cho API Port

1. **NamedInterface duy nhất**: Chỉ `modules.<owner>.port` được phép mang `@NamedInterface("api")`.
2. **Loại dữ liệu trong hợp đồng**:
   - Chỉ sử dụng kiểu nguyên thủy (primitive), kiểu JDK chuẩn (`UUID`, `Instant`, `String`, `BigDecimal`, v.v.), immutable record, contract enum, và `PageResponse` từ `shared`.
   - Tuyệt đối không expose hay nhận JPA Entity, JPA Proxy, Spring Web DTO (`*Request`, `*Response` của controller), Spring Page/Pageable, hay Lombok Builder.
3. **Mô hình phụ thuộc**:
   - `consumer -> owner.port` (Synchronous command/query qua interface và immutable record).
   - `consumer -> owner.event` (Asynchronous event sau commit hoặc qua outbox).
   - `consumer -> shared` (Shared value objects, exceptions, time, pagination).
   - **Cấm**: `consumer -> owner.domain / repository / service / controller / dto`.

---

## Ma trận Hợp đồng theo từng Module

### 1. Module `identity`
- **Owner**: `identity`
- **Mục đích**: Quản lý tài khoản người dùng, hồ sơ sinh viên, thông tin cơ sở/ngành học (academic), trạng thái khóa tài khoản và đồng bộ Google Calendar.
- **Các Port công khai**:
  - `UserQueryPort`: Tra cứu thông tin cơ bản người dùng (active, role, email, status) bằng scalar `UUID` và projection record `UserSummaryRecord`.
  - `UserLockPort`: Khóa/mở khóa tài khoản người dùng từ moderation.
  - `AcademicEligibilityQuery`: Kiểm tra tính hợp lệ sinh viên và điều kiện học tập.
  - `GoogleCalendarConnectionPort`: Kiểm tra trạng thái kết nối Google Calendar và kích hoạt sync job.
  - `UserAdminPort`: Tra cứu danh sách và chi tiết người dùng phục vụ Admin Dashboard.
  - `MentorCalendarEligibilityPort`: Xác nhận điều kiện tích hợp lịch cho mentor.
- **Consumer hợp lệ**: `booking`, `mentor`, `blog`, `forum`, `admin`, `payment`, `chat`.
- **Kỳ vọng Transaction**:
  - Query ports: `@Transactional(readOnly = true)`.
  - Command ports (`UserLockPort`): Độc lập, không kéo dài transaction của caller.

---

### 2. Module `mentor`
- **Owner**: `mentor`
- **Mục đích**: Quản lý hồ sơ mentor, dịch vụ giảng dạy (mentor services), quy trình xác thực (verification), chính sách vi phạm/kỷ luật, và siêu dữ liệu chia sẻ SEO.
- **Các Port công khai**:
  - `MentorQueryPort`: Tra cứu capability mentor, dịch vụ mentor qua immutable records.
  - `MentorBookingPolicyQuery`: Tra cứu chính sách đặt lịch (effective booking policy, notice hours, refund rules).
  - `MentorContentAccessPort`: Xác thực quyền truy cập nội dung dành riêng cho mentor và cung cấp `MentorBlogAuthorSummary` cho blog.
  - `MentorDisciplinePort` / `MentorViolationCommandPort`: Xử lý vi phạm, kỷ luật và cảnh báo mentor từ booking/moderation.
  - `MentorShareQueryPort`: Trả về `MentorShareMetadata` phục vụ SEO và public share card;
    port cũng cung cấp danh sách `UUID` mentor public cho sitemap.
  - `MentorVerificationAdminPort`: Cung cấp hàng đợi và thao tác phê duyệt hồ sơ mentor cho admin.
- **Consumer hợp lệ**: `booking`, `blog`, `feedback`, `chat`, `admin`, `seo`.
- **Kỳ vọng Transaction**:
  - Query: `readOnly = true`.
  - Discipline/Admin: Transaction độc lập tại provider adapter.

---

### 3. Module `booking`
- **Owner**: `booking`
- **Mục đích**: Quản lý toàn bộ vòng đời đặt lịch (quote, create, accept, reschedule, cancel, complete, dispute/SLA), slot thời gian và session.
- **Các Port công khai**:
  - `ContentEntitlementQuery`: Kiểm tra quyền sở hữu nội dung (service content entitlement) của học viên dựa trên lịch sử hoàn thành booking.
  - `BookingIssueEvidencePort`: Cung cấp bằng chứng và thông tin tranh chấp phục vụ moderation/admin.
  - Public Events: `BookingCreatedEvent`, `BookingAcceptedEvent`, `BookingCompletedEvent`, `BookingCancelledEvent`, `BookingRescheduledEvent`.
- **Consumer hợp lệ**: `blog`, `feedback`, `admin`, `chat`, `payment`, `notification`.
- **Kỳ vọng Transaction**:
  - Lifecycle state machine sở hữu hoàn toàn bởi Booking module.
  - Event publishing qua Outbox / TransactionalEventListener (phase = `AFTER_COMMIT`).

---

### 4. Module `payment`
- **Owner**: `payment`
- **Mục đích**: Quản lý số dư ví/credit, báo giá thanh toán (checkout quote), phiên thanh toán (checkout session), cổng thanh toán PayOS, chiến dịch khuyến mãi (campaign), coupon và quyết toán (settlement).
- **Các Port công khai**:
  - `CoursePaymentPort`: Kiểm tra và xử lý thanh toán khóa học.
  - `CampaignAdminPort`: Quản lý chiến dịch khuyến mại cho Admin.
  - `CouponAdminPort`: Quản lý mã giảm giá cho Admin.
  - `PaymentAdminPort`: Tra cứu thống kê tài chính và đối soát.
  - Public Events: `PaymentCompletedEvent`, `RefundProcessedEvent`.
- **Consumer hợp lệ**: `booking`, `course`, `admin`.
- **Kỳ vọng Transaction**:
  - Money scale và balance lock được cô lập trong Payment aggregate.
  - Giao tiếp với Booking hoàn toàn bất đồng bộ qua event sau commit, không gọi trực tiếp synchronous mutate sang booking state.

---

### 5. Module `notification`
- **Owner**: `notification`
- **Mục đích**: Tiếp nhận intent thông báo, gửi email template, duy trì hộp thư email outbox và phân phối thông báo người dùng.
- **Các Port công khai**:
  - `NotificationCommandPort`: Tiếp nhận notification intent (recipientId, templateKey, payload parameters).
  - `EmailOutboxAdminPort`: Dashboard quản trị email outbox, retry và thống kê cho admin.
- **Consumer hợp lệ**: Mọi module nghiệp vụ (`booking`, `mentor`, `identity`, `payment`, `forum`, `admin`).
- **Kỳ vọng Transaction**:
  - Notification chỉ là consumer của domain events hoặc gọi command intent nhẹ.
  - Lỗi gửi email hoặc dispatch không được làm rollback transaction nghiệp vụ chính.

---

### 6. Module `chat`
- **Owner**: `chat`
- **Mục đích**: Tin nhắn trực tiếp giữa mentee - mentor, nhóm thảo luận khóa học, quản lý cursor đã đọc và báo cáo vi phạm chat.
- **Các Port công khai**:
  - `ChatAccessSnapshotPort`: Xác nhận quyền tham gia cuộc trò chuyện dựa trên entitlement.
  - Public Events: `ChatMessageSentEvent`.
- **Consumer hợp lệ**: `booking`, `course`, `admin`.
- **Kỳ vọng Transaction**:
  - Cursor và sequence ordering được quản lý nội bộ.

---

### 7. Module `forum`
- **Owner**: `forum`
- **Mục đích**: Diễn đàn trao đổi học thuật, bài viết, bình luận, reaction, báo cáo vi phạm và quản lý danh sách từ cấm.
- **Các Port công khai**:
  - `ForumAdminPort`: Quản trị bài viết, bình luận và xử lý vi phạm diễn đàn.
  - `ForumProhibitedPhraseAdminPort`: Quản trị danh sách từ khóa cấm (`CreateForumProhibitedPhraseCommand`, `UpdateForumProhibitedPhraseCommand`, `SetForumProhibitedPhraseActiveCommand`, `ForumProhibitedPhraseView`).
- **Consumer hợp lệ**: `admin`.
- **Kỳ vọng Transaction**:
  - Read feed optimized; moderation actions transactional at command boundary.

---

### 8. Module `blog`
- **Owner**: `blog`
- **Mục đích**: Bài viết kiến thức, bài chia sẻ chuyên môn của mentor, bookmark, like, follow tác giả/chuyên mục và nội dung trả phí (premium content).
- **Các Port công khai**:
  - `BlogShareQueryPort`: Cung cấp siêu dữ liệu share card và public SEO preview;
    port cũng cung cấp danh sách slug public cho sitemap, không để SEO chạm
    `EntityManager`.
  - `BlogQueryPort`: Cung cấp `BlogMentorArticlePreview`, immutable projection do Blog sở hữu;
    consumer tự map sang response DTO của mình.
  - `BlogAuthorQueryPort`: Projection tác giả chỉ gồm UUID, display name, avatar và trạng thái active;
    không trả `User`/`MentorProfile` entity.
- **Consumer hợp lệ**: `seo`, `admin`.
- **Kỳ vọng Transaction**:
  - Tra cứu quyền sở hữu nội dung qua `booking` port (`ContentEntitlementQuery`) và thông tin mentor qua `mentor` port (`MentorContentAccessPort`).

---

### 9. Module `course`
- **Owner**: `course`
- **Mục đích**: Quản lý khóa học, bài giảng video, chương trình học và quyền truy cập khóa học.
- **Các Port công khai**:
  - `CourseQueryPort`: Tra cứu thông tin khóa học, trạng thái kích hoạt và giảng viên.
  - `CourseVideoProvider`: Cung cấp metadata streaming video (được chuyển đổi thành provider-neutral record).
- **Consumer hợp lệ**: `payment`, `chat`, `admin`.
- **Kỳ vọng Transaction**:
  - Isolation of video streaming provider adapter from domain logic.

---

### 10. Module `feedback`
- **Owner**: `feedback`
- **Mục đích**: Đánh giá và nhận xét sau buổi cố vấn (session feedback/review), tổng hợp điểm đánh giá mentor.
- **Các Port công khai**:
  - `FeedbackQueryPort`: Truy vấn danh sách đánh giá công khai của mentor (`findPublicMentorReviews` trả về `PageResponse<MentorReviewProjection>`).
  - `MentorReviewProjection`: Record snapshot gồm `reviewId`, `reviewerUserId`, `rating`, `comment`, `createdAt`.
- **Consumer hợp lệ**: `mentor`, `booking`, `admin`.
- **Kỳ vọng Transaction**:
  - Read-only projection queries; async aggregate rating update.

---

### 11. Module `filestorage`
- **Owner**: `filestorage`
- **Mục đích**: Quản lý tệp tin tải lên, tài liệu chứng chỉ mentor (verification documents), asset hình ảnh cho blog/portfolio, và tạo private presigned URL.
- **Các Port công khai**:
  - `VerificationDocumentStoragePort`: Đăng ký và truy vấn tài liệu xác thực mentor qua `FileAssetMetadata` (fileId, ownerUserId, filename, contentType, size, url).
  - `PublicAssetPort`: Tra cứu metadata asset public bằng immutable record; upload intent vẫn đi qua
    `PublicAssetUploadPort`.
- **Consumer hợp lệ**: `mentor`, `blog`, `admin`.
- **Kỳ vọng Transaction**:
  - Chỉ nhận và trả scalar ID + immutable metadata; không liên kết quan hệ JPA sang `User` entity của identity.

---

### 12. Module `catalog`
- **Owner**: `catalog`
- **Mục đích**: Danh mục kỹ năng, ngành nghề, từ khóa tìm kiếm và các danh mục phân loại hệ thống.
- **Các Port công khai**:
  - `CatalogKeywordQueryPort`: Tra cứu gợi ý từ khóa và danh mục kỹ năng chuẩn hóa.
- **Consumer hợp lệ**: `mentor`, `blog`, `identity`, `admin`.
- **Kỳ vọng Transaction**:
  - Read-only cached lookups.

---

### 13. Module `admin`
- **Owner**: `admin`
- **Mục đích**: Trung tâm điều phối quản trị (Dashboard, case moderation, verification queue, system telemetry, audit trail).
- **Đặc thù**:
  - Module `admin` là **Consumer** của các Admin Port do từng feature module cung cấp (`UserAdminPort`, `MentorVerificationAdminPort`, `PaymentAdminPort`, `ForumAdminPort`, `EmailOutboxAdminPort`).
  - Module `admin` không expose generic data repository mà tương tác theo hướng use-case cụ thể.
- **Consumer hợp lệ**: Web Controller của Admin portal.
- **Kỳ vọng Transaction**:
  - Admin commands gọi port của từng provider, bảo đảm quyền hạn theo role `ADMIN` / `MODERATOR`.

---

## Tóm tắt Ranh giới & Hướng thay thế

| Provider Module | Public API Port Package | Record Model Tiêu biểu | Không chứa |
|---|---|---|---|
| `identity` | `modules.identity.port` | `UserSummaryRecord`, `AcademicEligibilityQuery` | `User`, `StudentProfile`, `Campus` JPA Entity |
| `mentor` | `modules.mentor.port` | `MentorBlogAuthorSummary`, `MentorShareMetadata` | `MentorProfile`, `MentorService` JPA Entity |
| `booking` | `modules.booking.port` | `ContentEntitlementQuery`, `BookingIssueEvidencePort` | `Booking`, `Session` JPA Entity, `BookingTime` |
| `payment` | `modules.payment.port` | `AdminDashboardCampaignOverview`, `CoursePaymentPort` | `PaymentOrder`, `PayOS` SDK types |
| `notification` | `modules.notification.port` | `EmailOutboxAdminSummary`, `NotificationCommandPort` | `EmailOutbox` JPA Entity, obsolete outbox persistence port |
| `feedback` | `modules.feedback.port` | `MentorReviewProjection` | `SessionFeedback` JPA Entity |
| `filestorage` | `modules.filestorage.port` | `VerificationDocumentStoragePort` | `StoredFile` JPA Entity |
| `forum` | `modules.forum.port` | `ForumProhibitedPhraseView`, `ForumAdminPort` | `ForumPost`, `ForumComment` JPA Entity |
| `course` | `modules.course.port` | `CourseQueryPort` | `Course` JPA Entity, `Bunny` SDK types |
| `catalog` | `modules.catalog.port` | `CatalogKeywordQueryPort` | `SkillCategory` JPA Entity |
