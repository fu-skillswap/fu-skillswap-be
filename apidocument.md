# SkillSwap API Document

Tài liệu này mô tả toàn bộ API hiện có trong backend SkillSwap tại thời điểm hiện tại, dựa trên các controller đang được implement trong `src/main/java`.

## Quy ước chung

- Base response: tất cả API đều trả về `ApiResponse<T>` hoặc `ResponseEntity<ApiResponse<T>>`.
- Authentication:
  - Public: không cần token.
  - `bearerAuth`: cần JWT Access Token.
  - Role-based:
    - `MENTOR`: mentor đã đăng nhập.
    - `ADMIN`: admin vận hành.
    - `SYSTEM_ADMIN`: quản trị hệ thống.
- Trạng thái trả về phổ biến:
  - `200 OK`: thao tác thành công.
  - `201 Created`: tạo mới thành công.
  - `400 Bad Request`: dữ liệu đầu vào không hợp lệ.
  - `401 Unauthorized`: chưa đăng nhập / token không hợp lệ.
  - `403 Forbidden`: không đủ quyền.
  - `404 Not Found`: không tìm thấy tài nguyên.
  - `409 Conflict`: xung đột trạng thái / dữ liệu.

## 1. Auth

### POST `/api/auth/google`
- Auth: Public
- Mục đích: đăng nhập Google bằng `idToken`.
- Request body:
  - `idToken` `string` - Google ID Token
- Response:
  - `TokenResponse`
- Status:
  - `200 OK`
  - `400 Bad Request`
  - `403 Forbidden`

### POST `/api/auth/refresh`
- Auth: Public
- Mục đích: làm mới Access Token bằng Refresh Token.
- Request body:
  - `refreshToken` `string`
- Response:
  - `TokenResponse`
- Status:
  - `200 OK`
  - `401 Unauthorized`

### POST `/api/auth/logout`
- Auth: Public
- Mục đích: thu hồi Refresh Token hiện tại.
- Request body:
  - `refreshToken` `string`
- Response:
  - `string`
- Status:
  - `200 OK`

### GET `/api/auth/me`
- Auth: `bearerAuth`
- Mục đích: lấy thông tin user hiện tại để FE quyết định luồng điều hướng.
- Response:
  - `UserMeResponse`
- Status:
  - `200 OK`
  - `401 Unauthorized`

## 2. Academic

### GET `/api/campuses`
- Auth: Public
- Mục đích: lấy danh sách cơ sở FPT University.
- Response:
  - `List<CampusResponse>`

### GET `/api/academic-programs`
- Auth: Public
- Mục đích: lấy danh sách ngành học.
- Response:
  - `List<AcademicProgramResponse>`

### GET `/api/specializations`
- Auth: Public
- Mục đích: lấy toàn bộ chuyên ngành.
- Response:
  - `List<SpecializationResponse>`

### GET `/api/academic-programs/{programId}/specializations`
- Auth: Public
- Path param:
  - `programId` `UUID`
- Mục đích: lấy chuyên ngành theo ngành học.
- Response:
  - `List<SpecializationResponse>`
- Status:
  - `200 OK`
  - `404 Not Found`

### GET `/api/me/student-profile`
- Auth: `bearerAuth`
- Mục đích: xem hồ sơ học thuật hiện tại.
- Response:
  - `StudentProfileResponse`
- Status:
  - `200 OK`
  - `401 Unauthorized`
  - `404 Not Found`

### PUT `/api/me/student-profile`
- Auth: `bearerAuth`
- Mục đích: tạo hoặc cập nhật hồ sơ học thuật.
- Request body:
  - `StudentProfileRequest`
    - `studentCode` `string`
    - `displayName` `string` nullable
    - `avatarUrl` `string` nullable
    - `campusId` `UUID`
    - `programId` `UUID`
    - `specializationId` `UUID`
    - `semester` `integer`
    - `intakeYear` `integer`
    - `isAlumni` `boolean`
    - `graduationYear` `integer` nullable
    - `bio` `string` nullable
- Response:
  - `StudentProfileResponse`
- Status:
  - `200 OK`
  - `400 Bad Request`
  - `401 Unauthorized`
  - `404 Not Found`

## 3. Catalog

### GET `/api/catalog/help-topics`
- Auth: Public
- Mục đích: lấy danh sách help topics dùng cho mentor profile, mentor service và filter discovery.
- Response:
  - `List<HelpTopicResponse>`

## 4. Mentor Profile

### GET `/api/me/mentor-profile`
- Auth: `bearerAuth`
- Mục đích: xem hồ sơ mentor hiện tại.
- Response:
  - `MentorProfileResponse`

### PUT `/api/me/mentor-profile`
- Auth: `bearerAuth`
- Mục đích: tạo hoặc cập nhật hồ sơ mentor.
- Request body:
  - `MentorProfileUpsertRequest`
    - `headline` `string`
    - `expertiseDescription` `string`
    - `supportingSubjects` `string` nullable
    - `isAvailable` `boolean` nullable
    - `helpTopicIds` `List<UUID>`
    - `teachingMode` `TeachingMode`
    - `sessionDuration` `integer`
    - `linkedinUrl` `string` nullable
    - `githubUrl` `string` nullable
    - `portfolioUrl` `string` nullable
- Response:
  - `MentorProfileResponse`
- Status:
  - `200 OK`
  - `400 Bad Request`
  - `401 Unauthorized`

## 5. Mentor Services

### GET `/api/me/mentor-services`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: xem danh sách dịch vụ mentoring của mình.
- Response:
  - `List<MentorServiceResponse>`

### GET `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `serviceId` `UUID`
- Mục đích: xem chi tiết dịch vụ mentoring của mình.
- Response:
  - `MentorServiceResponse`

### POST `/api/me/mentor-services`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: tạo dịch vụ mentoring mới.
- Request body:
  - `MentorServiceUpsertRequest`
    - `title` `string`
    - `description` `string`
    - `durationMinutes` `integer`
    - `isFree` `boolean`
    - `priceAmount` `BigDecimal` nullable
    - `helpTopicIds` `List<UUID>`
- Response:
  - `MentorServiceResponse`

### PUT `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `serviceId` `UUID`
- Request body:
  - `MentorServiceUpsertRequest`
- Response:
  - `MentorServiceResponse`

### PATCH `/api/me/mentor-services/{serviceId}/active`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `serviceId` `UUID`
- Request body:
  - `MentorServiceActiveRequest`
    - `active` `boolean`
- Response:
  - `MentorServiceResponse`

### DELETE `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `serviceId` `UUID`
- Mục đích: xóa mềm dịch vụ mentoring.
- Response:
  - `MentorServiceResponse`

## 6. Mentor Discovery

### GET `/api/mentors/recommendations`
- Auth: `bearerAuth`
- Query params:
  - `limit` `int` mặc định `12`
- Mục đích: lấy danh sách mentor gợi ý nhanh cho dashboard.
- Response:
  - `List<MentorRecommendationResponse>`

### GET `/api/mentors`
- Auth: `bearerAuth`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `keyword`
  - `tagIds`
  - `campusId`
  - `specializationId`
  - `teachingMode`
  - `isAvailable`
- Mục đích: tìm kiếm, lọc và xếp hạng mentor.
- Response:
  - `PageResponse<MentorDiscoveryCardResponse>`

### GET `/api/mentors/{mentorUserId}`
- Auth: `bearerAuth`
- Path param:
  - `mentorUserId` `UUID`
- Mục đích: xem chi tiết mentor.
- Response:
  - `MentorDiscoveryDetailResponse`

### GET `/api/mentors/{mentorUserId}/availability`
- Auth: `bearerAuth`
- Path param:
  - `mentorUserId` `UUID`
- Query params:
  - `fromDate`
  - `toDate`
- Mục đích: xem slot rảnh của mentor.
- Response:
  - `List<MentorAvailabilitySlotResponse>`

### GET `/api/mentors/{mentorUserId}/reviews`
- Auth: `bearerAuth`
- Path param:
  - `mentorUserId` `UUID`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
- Mục đích: xem review công khai của mentor.
- Response:
  - `PageResponse<MentorReviewResponse>`

## 7. Mentor Verification - User

### POST `/api/me/mentor-verification/request`
- Auth: `bearerAuth`
- Mục đích: khởi tạo hoặc lấy hồ sơ xác thực mentor đang hoạt động.
- Response:
  - `MentorVerificationRequestResponse`
- Status:
  - `200 OK`
  - `201 Created`
  - `401 Unauthorized`

### GET `/api/me/mentor-verification`
- Auth: `bearerAuth`
- Mục đích: xem hồ sơ xác thực mentor hiện tại.
- Response:
  - `MentorVerificationRequestResponse`

### GET `/api/me/mentor-verification/timeline`
- Auth: `bearerAuth`
- Mục đích: xem timeline của hồ sơ xác thực mentor hiện tại.
- Response:
  - `List<MentorVerificationTimelineEventResponse>`

### GET `/api/me/mentor-verification/documents/{documentId}`
- Auth: `bearerAuth`
- Path param:
  - `documentId` `UUID`
- Mục đích: xem chi tiết một tài liệu xác thực mentor.
- Response:
  - `MentorVerificationDocumentResponse`

### POST `/api/me/mentor-verification/documents`
- Auth: `bearerAuth`
- Content-Type: `application/json`
- Request body:
  - `MentorVerificationDocumentUploadRequest`
    - `documentType` `VerificationDocumentType` (ví dụ: `FPTU_AFFILIATION_PROOF`)
    - `fileUrl` `string` (đường dẫn URL sau khi upload lên dịch vụ lưu trữ Cloudinary)
    - `publicId` `string` (Cloudinary public ID)
    - `originalFilename` `string`
    - `contentType` `string`
    - `sizeBytes` `integer`
- Mục đích: Lưu metadata của tài liệu xác thực mentor sau khi client-side đã tự upload lên Cloudinary. Backend nhận metadata này và gắn vào request xác thực.
- Response:
  - `MentorVerificationRequestResponse`
- Status:
  - `201 Created`

### POST `/api/me/mentor-verification/submit`
- Auth: `bearerAuth`
- Mục đích: nộp hồ sơ xác thực mentor để admin duyệt.
- Request body:
  - `MentorVerificationSubmitRequest`
    - `submitNote` `string` nullable
    - `agreeTerms` `boolean`
- Response:
  - `MentorVerificationRequestResponse`

### DELETE `/api/me/mentor-verification/documents/{documentId}`
- Auth: `bearerAuth`
- Path param:
  - `documentId` `UUID`
- Mục đích: xóa mềm tài liệu xác thực mentor.
- Response:
  - `MentorVerificationRequestResponse`

### POST `/api/me/mentor-verification/withdraw`
- Auth: `bearerAuth`
- Mục đích: rút hồ sơ xác thực mentor hiện tại.
- Response:
  - `MentorVerificationRequestResponse`

## 8. Mentor Verification - Admin

### GET `/api/admin/mentor-verification/requests`
- Auth: `bearerAuth`, role `ADMIN`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `status`
  - `keyword`
  - `submittedFrom`
  - `submittedTo`
- Mục đích: xem hàng đợi duyệt hồ sơ mentor.
- Response:
  - `PageResponse<AdminMentorVerificationQueueItemResponse>`

### GET `/api/admin/mentor-verification/requests/{requestId}`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `requestId` `UUID`
- Mục đích: xem chi tiết hồ sơ mentor và claim soft lock nếu cần.
- Response:
  - `AdminMentorVerificationRequestResponse`

### GET `/api/admin/mentor-verification/requests/{requestId}/lock`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `requestId` `UUID`
- Mục đích: xem trạng thái soft lock hiện tại.
- Response:
  - `AdminMentorVerificationLockResponse`

### POST `/api/admin/mentor-verification/requests/{requestId}/lock/refresh`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `requestId` `UUID`
- Mục đích: gia hạn soft lock thêm 5 phút.
- Response:
  - `AdminMentorVerificationLockResponse`

### POST `/api/admin/mentor-verification/requests/{requestId}/request-revision`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `requestId` `UUID`
- Request body:
  - `AdminMentorVerificationReviewRequest`
    - `note` `string`
- Mục đích: yêu cầu mentor chỉnh sửa hồ sơ trên request cũ.
- Response:
  - `AdminMentorVerificationRequestResponse`

### POST `/api/admin/mentor-verification/requests/{requestId}/approve`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `requestId` `UUID`
- Request body:
  - `AdminMentorVerificationReviewRequest` nullable
- Mục đích: phê duyệt hồ sơ mentor.
- Response:
  - `AdminMentorVerificationRequestResponse`

### POST `/api/admin/mentor-verification/requests/{requestId}/reject`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `requestId` `UUID`
- Request body:
  - `AdminMentorVerificationReviewRequest`
    - `note` `string`
- Mục đích: từ chối hồ sơ mentor.
- Response:
  - `AdminMentorVerificationRequestResponse`

## 9. Mentor Admin

### GET `/api/admin/mentors`
- Auth: `bearerAuth`, role `ADMIN`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `keyword`
  - `status`
  - `isAvailable`
- Mục đích: xem danh sách mentor trong hệ thống phục vụ vận hành.
- Response:
  - `PageResponse<AdminMentorListItemResponse>`

## 10. Booking

### POST `/api/bookings`
- Auth: `bearerAuth`
- Mục đích: mentee tạo booking request theo slot còn trống.
- Request body:
  - `CreateBookingRequest`
    - `mentorUserId` `UUID`
    - `availabilitySlotId` `UUID`
    - `serviceId` `UUID` nullable
    - `learningGoalTitle` `string`
    - `learningGoalDescription` `string` nullable
- Response:
  - `BookingResponse`
- Status:
  - `201 Created`

### GET `/api/me/bookings`
- Auth: `bearerAuth`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `status`
  - `role`
- Mục đích: xem danh sách booking của tôi theo vai trò mentee hoặc mentor.
- Response:
  - `PageResponse<BookingResponse>`

### GET `/api/me/bookings/{bookingId}`
- Auth: `bearerAuth`
- Path param:
  - `bookingId` `UUID`
- Mục đích: xem chi tiết booking thuộc về tôi.
- Response:
  - `BookingResponse`

### POST `/api/me/bookings/{bookingId}/cancel`
- Auth: `bearerAuth`
- Path param:
  - `bookingId` `UUID`
- Request body:
  - `CancelBookingRequest`
    - `cancelReason` `string`
- Mục đích: mentee hủy booking của chính mình.
- Response:
  - `BookingResponse`

### POST `/api/me/bookings/{bookingId}/complete`
- Auth: `bearerAuth`
- Path param:
  - `bookingId` `UUID`
- Request body:
  - `CompleteBookingRequest`
    - `completionNote` `string` nullable
- Mục đích: mentee hoặc mentor đánh dấu booking đã hoàn thành.
- Response:
  - `BookingResponse`

### POST `/api/mentor/bookings/{bookingId}/accept`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `bookingId` `UUID`
- Request body:
  - `AcceptBookingRequest`
    - `mentorResponseNote` `string` nullable
- Mục đích: mentor chấp nhận booking đang chờ.
- Response:
  - `BookingResponse`

### POST `/api/mentor/bookings/{bookingId}/reject`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `bookingId` `UUID`
- Request body:
  - `RejectBookingRequest`
    - `rejectReason` `string`
    - `mentorResponseNote` `string` nullable
- Mục đích: mentor từ chối booking đang chờ.
- Response:
  - `BookingResponse`

### POST `/api/mentor/bookings/{bookingId}/cancel`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `bookingId` `UUID`
- Request body:
  - `CancelBookingRequest`
    - `cancelReason` `string`
- Mục đích: mentor hủy booking đã chấp nhận.
- Response:
  - `BookingResponse`

### PATCH `/api/mentor/bookings/{bookingId}/meeting-link`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `bookingId` `UUID`
- Request body:
  - `SaveMeetingLinkRequest`
    - `meetingPlatform` `MeetingPlatform`
    - `meetingLink` `string`
    - `location` `string` nullable
- Mục đích: lưu hoặc cập nhật meeting link cho booking.
- Response:
  - `BookingResponse`

### GET `/api/admin/bookings`
- Auth: `bearerAuth`, role `ADMIN`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `status`
  - `mentorUserId`
  - `menteeUserId`
- Mục đích: admin xem danh sách booking toàn hệ thống.
- Response:
  - `PageResponse<BookingResponse>`

### GET `/api/admin/bookings/{bookingId}`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `bookingId` `UUID`
- Mục đích: admin xem chi tiết một booking.
- Response:
  - `BookingResponse`

## 11. Booking Availability

### GET `/api/mentor/availability-rules`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: xem các rule lịch rảnh hiện tại của mentor.
- Response:
  - `List<AvailabilityRuleResponse>`

### POST `/api/mentor/availability-rules`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: tạo rule lịch rảnh hoặc rule đóng lịch.
- Request body:
  - `UpsertAvailabilityRuleRequest`
    - `ruleType`
    - `repeatType`
    - `daysOfWeek` `List<DayOfWeek>` nullable
    - `effectiveFrom` `LocalDate`
    - `effectiveTo` `LocalDate` nullable
    - `startTime` `LocalTime` nullable
    - `endTime` `LocalTime` nullable
    - `note` `string` nullable
- Response:
  - `AvailabilityRuleResponse`
- Status:
  - `201 Created`

### PUT `/api/mentor/availability-rules/{ruleId}`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `ruleId` `UUID`
- Request body:
  - `UpsertAvailabilityRuleRequest`
- Response:
  - `AvailabilityRuleResponse`

### DELETE `/api/mentor/availability-rules/{ruleId}`
- Auth: `bearerAuth`, role `MENTOR`
- Path param:
  - `ruleId` `UUID`
- Mục đích: vô hiệu hóa rule lịch rảnh.
- Response:
  - `Void`

## 12. Session Feedback

### POST `/api/bookings/{bookingId}/feedback`
- Auth: `bearerAuth`
- Path param:
  - `bookingId` `UUID`
- Request body:
  - `SubmitFeedbackRequest`
    - `rating` `integer`
    - `title` `string` nullable
    - `comment` `string` nullable
- Mục đích: gửi feedback sau buổi mentoring hoàn thành.
- Response:
  - `SessionFeedbackResponse`
- Status:
  - `201 Created`

## 13. Chat & Conversation

### GET `/api/me/conversations`
- Auth: `bearerAuth`
- Mục đích: lấy danh sách các cuộc hội thoại (conversations) của người dùng hiện tại, được sắp xếp theo thời điểm có tin nhắn mới nhất giảm dần (`lastMessageAt DESC`). Trả về thông tin của đối phương (`otherUserId`, `otherUserName`, `otherUserAvatarUrl`).
- Query params:
  - `page` `integer` nullable
  - `size` `integer` nullable
  - `sortBy` `string` nullable
  - `direction` `string` nullable
- Response:
  - `PageResponse<ConversationResponse>`
    - `id` `UUID`
    - `sourceType` `string` (ví dụ: `BOOKING`)
    - `sourceId` `UUID`
    - `type` `string` (DIRECT)
    - `status` `string` (ACTIVE)
    - `otherUserId` `UUID`
    - `otherUserName` `string`
    - `otherUserAvatarUrl` `string` nullable
    - `lastMessageContent` `string` nullable
    - `lastMessageAt` `LocalDateTime` nullable
    - `createdAt` `LocalDateTime`

### GET `/api/me/conversations/{conversationId}/messages`
- Auth: `bearerAuth`
- Path param:
  - `conversationId` `UUID`
- Query params:
  - `page` `integer` nullable
  - `size` `integer` nullable
  - `sortBy` `string` nullable
  - `direction` `string` nullable
- Mục đích: lấy danh sách lịch sử tin nhắn của một cuộc hội thoại, sắp xếp theo thời gian tạo giảm dần (`createdAt DESC` - Mới nhất ở đầu).
- Response:
  - `PageResponse<MessageResponse>`
    - `id` `UUID`
    - `conversationId` `UUID`
    - `senderId` `UUID` nullable (null đối với tin nhắn hệ thống)
    - `senderName` `string` (trả về "Hệ thống" nếu senderId null)
    - `messageType` `string` (TEXT)
    - `content` `string`
    - `createdAt` `LocalDateTime`
    - `isMine` `boolean` (xác định tin nhắn có phải do người dùng hiện tại gửi hay không)

### POST `/api/me/conversations/{conversationId}/messages`
- Auth: `bearerAuth`
- Path param:
  - `conversationId` `UUID`
- Request body:
  - `SendMessageRequest`
    - `content` `string` (nội dung tin nhắn chat, tối đa 2000 ký tự)
- Mục đích: gửi tin nhắn văn bản vào cuộc hội thoại (REST API - Source of Truth). Tin nhắn lưu thành công sẽ tự động kích hoạt gửi tin realtime qua WebSocket.
- Response:
  - `MessageResponse`
- Status:
  - `201 Created`
  - `400 Bad Request` (nội dung rỗng hoặc vượt quá 2000 ký tự)
  - `403 Forbidden` (người dùng không tham gia cuộc hội thoại này)

### WebSocket Realtime Push
- Endpoint handshake: `/ws` (Hỗ trợ SockJS fallback)
- Query param (handshake): `token` (JWT Access Token)
- Subscribe Destination: `/user/queue/messages`
- Định dạng event đẩy về: `ChatMessageEvent`
  - `conversationId` `UUID`
  - `messageId` `UUID`
  - `senderId` `UUID`
  - `senderName` `string`
  - `messageType` `string` (TEXT)
  - `content` `string`
  - `createdAt` `LocalDateTime`
- Lưu ý cho FE: Kênh WebSocket chỉ nhận tin (Push-only), không gửi tin ngược lên. FE cần so khớp `senderId` với current user ID để phân biệt vị trí hiển thị bong bóng chat.

## 14. Wallet

### GET `/api/me/credit-wallet`
- Auth: `bearerAuth`, role `MENTEE`
- Mục đích: xem số dư Scoin khả dụng của mentee / user hiện tại và 15 giao dịch gần nhất.
- Response:
  - `CreditWalletResponse`
    - `availableScoin` `integer`: số Scoin khả dụng hiện tại.
    - `recentTransactions` `List<WalletTransactionResponse>`: tối đa 15 giao dịch gần nhất, sắp xếp mới nhất trước.
      - `id` `UUID`
      - `entryType` `LedgerEntryType`
      - `originType` `CreditOriginType` nullable
      - `sourceType` `LedgerSourceType`
      - `sourceId` `UUID` nullable
      - `amountScoin` `integer`
      - `balanceEffectScoin` `integer`
      - `memo` `string` nullable
      - `createdAt` `LocalDateTime`

### GET `/api/me/mentor-wallet`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: xem settlement earnings khả dụng của mentor hiện tại và 15 giao dịch gần nhất.
- Response:
  - `MentorWalletResponse`
    - `availableScoin` `integer`: số settlement earnings khả dụng hiện tại.
    - `recentTransactions` `List<WalletTransactionResponse>`: tối đa 15 giao dịch gần nhất, sắp xếp mới nhất trước.
      - `id` `UUID`
      - `entryType` `LedgerEntryType`
      - `originType` `CreditOriginType` nullable
      - `sourceType` `LedgerSourceType`
      - `sourceId` `UUID` nullable
      - `amountScoin` `integer`
      - `balanceEffectScoin` `integer`
      - `memo` `string` nullable
      - `createdAt` `LocalDateTime`

## 15. System Admin

### POST `/api/system/users/admin-role/grant`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Mục đích: cấp quyền `ADMIN` cho user theo email.
- Request body:
  - `AdminRoleChangeRequest`
    - `email` `string`
- Response:
  - `AdminUserResponse`
 
### POST `/api/system/users/admin-role/revoke`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Mục đích: thu hồi quyền `ADMIN` theo email.
- Request body:
  - `AdminRoleChangeRequest`
    - `email` `string`
- Response:
  - `AdminUserResponse`
 
### GET `/api/system/users/admins`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
- Mục đích: xem danh sách user đang có quyền `ADMIN`.
- Response:
  - `PageResponse<AdminUserResponse>`
 
### GET `/api/system/users`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
- Mục đích: xem toàn bộ user trong hệ thống.
- Response:
  - `PageResponse<SystemUserResponse>`
 
## 16. Admin User Management

### GET `/api/admin/users`
- Auth: `bearerAuth`, role `ADMIN`
- Query params:
  - `page`
  - `size`
  - `sortBy`
  - `direction`
  - `keyword`
  - `status`
  - `roles`
  - `isAlumni`
- Mục đích: admin xem danh sách người dùng (chỉ `MENTEE` và `MENTOR`, không bao gồm `ADMIN`).
- Response:
  - `PageResponse<AdminUserListItemResponse>`
  - Lưu ý: Response trả về có chứa thuộc tính `academicProfile` với các trường quan trọng phục vụ quản lý MSSV:
    - `claimedStudentCode` `string`: MSSV người dùng đã khai báo.
    - `verifiedStudentCode` `string`: MSSV đã được hệ thống xác thực.
    - `studentCodeVerified` `boolean`: Trạng thái xác thực MSSV.
    - `studentCodeConflict` `boolean`: `true` nếu có từ 2 người dùng trở lên khai báo cùng 1 MSSV chưa xác thực.
 
### POST `/api/admin/users/{userId}/ban`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `userId` `UUID`
- Request body:
  - `BanUserRequest`
    - `reason` `string`
- Mục đích: khóa tài khoản user.
- Response:
  - `SystemUserResponse`
 
### POST `/api/admin/users/{userId}/unban`
- Auth: `bearerAuth`, role `ADMIN`
- Path param:
  - `userId` `UUID`
- Request body:
  - `UnbanUserRequest`
    - `reason` `string`
- Mục đích: mở khóa tài khoản user.
- Response:
  - `SystemUserResponse`
 
## 17. Ghi chú cho FE
 
- `GET /api/auth/me` nên gọi ngay sau login để xác định:
  - user đã login thành công chưa,
  - đã hoàn thành `StudentProfile` chưa,
  - có vai trò `MENTOR`, `ADMIN`, `SYSTEM_ADMIN` hay chưa.
- Luồng mentor verification hiện tách rõ:
  - user flow: `request` -> upload document -> submit -> timeline / document detail -> delete / withdraw
  - admin flow: queue -> detail -> lock -> refresh lock -> revision / approve / reject
- Discovery mentor ưu tiên:
  - `recommendations` để render gợi ý ban đầu,
  - `GET /api/mentors` để search/filter/ranking,
  - `GET /api/mentors/{mentorUserId}/availability` để chuẩn bị booking.
- Chat & Conversation:
  - `GET /api/me/conversations` để tải danh sách hộp thư chat.
  - `GET /api/me/conversations/{id}/messages` để xem lịch sử chat. Chú ý mảng tin trả về dạng `DESC` (Mới nhất ở đầu), FE cần đảo ngược (reverse) mảng khi hiển thị.
  - Gửi tin nhắn dùng `POST /api/me/conversations/{id}/messages` (REST).
  - Lắng nghe realtime từ WebSocket qua destination `/user/queue/messages`. So sánh `senderId` trong Event với current user ID để đặt bong bóng chat phù hợp.
  - Khi WebSocket mất kết nối và reconnect thành công, FE cần gọi lại REST API lấy lịch sử tin để đồng bộ tránh mất mát tin trong lúc offline.

