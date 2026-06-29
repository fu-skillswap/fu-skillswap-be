# SkillSwap API Document

Tài liệu này phản ánh API backend hiện tại của SkillSwap dựa trên code trong `src/main/java`.

## 1. Quy ước chung

- Mọi API đều trả về `ApiResponse<T>` hoặc `ResponseEntity<ApiResponse<T>>`.
- `bearerAuth`: dùng JWT Access Token qua header `Authorization: Bearer <token>`.
- Refresh token:
  - được rotate khi login / refresh
  - được trả qua HttpOnly cookie
  - `POST /api/auth/refresh` và `POST /api/auth/logout` vẫn có thể nhận refresh token từ body nếu FE cần
- Các status code thường gặp:
  - `200 OK`: thành công
  - `201 Created`: tạo mới thành công
  - `400 Bad Request`: dữ liệu sai
  - `401 Unauthorized`: chưa đăng nhập / token sai
  - `403 Forbidden`: không đủ quyền
  - `404 Not Found`: không tìm thấy tài nguyên
  - `409 Conflict`: xung đột nghiệp vụ
  - `413 Payload Too Large`: file vượt dung lượng

## 2. Authentication

### POST `/api/auth/google`
- Auth: Public
- Mục đích: đăng nhập Google bằng `idToken`, backend phát hành access token của SkillSwap.
- Request body:
  - `idToken` `string`
- Ghi chú:
  - Notification hiển thị cho user thường, không bao gồm admin queue nội bộ.
  - Các `type` user-facing dùng ở beta gồm booking lifecycle, reschedule, feedback, forum moderation cho owner và `ACCOUNT_UNLOCKED`.
- Response:
  - `TokenResponse`
    - `accessToken`
    - `refreshToken` (thường được set null trong body vì trả qua HttpOnly cookie)
    - `tokenType`

### POST `/api/auth/refresh`
- Auth: Public
- Mục đích: cấp access token mới.
- Request body:
  - `refreshToken` `string` nullable nếu FE dùng cookie
- Response:
  - `TokenResponse`

### POST `/api/auth/logout`
- Auth: Public
- Mục đích: thu hồi refresh token hiện tại.
- Request body:
  - `refreshToken` `string` nullable nếu FE dùng cookie
- Response:
  - `string`

### GET `/api/auth/me`
- Auth: `bearerAuth`
- Mục đích: lấy thông tin user hiện tại để FE quyết định onboarding / dashboard / mentor flow.
- Response:
  - `UserMeResponse`
    - `publicId`
    - `email`
    - `fullName`
    - `avatarUrl`
    - `status`
    - `roles`
    - `profileCompleted`
    - `hasStudentProfile`

## 3. Academic / Onboarding

### GET `/api/campuses`
- Auth: Public
- Response: `List<CampusResponse>`

### GET `/api/academic-programs`
- Auth: Public
- Response: `List<AcademicProgramResponse>`

### GET `/api/specializations`
- Auth: Public
- Response: `List<SpecializationResponse>`

### GET `/api/academic-programs/{programId}/specializations`
- Auth: Public
- Path param:
  - `programId` `UUID`
- Response:
  - `List<SpecializationResponse>`

### GET `/api/me/student-profile`
- Auth: `bearerAuth`
- Mục đích: lấy hồ sơ học thuật hiện tại.
- Response:
  - `StudentProfileResponse`
    - `userId`, `email`, `studentCode`, `displayName`, `avatarUrl`
    - `campus`, `program`, `specialization`
    - `semester`, `intakeYear`, `isAlumni`, `graduationYear`
    - `bio`, `createdAt`, `updatedAt`

### PUT `/api/me/student-profile`
- Auth: `bearerAuth`
- Mục đích: tạo/cập nhật hồ sơ học thuật.
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

## 4. Catalog

### GET `/api/catalog/help-topics`
- Auth: Public
- Mục đích: lấy help topics dùng cho mentor profile, mentor service, discovery filter.
- Response: `List<HelpTopicResponse>`

## 5. Mentor Profile

### GET `/api/me/mentor-profile`
- Auth: `bearerAuth`
- Mục đích: lấy hồ sơ mentor của user hiện tại.
- Response:
  - `MentorProfileResponse`
    - `exists`
    - `requiredFieldsCompleted`
    - `userId`, `email`, `displayName`, `avatarUrl`
    - `mentorStatus`
    - `headline`, `expertiseDescription`, `supportingSubjects`
    - `isAvailable`
    - `bookingSuspendedUntil`
    - `lateCancellationPenaltyPoints`
    - `verifiedAt`
    - `helpTopics`
    - `linkedinUrl`, `githubUrl`, `portfolioUrl`
    - `phoneNumber`
    - `teachingMode`
    - `sessionDuration`
    - `ratingAverage`, `reviewCount`, `completedSessions`
    - `createdAt`, `updatedAt`

### PUT `/api/me/mentor-profile`
- Auth: `bearerAuth`
- Mục đích: tạo/cập nhật hồ sơ mentor.
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
    - `phoneNumber` `string`

## 6. Mentor Services

### GET `/api/me/mentor-services`
- Auth: `bearerAuth`, role `MENTOR`
- Query params:
  - `active=true|false|all` (mặc định `all`)
- Mục đích: xem danh sách mentor services của mình, bao gồm cả service bật/tắt hoặc soft delete tùy filter.
- Response: `List<MentorServiceResponse>`
  - `serviceId`
  - `mentorUserId`
  - `title`
  - `description`
  - `expectedOutcome`
  - `durationMinutes`
  - `free`
  - `priceScoin`
  - `active`
  - `helpTopics`
  - `createdAt`
  - `updatedAt`

### GET `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Response: `MentorServiceResponse`

### POST `/api/me/mentor-services`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `MentorServiceUpsertRequest`
    - `title` `string`
    - `description` `string`
    - `expectedOutcome` `string`
    - `durationMinutes` `integer`
    - `isFree` `boolean`
    - `priceScoin` `integer`
    - `helpTopicIds` `List<UUID>`

### PUT `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Request body: `MentorServiceUpsertRequest`
- Response: `MentorServiceResponse`

### PATCH `/api/me/mentor-services/{serviceId}/active`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `MentorServiceActiveRequest`
    - `active` `boolean`

### DELETE `/api/me/mentor-services/{serviceId}`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: xóa mềm service.

## 7. Mentor Discovery / Search / Ranking

### GET `/api/mentors/recommendations`
- Auth: `bearerAuth`
- Query params:
  - `limit` `int` (mặc định `12`)
- Mục đích: lấy mentor gợi ý cho dashboard.
- Response: `List<MentorRecommendationResponse>`
  - `mentor` (`MentorDiscoveryCardResponse`)
  - `matchScore`
  - `matchReasons`

### GET `/api/mentors`
- Auth: `bearerAuth`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
  - `keyword`
  - `tagIds`
  - `campusId`
  - `specializationId`
  - `teachingMode`
- Ghi chú:
  - `sortBy=relevance` là default cho discovery.
  - Hệ thống không dùng `isAvailable` như filter query chính thức ở endpoint này.
- Response: `PageResponse<MentorDiscoveryCardResponse>`
  - `mentorUserId`
  - `displayName`
  - `avatarUrl`
  - `headline`
  - `expertiseDescription`
  - `supportingSubjects`
  - `isAvailable`
  - `ratingAverage`
  - `reviewCount`
  - `completedSessions`
  - `teachingMode`
  - `verifiedAt`
  - `campusId`, `campusName`
  - `programId`, `programName`
  - `specializationId`, `specializationName`
  - `helpTopicTags`

### GET `/api/mentors/{mentorUserId}`
- Auth: `bearerAuth`
- Mục đích: xem detail public của mentor.
- Response: `MentorDiscoveryDetailResponse`
  - `mentorUserId`
  - `displayName`
  - `avatarUrl`
  - `headline`
  - `bio`
  - `expertiseDescription`
  - `supportingSubjects`
  - `isAvailable`
  - `bookingSuspendedUntil`
  - `ratingAverage`
  - `reviewCount`
  - `completedSessions`
  - `teachingMode`
  - `defaultSessionDuration`
  - `verifiedAt`
  - `campusId`, `campusName`
  - `programId`, `programName`
  - `specializationId`, `specializationName`
  - `semester`
  - `alumni`
  - `portfolioUrl`, `linkedinUrl`, `githubUrl`
  - `helpTopicTags`
  - `services`

### GET `/api/mentors/{mentorUserId}/availability`
- Auth: `bearerAuth`
- Legacy alias của `/api/mentors/{mentorUserId}/availability-slots`.
- Nên bỏ khỏi flow FE mới để tránh trùng contract.
- Query params:
  - `fromDate`
  - `toDate`
- Response: `List<MentorAvailabilitySlotResponse>`
  - `slotId`
  - `startTime`
  - `endTime`
  - `timezone`
  - `durationMinutes` (legacy convenience field, suy ra từ `endTime - startTime`)
  - `teachingMode`
  - `pendingRequestCount` (tổng số request `PENDING` hiện có trong parent slot)
  - `acceptedSlotCount` (số booking `ACCEPTED` hiện có trong parent slot)
  - `maxPendingRequests` (legacy, FE mới không nên dùng ở parent slot)
  - `remainingRequestSlots` (legacy, FE mới không nên dùng ở parent slot)
  - `services` (mỗi service có `serviceId`, `durationMinutes`, giá)

### GET `/api/mentors/{mentorUserId}/availability-slots`
- Auth: `bearerAuth`
- Endpoint chính FE nên dùng.
- Trả về parent slots và danh sách `services` đã gắn vào từng slot.
- Không nhận `serviceId` ở bước này vì đây là bước chọn slot cha trước.
- Sau khi user chọn 1 service trong `services`, FE gọi `GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId=...`.

### GET `/api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates`
- Auth: `bearerAuth`
- Query params:
  - `serviceId` `UUID`
- Response: `ServiceSlotCandidatesResponse`
  - `slotId`
  - `serviceId`
  - `serviceDurationMinutes`
  - `candidateServiceSlots`
    - `startTime`
    - `endTime`
    - `pendingCount`
    - `remainingPendingQuota`
    - `isSelectable`
    - `reasonIfBlocked`
    - `blockedByAcceptedBooking`
    - `blockingBookingId`
    - `blockingServiceId`
    - `blockingServiceTitle`
    - `blockedBySameService`
    - `blockedByDifferentService`
    - `bookingConflictNote`

### GET `/api/mentors/{mentorUserId}/reviews`
- Auth: `bearerAuth`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
- Response: `PageResponse<MentorReviewResponse>`

## 8. Mentor Verification - User Flow

### POST `/api/me/mentor-verification/request`
- Auth: `bearerAuth`
- Mục đích: khởi tạo hoặc lấy hồ sơ xác thực mentor hiện tại.
- Response: `MentorVerificationRequestResponse`
  - `requestId`
  - `mentorUserId`
  - `status`
  - `submitNote`
  - `reviewNote`
  - `rejectionReason`
  - `revisionCount`
  - `submittedAt`
  - `termsAcceptedAt`
  - `termsVersion`
  - `reviewedAt`
  - `createdAt`
  - `updatedAt`
  - `documents`
  - `timeline`
  - `checklist`
  - `allowedActions`

### GET `/api/me/mentor-verification`
- Auth: `bearerAuth`
- Mục đích: lấy hồ sơ mentor verification mới nhất của user, gồm cả request đã kết thúc để FE khôi phục wizard.

### GET `/api/me/mentor-verification/timeline`
- Auth: `bearerAuth`
- Mục đích: lấy timeline của request mới nhất.

### GET `/api/me/mentor-verification/documents/{documentId}`
- Auth: `bearerAuth`
- Mục đích: lấy chi tiết một document thuộc request mới nhất.

### POST `/api/me/mentor-verification/documents`
- Auth: `bearerAuth`
- Content-Type: `application/json`
- Mục đích: lưu metadata của file minh chứng sau khi FE upload file lên Cloudinary.
- Request body:
  - `MentorVerificationDocumentUploadRequest`
    - `documentType` `VerificationDocumentType`
      - `FPTU_AFFILIATION_PROOF`
      - `EXPERTISE_PROOF`
    - `fileUrl` `string`
    - `publicId` `string`
    - `originalFilename` `string`
    - `contentType` `string`
    - `sizeBytes` `Long`
- Response: `MentorVerificationRequestResponse`

### POST `/api/me/mentor-verification/submit`
- Auth: `bearerAuth`
- Request body:
  - `MentorVerificationSubmitRequest`
    - `submitNote` `string` nullable
    - `termsAccepted` `boolean`

### DELETE `/api/me/mentor-verification/documents/{documentId}`
- Auth: `bearerAuth`
- Mục đích: xóa mềm document khỏi request hiện tại.

### POST `/api/me/mentor-verification/withdraw`
- Auth: `bearerAuth`
- Mục đích: rút request mentor verification hiện tại.

## 9. Mentor Verification - Admin Flow

### GET `/api/admin/mentor-verification/requests`
- Auth: `bearerAuth`, role `ADMIN`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
  - `status` (default `PENDING_REVIEW`)
  - `keyword`
  - `submittedFrom`
  - `submittedTo`
- Ghi chú:
  - mặc định sort theo `submittedAt ASC`
- Response: `PageResponse<AdminMentorVerificationQueueItemResponse>`
  - `requestId`
  - `mentorUserId`
  - `mentorEmail`
  - `mentorFullName`
  - `mentorAvatarUrl`
  - `status`
  - `revisionCount`
  - `submittedAt`
  - `createdAt`
  - `updatedAt`

### GET `/api/admin/mentor-verification/requests/{requestId}`
- Auth: `bearerAuth`, role `ADMIN`
- Response: `AdminMentorVerificationRequestResponse`
  - `requestId`
  - `mentorUserId`
  - `mentorEmail`
  - `mentorFullName`
  - `mentorAvatarUrl`
  - `status`
  - `submitNote`
  - `reviewNote`
  - `rejectionReason`
  - `revisionCount`
  - `reviewerEmail`
  - `lockedByAdminEmail`
  - `lockedAt`
  - `lockExpiresAt`
  - `canReview`
  - `submittedAt`
  - `termsAcceptedAt`
  - `termsVersion`
  - `reviewedAt`
  - `approvedAt`
  - `withdrawnAt`
  - `createdAt`
  - `updatedAt`
  - `documents`
  - `timeline`
  - `checklist`
  - `mentorProfile`
  - `studentProfile`

### GET `/api/admin/mentor-verification/requests/{requestId}/lock`
- Auth: `bearerAuth`, role `ADMIN`
- Response: `AdminMentorVerificationLockResponse`

### POST `/api/admin/mentor-verification/requests/{requestId}/lock/refresh`
- Auth: `bearerAuth`, role `ADMIN`

### POST `/api/admin/mentor-verification/requests/{requestId}/request-revision`
- Auth: `bearerAuth`, role `ADMIN`
- Request body:
  - `AdminMentorVerificationReviewRequest`
    - `note` `string`

### POST `/api/admin/mentor-verification/requests/{requestId}/approve`
- Auth: `bearerAuth`, role `ADMIN`
- Request body:
  - `AdminMentorVerificationReviewRequest` nullable

### POST `/api/admin/mentor-verification/requests/{requestId}/reject`
- Auth: `bearerAuth`, role `ADMIN`
- Request body:
  - `AdminMentorVerificationReviewRequest`
    - `note` `string`

## 10. Booking

### POST `/api/bookings`
- Auth: `bearerAuth`
- Defense-in-depth:
  - `ADMIN` and `SYSTEM_ADMIN` bị chặn ở controller
  - `MENTOR` vẫn có thể tạo booking như một mentee hợp lệ nếu business rule cho phép
- Request body:
  - `CreateBookingRequest`
    - `availabilitySlotId` `UUID`
    - `serviceId` `UUID`
    - `selectedStartTime` `LocalDateTime`
    - `selectedEndTime` `LocalDateTime`
    - `learningGoalTitle` `string`
    - `learningGoalDescription` `string` nullable
- Response:
  - `BookingResponse`

> Ghi chú contract:
> - `sessionId` và `sessionStatus` là field legacy alias để giữ tương thích ngược.
> - `actualSessionId` và `actualSessionStatus` là field nguồn sự thật cho session thật do backend tạo từ booking.
> - FE mới nên ưu tiên đọc `actualSessionId` và `actualSessionStatus` khi có mặt.

### GET `/api/me/bookings`
- Auth: `bearerAuth`
- Query params:
  - `page`, `size`, `sortBy`, `direction`, `status`, `role`
- Mục đích: xem booking theo góc nhìn mentee hoặc mentor.

### GET `/api/me/bookings/{bookingId}`
- Auth: `bearerAuth`

### POST `/api/me/bookings/{bookingId}/cancel`
- Auth: `bearerAuth`
- Request body:
  - `CancelBookingRequest`
    - `cancelReason` `string`
- Rule:
  - booking `PENDING`: mentee hủy tự do
  - booking `ACCEPTED`:
    - trước `6h`: refund `100%` về ví Scoin mentee
    - sau `6h`: mentee mất `50%`, trong đó `35%` vào ví mentor, `15%` commission app

### POST `/api/me/bookings/{bookingId}/complete`
- Auth: `bearerAuth`
- Mục đích: alias chuyển tiếp:
  - nếu current user là mentor -> mentor complete
  - nếu là participant còn lại -> confirm sau session
- Request body:
  - `CompleteBookingRequest`
    - `completionNote` `string` nullable

### POST `/api/me/bookings/{bookingId}/confirm`
- Auth: `bearerAuth`
- Request body:
  - `ConfirmBookingRequest`
    - `confirmationNote` `string` nullable

### POST `/api/me/bookings/{bookingId}/issue`
- Auth: `bearerAuth`
- Request body:
  - `SubmitBookingIssueRequest`
    - `issueType` `BookingIssueType`
      - hiện có: `NO_SHOW_OR_QUALITY_OR_OTHER`
    - `description` `string`
    - `wantsAdminReview` `boolean`
- Response:
  - `BookingIssueResponse`
    - `bookingId`
    - `status`
    - `issueSubmittedAt`

### Mentor actions

#### POST `/api/mentor/bookings/{bookingId}/accept`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `AcceptBookingRequest`
    - `mentorResponseNote` `string` nullable

#### POST `/api/mentor/bookings/{bookingId}/reject`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `RejectBookingRequest`
    - `rejectReason` `string`
    - `mentorResponseNote` `string` nullable

#### POST `/api/mentor/bookings/{bookingId}/cancel`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `CancelBookingRequest`
    - `cancelReason` `string`
- Rule:
  - chỉ hủy được khi booking đang `ACCEPTED`
  - mentor hủy thì mentee được refund `100%` về ví Scoin
  - mentor vẫn bị penalty theo mốc hệ thống:
    - `< 6h`: suspend nhận booking 3 ngày
    - `>= 6h` và `< 12h`: cộng `0.5` penalty points

#### POST `/api/mentor/bookings/{bookingId}/complete`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `CompleteBookingRequest`
    - `completionNote` `string` nullable

#### PATCH `/api/mentor/bookings/{bookingId}/meeting-link`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `SaveMeetingLinkRequest`
    - `meetingPlatform` `MeetingPlatform`
    - `meetingLink` `string`
    - `location` `string` nullable

### Admin booking

#### GET `/api/admin/bookings`
- Auth: `bearerAuth`, role `ADMIN`
- Query params:
  - `page`, `size`, `sortBy`, `direction`, `status`, `mentorUserId`, `menteeUserId`

#### GET `/api/admin/bookings/{bookingId}`
- Auth: `bearerAuth`, role `ADMIN`

### Booking Reschedule

#### POST `/api/me/bookings/{bookingId}/reschedule-requests`
- Auth: `bearerAuth`
- Mục đích: mentee tạo reschedule request cho booking `ACCEPTED`
- Request body:
  - `CreateBookingRescheduleRequest`
    - `proposedSlotId` `UUID`
    - `proposedSelectedStartTime` `LocalDateTime`
    - `proposedSelectedEndTime` `LocalDateTime`
    - `reason` `string`
- Rule:
  - chỉ tạo được nếu còn ít nhất `6h` trước giờ học cũ
  - mỗi booking chỉ được reschedule tối đa `1 lần`
  - không được đổi service
  - không giữ slot tạm

#### GET `/api/me/bookings/{bookingId}/reschedule-requests`
- Auth: `bearerAuth`
- Mục đích: mentee/mentor xem lịch sử reschedule của booking

#### POST `/api/me/bookings/reschedule-requests/{requestId}/accept`
- Auth: `bearerAuth`
- Mục đích: participant còn lại accept request
- Request body:
  - `RespondBookingRescheduleRequest`
    - `reason` `string`
- Rule:
  - mentee tạo -> chỉ mentor accept
  - mentor tạo -> chỉ mentee accept
  - requester không được tự accept
  - request chỉ còn hiệu lực đến mốc `currentStartTime - 2h`

#### POST `/api/me/bookings/reschedule-requests/{requestId}/reject`
- Auth: `bearerAuth`
- Mục đích: participant còn lại reject request
- Request body:
  - `RespondBookingRescheduleRequest`
    - `reason` `string`

#### POST `/api/mentor/bookings/{bookingId}/reschedule-requests`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: mentor tạo reschedule request cho booking `ACCEPTED`

#### POST `/api/mentor/bookings/reschedule-requests/{requestId}/accept`
- Auth: `bearerAuth`, role `MENTOR`

#### POST `/api/mentor/bookings/reschedule-requests/{requestId}/reject`
- Auth: `bearerAuth`, role `MENTOR`

#### GET `/api/admin/bookings/{bookingId}/reschedule-requests`
- Auth: `bearerAuth`, role `ADMIN`
- Mục đích: admin xem history reschedule của booking khi cần support/dispute

#### POST `/api/admin/bookings/reschedule-requests/{requestId}/force-approve`
- Auth: `bearerAuth`, role `ADMIN`
- Request body:
  - `RespondBookingRescheduleRequest`
    - `reason` `string`
- Rule:
  - admin chỉ force approve request đã tồn tại
  - bắt buộc có `reason`
  - có audit log

#### POST `/api/admin/bookings/reschedule-requests/{requestId}/force-reject`
- Auth: `bearerAuth`, role `ADMIN`
- Request body:
  - `RespondBookingRescheduleRequest`
    - `reason` `string`
- Rule:
  - admin chỉ force reject request đã tồn tại
  - bắt buộc có `reason`
  - có audit log

## 11. Booking Availability / Slot-Service Mapping

### PUT `/api/me/availability-slots/{slotId}/services`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: mentor thay toàn bộ danh sách service được gắn vào một availability slot.
- Request body:
  - `ReplaceAvailabilitySlotServicesRequest`
    - `serviceIds` `List<UUID>`
- Response:
  - `MentorManagedAvailabilitySlotResponse`

> Ghi chú: hiện tại code public chỉ có endpoint gắn service vào slot. Các method `getMyRules/createRule/updateRule/deleteRule` tồn tại trong service nội bộ nhưng không có public controller.

## 12. Session Feedback / Review & Rating

### POST `/api/bookings/{bookingId}/feedback`
- Auth: `bearerAuth`
- Mục đích: mentee gửi feedback cho booking đã hoàn thành.
- Request body:
  - `SubmitFeedbackRequest`
    - `rating` `integer` 1..5
    - `satisfactionLevel` `integer` 1..5 nullable
    - `comment` `string` nullable
    - `wouldRecommend` `boolean` nullable
    - `isPublic` `boolean` nullable
- Response:
  - `SessionFeedbackResponse`
- Ghi chú:
  - chỉ mentee của booking mới gửi được
  - booking phải ở trạng thái completed
  - một booking chỉ có một feedback

## 13. Conversation / Chat

### GET `/api/me/conversations`
- Auth: `bearerAuth`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
- Mục đích: lấy inbox conversation của user hiện tại.
- Response:
  - `PageResponse<ConversationResponse>`
    - `id`
    - `sourceType`
    - `sourceId`
    - `type`
    - `status`
    - `otherUserId`
    - `otherUserName`
    - `otherUserAvatarUrl`
    - `lastMessageContent`
    - `lastMessageAt`
    - `createdAt`

### GET `/api/me/conversations/{conversationId}/messages`
- Auth: `bearerAuth`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
- Response:
  - `PageResponse<MessageResponse>`
    - `id`
    - `conversationId`
    - `senderId`
    - `senderName`
    - `messageType`
    - `content`
    - `createdAt`
    - `isMine`
- Ghi chú:
  - API đang trả newest-first

### POST `/api/me/conversations/{conversationId}/messages`
- Auth: `bearerAuth`
- Request body:
  - `SendMessageRequest`
    - `content` `string`
- Response:
  - `MessageResponse`
- Ghi chú:
  - REST là source of truth
  - gửi xong sẽ đẩy realtime qua WebSocket

## 14. Notification

### GET `/api/me/notifications`
- Auth: `bearerAuth`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
  - `unreadOnly` `boolean` (mặc định `false`)
- Response:
  - `PageResponse<NotificationResponse>`
    - `notificationId`
    - `type`
    - `title`
    - `message`
    - `relatedEntityType`
    - `relatedEntityId`
    - `read`
    - `readAt`
    - `createdAt`

### GET `/api/me/notifications/unread-count`
- Auth: `bearerAuth`
- Response:
  - `UnreadCountResponse`
    - `unreadCount`

### PATCH `/api/me/notifications/{id}/read`
- Auth: `bearerAuth`

### PATCH `/api/me/notifications/read-all`
- Auth: `bearerAuth`

## 15. Wallet

### GET `/api/me/credit-wallet`
- Auth: `bearerAuth`, role `MENTEE`
- Mục đích: xem ví Scoin của mentee/user hiện tại.
- Response:
  - `CreditWalletResponse`
    - `availableScoin`
    - `recentTransactions` (tối đa 15 giao dịch gần nhất)
      - `id`
      - `entryType`
      - `originType`
      - `sourceType`
      - `sourceId`
      - `amountScoin`
      - `balanceEffectScoin`
      - `memo`
      - `createdAt`

### GET `/api/me/mentor-wallet`
- Auth: `bearerAuth`, role `MENTOR`
- Mục đích: xem settlement earnings của mentor.
- Response:
  - `MentorWalletResponse`
    - `availableScoin`
    - `recentTransactions` (tối đa 15 giao dịch gần nhất)
      - cùng shape như `WalletTransactionResponse`

## 16. Payment / PayOS

### POST `/api/me/payment-orders/checkout`
- Auth: `bearerAuth`
- Mục đích: tạo payment order cho booking và trả hosted payment link.
- Request body:
  - `PaymentCheckoutRequest`
    - `bookingId` `UUID`
    - `couponCode` `string` nullable
- Response:
  - `PaymentCheckoutResponse`
    - `paymentOrderId`
    - `orderCode`
    - `bookingId`
    - `attemptNo`
    - `basePriceScoin`
    - `couponDiscountScoin`
    - `campaignCreditAppliedScoin`
    - `userCreditAppliedScoin`
    - `remainingPayableScoin`
    - `remainingPayableVnd`
    - `status`
    - `paymentProvider`
    - `providerOrderCode`
    - `providerPaymentLinkId`
    - `providerStatus`
    - `checkoutUrl`
    - `paymentLink`
    - `expiresAt`

### GET `/api/me/payment-orders/{bookingId}`
- Auth: `bearerAuth`
- Mục đích: poll trạng thái payment order theo booking.

### POST `/api/payments/webhook/payos`
- Auth: Public
- Mục đích: nhận webhook PayOS, verify chữ ký, idempotent, chốt trạng thái payment.
- Request body:
  - `PaymentWebhookRequest`
    - `code`
    - `desc`
    - `success`
    - `data.orderCode`
    - `signature`
    - các field data khác do PayOS gửi

## 17. Payout Profiles / Payout Requests

### POST `/api/mentor/payout-profiles`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `MentorPayoutProfileUpsertRequest`
    - `accountHolderName`
    - `bankCode` nullable
    - `bankName`
    - `accountNumber`
    - `isDefault` nullable
    - `isActive` nullable

### PUT `/api/mentor/payout-profiles/{payoutProfileId}`
- Auth: `bearerAuth`, role `MENTOR`

### GET `/api/mentor/payout-profiles`
- Auth: `bearerAuth`, role `MENTOR`

### POST `/api/mentor/payout-requests`
- Auth: `bearerAuth`, role `MENTOR`
- Request body:
  - `PayoutRequestCreateRequest`
    - `amountScoin` `integer`
    - `payoutProfileId` `UUID` nullable
    - `note` `string` nullable
- Response:
  - `PayoutRequestResponse`
    - `payoutRequestId`
    - `mentorUserId`
    - `settlementAccountId`
    - `payoutProfileId`
    - `amountScoin`
    - `status`
    - `bankAccountNameSnapshot`
    - `bankNameSnapshot`
    - `bankAccountNumberMaskedSnapshot`
    - `adminUserId`
    - `adminNote`
    - `requestedAt`
    - `reviewedAt`
    - `approvedAt`
    - `paidAt`
    - `rejectedAt`

### GET `/api/mentor/payout-requests`
- Auth: `bearerAuth`, role `MENTOR`

### POST `/api/admin/payout-requests/{payoutRequestId}/approve`
- Auth: `bearerAuth`, role `ADMIN` hoặc `SYSTEM_ADMIN`
- Request body:
  - `AdminNoteRequest` nullable

### POST `/api/admin/payout-requests/{payoutRequestId}/reject`
- Auth: `bearerAuth`, role `ADMIN` hoặc `SYSTEM_ADMIN`
- Request body:
  - `AdminNoteRequest` nullable

### POST `/api/admin/payout-requests/{payoutRequestId}/mark-paid`
- Auth: `bearerAuth`, role `ADMIN` hoặc `SYSTEM_ADMIN`
- Request body:
  - `AdminNoteRequest` nullable

## 18. System Admin / Admin User Management

### POST `/api/system/users/admin-role/grant`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Request body:
  - `AdminRoleChangeRequest`
    - `email`
- Behavior:
  - Nếu user đã có `ADMIN` thì trả `409 Conflict`.
  - Nếu cấp thành công, backend gỡ `MENTEE` và `MENTOR` khỏi user để tài khoản trở thành admin-only.

### POST `/api/system/users/admin-role/revoke`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Request body:
  - `AdminRoleChangeRequest`
    - `email`
- Behavior:
  - Nếu user chưa có `ADMIN` thì trả `409 Conflict`.
  - Nếu thu hồi thành công, backend gỡ `ADMIN`, gỡ `MENTOR` nếu còn dữ liệu cũ, và gán lại `MENTEE` mặc định.

### GET `/api/system/users/admins`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Mục đích: xem danh sách user đang có quyền ADMIN.
- Response: `PageResponse<AdminUserResponse>`

### GET `/api/system/users`
- Auth: `bearerAuth`, role `SYSTEM_ADMIN`
- Mục đích: xem toàn bộ system users.
- Response: `PageResponse<SystemUserResponse>`

### GET `/api/admin/users`
- Auth: `bearerAuth`, role `ADMIN` hoặc `SYSTEM_ADMIN`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
  - `keyword`
  - `role` (chỉ `MENTEE` hoặc `MENTOR`)
  - `status`
- Mục đích: admin xem danh sách user thường, chỉ trả user có role MENTEE/MENTOR; loại admin/sysadmin khỏi list.
- Response:
  - `PageResponse<AdminUserListItemResponse>`
    - `userId`
    - `email`
    - `fullName`
    - `avatarUrl`
    - `status`
    - `roles`
    - `lastLoginAt`
    - `createdAt`
    - `academicProfile.claimedStudentCode`

### POST `/api/admin/users/{userId}/ban`
- Auth: `bearerAuth`, role `ADMIN` hoặc `SYSTEM_ADMIN`
- Request body:
  - `BanUserRequest`
    - `reason`

### POST `/api/admin/users/{userId}/unban`
- Auth: `bearerAuth`, role `ADMIN` hoặc `SYSTEM_ADMIN`
- Request body:
  - `UnbanUserRequest`
    - `reason`

## 19. Admin Mentors

### GET `/api/admin/mentors`
- Auth: `bearerAuth`, role `ADMIN`
- Query params:
  - `page`, `size`, `sortBy`, `direction`
  - `keyword`
  - `status`
  - `isAvailable`
- Mục đích: danh sách mentor cho vận hành.
- Response: `PageResponse<AdminMentorListItemResponse>`

### GET `/api/admin/mentors/{mentorUserId}`
- Auth: `bearerAuth`, role `ADMIN`
- Response: `AdminMentorDetailResponse`

## 20. Realtime WebSocket

- Endpoint handshake: `wss://api.skillswap.asia/ws?token=<accessToken>`
- Kênh hiện tại là **raw WebSocket**, không phải SockJS/STOMP.
- Lưu ý:
  - chỉ dùng **access token**
  - `?token=` chỉ dành cho `/ws`
  - FE nên reconnect nhẹ và resync lại REST sau reconnect

### Message types hiện có

- Client -> server:
  - `PING`
- Server -> client:
  - `AUTH_OK`
  - `PONG`
  - `ERROR`
  - `CHAT_MESSAGE_CREATED`
  - `NEW_NOTIFICATION`
  - `NOTIFICATION_BADGE_UPDATED`

### Payload realtime

- `CHAT_MESSAGE_CREATED`
  - `conversationId`
  - `messageId`
  - `senderId`
  - `senderName`
  - `messageType`
  - `content`
  - `createdAt`
  - `conversationType`
  - `isSelf`
  - `unreadCount`
- `NEW_NOTIFICATION`
  - payload là `NotificationResponse`
  - realtime có thể bổ sung:
    - `unreadCount`
    - `realtimeEventKind`
- `NOTIFICATION_BADGE_UPDATED`
  - `unreadCount`
  - `eventKind`

## 21. Core status enums

### BookingStatus

- `PENDING`
- `ACCEPTED`
- `REJECTED`
- `EXPIRED`
- `CANCELLED_BY_MENTEE`
- `CANCELLED_BY_MENTOR`
- `AWAITING_MENTOR_COMPLETION`
- `AWAITING_MENTEE_CONFIRMATION`
- `COMPLETED`
- `AUTO_CLOSED`
- `UNDER_REVIEW`
- `NO_SHOW`

### VerificationStatus

- `DRAFT`
- `PENDING_REVIEW`
- `NEEDS_REVISION`
- `APPROVED`
- `REJECTED`
- `WITHDRAWN`

### PaymentOrderStatus

- `PENDING`
- `PARTIALLY_COVERED_BY_CREDIT`
- `AWAITING_PROVIDER_PAYMENT`
- `PAID`
- `FAILED`
- `CANCELLED`
- `EXPIRED`

### PayoutRequestStatus

- `REQUESTED`
- `APPROVED`
- `REJECTED`
- `PAID`
- `CANCELLED`

### MentorStatus

- `DRAFT`
- `PENDING_VERIFICATION`
- `ACTIVE`
- `PAUSED`
- `REJECTED`
- `SUSPENDED`

### TeachingMode

- `ONLINE`
- `OFFLINE`
- `HYBRID`

### MeetingPlatform

- `GOOGLE_MEET`
- `ZOOM`
- `MICROSOFT_TEAMS`
- `DISCORD`
- `OFFLINE`
- `OTHER`

### CreditOriginType

- `CAMPAIGN_BONUS`
- `COUPON_BONUS`
- `REFUND`
- `MANUAL`
- `PAYMENT_RESERVATION`

### LedgerEntryType

- `ISSUE`
- `RESERVE`
- `CONSUME`
- `RELEASE`
- `REFUND`
- `ADJUSTMENT`
- `HOLD`
- `PAID_OUT`
- `COMMISSION`
- `VOID`

### LedgerSourceType

- `PAYMENT_ORDER`
- `BOOKING`
- `CAMPAIGN`
- `COUPON`
- `MANUAL`
- `PAYOUT_REQUEST`
- `REFUND`

## 22. Ghi chú thực dụng cho FE

- `GET /api/auth/me` là API đầu tiên sau login.
- Mentor verification flow:
  - user: `request` -> upload document metadata -> submit -> timeline/detail -> delete/withdraw
  - admin: queue -> detail -> lock -> refresh -> request revision / approve / reject
- Discovery:
  - `GET /api/mentors/recommendations` cho dashboard
  - `GET /api/mentors` cho search/filter/ranking
  - `GET /api/mentors/{mentorUserId}/availability-slots` rồi `.../candidates` để tạo booking
- Booking:
  - mentee tạo booking bằng exact candidate segment
  - mentor accept/reject/cancel/complete trên booking của họ
  - mentee confirm hoặc issue trong cửa sổ sau session
- Chat:
  - REST là nguồn dữ liệu chính
  - WebSocket chỉ push realtime
- Notification:
  - đọc danh sách + unread count bằng REST
  - realtime chỉ là push bổ trợ
- Wallet:
  - mỗi ví chỉ trả số dư hiện tại và 15 giao dịch gần nhất
- Forum:
  - hiện **chưa thấy controller/API forum nào trong code**
