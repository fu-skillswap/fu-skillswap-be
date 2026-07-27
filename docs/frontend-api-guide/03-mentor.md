# Mentor

## Mục tiêu
File này là guide cho toàn bộ phần mentor-facing:
- mentor profile
- mentor services
- mentor verification
- mentor readiness cho discovery/booking

FE phải phân biệt rõ:
- `mentor profile` là hồ sơ cá nhân/chuyên môn
- `mentor verification` là quy trình xác thực
- `mentor service` là sản phẩm/offer mentor bán
- `availability` và policy đặt lịch nằm ở booking/discovery, không phải profile

## API inventory
### Master data cho form mentor
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/catalog/help-topics` | Public | - | - | `List<HelpTopicResponse>` | - | Help topic dùng cho profile/service/discovery |
| GET | `/api/catalog/mentor-profile-options` | Public | - | - | `MentorProfileOptionsResponse` | - | Label support level 1..4 |

### Mentor profile
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/mentor-profile` | Authenticated | mentee or mentor | - | `MentorProfileResponse` | - | Hồ sơ mentor hiện tại |
| PUT | `/api/me/mentor-profile` | Authenticated | mentee or mentor | `MentorProfileUpsertRequest` | `MentorProfileResponse` | - | Tạo/cập nhật profile mentor |

### Mentor services
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/mentor-services` | Authenticated | mentor | optional query `isActive=true|false` | `List<MentorServiceResponse>` | - | Omit query để lấy cả active và inactive |
| GET | `/api/me/mentor-services/constraints` | Authenticated | mentor | - | `MentorServiceConstraintsResponse` | - | Allowed durations và price bounds chỉ đọc cho form tạo/sửa service |
| GET | `/api/me/mentor-services/{serviceId}` | Authenticated | mentor | path `serviceId` | `MentorServiceResponse` | - | Chi tiết service |
| POST | `/api/me/mentor-services` | Authenticated | mentor | `CreateMentorServiceRequest` | `MentorServiceResponse` | - | Tạo service, gồm duration |
| PUT | `/api/me/mentor-services/{serviceId}` | Authenticated | mentor | `UpdateMentorServiceRequest` | `MentorServiceResponse` | - | Cập nhật mutable fields với `expectedVersion`; duration immutable |
| PATCH | `/api/me/mentor-services/{serviceId}/active` | Authenticated | mentor | `MentorServiceActiveRequest` | `MentorServiceResponse` | - | Bật/tắt service |
| GET | `/api/me/mentor-booking-policy` | Authenticated | mentor | - | `MentorBookingPolicyResponse` | - | Policy mentor tự sửa được |
| PATCH | `/api/me/mentor-booking-policy` | Authenticated | mentor | `UpdateMentorBookingPolicyRequest` | `MentorBookingPolicyResponse` | - | Patch policy với `expectedVersion` |
| GET | `/api/me/mentor-scheduling-constraints` | Authenticated | mentor | - | `MentorSchedulingConstraintsResponse` | - | Giới hạn platform, read-only |

### Service learning resources
Mentor quản lý tài liệu theo từng service qua `/api/me/mentor-services/{serviceId}/resources`.
Upload là flow hai bước: gọi `POST .../upload-url` với `filename`, `resourceType`; PUT object tới `uploadUrl` với đúng `requiredContentType`; sau đó confirm bằng `POST .../resources` với `uploadIntentId`, không gửi object key.

Chỉ hỗ trợ `PDF`, `DOCX`, `PPTX`, `TEXT`, `MARKDOWN`, `PNG`, `JPEG`. `.img` không được hỗ trợ. Tài liệu mặc định nên là `BOOKED_MEMBERS`; `AUTHENTICATED` cho mọi người dùng đã đăng nhập.

### Mentor verification
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/me/mentor-verification/request` | Authenticated | any authenticated except admin/system | - | `MentorVerificationRequestResponse` | - | Mở/khôi phục wizard verification |
| GET | `/api/me/mentor-verification` | Authenticated | any authenticated except admin/system | - | `MentorVerificationRequestResponse` | - | Lấy request hiện tại |
| GET | `/api/me/mentor-verification/timeline` | Authenticated | any authenticated except admin/system | - | `List<MentorVerificationTimelineEventResponse>` | - | Timeline trạng thái |
| GET | `/api/me/mentor-verification/documents/{documentId}` | Authenticated | any authenticated except admin/system | path `documentId` | `MentorVerificationDocumentResponse` | - | Metadata document |
| POST | `/api/me/mentor-verification/documents/upload-intents` | Authenticated | mentee or mentor | `MentorVerificationDocumentUploadIntentRequest` | `MentorVerificationDocumentUploadIntentResponse` | - | Xin private presigned PUT URL cho một document |
| POST | `/api/me/mentor-verification/documents` | Authenticated | mentee or mentor | `MentorVerificationDocumentUploadRequest` | `MentorVerificationRequestResponse` | - | Confirm `uploadIntentId` sau khi upload object |
| DELETE | `/api/me/mentor-verification/documents/{documentId}` | Authenticated | any authenticated except admin/system | path `documentId` | `MentorVerificationRequestResponse` | - | Xóa mềm document khi draft/revision |
| POST | `/api/me/mentor-verification/submit` | Authenticated | any authenticated except admin/system | `MentorVerificationSubmitRequest` | `MentorVerificationRequestResponse` | - | Nộp hồ sơ cho admin review |
| POST | `/api/me/mentor-verification/withdraw` | Authenticated | any authenticated except admin/system | - | `MentorVerificationRequestResponse` | - | Rút hồ sơ khi trạng thái cho phép |

## Call order chuẩn
### 1) Mentor profile
1. FE load `/api/catalog/help-topics` và `/api/catalog/mentor-profile-options` nếu cần render form.
2. FE gọi `GET /api/me/mentor-profile`.
3. FE hiển thị form edit/create theo response.
4. FE gọi `PUT /api/me/mentor-profile`.
5. Sau save, FE refresh lại mentor profile để lấy canonical state.

### 2) Verification
1. FE gọi `POST /api/me/mentor-verification/request`.
2. FE gọi `POST /api/me/mentor-verification/documents/upload-intents` với `filename`, `contentType`, `sizeBytes`.
3. FE PUT bytes tới `uploadUrl` trước `expiresAt`, gửi đúng header trong `requiredHeaders`.
4. FE gọi `POST /api/me/mentor-verification/documents` với `{ "documentType": "...", "uploadIntentId": "uuid" }`.
5. Khi checklist đủ, FE gọi `POST /api/me/mentor-verification/submit`.
6. FE dùng `GET /api/me/mentor-verification/timeline` để render tiến độ.
7. Nếu admin yêu cầu sửa, FE cho phép user quay lại wizard và tạo intent mới.

### 3) Service catalog
1. FE chỉ mở phần service management khi user có quyền mentor.
2. FE gọi `GET /api/me/mentor-services`.
3. FE tạo/sửa/bật tắt service bằng đúng endpoint của service.
4. Sau mỗi action, refresh lại list service.

## Post-session chat policy
`CreateMentorServiceRequest` và `UpdateMentorServiceRequest` có field boolean `maintainPostSessionChat`.

- `false` (mặc định): sau booking effective, chat mở đến `scheduledEndAt + 24 giờ`.
- `true`: khi booking hoàn tất với outcome hợp lệ, mentor và mentee tiếp tục chat dài hạn.
- Field này là một phần của offer. FE phải hiển thị rõ “Chat đến 24h sau buổi học” hoặc “Bao gồm chat sau buổi học”.
- Thay đổi service chỉ áp dụng cho booking tạo sau đó; booking giữ snapshot policy riêng.

Mentor service response exposes `maintainPostSessionChat` so mentees know the communication promise before booking.

## Ý nghĩa field quan trọng
### Mentor profile
- `headline`
  - tagline ngắn dùng trên card/detail
- `expertiseDescription`
  - mô tả kinh nghiệm/chuyên môn
- `isAvailable`
  - mentor đang mở lịch hay không
- `helpTopics`
  - chủ đề mentor hỗ trợ
- `subjectResults`
  - điểm/môn học dùng cho matching
- `foundationSupportLevel`, `outputReviewSupportLevel`, `directionSupportLevel`
  - mức support 1..4, dùng cho discovery/matching
- `minimumBookingLeadTimeMinutes`
  - lead time đặt lịch tối thiểu
- `maximumBookingHorizonDays`
  - horizon đặt lịch tối đa
- `bookingTimezone`
  - timezone của mentor
- `bookingSuspendedUntil`
  - nếu có, mentor tạm ngưng nhận booking tới thời điểm này
- `verifiedAt`
  - mốc mentor được xác thực

### Mentor service
- `durationMinutes`
  - thời lượng immutable sau khi tạo; booking snapshot lại ngay lúc mentee gửi request
- `isFree`
  - service free hay có phí
- `priceScoin`
  - giá nếu có phí
- `helpTopics`
  - chủ đề liên quan tới service
- `isActive`
  - service đang mở hay tắt
- `version`
  - optimistic-lock version. FE gửi lại đúng giá trị này trong update/active mutation và phải dùng version server trả về sau success.

### Mentor booking policy
- `minimumBookingLeadTimeMinutes`, `maximumBookingHorizonDays`, `timezone`
  - chỉ ảnh hưởng availability reads và booking mới; không hồi tố pending/confirmed booking hay slot cũ.
- `version`
  - bắt buộc khi PATCH policy. Hai tab cùng sửa: conflict 409 thì reload policy, giữ draft cục bộ rồi để mentor xác nhận gửi lại.
- `maximumAvailabilityQueryDays`, `maximumParentSlotDurationMinutes`
  - platform constraints read-only. Không gửi trong PATCH policy.

### Verification
- `requestStatus`
  - trạng thái request hiện tại
- `documents`
  - danh sách minh chứng đã upload
- `timeline`
  - chuỗi sự kiện submit/review/revision/approve/reject/withdraw
- `MentorVerificationDocumentUploadIntentRequest`
  - `filename`, `contentType`, `sizeBytes`; chỉ nhận JPG, PNG hoặc PDF, tối đa 15 MiB.
- `MentorVerificationDocumentUploadIntentResponse`
  - `uploadIntentId`, `uploadUrl`, `expiresAt`, `requiredHeaders`.
  - Không chứa `objectKey`, bucket URL hoặc public URL.
- `MentorVerificationDocumentUploadRequest`
  - chỉ chứa `documentType` và `uploadIntentId`; không gửi filename, MIME, size hay object key lần nữa.

## State / phase guide
### Mentor profile readiness
- `exists = false`
  - chưa có profile
- `requiredFieldsCompleted = false`
  - chưa đủ field bắt buộc cho discovery/verification
- `hasCompletedProfile = true` ở discovery
  - mentor đã đủ dữ liệu public

### Verification status
- `NOT_STARTED`
  - chưa mở request
- `PENDING_REVIEW`
  - đã submit, đang chờ admin review
- `NEEDS_REVISION`
  - bị yêu cầu sửa hồ sơ/minh chứng
- `APPROVED`
  - đã được duyệt
- `REJECTED`
  - bị từ chối
- `WITHDRAWN`
  - user rút hồ sơ

### Service readiness
- `isActive = true`
  - service có thể xuất hiện trong booking/discovery
- `active = false`
  - service vẫn còn lịch sử nhưng không nhận request mới

## FE không được làm
- Không dùng `isAvailable` để thay thế slot availability.
- Không cho mentor dùng service CRUD nếu chưa vào đúng mentor flow.
- Không coi verification là tự động có role mentor; role và verification là hai chuyện khác nhau.
- Không upload file verification trực tiếp qua multipart vào BE ở flow prod.
- Không dùng `/api/files/upload-url` cho verification. Endpoint generic chỉ tồn tại ở local profile.
- Không lưu, tự tạo hoặc gửi `objectKey`/`publicUrl` cho verification document.
- Không tự quyết định mentor đủ điều kiện booking chỉ từ `mentor profile` mà bỏ qua service/availability.
- Không đưa `durationMinutes` vào update request hoặc tự đổi duration của service đã có booking history.
- Không dùng `DELETE` để xóa service. Tắt service bằng endpoint `/active`; reactivation không tự gắn lại service vào slot cũ.

## FE anti-patterns
- Không trộn mentor profile form với verification wizard.
- Không dùng `helpTopics` của blog/forum để thay cho mentor help topics.
- Không hiển thị service action cho user không phải mentor.
- Không coi `mentorStatus` hoặc `verifiedAt` là đủ; luôn xem thêm service/availability trước khi cho booking.

## Response JSON example
### Mentor profile
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "exists": true,
    "requiredFieldsCompleted": true,
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "mentor@fpt.edu.vn",
    "displayName": "Nguyễn Văn A",
    "avatarUrl": "https://example.com/avatar.jpg",
    "mentorStatus": "ACTIVE",
    "headline": "Senior Backend Developer, Spring Boot Expert",
    "expertiseDescription": "Có 2 năm kinh nghiệm làm Java Spring Boot...",
    "isAvailable": true,
    "bookingSuspendedUntil": null,
    "lateCancellationPenaltyPoints": 0,
    "verifiedAt": "2026-05-15T10:00:00",
    "minimumBookingLeadTimeMinutes": 120,
    "maximumBookingHorizonDays": 30,
    "bookingTimezone": "Asia/Ho_Chi_Minh",
    "helpTopics": [
      { "id": "44444444-4444-4444-4444-444444444444", "code": "SPRING_BOOT", "nameVi": "Spring Boot", "nameEn": "Spring Boot", "weight": 10 }
    ],
    "subjectResults": [],
    "foundationSupportLevel": 4,
    "outputReviewSupportLevel": 4,
    "directionSupportLevel": 3,
    "featuredProjects": [],
    "achievements": [],
    "githubUrl": "https://github.com/mentor",
    "portfolioUrl": "https://mentor.dev",
    "phoneNumber": "0900000000",
    "ratingAverage": 4.8,
    "reviewCount": 12,
    "completedSessions": 15,
    "createdAt": "2026-06-01T10:00:00",
    "updatedAt": "2026-07-01T10:00:00"
  }
}
```

### Mentor service
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "serviceId": "55555555-5555-5555-5555-555555555555",
      "mentorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "title": "Spring Boot Interview Coaching",
      "description": "Ôn phỏng vấn backend",
      "expectedOutcome": "Chuẩn bị bộ câu hỏi thực chiến",
      "durationMinutes": 90,
      "isFree": false,
      "priceScoin": 100,
      "isActive": true,
      "version": 4,
      "helpTopics": [],
      "createdAt": "2026-06-01T10:00:00",
      "updatedAt": "2026-07-01T10:00:00"
    }
  ]
}
```

## UI mapping
- Mentor profile screen:
  - render readiness, headline, support levels, help topics và trạng thái `isAvailable`
- Mentor service screen:
  - tách rõ list service active/inactive, CTA create/edit/activate/archive
- Verification wizard:
  - hiển thị checklist và timeline, không trộn với profile form
- Discovery card:
  - dùng profile/service đã xác thực để quyết định mentor có lên listing hay không

## API success/error behavior
- `GET /api/me/mentor-profile`
  - success: fill form/view
  - nếu `exists=false`: FE mở create flow
- `PUT /api/me/mentor-profile`
  - success: refresh profile và discovery-relevant state
  - 400: fix field/selection relation
- `POST/PUT/PATCH mentor-services`
  - success: refresh service list
  - 409: service đang được dùng trong flow khác, refresh rồi thử lại
- `verification request/submit/documents`
  - success: refresh timeline và request detail
  - 400: checklist chưa đủ, upload intent hết hạn hoặc metadata storage không khớp
  - 404: upload intent không thuộc user hiện tại hoặc không tồn tại; tạo intent mới thay vì retry bằng ID cũ

## Ghi chú cho AI Agent và FE dev
- `isAvailable` không thay thế slot availability.
- `mentorStatus` và `verifiedAt` là signal, không phải toàn bộ readiness.
- Verification không đồng nghĩa có role mentor; mentor flow và role flow là hai lớp khác nhau.

## Mentor Blog Authoring
Mentor Blog is separate from service learning resources. Service resources are private/downloadable material; Blog images are public assets used for public article presentation.

| Method | Endpoint | When |
| --- | --- | --- |
| GET/POST | `/api/me/blog/posts` | List/create own mentor drafts. |
| GET/PUT | `/api/me/blog/posts/{postId}` | Load or edit own Markdown article. |
| POST | `/api/me/blog/posts/{postId}/publish` | Publish a complete draft with `expectedVersion`. |
| POST | `/api/me/blog/posts/{postId}/archive` | Remove own article from readers with `expectedVersion`. |
| POST | `/api/me/blog/assets/upload-intents` | Request a public Blog image upload credential. |
| POST | `/api/me/blog/assets/{intentId}/confirm` | Confirm upload and receive an `assetId`. |

Only an `ACTIVE` verified mentor can create, edit, upload Blog images or publish. Loss of normal publishing eligibility does not remove an existing article; `SUSPENDED` is a moderation state and hides/archive-publishes articles server-side.

## Conversion Signals
`GET /api/mentors/{mentorUserId}` includes `authorityContent.publishedArticleCount` and `authorityContent.latestPublishedAt`. These count only the mentor's published `PUBLIC` Blog articles; premium, authenticated-only, archived and deleted articles never contribute.

For client-only funnel interactions, authenticated FE may call `POST /api/mentor-discovery/funnel-events` with `{ eventType, mentorUserId, serviceId?, slotId?, source }`. Allowed events: `SERVICE_VIEWED`, `CANDIDATE_SELECTED`, `BOOKING_STARTED`. Allowed sources: `MENTOR_PROFILE`, `DISCOVERY_SEARCH`, `BLOG_ARTICLE`, `DIRECT_LINK`. This endpoint is best-effort analytics: do not retry aggressively or block navigation if it fails.

## Discovery pricing preview
Authenticated users may call `GET /api/mentor-services/{serviceId}/pricing-preview` for an active service of a discoverable mentor. The response contains `basePriceScoin`, mentee surcharge, currently eligible campaign discount, `estimatedPayableScoin`, `campaignName`, `pricingVersion`, `calculatedAt`, `isEstimate=true`, and the disclaimer `Final price is calculated at checkout.`

This is a browse-time estimate only. It never reserves campaign budget, coupon, wallet credit, candidate time, or a booking. Coupon and wallet credit are intentionally not included; FE must label the result as an estimate and refresh the transactional checkout preview after mentor acceptance.
