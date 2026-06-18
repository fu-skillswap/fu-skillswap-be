# SkillSwap Frontend Guidline

## Project Overview

SkillSwap la nen tang ket noi sinh vien FPT University voi mentor la sinh vien khoa tren hoac alumni FPT. Backend hien tai da co cac module chinh:

- `Identity`: Google OAuth login, refresh token rotation, logout, get current user
- `Academic`: onboarding student profile, campuses, programs, specializations
- `Mentor Verification`: yeu cau tro thanh mentor, upload minh chung, submit, admin review
- `Mentor Profile`: onboarding profile mentor
- `Mentor Services`: mentor tao goi mentoring
- `Discovery`: recommendations, search, mentor detail, mentor reviews, mentor availability
- `Booking`: create booking, mentor accept/reject/cancel, mentee cancel, complete, meeting link, availability rules
- `Feedback`: mentee review mentor sau session completed
- `Admin/System`: mentor verification queue, user role management, user ban/unban, mentor list, booking list

Luong san pham tong the theo source hien tai:

1. User login bang Google.
2. Backend tu dong tao `User` moi voi role mac dinh `MENTEE` neu chua ton tai.
3. FE goi `GET /api/auth/me`.
4. Neu `profileCompleted = false` thi di den form `student-profile`.
5. Sau khi hoan tat student profile, user co the:
   - tim mentor
   - xem mentor detail
   - tao booking
   - mo luong become mentor
6. Neu user muon tro thanh mentor:
   - tao mentor verification request
   - dien mentor profile
   - upload minh chung
   - submit
   - admin approve / reject / request revision
7. Mentor da duyet co the quan ly service, availability, booking mentor-side.
8. Sau buoi hoc completed, mentee moi duoc submit review.

## Authentication & Authorization

### Google OAuth login

Backend dang dung luong `Google ID Token -> BE verify -> issue JWT`.

- FE lay `idToken` tu Google Identity Services
- FE goi `POST /api/auth/google`
- Backend verify token voi Google va `GOOGLE_CLIENT_ID`
- Backend tu tao hoac lien ket user
- Backend tra `accessToken` trong body
- Backend tra `refreshToken` qua `HttpOnly cookie`

Response body cua login:

```json
{
  "timestamp": "2026-06-17 15:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "success",
  "data": {
    "accessToken": "jwt-access-token",
    "tokenType": "Bearer"
  }
}
```

### Access token

- FE luu `accessToken` o memory state hoac secure client store tuy chien luoc FE
- Moi API can auth dung header:

```http
Authorization: Bearer <accessToken>
```

### Refresh token

Backend uu tien refresh token trong cookie.

- Cookie mac dinh: `skillswap_refresh_token`
- `HttpOnly = true`
- `Secure = true`
- `SameSite = Lax`
- `Path = /`

FE browser phai goi refresh/logout voi `credentials: 'include'`.

Refresh flow:

1. Access token het han
2. FE goi `POST /api/auth/refresh`
3. Khong can gui body neu cookie ton tai
4. Backend rotate refresh token
5. Backend tra access token moi trong body va set cookie moi

Luu y:

- `POST /api/auth/refresh` van chap nhan body `refreshToken`, nhung FE web nen dung cookie-first
- `POST /api/auth/logout` can cookie refresh token, neu khong co se bi `400 Refresh token không được để trống`

### Roles hien co

- `MENTEE`
- `MENTOR`
- `ADMIN`
- `SYSTEM_ADMIN`

### Phan quyen tong quan

- Tat ca `/api/mentors/**` deu yeu cau login
- `MENTOR` role moi vao duoc:
  - `/api/me/mentor-services/**`
  - `/api/mentor/availability-rules/**`
  - `/api/mentor/bookings/**`
- `ADMIN` role moi vao duoc:
  - `/api/admin/users/**`
  - `/api/admin/mentor-verification/requests/**`
  - `/api/admin/mentors`
  - `/api/admin/bookings`
- `SYSTEM_ADMIN` role moi vao duoc:
  - `/api/system/users/**`

### Quan trong cho FE

Source hien tai co mot diem can canh bao:

- Admin approve mentor chi update `mentorProfile.status = ACTIVE`
- Trong source hien tai khong thay logic gan them `RoleCode.MENTOR` cho user sau approve
- Trong khi cac API mentor-side lai gate bang `@PreAuthorize("hasRole('MENTOR')")`

Ket luan:

- Day la `Need Backend Confirmation / Possible Backend Gap`
- FE khong duoc tu suy dien rang `mentorStatus = ACTIVE` la da goi duoc mentor API
- FE phai check ca `roles` trong `GET /api/auth/me`

## First-login Onboarding

### Khi nao can bat user dien academic profile

Sau login, FE bat buoc goi `GET /api/auth/me`.

Neu:

- `profileCompleted = false` -> redirect den onboarding student profile
- `profileCompleted = true` -> vao app binh thuong

`hasStudentProfile` hien tai la alias cua `profileCompleted`.

### API lien quan

- `GET /api/auth/me`
- `GET /api/me/student-profile`
- `PUT /api/me/student-profile`
- Public catalogs:
  - `GET /api/campuses`
  - `GET /api/academic-programs`
  - `GET /api/academic-programs/{programId}/specializations`
  - `GET /api/specializations`

### Student profile required fields

Request body:

```json
{
  "studentCode": "SE192621",
  "displayName": "Nguyen Van A",
  "avatarUrl": "https://example.com/avatar.jpg",
  "campusId": "uuid",
  "programId": "uuid",
  "specializationId": "uuid",
  "semester": 5,
  "intakeYear": 2022,
  "isAlumni": false,
  "graduationYear": null,
  "bio": "Gioi thieu ban than"
}
```

Validation rules tu backend:

- `studentCode`: bat buoc, regex `^(?i)[HSDQC][ESA](0[1-9]|1[0-9]|2[0-2])\\d{4}$`
- `displayName`: max 150
- `campusId`, `programId`, `specializationId`, `semester`, `intakeYear`, `isAlumni`: bat buoc
- `semester >= 0`
- `intakeYear` trong khoang hop le
- neu `isAlumni = true` thi `graduationYear` bat buoc
- `graduationYear >= intakeYear`
- `specializationId` phai thuoc `programId`
- `studentCode` unique toan he thong

### FE redirect logic de xuat

- User thuong:
  - chua complete -> ep vao onboarding
- Admin/System Admin:
  - backend khong gate admin APIs bang student profile
  - co the cho vao admin area du `profileCompleted = false`
  - day la logic FE co the dung, duoc suy ra tu permission source hien tai

## Mentor Verification

### Muc tieu

User tu `MENTEE` di qua luong xac thuc de tro thanh mentor.

### Trang thai request

- `DRAFT`
- `PENDING_REVIEW`
- `NEEDS_REVISION`
- `APPROVED`
- `REJECTED`
- `WITHDRAWN`

### Trang thai document

- `UPLOADED`
- `ACCEPTED`
- `REJECTED`
- `REMOVED`

### Loai document

- `FPTU_AFFILIATION_PROOF`: toi da 1 file dang active
- `EXPERTISE_PROOF`: toi da 3 file dang active

### Storage behavior

FE phai gui file that len BE, khong gui URL.

- `image/jpeg`, `image/png` -> BE upload qua Cloudinary
- `application/pdf` -> BE upload qua Cloudflare R2

Source hien tai dang config multipart:

- `max-file-size = 1536KB`
- `max-request-size = 1536KB`

Nen FE nen validate truoc o client theo `1.5MB`, khong phai `1MB`.

### User flow

1. `POST /api/me/mentor-verification/request`
   - neu chua co active request -> tao moi, status `DRAFT`, HTTP `201`
   - neu da co active request -> tra request hien tai, HTTP `200`
2. `PUT /api/me/mentor-profile`
   - dien profile mentor
3. `POST /api/me/mentor-verification/documents`
   - upload minh chung
4. `GET /api/me/mentor-verification`
   - load full request
5. `GET /api/me/mentor-verification/timeline`
   - load timeline rieng
6. `GET /api/me/mentor-verification/documents/{documentId}`
   - load metadata 1 file
7. `POST /api/me/mentor-verification/submit`
8. `POST /api/me/mentor-verification/withdraw`
9. `DELETE /api/me/mentor-verification/documents/{documentId}`
   - chi duoc khi request dang `DRAFT` hoac `NEEDS_REVISION`

### Rule submit

Backend chi cho submit khi:

- student profile completed
- mentor profile completed
- co it nhat 1 `FPTU_AFFILIATION_PROOF`
- co it nhat 1 `EXPERTISE_PROOF`
- `termsAccepted = true` neu chua accept current terms version

Checklist tra ve trong response:

- `academicProfileCompleted`
- `mentorProfileCompleted`
- `hasAffiliationProof`
- `hasExpertiseProof`
- `canSubmit`

FE nen disable nut submit dua tren:

- `allowedActions.canSubmit`
- va hien checklist ro rang

### Allowed actions

Response request co:

- `allowedActions.canUploadDocuments`
- `allowedActions.canSubmit`
- `allowedActions.canWithdraw`

Dung source nay de bat/tat button, khong tu suy luan.

### Admin review flow

APIs:

- `GET /api/admin/mentor-verification/requests`
- `GET /api/admin/mentor-verification/requests/{requestId}`
- `GET /api/admin/mentor-verification/requests/{requestId}/lock`
- `POST /api/admin/mentor-verification/requests/{requestId}/lock/refresh`
- `POST /api/admin/mentor-verification/requests/{requestId}/request-revision`
- `POST /api/admin/mentor-verification/requests/{requestId}/approve`
- `POST /api/admin/mentor-verification/requests/{requestId}/reject`

Soft lock behavior:

- admin mo detail request `PENDING_REVIEW` -> request co the bi auto claim lock
- lock TTL = 5 phut
- admin khac van xem duoc detail
- nhung khong duoc review neu lock dang thuoc admin khac
- FE nen hien:
  - ai dang lock
  - con bao nhieu giay
  - nut refresh lock neu current admin dang giu lock

### FE display by status

- `DRAFT`: cho phep edit profile, upload/delete doc, submit, withdraw
- `PENDING_REVIEW`: read-only, cho phep withdraw
- `NEEDS_REVISION`: hien `reviewNote`, mo lai edit + upload + submit lai
- `APPROVED`: read-only, flow xac minh xong
- `REJECTED`: request khoa, FE chi nen hien ket qua va huong dan tao request moi
- `WITHDRAWN`: request khoa

## Mentor Profile, Services

### Mentor Profile hien tai backend ho tro

Khong co entity education/experience rieng. Source hien tai chi co:

- `headline`
- `expertiseDescription`
- `supportingSubjects`
- `isAvailable`
- `helpTopicIds`
- `teachingMode`
- `sessionDuration`
- `linkedinUrl`
- `githubUrl`
- `portfolioUrl`

`bio` khong nam trong mentor profile. FE lay `bio` tu `studentProfile`.

### Mentor profile request

```json
{
  "headline": "Backend Developer | Spring Boot Mentor",
  "expertiseDescription": "Mo ta chuyen mon",
  "supportingSubjects": "EXE101, EXE201, PRJ301",
  "isAvailable": true,
  "helpTopicIds": ["uuid1", "uuid2"],
  "teachingMode": "ONLINE",
  "sessionDuration": 60,
  "linkedinUrl": "https://linkedin.com/in/...",
  "githubUrl": "https://github.com/...",
  "portfolioUrl": "https://..."
}
```

Validation:

- `headline`: required, max 200
- `expertiseDescription`: required, max 1000
- `supportingSubjects`: optional, max 1000
- `helpTopicIds`: required, max 20, khong duoc duplicate
- `teachingMode`: required
- `sessionDuration`: chi `15 | 30 | 60 | 90`

### Mentor profile complete rule

Backend coi mentor profile complete khi co:

- `headline`
- `expertiseDescription`
- it nhat 1 help topic
- `teachingMode`
- `sessionDuration`

### Mentor Services

Chi mentor da verified moi duoc manage services.

Quan trong:

- controller gate bang role `MENTOR`
- service layer con check `mentorProfile.status == ACTIVE` va `verifiedAt != null`

APIs:

- `GET /api/me/mentor-services`
- `GET /api/me/mentor-services/{serviceId}`
- `POST /api/me/mentor-services`
- `PUT /api/me/mentor-services/{serviceId}`
- `PATCH /api/me/mentor-services/{serviceId}/active`
- `DELETE /api/me/mentor-services/{serviceId}`

Request body create/update:

```json
{
  "title": "Review CV xin thuc tap",
  "description": "Mo ta dich vu",
  "durationMinutes": 60,
  "isFree": false,
  "priceAmount": 120000,
  "helpTopicIds": ["uuid1", "uuid2"]
}
```

Validation:

- `title`: required, max 200
- `description`: required, max 1000
- `durationMinutes`: `15 | 30 | 60 | 90`
- `isFree`: required
- neu `isFree = true` -> `priceAmount` phai = 0 hoac null
- neu `isFree = false` -> `priceAmount > 0`
- `helpTopicIds`: required, max 20, active, type `HELP_TOPIC`

Source hien tai:

- `POST /api/me/mentor-services` tra `200`, khong phai `201`
- `DELETE` la soft-off theo huong `isActive = false`

## Discovery & Matching

### Discovery APIs

- `GET /api/mentors/recommendations?limit=12`
- `GET /api/mentors`
- `GET /api/mentors/{mentorUserId}`
- `GET /api/mentors/{mentorUserId}/availability`
- `GET /api/mentors/{mentorUserId}/reviews`

Tat ca deu can login.

### Search filters hien co

Query request `GET /api/mentors`:

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

### Search behavior theo source

Backend search tren cac field:

- mentor `displayName`
- `headline`
- `expertiseDescription`
- `supportingSubjects`
- `studentProfile.bio`
- `program.nameVi`
- `specialization.nameVi`
- mentor service `title`
- mentor service `description`
- help topic tag name/code

Backend co normalize don gian:

- lowercase
- bo dau tieng Viet
- bo ky tu dac biet
- tach token
- co mot vai synonym expansion:
  - `cv` -> `cv`, `resume`, `ho so`
  - `interview` -> `phong van`
  - `project` -> `do an`
  - `study/plan/planning` -> `ke hoach hoc tap`

### Search ranking

Neu co `keyword`:

- search relevance sap xep truoc
- sau do moi den smart matching va quality metrics

Keyword scoring hien tai:

- exact phrase trong corpus: `+50`
- token match moi token: `+8`
- tag token match: `+10`
- service token match: `+12`
- bio token match: `+8`
- coverage bonus theo % token match

### Smart matching scoring

Recommendations va relevance ranking dang dung score:

- cung `program`: `+40`
- cung `specialization`: `+30`
- cung `campus`: `+10`
- mentor la alumni: `+20`
- mentor semester > mentee semester: `+15`
- mentor semester = mentee semester: `+10`

`matchReasons` hien tai chi co o API recommendations.

### Discovery constraints

Mentor chi duoc hien thi neu:

- `mentorProfile.status = ACTIVE`
- `verifiedAt != null`
- `isAvailable = true`
- `bookingSuspendedUntil` null hoac da qua han
- `headline` co data
- `expertiseDescription` co data
- `teachingMode` co data
- `sessionDuration` co data
- user status `ACTIVE`
- co it nhat 1 `HELP_TOPIC`

### Fallback behavior

Neu search/recommend theo matching ma khong du so luong page size, backend co bo sung them active mentors khac de lap day danh sach.

### Mentor card FE nen hien

Dung tu `MentorDiscoveryCardResponse`:

- avatar
- displayName
- headline
- expertiseDescription
- supportingSubjects
- campusName
- programName
- specializationName
- ratingAverage
- reviewCount
- completedSessions
- teachingMode
- helpTopicTags
- verifiedAt

### Mentor detail FE nen hien

Dung `MentorDiscoveryDetailResponse`:

- thong tin card
- `bio`
- `supportingSubjects`
- `bookingSuspendedUntil`
- `defaultSessionDuration`
- social links
- services
- help topics
- semester / alumni

### Missing APIs / Need Backend Support trong discovery

- Filter theo gia dich vu: chua co
- Filter theo `programId`: chua co request param rieng
- Match rationale tren search result page: chua co, chi co recommendations
- Public discovery khong login: chua co, hien tai bat buoc auth

## Booking

### Booking APIs

- `POST /api/bookings`
- `GET /api/me/bookings`
- `GET /api/me/bookings/{bookingId}`
- `POST /api/me/bookings/{bookingId}/cancel`
- `POST /api/me/bookings/{bookingId}/complete`
- `POST /api/mentor/bookings/{bookingId}/accept`
- `POST /api/mentor/bookings/{bookingId}/reject`
- `POST /api/mentor/bookings/{bookingId}/cancel`
- `PATCH /api/mentor/bookings/{bookingId}/meeting-link`

### Booking creation

Request:

```json
{
  "mentorUserId": "uuid",
  "availabilitySlotId": "uuid",
  "serviceId": "uuid-or-null",
  "learningGoalTitle": "Muc tieu chinh",
  "learningGoalDescription": "Mo ta chi tiet"
}
```

Validation:

- `mentorUserId`: required
- `availabilitySlotId`: required
- `learningGoalTitle`: required, max 200
- `learningGoalDescription`: optional, max 2000
- `serviceId`: optional

Booking create rules:

- mentee phai `ACTIVE`
- slot phai ton tai, `active = true`, `booked = false`, startTime o tuong lai
- slot phai thuoc dung mentor
- user khong duoc tu book chinh minh
- mentor phai `ACTIVE`, verified, `isAvailable = true`
- neu `bookingSuspendedUntil > now` -> khong duoc book
- neu slot vua bi nguoi khac dat -> backend tra `409`

Concurrency:

- backend lock slot bang pessimistic lock trong DB
- FE van phai handle `409` va yeu cau user chon slot khac

### Booking status

- `PENDING`
- `ACCEPTED`
- `REJECTED`
- `CANCELLED_BY_MENTEE`
- `CANCELLED_BY_MENTOR`
- `COMPLETED`
- `NO_SHOW`

`NO_SHOW` co enum nhung source hien tai chua thay API xu ly.

### Mentor actions

- Accept chi duoc khi `PENDING`
- Reject chi duoc khi `PENDING`
- Save meeting link chi duoc khi `ACCEPTED`
- Mentor cancel chi duoc khi `ACCEPTED`

Mentor cancel rules:

- bat buoc `cancelReason`
- neu booking da bat dau -> reject
- huy truoc 12h: khong phat
- huy trong khoang `12h > t >= 6h`: cong `0.5` vao `lateCancellationPenaltyPoints`
- huy duoi 6h: set `bookingSuspendedUntil = now + 3 days`
- khi mentor cancel:
  - booking -> `CANCELLED_BY_MENTOR`
  - slot -> `booked = false`, `active = false`

### Mentee actions

- mentee cancel duoc khi `PENDING` hoac `ACCEPTED`
- neu `ACCEPTED` thi phai huy truoc gio hoc it nhat 12h
- bat buoc `cancelReason`
- mentee cancel se tra slot ve `booked = false`

### Complete booking

- mentee hoac mentor deu goi duoc `POST /api/me/bookings/{bookingId}/complete`
- chi duoc khi booking `ACCEPTED`
- khong duoc complete truoc `requestedStartTime`
- request body co `completionNote` optional
- backend luu note vao `mentorNote` neu mentor complete, vao `menteeNote` neu mentee complete

Quan sat source hien tai:

- khong co API reschedule
- khong co API no-show
- khong co separate Session entity/API
- `sessionId` trong `BookingResponse` hien tai = `bookingId`
- `sessionStatus` hien tai = `booking.status`

Canh bao cho FE:

- xem booking va session la cung mot flow hien tai

## Availability

### APIs

- `GET /api/mentor/availability-rules`
- `POST /api/mentor/availability-rules`
- `PUT /api/mentor/availability-rules/{ruleId}`
- `DELETE /api/mentor/availability-rules/{ruleId}`
- `GET /api/mentors/{mentorUserId}/availability?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD`

### Rule model

`UpsertAvailabilityRuleRequest`:

```json
{
  "ruleType": "OPEN",
  "repeatType": "WEEKLY",
  "daysOfWeek": ["MONDAY", "WEDNESDAY"],
  "effectiveFrom": "2026-06-17",
  "effectiveTo": "2026-07-17",
  "startTime": "18:00:00",
  "endTime": "21:00:00",
  "note": "After class"
}
```

Enums:

- `AvailabilityRuleType`: `OPEN`, `CLOSED`
- `AvailabilityRepeatType`: `NONE`, `DAILY`, `WEEKLY`

Validation:

- `ruleType`, `repeatType`, `effectiveFrom`: required
- `effectiveTo >= effectiveFrom`
- `repeatType = WEEKLY` thi `daysOfWeek` bat buoc co
- repeat khac `WEEKLY` thi khong nen gui `daysOfWeek`
- rule `OPEN` bat buoc co `startTime`, `endTime`
- rule `CLOSED` neu gui time range thi `endTime > startTime`

Availability query rules:

- chi xem toi da `31 ngay` moi lan
- default range neu khong gui:
  - `fromDate = hom nay`
  - `toDate = fromDate + 13 ngay`
- chi tra slot `active`, chua booked, o tuong lai
- timezone app: `Asia/Ho_Chi_Minh`

## Feedback & Review

### API

- `POST /api/bookings/{bookingId}/feedback`
- `GET /api/mentors/{mentorUserId}/reviews`

### Ai duoc review

Theo source hien tai:

- chi `MENTEE` moi duoc review `MENTOR`
- mentor khong co API review mentee
- mentor cung khong co API feedback rieng

### Rule submit feedback

- booking phai `COMPLETED`
- reviewer phai la mentee cua booking do
- moi booking chi duoc review 1 lan / reviewer

Request:

```json
{
  "rating": 5,
  "satisfactionLevel": 5,
  "comment": "Rat huu ich",
  "wouldRecommend": true,
  "isPublic": true
}
```

Validation:

- `rating`: required, 1..5
- `satisfactionLevel`: optional, 1..5
- `comment`: backend chua dat max length
- `isPublic`: neu null -> backend mac dinh `true`

Sau khi submit:

- backend recalculate `mentorProfile.averageRating`
- backend recalculate `mentorProfile.totalReviews`

Review public list:

- chi tra feedback co `isPublic = true`
- response hien reviewer display name, avatar, rating, comment, createdAt

### Missing APIs / Need Backend Support

- Mentor review mentee: chua co
- Update/delete feedback: chua co
- Load danh sach feedback cua current user: chua co

## Admin Flows

### Co trong backend

#### System Admin

- `POST /api/system/users/admin-role/grant`
- `POST /api/system/users/admin-role/revoke`
- `GET /api/system/users/admins`
- `GET /api/system/users`

#### Admin

- `POST /api/admin/users/{userId}/ban`
- `POST /api/admin/users/{userId}/unban`
- `GET /api/admin/mentor-verification/requests`
- `GET /api/admin/mentor-verification/requests/{requestId}`
- `GET /api/admin/mentor-verification/requests/{requestId}/lock`
- `POST /api/admin/mentor-verification/requests/{requestId}/lock/refresh`
- `POST /api/admin/mentor-verification/requests/{requestId}/request-revision`
- `POST /api/admin/mentor-verification/requests/{requestId}/approve`
- `POST /api/admin/mentor-verification/requests/{requestId}/reject`
- `GET /api/admin/mentors`
- `GET /api/admin/bookings`
- `GET /api/admin/bookings/{bookingId}`

### User management rules

- admin khong duoc tu ban chinh minh
- admin khong duoc ban `SYSTEM_ADMIN` hoac admin khac
- ban user:
  - revoke active sessions
  - mentor profile `isAvailable = false`
  - deactivate future unbooked slots
  - reject pending bookings cua mentor do

### Missing APIs / Need Backend Support

- Dashboard metrics: missing
- Forum moderation: missing
- Tag approval: missing
- Report handling: missing
- Notification center UI APIs: missing
- Admin note history / audit API doc cho FE: missing

## API Usage Guide

### Auth

| Endpoint | Method | Auth | Request | Success | FE Notes |
|---|---|---|---|---|---|
| `/api/auth/google` | `POST` | No | `{ idToken }` | `200` | Sau login goi ngay `/api/auth/me` |
| `/api/auth/refresh` | `POST` | No | body optional | `200` | Browser phai `credentials: include` |
| `/api/auth/logout` | `POST` | No | body optional | `200` | Browser phai `credentials: include` |
| `/api/auth/me` | `GET` | Bearer | none | `200` | Source de redirect role/onboarding |

### Academic

| Endpoint | Method | Auth | Success | FE Notes |
|---|---|---|---|---|
| `/api/campuses` | `GET` | No | `200` | Cache client duoc |
| `/api/academic-programs` | `GET` | No | `200` | Cache client duoc |
| `/api/academic-programs/{programId}/specializations` | `GET` | No | `200` | Re-fetch khi doi program |
| `/api/me/student-profile` | `GET` | Bearer | `200` | `404` neu chua co profile |
| `/api/me/student-profile` | `PUT` | Bearer | `200` | Form submit chinh |

### Mentor Verification

| Endpoint | Method | Auth | Success | FE Notes |
|---|---|---|---|---|
| `/api/me/mentor-verification/request` | `POST` | Bearer | `200/201` | Phan biet created qua `status` HTTP |
| `/api/me/mentor-verification` | `GET` | Bearer | `200` | Load full request |
| `/api/me/mentor-verification/timeline` | `GET` | Bearer | `200` | Load timeline rieng |
| `/api/me/mentor-verification/documents/{documentId}` | `GET` | Bearer | `200` | Metadata file |
| `/api/me/mentor-verification/documents` | `POST multipart` | Bearer | `201` | Gui file that, khong gui URL |
| `/api/me/mentor-verification/submit` | `POST` | Bearer | `200` | Can `termsAccepted` |
| `/api/me/mentor-verification/documents/{documentId}` | `DELETE` | Bearer | `200` | Chi DRAFT/NEEDS_REVISION |
| `/api/me/mentor-verification/withdraw` | `POST` | Bearer | `200` | DRAFT/NEEDS_REVISION/PENDING_REVIEW |

### Mentor Profile / Services

| Endpoint | Method | Auth | Success | FE Notes |
|---|---|---|---|---|
| `/api/me/mentor-profile` | `GET` | Bearer | `200` | `exists=false` neu chua tao |
| `/api/me/mentor-profile` | `PUT` | Bearer | `200` | Upsert 1 form |
| `/api/me/mentor-services` | `GET` | Bearer + `MENTOR` | `200` | Role gate |
| `/api/me/mentor-services` | `POST` | Bearer + `MENTOR` | `200` | Source hien tai khong tra `201` |
| `/api/me/mentor-services/{serviceId}` | `PUT` | Bearer + `MENTOR` | `200` | Update |
| `/api/me/mentor-services/{serviceId}/active` | `PATCH` | Bearer + `MENTOR` | `200` | Toggle active |
| `/api/me/mentor-services/{serviceId}` | `DELETE` | Bearer + `MENTOR` | `200` | Soft delete |

### Discovery

| Endpoint | Method | Auth | Success | FE Notes |
|---|---|---|---|---|
| `/api/mentors/recommendations` | `GET` | Bearer | `200` | Tra `matchScore`, `matchReasons` |
| `/api/mentors` | `GET` | Bearer | `200` | Search + filter + paging |
| `/api/mentors/{mentorUserId}` | `GET` | Bearer | `200` | Detail page |
| `/api/mentors/{mentorUserId}/availability` | `GET` | Bearer | `200` | Slot list |
| `/api/mentors/{mentorUserId}/reviews` | `GET` | Bearer | `200` | Public reviews |

### Booking

| Endpoint | Method | Auth | Success | FE Notes |
|---|---|---|---|---|
| `/api/bookings` | `POST` | Bearer | `201` | Handle `409` conflict |
| `/api/me/bookings` | `GET` | Bearer | `200` | Query `role=MENTEE|MENTOR` |
| `/api/me/bookings/{bookingId}` | `GET` | Bearer | `200` | Detail |
| `/api/me/bookings/{bookingId}/cancel` | `POST` | Bearer | `200` | Mentee cancel |
| `/api/me/bookings/{bookingId}/complete` | `POST` | Bearer | `200` | Mentor/mentee complete |
| `/api/mentor/bookings/{bookingId}/accept` | `POST` | Bearer + `MENTOR` | `200` | Mentor only |
| `/api/mentor/bookings/{bookingId}/reject` | `POST` | Bearer + `MENTOR` | `200` | Mentor only |
| `/api/mentor/bookings/{bookingId}/cancel` | `POST` | Bearer + `MENTOR` | `200` | Mentor only |
| `/api/mentor/bookings/{bookingId}/meeting-link` | `PATCH` | Bearer + `MENTOR` | `200` | Accepted only |

### Feedback

| Endpoint | Method | Auth | Success | FE Notes |
|---|---|---|---|---|
| `/api/bookings/{bookingId}/feedback` | `POST` | Bearer | `201` | Chi mentee, chi completed |

## FE Data Model Guide

### Core enums FE phai map

#### Roles

- `MENTEE`
- `MENTOR`
- `ADMIN`
- `SYSTEM_ADMIN`

#### UserStatus

- `ACTIVE`
- `INACTIVE`
- `BANNED`
- `DELETED`

#### MentorStatus

- `DRAFT`
- `PENDING_VERIFICATION`
- `ACTIVE`
- `PAUSED`
- `REJECTED`
- `SUSPENDED`

Luu y:

- Source hien tai chu yeu dung `DRAFT`, `PENDING_VERIFICATION`, `ACTIVE`
- `PAUSED`, `REJECTED`, `SUSPENDED` chua thay full flow API

#### VerificationStatus

- `DRAFT`
- `PENDING_REVIEW`
- `NEEDS_REVISION`
- `APPROVED`
- `REJECTED`
- `WITHDRAWN`

#### VerificationDocumentType

- `FPTU_AFFILIATION_PROOF`
- `EXPERTISE_PROOF`

#### VerificationDocumentStatus

- `UPLOADED`
- `ACCEPTED`
- `REJECTED`
- `REMOVED`

#### VerificationStorageKind

- `IMAGE`
- `DOCUMENT`

#### TeachingMode

- `ONLINE`
- `OFFLINE`
- `HYBRID`

#### BookingStatus

- `PENDING`
- `ACCEPTED`
- `REJECTED`
- `CANCELLED_BY_MENTEE`
- `CANCELLED_BY_MENTOR`
- `COMPLETED`
- `NO_SHOW`

#### MeetingPlatform

- `GOOGLE_MEET`
- `ZOOM`
- `MICROSOFT_TEAMS`
- `DISCORD`
- `OFFLINE`
- `OTHER`

#### AvailabilityRuleType

- `OPEN`
- `CLOSED`

#### AvailabilityRepeatType

- `NONE`
- `DAILY`
- `WEEKLY`

#### TagType

- `MAJOR`
- `SPECIALIZATION`
- `TECH_SKILL`
- `BUSINESS_SKILL`
- `LANGUAGE`
- `CAREER`
- `SOFT_SKILL`
- `TOOL`
- `INDUSTRY`
- `HELP_TOPIC`

#### TagStatus

- `ACTIVE`
- `INACTIVE`
- `PENDING_REVIEW`
- `REJECTED`

### Report / Reaction / Forum related

Khong tim thay API backend hien tai cho:

- report status
- reaction type
- forum post/comment

Danh dau: `Missing API / Need Backend Support`

## Error Handling

Backend dung mot envelope chung:

```json
{
  "timestamp": "2026-06-17 15:00:00",
  "status": 400,
  "code": "VAL_3001",
  "message": "studentCode: Mã số sinh viên không đúng định dạng"
}
```

### FE xu ly theo status

- `400`: validation/input error
- `401`: chua auth / session expired
- `403`: khong du quyen / user banned / access denied
- `404`: khong tim thay resource
- `409`: business conflict
- `413`: file qua lon
- `500`: server/storage/database/config error

### Error codes hay gap

- `AUTH_1001`: unauthenticated
- `AUTH_1003`: session expired
- `AUTH_1004`: user banned
- `AUTH_1005`: user inactive
- `AUTH_1006`: Google token verify fail
- `SYS_0006`: database error
- `SYS_0007`: resource conflict
- `VAL_3001`: invalid input

### FE best practice

- `401`:
  - goi refresh 1 lan neu flow dung access token expired
  - neu refresh fail -> logout local + ve login
- `403`:
  - hien screen permission/business rule
- `409`:
  - khong retry blind
  - hien message BE
- `500`:
  - toast chung + cho retry

Khong hardcode message tieng Anh. Backend dang tra message tieng Viet.

## Recommended Next.js Structure

De xuat cho `app/` router:

```text
src/
  app/
    login/
    onboarding/
    mentors/
      page.tsx
      [mentorUserId]/
    my-bookings/
    mentor/
      verification/
      profile/
      services/
      availability/
      bookings/
    admin/
      mentor-verification/
      mentors/
      bookings/
      users/
    system/
      admins/
      users/
  components/
    auth/
    forms/
    mentor/
    booking/
    admin/
    shared/
  lib/
    api/
      http-client.ts
      auth.api.ts
      academic.api.ts
      mentor.api.ts
      booking.api.ts
      feedback.api.ts
      admin.api.ts
      system.api.ts
    auth/
      auth-context.tsx
      token-store.ts
      route-guard.ts
    types/
      api.ts
      auth.ts
      academic.ts
      mentor.ts
      booking.ts
      feedback.ts
      admin.ts
```

### Routing de xuat

- `/login`
- `/onboarding/student-profile`
- `/become-mentor`
- `/mentor/verification`
- `/mentor/profile`
- `/mentor/services`
- `/mentor/availability`
- `/mentor/bookings`
- `/mentors`
- `/mentors/[mentorUserId]`
- `/my-bookings`
- `/admin/mentor-verification`
- `/admin/mentors`
- `/admin/bookings`
- `/admin/users`
- `/system/users`
- `/system/admins`

## State Management & UX

### Global state nen co

- current user (`/api/auth/me`)
- auth status
- access token
- role flags
- refresh-in-progress flag

### Refetch strategy

- Sau login -> refetch `/api/auth/me`
- Sau save student profile -> refetch `/api/auth/me`
- Sau update mentor profile -> refetch mentor profile
- Sau upload/delete/submit verification -> refetch request detail
- Sau create/update/delete service -> refetch services list
- Sau accept/reject/cancel/complete booking -> refetch booking detail + related list

### Loading / empty / disabled

- Tat ca list pages phai co state:
  - loading
  - empty
  - error
- Disable submit button khi:
  - form invalid
  - request dang pending
  - `allowedActions` false
  - current role/status khong hop le

### Search UX

- debounce `keyword` 300-500ms
- reset `page=0` khi doi keyword/filter
- keep query params tren URL

### Pagination

Dung `PageResponse`:

- `content`
- `page`
- `size`
- `totalElements`
- `totalPages`
- `last`

### Toast / modal

- cancel booking: xac nhan truoc khi submit
- reject booking: modal bat buoc reason
- withdraw verification: confirm modal
- admin approve/reject/revision: confirm + form note

### Optimistic update

Khong nen optimistic cho:

- booking create
- mentor verification review
- upload documents
- availability rule mutation

Co the optimistic nhe cho UI toggles local, nhung refetch ngay sau success.

## Business Logic Warnings

- Khong cho user submit mentor verification neu checklist BE chua du.
- Khong cho FE mo mentor service / availability screens chi vi `mentorStatus = ACTIVE`; source hien tai con phu thuoc `roles` JWT.
- Khong cho mentee book neu mentor dang `isAvailable = false` hoac bi suspend; backend da chan nhung FE nen an nut book.
- Khong cho mentee cancel booking `ACCEPTED` neu con duoi 12h.
- Khong cho mentor cancel ma khong co ly do.
- Khong cho submit feedback truoc khi booking `COMPLETED`.
- Khong cho submit feedback 2 lan cho cung booking.
- Mentor reviews page chi la review public.
- Verification documents phai gui file that len BE, khong gui URL.
- `DELETE mentor-service` hien tai la soft disable, khong phai xoa khoi DB.
- `request-revision` sua tren request cu; `REJECTED` va `WITHDRAWN` la request khoa.
- Admin review queue co soft lock 5 phut, FE phai ton trong lock state.

## Implementation Order

Thu tu nen code:

1. `auth`
2. `academic onboarding`
3. `current user bootstrap + route guard`
4. `mentor verification + mentor profile`
5. `mentor discovery list`
6. `mentor detail + reviews + availability`
7. `booking flow`
8. `my bookings / mentor bookings`
9. `feedback/review`
10. `mentor services`
11. `admin mentor verification`
12. `admin mentors + admin bookings + user management`
13. `system admin`
14. `forum` sau, vi backend hien tai chua co

## Final FE Checklist

- Kiem tra API da ton tai that su trong backend truoc khi tao page.
- Kiem tra role gate va business status gate rieng biet.
- Kiem tra enum va status transition tu source BE.
- Kiem tra request validation truoc submit.
- Kiem tra can gui `credentials: include` cho refresh/logout hay khong.
- Kiem tra response status code that te, khong tu suy dien REST ly tuong.
- Kiem tra loading, empty, error, forbidden states.
- Kiem tra `409 conflict` cho booking, verification, service flows.
- Kiem tra file upload theo multipart that.
- Kiem tra `allowedActions`/`checklist` truoc khi enable button.
- Kiem tra refetch sau mutation quan trong.
- Khong duplicate business logic phuc tap neu backend da tra state ro rang.
- Danh dau ro `Missing API / Need Backend Support` thay vi tu invent endpoint.

## Missing API / Need Backend Support Summary

- Role `MENTOR` assignment sau approve mentor verification can backend xac nhan / bo sung
- Public discovery khong login
- Filter mentor theo price
- Match rationale tren search list
- Booking reschedule
- Booking no-show flow
- Session entity/Session APIs rieng
- Mentor feedback cho mentee
- Feedback edit/delete/list cua current user
- Dashboard metrics
- Forum module
- Report handling
- Tag approval / moderation APIs
