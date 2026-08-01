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

### Mentor profile items
| Method | Endpoint | Auth | Role | Request | Response | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| GET/POST | `/api/me/mentor-projects` | Authenticated | mentee or mentor | `MentorFeaturedProjectRequest` for POST | `List`/`MentorFeaturedProjectResponse` | List or create featured projects. |
| PUT/DELETE | `/api/me/mentor-projects/{projectId}` | Authenticated | owner | `MentorFeaturedProjectRequest` for PUT | `MentorFeaturedProjectResponse`/`Void` | Update or remove a featured project. |
| PUT | `/api/me/mentor-projects/{projectId}/picture` | Authenticated | owner | multipart part `file` | `MentorFeaturedProjectResponse` | Existing direct multipart project-picture endpoint; do not reuse it for verification, resources or Blog images. |
| GET/POST | `/api/me/mentor-achievements` | Authenticated | mentee or mentor | `MentorAchievementRequest` for POST | `List`/`MentorAchievementResponse` | List or create education/achievement items. |
| PUT/DELETE | `/api/me/mentor-achievements/{achievementId}` | Authenticated | owner | `MentorAchievementRequest` for PUT | `MentorAchievementResponse`/`Void` | Update or remove an achievement. |

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
| Method | Endpoint | Auth | Role | Request | Response | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/me/mentor-services/{serviceId}/resources/upload-url` | Authenticated | mentor | `MentorServiceResourceUploadUrlRequest` | `MentorServiceResourceUploadUrlResponse` | Creates one private upload intent. |
| POST | `/api/me/mentor-services/{serviceId}/resources` | Authenticated | mentor | `MentorServiceResourceCreateRequest` | `MentorServiceResourceResponse` | Confirms the intent and creates metadata. |
| GET | `/api/me/mentor-services/{serviceId}/resources` | Authenticated | mentor | - | `List<MentorServiceResourceResponse>` | Full management list, including inactive history. |
| PUT | `/api/me/mentor-services/{serviceId}/resources/{resourceId}` | Authenticated | mentor | `MentorServiceResourceUpdateRequest` | `MentorServiceResourceResponse` | Metadata only; file content is immutable. |
| DELETE | `/api/me/mentor-services/{serviceId}/resources/{resourceId}?expectedVersion=` | Authenticated | mentor | query `expectedVersion` | `Void` | Soft-deletes and immediately revokes downloads. |
| GET | `/api/mentor-services/{serviceId}/resources` | Authenticated | any logged-in user | - | `List<MentorServiceResourceResponse>` | Reader metadata; never exposes a storage key or URL. |
| POST | `/api/mentor-service-resources/{resourceId}/download-url` | Authenticated | entitled reader | - | `MentorServiceResourceDownloadResponse` | Re-authorizes and returns a short-lived private credential. |

Upload is a two-step flow: call `POST .../upload-url` with `filename`, `resourceType`; PUT bytes to `uploadUrl` using the returned `requiredContentType`; then confirm through `POST .../resources` with `uploadIntentId`. The client never submits an object key.

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
      "deliveryMode": "ONE_TO_ONE",
      "maintainPostSessionChat": false,
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

## Public Mentor Discovery Profile
`GET /api/mentors/{mentorUserId}` is public and returns exactly six sections in the required FE render order:

```text
identity -> mentoring -> services -> evidence -> reputation -> availability
```

```json
{
  "identity": {
    "mentorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "displayName": "Nguyen Van A",
    "avatarUrl": "https://example.com/avatar.jpg",
    "headline": "Backend Engineer and Java Mentor",
    "isVerified": true,
    "verifiedAt": "2026-07-20T08:30:00"
  },
  "mentoring": {
    "bio": "Peer mentor for Java backend and internship preparation.",
    "expertiseDescription": "I help students build REST APIs and improve interview readiness.",
    "helpTopics": [],
    "supportLevels": { "foundation": 4, "outputReview": 3, "direction": 3 }
  },
  "services": [],
  "evidence": {
    "education": {
      "campusId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "campusName": "FPT University HCM",
      "programId": null,
      "programName": null,
      "specializationId": null,
      "specializationName": null,
      "semester": 7,
      "alumni": false
    },
    "subjectResults": [],
    "featuredProjects": [],
    "achievements": [],
    "portfolioUrl": null,
    "githubUrl": null,
    "authorityContent": {
      "publishedArticleCount": 1,
      "latestPublishedAt": "2026-07-25T08:00:00",
      "recentPublicArticles": [
        {
          "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "title": "Spring Boot interview checklist",
          "slug": "spring-boot-interview-checklist",
          "excerpt": "Checklist for a backend internship interview.",
          "coverImageUrl": null,
          "readingTimeMinutes": 4,
          "publishedAt": "2026-07-25T08:00:00"
        }
      ]
    }
  },
  "reputation": {
    "ratingState": "NO_REVIEWS",
    "ratingAverage": null,
    "reviewCount": 0,
    "completedSessions": 0
  },
  "availability": {
    "isAvailable": true,
    "suspendedUntil": null,
    "canRequestBooking": true
  }
}
```

- Collections are always `[]` when empty. `education` and `authorityContent` are always objects; optional scalar fields use `null`.
- `ratingState=NO_REVIEWS` means FE renders “Mới tham gia, chưa có đánh giá” and must not render a fabricated 5.0 rating. `RATED` includes a numeric `ratingAverage`.
- `completedSessions` is the mentor's persisted `totalCompletedSessions`: only a booking explicitly confirmed by the mentee (`USER_CONFIRMED`) increments it. Auto-close, no-show resolution, session timeline completion and settlement alone do not.
- `canRequestBooking` is public offer readiness only. It is not viewer-specific authorization; quote/create validates the current mentee, service, slot and candidate again.
- `authorityContent.recentPublicArticles` contains at most three newest mentor-owned `PUBLISHED` `PUBLIC` Blog posts. Platform, draft, archived, deleted, authenticated-only and premium posts never appear. Cover URLs are resolved public URLs only; storage keys are never returned.
- Discovery cards and recommendation cards remain compact. They also use `ratingState` plus nullable `ratingAverage`.

The availability-slot and candidate endpoints remain authenticated:

```text
GET /api/mentors/{mentorUserId}/availability-slots
GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId=...
```

For client-only funnel interactions, authenticated FE may call `POST /api/mentor-discovery/funnel-events` with `{ eventType, mentorUserId, serviceId?, slotId?, source }`. Allowed events: `SERVICE_VIEWED`, `CANDIDATE_SELECTED`, `BOOKING_STARTED`. Allowed sources: `MENTOR_PROFILE`, `DISCOVERY_SEARCH`, `BLOG_ARTICLE`, `DIRECT_LINK`. This endpoint is best-effort analytics: do not retry aggressively or block navigation if it fails.

## Discovery pricing preview
Authenticated users may call `GET /api/mentor-services/{serviceId}/pricing-preview` for an active service of a discoverable mentor. The response contains `basePriceScoin`, mentee surcharge, currently eligible campaign discount, `estimatedPayableScoin`, `campaignName`, `pricingVersion`, `calculatedAt`, `isEstimate=true`, and the disclaimer `Final price is calculated at checkout.`

This is a browse-time estimate only. It never reserves campaign budget, coupon, wallet credit, candidate time, or a booking. Coupon and wallet credit are intentionally not included; FE must label the result as an estimate and refresh the transactional checkout preview after mentor acceptance.

## Group-session supply and seat commerce (Phase 2)
`MentorService.deliveryMode` is `ONE_TO_ONE` by default or `GROUP_SESSION`. The delivery mode is fixed after service creation; create a separate service when a mentor offers both formats.

Group-session APIs are enabled only when `APPLICATION_GROUP_SESSIONS_ENABLED=true`. A group session freezes service title, description, expected outcome, duration, free flag and base SCoin price on publish. Later service edits never change a published session or its seat bookings.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/me/mentor-services/{serviceId}/group-sessions` | Create a `DRAFT` from a bound availability slot and UTC candidate start. |
| GET | `/api/me/mentor-services/{serviceId}/group-sessions` | List mentor-owned group sessions for a service. |
| GET/PUT | `/api/me/group-sessions/{groupSessionId}` | Read or edit a draft with `expectedVersion`. |
| POST | `/api/me/group-sessions/{groupSessionId}/publish` | Atomically transition `DRAFT -> OPEN` and reserve the interval. |
| POST | `/api/me/group-sessions/{groupSessionId}/close-registration` | Stop future enrolment for an open session. |
| POST | `/api/me/group-sessions/{groupSessionId}/increase-capacity` | Increase capacity only while both session and registration are `OPEN`; it never reopens registration. |
| POST | `/api/me/group-sessions/{groupSessionId}/cancel` | Cancel a draft or open session. |
| GET | `/api/me/group-sessions/{groupSessionId}/attendees?limit=` | Mentor-only roster with attendee identity, joined time and derived `WAITING_PAYMENT`, `CONFIRMED` or `CANCELLED` state. |

`startAt` and optional `registrationClosesAt` are whole-minute UTC Instants. `scheduledEndAt` is derived from the service duration. Capacity is 2-20. `reservedSeatCount` includes both paid seats and still-live payment holds. A published group session reserves only its exact interval inside the parent availability slot.

Mentor cancellation closes registration, expires unpaid holds, cancels every attendee booking and starts the existing full-refund path for paid seats. Do not expose payment-provider IDs, wallet use, coupon values or checkout links in the roster.

## Group experience and attendance (Phase 3)
Publishing creates one shared group session and one group conversation immediately, so the mentor can prepare its meeting details before learners join. It never creates a direct session, direct chat or calendar event for an attendee seat.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/me/group-sessions/{groupSessionId}/experience` | Read shared session, meeting and group conversation IDs. |
| PUT | `/api/me/group-sessions/{groupSessionId}/meeting` | Set the one shared meeting platform/link. |
| POST | `/api/me/group-sessions/{groupSessionId}/attendance` | Submit the final complete attendee roster with `expectedVersion`. |

Attendance is available after `scheduledEndAt` until the configured `APPLICATION_GROUP_SESSION_ATTENDANCE_SUBMISSION_WINDOW_HOURS` deadline (48 hours by default). The roster is immutable: mark each current attendee `PRESENT` or `MENTEE_NO_SHOW`; corrections use booking issue/admin resolution.
