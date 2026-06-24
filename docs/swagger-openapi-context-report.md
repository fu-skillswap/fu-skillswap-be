# Báo cáo context phục vụ refactor Swagger/OpenAPI

## 1. Tổng quan dự án

`SkillSwap` là backend Spring Boot cho nền tảng mentoring giữa sinh viên và cựu sinh viên FPT University. Từ code hiện tại có thể xác định hệ thống tập trung vào các năng lực chính: xác thực Google + JWT, hồ sơ học thuật, hồ sơ mentor, xác thực mentor, khám phá mentor, đặt lịch mentoring, chat gắn với booking, thông báo, đánh giá sau buổi mentoring và vận hành admin.

Đối tượng người dùng chính:

- `Public/Auth user`: người dùng mới đăng nhập Google hoặc dùng các lookup public.
- `MENTEE`: vai trò mặc định sau khi đăng nhập Google.
- `MENTOR`: người dùng đã được duyệt mentor.
- `ADMIN`: vận hành nội bộ, đặc biệt cho mentor verification, booking và user moderation.
- `SYSTEM_ADMIN`: quản trị hệ thống, cấp/thu hồi role `ADMIN`.

Luồng sản phẩm chính suy ra từ code:

1. User đăng nhập Google.
2. User hoàn thiện `Student Profile`.
3. Nếu muốn làm mentor, user tạo/cập nhật `Mentor Profile`, nộp hồ sơ `Mentor Verification`, chờ admin duyệt.
4. Khi mentor đã `ACTIVE`, mentee khám phá mentor, xem slot trống, tạo booking request.
5. Mentor accept/reject booking. Khi accept, hệ thống tự tạo `Session` và `Conversation`.
6. Sau buổi mentoring, hai bên có thể complete booking; mentee gửi feedback cho mentor.
7. Các sự kiện chính sinh ra `Notification`.

## 2. Cấu trúc module hiện tại

| Module | Package/Folder liên quan | Controller chính | Service chính | Entity chính | Mục đích nghiệp vụ |
| ------ | ------------------------ | ---------------- | ------------- | ------------ | ------------------ |
| Authentication / Identity | `modules.identity`, `infrastructure.security`, `infrastructure.config` | `AuthController` | `IdentityService`, `GoogleAuthService`, `IdentityLoginTransactionService` | `User`, `UserRole`, `UserSession`, `OauthAccount` | Đăng nhập Google, phát hành JWT, refresh token, logout, trả thông tin user hiện tại |
| Academic Catalog | `modules.academic`, `modules.catalog` | `AcademicController`, `CatalogController` | `AcademicService`, `CatalogService` | `Campus`, `AcademicProgram`, `Specialization`, `Tag` | Cấp dữ liệu lookup cho onboarding, discovery và mentor profile |
| Academic Profile | `modules.academic` | `StudentProfileController` | `AcademicService` | `StudentProfile` | Lưu hồ sơ học thuật của user và xác định profile completion |
| Mentor Profile | `modules.mentor` | `MentorProfileController`, `MentorServiceController` | `MentorProfileService`, `MentorServiceManagementService` | `MentorProfile`, `MentorService` | Quản lý hồ sơ mentor và các gói/dịch vụ mentoring |
| Mentor Verification | `modules.mentor`, `modules.filestorage`, `infrastructure.storage` | `MentorVerificationController`, `AdminMentorVerificationController` | `MentorVerificationService`, `AdminMentorVerificationService` | `MentorVerificationRequest`, `MentorVerificationDocument`, `MentorVerificationRequestEvent`, `StoredFile` | Nộp và duyệt hồ sơ xác thực mentor |
| Mentor Discovery | `modules.mentor`, `modules.booking`, `modules.feedback` | `MentorDiscoveryController` | `MentorDiscoveryService`, `MentorAvailabilityService` | `MentorProfile`, `MentorTag`, `MentorAvailabilitySlot`, `SessionFeedback` | Tìm mentor, xem profile public, availability và review |
| Booking & Session | `modules.booking`, `modules.session` | `BookingController`, `MyBookingController`, `MentorBookingController`, `MentorAvailabilityController`, `AdminBookingController` | `BookingService`, `MentorAvailabilityService`, `SessionService` | `Booking`, `MentorAvailabilityRule`, `MentorAvailabilitySlot`, `Session` | Tạo booking request, xử lý queue, quản lý lịch rảnh mentor, theo dõi session |
| Conversation / Chat | `modules.conversation` | `ChatController` | `ConversationService` | `Conversation`, `ConversationParticipant`, `Message` | Tạo và vận hành chat giữa mentor/mentee sau khi booking được accept |
| Notification | `modules.notification` | `NotificationController` | `NotificationService` | `Notification`, `EmailOutbox` | Trung tâm thông báo in-app cho user |
| Review / Rating | `modules.feedback` | `SessionFeedbackController` | `SessionFeedbackService` | `SessionFeedback` | Mentee đánh giá mentor sau booking đã hoàn thành |
| Admin Users / Roles | `modules.system` | `AdminUserController`, `SystemUserRoleController` | `AdminUserService`, `SystemUserRoleService` | `User`, `UserRole` | Quản trị user, ban/unban, cấp/thu hồi quyền admin |
| System / Shared | `shared`, `infrastructure.config`, `shared.exception` | `HealthController` | chưa có service riêng | `ApiResponse` không phải entity | Health check, response envelope, exception handling, OpenAPI config |

## 3. Danh sách API theo module

### Module: Authentication / Identity

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| POST | `/api/auth/google` | Google sign in | Public | Xác thực người dùng qua Google và tạo/cập nhật tài khoản hệ thống | `GoogleLoginRequest{idToken}` | `TokenResponse` trong `ApiResponse` | Cần ghi rõ refresh token trả qua cookie HttpOnly, body chỉ còn access token |
| POST | `/api/auth/refresh` | Refresh access token | Public với refresh token | Cấp lại access token từ refresh token hợp lệ | `RefreshTokenRequest` hoặc cookie | `TokenResponse` | Cần mô tả rõ có thể nhận token từ cookie hoặc body |
| POST | `/api/auth/logout` | Logout current session | Public với refresh token | Thu hồi refresh token hiện tại và kết thúc phiên đăng nhập | `RefreshTokenRequest` hoặc cookie | `ApiResponse<String>` | Cần document rõ access token không bị revoke ngay |
| GET | `/api/auth/me` | Get current user profile | Authenticated | Trả thông tin user hiện tại để FE quyết định onboarding/dashboard | không có body | `UserMeResponse` | Nên nhấn mạnh `profileCompleted` và `hasStudentProfile` dùng cho onboarding |

### Module: Academic Catalog

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/campuses` | List campuses | Public | Lấy danh sách cơ sở FPT để điền profile | query trống | `List<CampusResponse>` | Nên có response example |
| GET | `/api/academic-programs` | List academic programs | Public | Lấy danh sách ngành học để điền profile | query trống | `List<AcademicProgramResponse>` | Nên có response example |
| GET | `/api/specializations` | List all specializations | Public | Lấy toàn bộ chuyên ngành đang hoạt động | query trống | `List<SpecializationResponse>` | Nên nêu rõ khi nào dùng API này thay vì API theo program |
| GET | `/api/academic-programs/{programId}/specializations` | List specializations by program | Public | Lấy chuyên ngành thuộc một ngành cụ thể | `programId` | `List<SpecializationResponse>` | Nên có example `programId` và lỗi 404 rõ format |
| GET | `/api/catalog/help-topics` | List help topics | Public | Cấp danh mục chủ đề hỗ trợ cho mentor profile/service | query trống | `List<HelpTopicResponse>` | Tag hiện đang gộp chung với Academic Catalog, cần mô tả rõ hơn đây là mentor catalog |

### Module: Academic Profile

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/me/student-profile` | Get my academic profile | Authenticated | Lấy hồ sơ học thuật hiện tại của user | không có body | `StudentProfileResponse` | Nên có response example đầy đủ object lồng nhau |
| PUT | `/api/me/student-profile` | Upsert my academic profile | Authenticated | Tạo/cập nhật hồ sơ học thuật dùng cho onboarding và eligibility | `StudentProfileRequest` | `StudentProfileResponse` | Cần example request thực tế và business rule về MSSV, `graduationYear`, `specializationId` |

### Module: Mentor Profile

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/me/mentor-profile` | Get my mentor profile | Authenticated | Lấy hồ sơ mentor hiện tại để hiển thị onboarding/trạng thái sẵn sàng | không có body | `MentorProfileResponse` | Nên ghi rõ trường hợp `data.exists=false` |
| PUT | `/api/me/mentor-profile` | Upsert my mentor profile | Authenticated | Tạo/cập nhật hồ sơ mentor nền tảng trước khi mở discovery và verification | `MentorProfileUpsertRequest` | `MentorProfileResponse` | Cần mô tả chi tiết field required, phone number VN, `sessionDuration` hợp lệ |
| GET | `/api/me/mentor-services` | List my mentor services | `MENTOR` | Lấy danh sách dịch vụ mentoring của mentor hiện tại | query trống | `List<MentorServiceResponse>` | Nên có note role `MENTOR` rõ ràng |
| GET | `/api/me/mentor-services/{serviceId}` | Get my mentor service detail | `MENTOR` | Xem chi tiết một dịch vụ mentoring của mình | `serviceId` | `MentorServiceResponse` | Nên có lỗi 404/403 document |
| POST | `/api/me/mentor-services` | Create mentor service | `MENTOR` | Tạo gói/dịch vụ mentoring để mentee có thể booking theo dịch vụ | `MentorServiceUpsertRequest` | `MentorServiceResponse` | Nên trả `201` thay vì `200` trong doc hoặc ít nhất document chính xác hành vi hiện tại |
| PUT | `/api/me/mentor-services/{serviceId}` | Update mentor service | `MENTOR` | Cập nhật nội dung và topic của dịch vụ mentoring | `serviceId`, `MentorServiceUpsertRequest` | `MentorServiceResponse` | Cần error cases rõ hơn |
| PATCH | `/api/me/mentor-services/{serviceId}/active` | Change mentor service active status | `MENTOR` | Bật/tắt khả năng nhận booking của một dịch vụ | `MentorServiceActiveRequest` | `MentorServiceResponse` | Cần example request nhỏ |
| DELETE | `/api/me/mentor-services/{serviceId}` | Soft delete mentor service | `MENTOR` | Ngừng sử dụng một dịch vụ mentoring cũ | `serviceId` | `MentorServiceResponse` | Nên ghi rõ là soft delete |

### Module: Mentor Verification

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| POST | `/api/me/mentor-verification/request` | Open my mentor verification request | Authenticated | Khởi tạo hoặc lấy lại hồ sơ xác thực mentor đang còn hoạt động | không có body | `MentorVerificationRequestResponse` | Cần mô tả rõ khi nào trả `200` vs `201` |
| GET | `/api/me/mentor-verification` | Get latest mentor verification request | Authenticated | Lấy hồ sơ xác thực mentor mới nhất kể cả đã kết thúc | query trống | `MentorVerificationRequestResponse` | Cần response example lớn vì object có checklist, timeline, documents |
| GET | `/api/me/mentor-verification/timeline` | Get mentor verification timeline | Authenticated | Xem lịch sử xử lý hồ sơ xác thực mentor | query trống | `List<MentorVerificationTimelineEventResponse>` | Cần giải thích ý nghĩa từng event/status |
| GET | `/api/me/mentor-verification/documents/{documentId}` | Get mentor verification document detail | Authenticated | Xem chi tiết metadata của một minh chứng đã lưu | `documentId` | `MentorVerificationDocumentResponse` | Nên có example response |
| POST | `/api/me/mentor-verification/documents` | Save uploaded verification document metadata | Authenticated | Lưu metadata file minh chứng FE đã upload sẵn lên storage ngoài | `MentorVerificationDocumentUploadRequest` | `MentorVerificationRequestResponse` | Cần mô tả rõ đây không phải multipart upload từ BE |
| POST | `/api/me/mentor-verification/submit` | Submit mentor verification request | Authenticated | Nộp hồ sơ xác thực mentor để admin review | `MentorVerificationSubmitRequest` | `MentorVerificationRequestResponse` | Cần mô tả checklist submit, terms version, điều kiện profile/document |
| DELETE | `/api/me/mentor-verification/documents/{documentId}` | Remove verification document | Authenticated | Xóa mềm minh chứng khỏi hồ sơ đang sửa | `documentId` | `MentorVerificationRequestResponse` | Cần nêu chỉ cho phép ở `DRAFT`/`NEEDS_REVISION` |
| POST | `/api/me/mentor-verification/withdraw` | Withdraw mentor verification request | Authenticated | Rút hồ sơ xác thực khi chưa muốn tiếp tục flow | không có body | `MentorVerificationRequestResponse` | Cần nêu rule lock khi admin đang xử lý |

### Module: Mentor Discovery

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/mentors/recommendations` | Get recommended mentors | Authenticated | Lấy danh sách mentor gợi ý nhanh cho dashboard dựa trên profile mentee và quality signals | `limit` | `List<MentorRecommendationResponse>` | Cần mô tả default/max limit và logic gợi ý ở mức business |
| GET | `/api/mentors` | Search mentors | Authenticated | Tìm kiếm, lọc và xếp hạng mentor trên trang discovery | `MentorDiscoverySearchRequest` qua query | `PageResponse<MentorDiscoveryCardResponse>` | Cần document rõ các sort hợp lệ, query params, behavior keyword normalize |
| GET | `/api/mentors/{mentorUserId}` | Get mentor public detail | Authenticated | Xem trang chi tiết public của mentor có thể booking | `mentorUserId` | `MentorDiscoveryDetailResponse` | Cần nêu chỉ mentor discoverable mới truy cập được |
| GET | `/api/mentors/{mentorUserId}/availability` | Get mentor visible availability | Authenticated | Lấy slot còn hiển thị để mentee chuẩn bị booking | `mentorUserId`, `fromDate`, `toDate` | `List<MentorAvailabilitySlotResponse>` | Cần mô tả giới hạn 31 ngày và queue metadata |
| GET | `/api/mentors/{mentorUserId}/reviews` | Get mentor public reviews | Authenticated | Xem review công khai của mentor để hỗ trợ quyết định booking | `mentorUserId`, paging | `PageResponse<MentorReviewResponse>` | Cần document sort/paging chuẩn |

### Module: Booking & Session

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| POST | `/api/bookings` | Create booking request | Authenticated, user hợp lệ để book | Tạo yêu cầu booking giữa mentee và mentor cho một slot và service cụ thể | `CreateBookingRequest` | `BookingResponse` | Cần mô tả quota pending per mentee/slot, điều kiện mentor discoverable, overlap accepted booking |
| GET | `/api/me/bookings` | List my bookings | Authenticated | Xem danh sách booking theo góc nhìn mentee hoặc mentor | `BookingListRequest` | `PageResponse<BookingResponse>` | Cần mô tả query `role`, `status`, sort fields |
| GET | `/api/me/bookings/{bookingId}` | Get my booking detail | Authenticated | Xem chi tiết booking nếu user là mentee hoặc mentor của booking đó | `bookingId` | `BookingResponse` | Cần ghi rõ access scope |
| POST | `/api/me/bookings/{bookingId}/cancel` | Cancel my booking as mentee | Authenticated | Mentee hủy booking của mình với lý do bắt buộc | `CancelBookingRequest` | `BookingResponse` | Cần mô tả behavior khác nhau khi booking là `PENDING` hay `ACCEPTED` |
| POST | `/api/me/bookings/{bookingId}/complete` | Complete my booking | Authenticated | Đánh dấu buổi mentoring đã hoàn thành | `CompleteBookingRequest` | `BookingResponse` | Cần nêu booking phải `ACCEPTED` và tới thời điểm phù hợp |
| POST | `/api/mentor/bookings/{bookingId}/accept` | Accept booking request | `MENTOR` | Mentor chấp nhận booking đang chờ, chốt slot và auto reject các request pending còn lại cùng slot | `AcceptBookingRequest` | `BookingResponse` | Đây là API quan trọng cần business note rất rõ |
| POST | `/api/mentor/bookings/{bookingId}/reject` | Reject booking request | `MENTOR` | Mentor từ chối booking đang chờ phản hồi | `RejectBookingRequest` | `BookingResponse` | Cần note `rejectReason` bắt buộc |
| POST | `/api/mentor/bookings/{bookingId}/cancel` | Cancel accepted booking as mentor | `MENTOR` | Mentor hủy booking đã accept và backend tự áp penalty/suspension nếu hủy sát giờ | `CancelBookingRequest` | `BookingResponse` | Cần mô tả penalty windows 12h/6h |
| PATCH | `/api/mentor/bookings/{bookingId}/meeting-link` | Save meeting link | `MENTOR` | Mentor lưu hoặc cập nhật link/địa điểm buổi mentoring | `SaveMeetingLinkRequest` | `BookingResponse` | Cần document valid URL và `meetingPlatform` |
| GET | `/api/mentor/availability-rules` | List my availability rules | `MENTOR` | Lấy các rule lịch rảnh hiện tại của mentor | query trống | `List<AvailabilityRuleResponse>` | Cần example response |
| POST | `/api/mentor/availability-rules` | Create availability rule | `MENTOR` | Tạo rule mở lịch hoặc đóng lịch để hệ thống sinh slot | `UpsertAvailabilityRuleRequest` | `AvailabilityRuleResponse` | Cần mô tả `OPEN`/`CLOSED`, `NONE`/`DAILY`/`WEEKLY` |
| PUT | `/api/mentor/availability-rules/{ruleId}` | Update availability rule | `MENTOR` | Cập nhật rule lịch rảnh hiện có | `ruleId`, `UpsertAvailabilityRuleRequest` | `AvailabilityRuleResponse` | Cần mô tả reset future unbooked slots |
| DELETE | `/api/mentor/availability-rules/{ruleId}` | Disable availability rule | `MENTOR` | Tắt một rule lịch rảnh, không xóa booking đã tồn tại | `ruleId` | `ApiResponse<Void>` | Cần thống nhất response example khi `data=null` |
| GET | `/api/admin/bookings` | Admin list bookings | `ADMIN` | Theo dõi booking toàn hệ thống theo trạng thái hoặc người tham gia | `AdminBookingListRequest` | `PageResponse<BookingResponse>` | Cần document đầy đủ query filter/sort |
| GET | `/api/admin/bookings/{bookingId}` | Admin get booking detail | `ADMIN` | Kiểm tra chi tiết một booking để hỗ trợ vận hành | `bookingId` | `BookingResponse` | Nên có response example |

### Module: Conversation / Chat

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/me/conversations` | List my conversations | Authenticated | Lấy danh sách conversation của user hiện tại | paging query | `PageResponse<ConversationResponse>` | Hiện thiếu `@SecurityRequirement`, thiếu `@ApiResponses`, thiếu description business |
| GET | `/api/me/conversations/{conversationId}/messages` | List conversation messages | Authenticated, participant only | Lấy lịch sử tin nhắn của một conversation mà user tham gia | `conversationId`, paging | `PageResponse<MessageResponse>` | Cần nêu access rule và sort order hiện tại là mới nhất trước |
| POST | `/api/me/conversations/{conversationId}/messages` | Send conversation message | Authenticated, participant only | Gửi tin nhắn trong conversation đã tồn tại | `SendMessageRequest` | `MessageResponse` | Cần nêu conversation thường được tạo sau khi booking được accept |

### Module: Notification

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/me/notifications` | List my notifications | Authenticated | Lấy danh sách thông báo của user hiện tại | `unreadOnly`, pageable | `PageResponse<NotificationResponse>` | Cần mô tả query pageable và schema response đang thiếu `@Schema` |
| GET | `/api/me/notifications/unread-count` | Get unread notification count | Authenticated | Lấy số lượng thông báo chưa đọc để hiển thị badge | query trống | `UnreadCountResponse` | Nên có schema |
| PATCH | `/api/me/notifications/{id}/read` | Mark notification as read | Authenticated | Đánh dấu một thông báo là đã đọc | `id` | `ApiResponse<Void>` | Response code/message hiện custom khác chuẩn chung, cần document hoặc chuẩn hóa |
| PATCH | `/api/me/notifications/read-all` | Mark all notifications as read | Authenticated | Đánh dấu toàn bộ thông báo là đã đọc | không có body | `ApiResponse<Void>` | Tương tự trên |

### Module: Review / Rating

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| POST | `/api/bookings/{bookingId}/feedback` | Submit session feedback | Authenticated, mentee of booking | Cho phép mentee đánh giá mentor sau buổi mentoring đã hoàn thành | `SubmitFeedbackRequest` | `SessionFeedbackResponse` | Cần mô tả rõ chỉ mentee được gửi và chỉ 1 feedback/booking |

### Module: Admin Users / Roles

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/admin/users` | Admin list visible users | `ADMIN` hoặc `SYSTEM_ADMIN` | Xem danh sách user thường để vận hành, loại trừ tài khoản admin/system admin | `AdminUserListRequest` | `PageResponse<AdminUserListItemResponse>` | Cần mô tả rule filter role `MENTEE` vs `MENTOR` |
| POST | `/api/admin/users/{userId}/ban` | Ban user | `ADMIN` hoặc `SYSTEM_ADMIN` | Khóa tài khoản người dùng với lý do bắt buộc | `userId`, `BanUserRequest` | `SystemUserResponse` | Cần example request |
| POST | `/api/admin/users/{userId}/unban` | Unban user | `ADMIN` hoặc `SYSTEM_ADMIN` | Mở khóa tài khoản người dùng với lý do vận hành | `userId`, `UnbanUserRequest` | `SystemUserResponse` | Cần example request |
| POST | `/api/system/users/admin-role/grant` | Grant admin role | `SYSTEM_ADMIN` | Cấp quyền `ADMIN` cho user đã tồn tại trong hệ thống | `AdminRoleChangeRequest` | `AdminUserResponse` | Không nên tag chung với admin users nếu muốn FE dễ hiểu |
| POST | `/api/system/users/admin-role/revoke` | Revoke admin role | `SYSTEM_ADMIN` | Thu hồi quyền `ADMIN` của user | `AdminRoleChangeRequest` | `AdminUserResponse` | Nên document rõ `409` khi role state không phù hợp |
| GET | `/api/system/users/admins` | List admin users | `SYSTEM_ADMIN` | Xem danh sách user đang có quyền `ADMIN` | paging query | `PageResponse<AdminUserResponse>` | Cần mô tả sort/paging |
| GET | `/api/system/users` | List all system users | `SYSTEM_ADMIN` | Xem toàn bộ user của hệ thống | paging query | `PageResponse<SystemUserResponse>` | Cần giải thích khác gì với `/api/admin/users` |

### Module: Admin Mentor Verification

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/admin/mentor-verification/requests` | List mentor verification queue | `ADMIN` | Xem hàng chờ hồ sơ xác thực mentor theo filter/search vận hành | `AdminMentorVerificationQueueFilterRequest` | `PageResponse<AdminMentorVerificationQueueItemResponse>` | Cần mô tả đầy đủ query filter/sort |
| GET | `/api/admin/mentor-verification/requests/{requestId}` | Get mentor verification request detail | `ADMIN` | Xem chi tiết hồ sơ xác thực mentor và claim soft lock nếu đang pending | `requestId` | `AdminMentorVerificationRequestResponse` | Đây là API quan trọng cần mô tả side effect claim lock |
| GET | `/api/admin/mentor-verification/requests/{requestId}/lock` | Get verification request lock status | `ADMIN` | Xem hồ sơ đang bị admin nào lock tạm thời | `requestId` | `AdminMentorVerificationLockResponse` | Cần example response |
| POST | `/api/admin/mentor-verification/requests/{requestId}/lock/refresh` | Refresh verification request lock | `ADMIN` | Gia hạn lock thêm 5 phút cho admin đang xử lý | `requestId` | `AdminMentorVerificationLockResponse` | Cần note chỉ lock owner mới refresh hợp lệ |
| POST | `/api/admin/mentor-verification/requests/{requestId}/request-revision` | Request mentor revision | `ADMIN` | Yêu cầu mentor chỉnh sửa hồ sơ trên chính request hiện tại | `AdminMentorVerificationReviewRequest` | `AdminMentorVerificationRequestResponse` | Cần mô tả note/review note |
| POST | `/api/admin/mentor-verification/requests/{requestId}/approve` | Approve mentor verification | `ADMIN` | Duyệt hồ sơ xác thực mentor và hoàn tất flow | `requestId`, body optional | `AdminMentorVerificationRequestResponse` | Cần mô tả body optional |
| POST | `/api/admin/mentor-verification/requests/{requestId}/reject` | Reject mentor verification | `ADMIN` | Từ chối hồ sơ xác thực mentor và đóng request | `AdminMentorVerificationReviewRequest` | `AdminMentorVerificationRequestResponse` | Cần mô tả hậu quả nghiệp vụ sau reject |

### Module: Admin Mentors

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/api/admin/mentors` | Admin list mentors | `ADMIN` | Xem danh sách mentor phục vụ vận hành | `AdminMentorListRequest` | `PageResponse<AdminMentorListItemResponse>` | Cần mô tả rule mặc định ẩn `DRAFT` |
| GET | `/api/admin/mentors/{mentorUserId}` | Admin get mentor detail | `ADMIN` | Xem chi tiết hồ sơ mentor cho mục đích kiểm tra vận hành | `mentorUserId` | `AdminMentorDetailResponse` | Nên có response example |

### Module: System

| Method | Path | API name đề xuất | Role/Auth | Mục đích nghiệp vụ | Request chính | Response chính | Note Swagger cần bổ sung |
| ------ | ---- | ---------------- | --------- | ------------------ | ------------- | -------------- | ------------------------ |
| GET | `/health` | Health check | Thực tế: Authenticated theo `SecurityConfig` | Kiểm tra server còn hoạt động | không có body | `ResponseEntity<String>` | Swagger hiện mô tả public nhưng security config không permit endpoint này |

## 4. Luồng nghiệp vụ chính

### Luồng Authentication / Onboarding

1. User đăng nhập bằng Google qua `POST /api/auth/google`.
2. FE gọi `GET /api/auth/me` để lấy roles và cờ `profileCompleted`.
3. Nếu chưa hoàn thành hồ sơ học thuật, user điền `PUT /api/me/student-profile`.
4. Sau khi có `Student Profile`, user có thể dùng discovery/booking hoặc tiếp tục luồng mentor.

### Luồng Mentor Verification

1. User tạo/lấy draft verification request qua `POST /api/me/mentor-verification/request`.
2. User hoàn thiện `Mentor Profile` qua `PUT /api/me/mentor-profile`.
3. User upload metadata minh chứng qua `POST /api/me/mentor-verification/documents`.
4. User xem lại hồ sơ/timeline qua `GET /api/me/mentor-verification` và `GET /timeline`.
5. User nộp hồ sơ qua `POST /api/me/mentor-verification/submit`.
6. Admin xem queue qua `GET /api/admin/mentor-verification/requests`.
7. Admin mở detail và claim lock qua `GET /api/admin/mentor-verification/requests/{requestId}`.
8. Admin `approve`, `reject` hoặc `request-revision`.
9. Nếu `request-revision`, user sửa lại document/hồ sơ và submit lại trên cùng request.

### Luồng Mentor Service & Availability

1. Mentor đã `ACTIVE` tạo/cập nhật dịch vụ qua `/api/me/mentor-services`.
2. Mentor tạo rule lịch rảnh qua `POST /api/mentor/availability-rules`.
3. Hệ thống sinh `MentorAvailabilitySlot` từ rule.
4. Mentee xem slot qua `GET /api/mentors/{mentorUserId}/availability`.

### Luồng Mentor Discovery

1. User lấy recommendation dashboard qua `GET /api/mentors/recommendations`.
2. User search/filter mentor qua `GET /api/mentors`.
3. User vào trang detail mentor qua `GET /api/mentors/{mentorUserId}`.
4. User xem review mentor qua `GET /api/mentors/{mentorUserId}/reviews`.

### Luồng Mentor Booking

1. Mentee chọn mentor, service, slot.
2. Mentee tạo booking request qua `POST /api/bookings`.
3. Mentor xem danh sách booking của mình qua `GET /api/me/bookings?role=MENTOR` hoặc detail qua `/api/me/bookings/{bookingId}`.
4. Mentor accept qua `POST /api/mentor/bookings/{bookingId}/accept` hoặc reject qua `/reject`.
5. Khi accept, hệ thống:
   - chuyển booking sang `ACCEPTED`
   - auto reject các booking `PENDING` khác cùng slot
   - tạo `Session`
   - tạo `Conversation`
   - phát thông báo cho mentee
6. Mentor cập nhật link họp qua `PATCH /api/mentor/bookings/{bookingId}/meeting-link`.
7. Mentee hoặc mentor complete booking qua `POST /api/me/bookings/{bookingId}/complete`.
8. Mentee có thể hủy booking qua `/api/me/bookings/{bookingId}/cancel`; mentor có thể hủy booking đã accept qua `/api/mentor/bookings/{bookingId}/cancel`.

### Luồng Conversation / Chat

1. Conversation không được tạo thủ công từ API.
2. Khi booking được accept, `ConversationService.createDirectForAcceptedBooking` tự tạo conversation gắn `sourceType=BOOKING`.
3. Hai bên xem danh sách hội thoại qua `GET /api/me/conversations`.
4. Hai bên xem tin nhắn qua `GET /api/me/conversations/{conversationId}/messages`.
5. Hai bên gửi tin nhắn qua `POST /api/me/conversations/{conversationId}/messages`.

### Luồng Review / Rating

1. Booking phải ở trạng thái `COMPLETED`.
2. Mentee gửi feedback qua `POST /api/bookings/{bookingId}/feedback`.
3. Hệ thống cập nhật thống kê rating/review cho mentor.
4. Review public được hiển thị qua `GET /api/mentors/{mentorUserId}/reviews`.

### Luồng Notification

Notification được tạo gián tiếp từ service khi có sự kiện như:

- booking request created
- booking accepted
- booking auto rejected
- mentee cancel
- mentor cancel
- feedback received

User đọc thông báo qua `/api/me/notifications`, `/unread-count`, `/read`, `/read-all`.

### Luồng Admin User Management

1. Admin/System Admin xem user visible qua `GET /api/admin/users`.
2. Admin/System Admin ban/unban user qua `/ban` và `/unban`.
3. System Admin cấp/thu hồi quyền admin qua `/api/system/users/admin-role/grant` và `/revoke`.

## 5. Đề xuất cấu trúc Swagger Tag

| Swagger Tag | Mục đích | Controller/API nên gom vào |
| ----------- | -------- | -------------------------- |
| `Authentication` | APIs for Google sign-in, token refresh, logout, and reading the current authenticated user profile. | `AuthController` |
| `Academic Catalog` | APIs for loading campus, program, specialization, and shared dropdown catalog data used during onboarding. | `AcademicController`, cân nhắc tách help topics ra tag khác nếu muốn rõ hơn |
| `Help Topic Catalog` | APIs for loading mentor help topics used in mentor profile, mentor service, and discovery filters. | `CatalogController` |
| `Academic Profile` | APIs for creating and maintaining the current user's academic/student profile. | `StudentProfileController` |
| `Mentor Profile` | APIs for mentors or aspiring mentors to manage their mentor-facing profile and discoverability fields. | `MentorProfileController` |
| `Mentor Services` | APIs for mentors to create, update, activate, or archive mentoring services used during booking. | `MentorServiceController` |
| `Mentor Verification` | APIs for submitting and tracking mentor verification requests and supporting documents. | `MentorVerificationController` |
| `Mentor Discovery` | APIs for mentees to browse mentors, search/filter them, inspect public profiles, and read reviews. | `MentorDiscoveryController` except availability nếu muốn tách |
| `Mentor Availability` | APIs for mentors to manage availability rules and for mentees to view bookable availability slots. | `MentorAvailabilityController`, `GET /api/mentors/{mentorUserId}/availability` |
| `Mentor Booking` | APIs for mentees to create booking requests and for mentors/mentees to accept, reject, cancel, complete, or inspect bookings. | `BookingController`, `MyBookingController`, `MentorBookingController` |
| `Conversation` | APIs for listing conversations and sending or retrieving chat messages related to accepted bookings. | `ChatController` |
| `Notification` | APIs for reading, counting, and marking in-app notifications as read. | `NotificationController` |
| `Review & Rating` | APIs for mentees to submit reviews after completed mentoring sessions. | `SessionFeedbackController` |
| `Admin - Mentor Verification` | Admin workflow APIs for reviewing and deciding mentor verification requests. | `AdminMentorVerificationController` |
| `Admin - Mentors` | Admin operational APIs for browsing mentor records and mentor details. | `AdminMentorController` |
| `Admin - Users` | Admin operational APIs for user listing and account status actions such as ban/unban. | `AdminUserController` |
| `System Admin - Roles` | System-level APIs for granting or revoking admin privileges and listing admin accounts. | `SystemUserRoleController` |
| `Admin - Bookings` | Admin monitoring APIs for system-wide bookings and session records. | `AdminBookingController` |
| `System` | Health and technical diagnostics APIs. | `HealthController` |

Nhận xét:

- Tag hiện tại đang thiếu `Chat & Conversation` trong `OpenApiConfig`, dù controller có dùng tag này.
- Tag `Booking & Session` đang quá rộng, khó đọc cho FE vì trộn create booking, self booking, mentor actions, availability rules.
- Tag `Admin - Users` đang gộp cả admin moderation và system-admin role assignment, dễ gây hiểu nhầm về quyền truy cập.
- `CatalogController` đang dùng tag `Academic Catalog` dù API thực chất là help topics phục vụ mentor/discovery.

## 6. Chuẩn viết Swagger cho từng API quan trọng

### [POST] `/api/bookings`

**Purpose:**  
Cho phép mentee tạo yêu cầu đặt lịch với mentor dựa trên slot còn trống và dịch vụ mentoring đã chọn.

**Recommended Summary:**  
Create a booking request for a mentor slot

**Recommended Description:**  
Creates a new booking request in `PENDING` status for a visible mentor availability slot. The caller must have completed the academic profile, the selected mentor must still be discoverable and available, and the selected slot must still accept pending requests. This API does not guarantee the slot is reserved until the mentor accepts the request.

**Auth/Role:**  
Authenticated user. Backend rejects admin/system-admin accounts. User must have completed `Student Profile`.

**Request Example:**

```json
{
  "mentorUserId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
  "availabilitySlotId": "019f2234-aaaa-bbbb-cccc-1234567890ab",
  "serviceId": "019f3234-aaaa-bbbb-cccc-1234567890ab",
  "learningGoalTitle": "Review lộ trình học Spring Boot và chuẩn bị phỏng vấn intern",
  "learningGoalDescription": "Em muốn được góp ý CV backend, định hướng học PRJ301 và cách làm project REST API với PostgreSQL."
}
```

**Success Response Example:**

```json
{
  "timestamp": "2026-06-24 11:10:00",
  "status": 201,
  "code": "CREATED_0201",
  "message": "Tạo mới thành công",
  "data": {
    "bookingId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
    "status": "PENDING",
    "mentorUserId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
    "slotId": "019f2234-aaaa-bbbb-cccc-1234567890ab",
    "serviceId": "019f3234-aaaa-bbbb-cccc-1234567890ab",
    "learningGoalTitle": "Review lộ trình học Spring Boot và chuẩn bị phỏng vấn intern"
  }
}
```

**Common Error Cases:**

- `400`: request thiếu field bắt buộc hoặc dữ liệu learning goal không hợp lệ
- `401`: chưa đăng nhập
- `403`: tài khoản không được phép tạo booking
- `404`: mentor, slot hoặc service không tồn tại
- `409`: slot không còn nhận request, mentee vượt quota pending, mentor không còn sẵn sàng, hoặc slot không thuộc mentor/service đã chọn

**Swagger improvement note:**  
Cần mô tả quota rõ ràng: tối đa `5` booking `PENDING`/mentee, tối đa `3` booking `PENDING`/slot, không được trùng accepted booking.

### [POST] `/api/mentor/bookings/{bookingId}/accept`

**Purpose:**  
Mentor chấp nhận yêu cầu booking và chốt slot chính thức cho mentee.

**Recommended Summary:**  
Accept a pending booking request

**Recommended Description:**  
Accepts a pending booking that belongs to the current mentor. Once accepted, the slot becomes booked, all other pending requests for the same slot are automatically rejected, and the system creates the related session and direct conversation.

**Auth/Role:**  
Authenticated user with role `MENTOR`.

**Request Example:**

```json
{
  "mentorResponseNote": "Anh đã xem mục tiêu của em, mình sẽ tập trung vào phần REST API và mock interview."
}
```

**Success Response Example:**

```json
{
  "timestamp": "2026-06-24 11:20:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "bookingId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
    "status": "ACCEPTED",
    "acceptedAt": "2026-06-24T11:20:00",
    "mentorResponseNote": "Anh đã xem mục tiêu của em, mình sẽ tập trung vào phần REST API và mock interview."
  }
}
```

**Common Error Cases:**

- `401`: chưa đăng nhập
- `403`: người gọi không phải mentor của booking
- `404`: không tìm thấy booking
- `409`: booking không còn `PENDING` hoặc slot đã được accept bởi request khác

**Swagger improvement note:**  
Đây là API cần mô tả side effect rất rõ: auto reject sibling requests, tạo session, tạo conversation, gửi notification.

### [GET] `/api/mentors`

**Purpose:**  
Tìm kiếm và lọc mentor trên trang khám phá.

**Recommended Summary:**  
Search and filter discoverable mentors

**Recommended Description:**  
Returns a paginated mentor list for discovery. Supports keyword search, help topic filtering, campus filtering, specialization filtering, teaching mode filtering, and multiple sorting modes. Only mentors that are currently discoverable are returned.

**Auth/Role:**  
Authenticated user.

**Request Example:**

```json
{}
```

**Success Response Example:**

```json
{
  "timestamp": "2026-06-24 11:30:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "content": [
      {
        "mentorUserId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
        "displayName": "Nguyen Van B",
        "headline": "Backend Developer | Spring Boot Mentor",
        "teachingMode": "ONLINE",
        "ratingAverage": 4.8
      }
    ],
    "page": 0,
    "size": 12,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

**Common Error Cases:**

- `401`: chưa đăng nhập
- `400`: query param không hợp lệ

**Swagger improvement note:**  
Cần document rõ các giá trị sort hợp lệ: `relevance`, `ratingAverage`, `reviewCount`, `completedSessions`, `updatedAt`.

### [POST] `/api/me/mentor-verification/documents`

**Purpose:**  
Lưu metadata minh chứng xác thực mentor sau khi FE đã upload file lên dịch vụ lưu trữ ngoài.

**Recommended Summary:**  
Attach an uploaded verification document to the current mentor verification request

**Recommended Description:**  
Stores document metadata for the current editable mentor verification request. The frontend must upload the file to an approved external storage host first, then send the resulting URL, public ID, content type, file name, and size to this API.

**Auth/Role:**  
Authenticated user.

**Request Example:**

```json
{
  "documentType": "FPTU_AFFILIATION_PROOF",
  "fileUrl": "https://res.cloudinary.com/demo/image/upload/v12345/mentor-verification/proof.jpg",
  "publicId": "mentor-verification/user-123/proof_abc123",
  "originalFilename": "proof.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 245678
}
```

**Success Response Example:**

```json
{
  "timestamp": "2026-06-24 11:40:00",
  "status": 201,
  "code": "CREATED_0201",
  "message": "Tạo mới thành công",
  "data": {
    "status": "DRAFT",
    "documents": [
      {
        "documentType": "FPTU_AFFILIATION_PROOF",
        "contentType": "image/jpeg",
        "sizeBytes": 245678,
        "isActive": true
      }
    ]
  }
}
```

**Common Error Cases:**

- `400`: fileUrl/publicId/contentType không hợp lệ, host không nằm trong allowlist, vượt quota file theo loại
- `401`: chưa đăng nhập
- `404`: chưa có hồ sơ verification editable
- `413`: file vượt quá 4MB

**Swagger improvement note:**  
Cần sửa mô tả để FE không hiểu nhầm đây là multipart upload trực tiếp lên backend.

### [POST] `/api/me/mentor-verification/submit`

**Purpose:**  
Nộp hồ sơ xác thực mentor để admin duyệt.

**Recommended Summary:**  
Submit the current mentor verification request for admin review

**Recommended Description:**  
Submits the current editable mentor verification request. Submission is allowed only when the required academic profile, mentor profile, and required proof documents are complete, and the caller accepts the current mentor terms version.

**Auth/Role:**  
Authenticated user.

**Request Example:**

```json
{
  "submitNote": "Em đã cập nhật đầy đủ portfolio và minh chứng năng lực.",
  "termsAccepted": true
}
```

**Success Response Example:**

```json
{
  "timestamp": "2026-06-24 11:45:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "status": "PENDING_REVIEW",
    "termsVersion": "SKILLSWAP_MENTOR_TERMS_V1"
  }
}
```

**Common Error Cases:**

- `400`: chưa hoàn tất profile hoặc chưa đủ minh chứng, chưa accept terms
- `401`: chưa đăng nhập
- `409`: đang có request khác chờ duyệt

**Swagger improvement note:**  
Nên hiển thị checklist business rule ngay trong description thay vì chỉ summary ngắn.

### [GET] `/api/me/conversations`

**Purpose:**  
Lấy danh sách các hội thoại liên quan tới booking đã được accept.

**Recommended Summary:**  
List my conversations

**Recommended Description:**  
Returns paginated conversations for the current user. In the current codebase, conversations are created automatically when a booking is accepted and are linked to the booking as the conversation source.

**Auth/Role:**  
Authenticated participant only.

**Request Example:**

```json
{}
```

**Success Response Example:**

```json
{
  "timestamp": "2026-06-24 11:50:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "content": [
      {
        "id": "019f5234-aaaa-bbbb-cccc-1234567890ab",
        "sourceType": "BOOKING",
        "sourceId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
        "otherUserName": "Nguyen Van B"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

**Common Error Cases:**

- `401`: chưa đăng nhập

**Swagger improvement note:**  
Hiện controller thiếu `@SecurityRequirement`, `@ApiResponses` và description business flow.

## 7. Những điểm Swagger hiện tại còn yếu

### P0 - Cần sửa ngay

- Thiếu tag `Chat & Conversation` trong `OpenApiConfig`, khiến cấu hình tag global không khớp controller thực tế.
- `HealthController` mô tả API `/health` là public, nhưng `SecurityConfig` không permit `/health`; tài liệu hiện tại dễ làm FE/QA hiểu sai.
- README và code mentor verification upload đang mâu thuẫn:
  - README mô tả upload qua BE, max `1MB`
  - code hiện tại lưu metadata JSON sau khi FE upload ngoài, max `4MB`
- README mô tả state `READY_FOR_SUBMISSION`, nhưng enum `VerificationStatus` trong code không có trạng thái này. Code hiện dùng `checklist.canSubmit` thay cho status readiness.
- `ChatController` thiếu `@SecurityRequirement`, thiếu `@ApiResponses`, thiếu description; đây là nhóm API yêu cầu auth nhưng Swagger hiện chưa nói rõ.
- `Admin - Users` đang gộp chung API admin moderation và system admin role assignment, làm mờ ranh giới quyền hạn.
- `Booking & Session` đang gom quá rộng, khó giúp FE hiểu boundary giữa booking request, mentor action và availability rule.
- Error response chung chưa được document thành schema dùng lại trong `@ApiResponses`.

### P1 - Nên sửa

- Nhiều endpoint chỉ có summary ngắn, thiếu description theo business flow, đặc biệt ở `MentorServiceController`, `NotificationController`, `SystemUserRoleController`.
- Nhiều DTO/request/response chưa có `@Schema` hoặc thiếu example:
  - `RefreshTokenRequest`
  - `SendMessageRequest`
  - `NotificationResponse`
  - `UnreadCountResponse`
  - `BanUserRequest`
  - `UnbanUserRequest`
- Query param paging/filter/sort chưa được mô tả nhất quán cho các list API.
- Các enum business quan trọng chưa có description nghiệp vụ:
  - `BookingStatus`
  - `VerificationStatus`
  - `MeetingPlatform`
  - `AvailabilityRuleType`
  - `AvailabilityRepeatType`
  - `TeachingMode`
- Một số API tạo mới về mặt nghiệp vụ nhưng doc/behavior HTTP code chưa nhất quán, ví dụ `MentorServiceController#createService` đang trả `200`.
- `NotificationController` trả code `"SUCCESS"` thay vì `"SUCCESS_0200"`, làm response contract kém nhất quán nếu Swagger mô tả envelope chuẩn chung.

### P2 - Nice to have

- Thêm flow note trong tag description cho booking, verification, conversation.
- Thêm external docs hoặc markdown guide cho onboarding và booking lifecycle.
- Chuẩn hóa naming English summary cho frontend/internal teams khi cần chia sẻ rộng hơn.
- Bổ sung example response thực tế cho các object nested lớn như verification request, booking response, mentor detail.

## 8. Đề xuất thứ tự thực hiện refactor Swagger

### 1. Global OpenAPI config

- File cần sửa:
  - `src/main/java/com/fptu/exe/skillswap/infrastructure/config/OpenApiConfig.java`
- Mục tiêu sửa:
  - chỉnh lại danh sách tag
  - thêm/đổi tag descriptions theo feature boundary
  - thêm tag `Conversation`
  - cân nhắc tách `Mentor Services`, `Mentor Availability`, `System Admin - Roles`
- Rủi ro:
  - đổi tag có thể làm thứ tự nhóm API trên Swagger UI thay đổi
- Cách kiểm tra:
  - mở Swagger UI, kiểm tra mỗi controller nằm đúng tag mong muốn

### 2. Common response/error schema

- File cần sửa:
  - `shared/dto/response/ApiResponse.java`
  - DTO lỗi dùng lại nếu tạo mới
  - annotation tại các controller
- Mục tiêu sửa:
  - chuẩn hóa envelope success/error
  - document error schema và mã lỗi phổ biến
- Rủi ro:
  - nếu thêm class schema mới phải tránh làm thay đổi runtime behavior
- Cách kiểm tra:
  - xác nhận mỗi endpoint có `@ApiResponses` tham chiếu đúng schema

### 3. Auth/Identity APIs

- File cần sửa:
  - `AuthController.java`
  - `GoogleLoginRequest.java`
  - `RefreshTokenRequest.java`
  - `TokenResponse.java`
  - `UserMeResponse.java`
- Mục tiêu sửa:
  - làm rõ cookie refresh token, onboarding flags, auth requirement
- Rủi ro:
  - FE có thể đang hiểu refresh token từ body
- Cách kiểm tra:
  - review Swagger examples, thử flow login -> me -> refresh

### 4. Mentor Profile/Service APIs

- File cần sửa:
  - `MentorProfileController.java`
  - `MentorServiceController.java`
  - DTO mentor profile/service
- Mục tiêu sửa:
  - mô tả rõ onboarding mentor, field required, role MENTOR
- Rủi ro:
  - nếu wording không rõ dễ khiến FE hiểu nhầm profile vs service
- Cách kiểm tra:
  - Swagger UI cho thấy 2 nhóm API tách biệt, examples đủ dùng

### 5. Booking APIs

- File cần sửa:
  - `BookingController.java`
  - `MyBookingController.java`
  - `MentorBookingController.java`
  - `MentorAvailabilityController.java`
  - DTO booking/availability liên quan
- Mục tiêu sửa:
  - document booking queue, slot quota, accept side effects, cancellation rules, meeting link rules
- Rủi ro:
  - phần mô tả quá ít sẽ làm FE tích hợp sai; quá nhiều nhưng không có cấu trúc cũng khó đọc
- Cách kiểm tra:
  - Swagger có thể giải thích rõ create -> accept/reject -> complete/cancel

### 6. Conversation APIs

- File cần sửa:
  - `ChatController.java`
  - `SendMessageRequest.java`
  - response DTO chat nếu cần schema
- Mục tiêu sửa:
  - thêm security requirement, response/error cases, flow note conversation tạo sau accept booking
- Rủi ro:
  - FE có thể kỳ vọng conversation được tạo thủ công nếu doc không rõ
- Cách kiểm tra:
  - Swagger thể hiện rõ quyền truy cập participant-only

### 7. Admin APIs

- File cần sửa:
  - `AdminUserController.java`
  - `SystemUserRoleController.java`
  - `AdminMentorVerificationController.java`
  - `AdminMentorController.java`
  - `AdminBookingController.java`
- Mục tiêu sửa:
  - tách boundary quyền `ADMIN` vs `SYSTEM_ADMIN`
  - mô tả admin queue, soft lock, moderation rules
- Rủi ro:
  - tag/grouping sai làm khó cho vận hành nội bộ
- Cách kiểm tra:
  - mỗi API admin hiển thị đúng auth/role note và filter params

### 8. Các module còn lại

- File cần sửa:
  - `StudentProfileController.java`
  - `AcademicController.java`
  - `CatalogController.java`
  - `NotificationController.java`
  - `SessionFeedbackController.java`
  - `HealthController.java`
- Mục tiêu sửa:
  - hoàn thiện examples, query docs, response docs, sửa mismatch health/public
- Rủi ro:
  - tài liệu lookup tuy đơn giản nhưng nếu thiếu examples vẫn làm FE mất thời gian
- Cách kiểm tra:
  - đọc nhanh từ góc nhìn FE mới vào dự án phải hiểu được không cần hỏi lại backend

## 9. Checklist acceptance sau khi refactor Swagger

- [ ] API được gom đúng tag theo feature.
- [ ] Mỗi API có summary ngắn, rõ.
- [ ] Mỗi API có description giải thích mục đích nghiệp vụ.
- [ ] API có role/auth requirement rõ ràng.
- [ ] Request DTO có description và example cho field quan trọng.
- [ ] Response DTO có description và example cho field quan trọng.
- [ ] Error response dùng format thống nhất.
- [ ] Pagination/sort/filter được mô tả rõ.
- [ ] Enum có ý nghĩa nghiệp vụ.
- [ ] Các API side-effect mạnh có business note rõ ràng.
- [ ] Các API tạo conversation/session tự động được mô tả rõ.
- [ ] Mismatch giữa README và code đã được note hoặc sửa tài liệu.
- [ ] Không thay đổi business logic.
- [ ] Không đổi path/method/signature.
- [ ] Swagger UI dễ đọc cho frontend.

## Phụ lục: các khác biệt tài liệu hiện tại so với code

1. `README.md` mô tả mentor verification có trạng thái `READY_FOR_SUBMISSION`, nhưng enum code hiện tại không có trạng thái này.
2. `README.md` mô tả upload document “qua BE tối đa 1MB”, nhưng code hiện tại:
   - FE upload lên storage ngoài trước
   - backend chỉ nhận metadata JSON
   - giới hạn size là `4MB`
3. `HealthController` mô tả `/health` không yêu cầu auth, nhưng `SecurityConfig` hiện không mở public endpoint này.
4. `OpenApiConfig` chưa khai báo tag `Chat & Conversation` dù `ChatController` đang dùng tag này.

