# Frontend Guide - Mentor Discovery va Public Profile

> **Quy tac URL:** FE chi dung URL backend tra ve. Khong tu ghep `https://cdn.skillswap.asia` voi ID, ten file, `storageKey` hoac `objectKey`. `STORAGE_PUBLIC_URL_PREFIX` la cau hinh backend, khong phai bien `.env` cua FE.

> **Envelope chung:** Tat ca response nam trong `ApiResponse<T>`. Xem [identity.md](identity.md) de biet envelope, xu ly validation va `429 Retry-After`.

Guide nay danh cho man hinh cong khai va mentee: tim mentor, xem profile, review, blog va chon lich de bat dau booking. Xem [mentor-service.md](mentor-service.md) neu dang lam giao dien mentor quan ly service/lich; xem [mentor-verification.md](mentor-verification.md) neu dang lam onboarding va admin duyet mentor.

## 1. Phan quyen va journey

| Viec can lam | Ai duoc goi API |
| --- | --- |
| Xem danh sach, profile, review va lich xem truoc | Moi nguoi, khong can dang nhap |
| Xem candidate segment chinh xac de booking | User da dang nhap |
| Lay recommendations ca nhan hoa | User da dang nhap |

```text
Discovery list
-> Mentor detail
-> Public availability preview
-> Login neu can
-> Tai lai authenticated availability va candidate
-> Tao booking theo booking guide
```

Khong dung du lieu preview cu de tao booking. Sau khi dang nhap, FE phai tai lai slot/candidate theo authenticated API.

## 2. Tim va loc mentor

`GET /api/mentors` la public. Co the gui Bearer token khi user da dang nhap de backend dung them ngu canh matching; token khong bat buoc de hien thi danh sach.

| Query | Kieu | Mac dinh | Ghi chu |
| --- | --- | --- | --- |
| `page` | number | `0` | Bat dau tu `0` |
| `size` | number | `12` | Backend gioi han tu `1` den `30` |
| `keyword` | string | - | Tim theo profile, service, mon hoc, project va achievement |
| `campusId` | UUID | - | Loc co so hoc thuat |
| `specializationId` | UUID | - | Loc chuyen nganh |
| `sortBy` | string | `relevance` | `relevance`, `ratingAverage`, `reviewCount`, `completedSessions`, `updatedAt` |
| `direction` | `ASC` hoac `DESC` | `DESC` | Ap dung cho sort khac `relevance` |

Response la `ApiResponse<PageResponse<MentorDiscoveryCardResponse>>`:

```ts
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

interface MentorDiscoveryCardResponse {
  identity: {
    mentorUserId: string;
    displayName: string;
    avatarUrl: string | null;
    headline: string | null;
    isVerified: boolean;
    verifiedAt: string | null;
  };
  mentoring: {
    expertiseDescription: string | null;
    foundationSupportLevel: number | null;
    outputReviewSupportLevel: number | null;
    directionSupportLevel: number | null;
  };
  evidence: {
    campusId: string | null;
    campusName: string | null;
    programId: string | null;
    programName: string | null;
    specializationId: string | null;
    specializationName: string | null;
    subjectHighlights: MentorSubjectResult[]; // Toi da 2 item
    featuredProjects: MentorFeaturedProject[]; // Toi da 2 item
    achievements: MentorAchievement[]; // Toi da 2 item
  };
  reputation: {
    ratingState: "NO_REVIEWS" | "RATED";
    ratingAverage: number | null;
    reviewCount: number;
    completedSessions: number;
  };
  availability: { isAvailable: boolean };
  match: { score: number | null };
}
```

- `ratingState = "NO_REVIEWS"` thi `ratingAverage` luon `null`. Khong hien thi `5.0` mac dinh.
- `match.score` co the `null`, dac biet khi sort khong theo relevance. Khong dung score lam thong tin bat buoc tren card.
- Ket qua discovery da co verification, profile cong khai va it nhat mot service `ONE_TO_ONE` active.

## 3. Mentor detail

`GET /api/mentors/{mentorUserId}` la public. Response luon co sau section; collection rong la `[]`, field tuy chon la `null`.

```ts
interface MentorDiscoveryDetailResponse {
  identity: {
    mentorUserId: string;
    displayName: string;
    avatarUrl: string | null;
    headline: string | null;
    isVerified: boolean;
    verifiedAt: string | null;
  };
  mentoring: {
    bio: string | null;
    expertiseDescription: string | null;
    supportLevels: {
      foundation: number | null;
      outputReview: number | null;
      direction: number | null;
    };
  };
  services: MentorServiceResponse[];
  evidence: {
    education: {
      campusId: string | null;
      campusName: string | null;
      programId: string | null;
      programName: string | null;
      specializationId: string | null;
      specializationName: string | null;
      semester: number | null;
      alumni: boolean | null;
    };
    subjectResults: MentorSubjectResult[];
    featuredProjects: MentorFeaturedProject[];
    achievements: MentorAchievement[];
    portfolioUrl: string | null;
    githubUrl: string | null;
    authorityContent: {
      publishedArticleCount: number;
      latestPublishedAt: string | null;
      recentPublicArticles: MentorPublicArticlePreview[];
    };
  };
  reputation: {
    ratingState: "NO_REVIEWS" | "RATED";
    ratingAverage: number | null;
    reviewCount: number;
    completedSessions: number;
  };
  availability: {
    isAvailable: boolean | null;
    suspendedUntil: string | null;
    canRequestBooking: boolean;
  };
}

interface MentorServiceResponse {
  serviceId: string;
  mentorUserId: string;
  title: string;
  description: string;
  expectedOutcome: string;
  durationMinutes: number;
  isFree: boolean;
  priceScoin: number;
  isActive: boolean;
  maintainPostSessionChat: boolean;
  deliveryMode: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

interface MentorPublicArticlePreview {
  id: string;
  title: string;
  slug: string;
  excerpt: string;
  coverImageUrl: string | null;
  readingTimeMinutes: number;
  publishedAt: string;
}

interface MentorSubjectResult {
  id: string;
  subjectCode: string;
  subjectName: string | null;
  scoreValue: number;
  displayOrder: number;
}

interface MentorFeaturedProject {
  id: string;
  title: string;
  pictureUrl: string | null;
  content: string | null;
  projectDescription: string | null;
  liveDemoUrl: string | null;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

interface MentorAchievement {
  id: string;
  title: string;
  awardDescription: string | null;
  achievedAt: string | null;
  productHeader: string | null;
  productDescription: string | null;
  demoUrl: string | null;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}
```

`availability.canRequestBooking` chi noi mentor co offer cong khai san sang hay khong. Quote va create booking van kiem tra lai user, Academic Profile va candidate hien tai.

So dien thoai mentor khong nam trong public detail. Khong lay `phoneNumber` tu API profile rieng de hien thi cho mentee.

## 4. Review va recommendations

- `GET /api/mentors/{mentorUserId}/reviews?page=0&size=10&sortBy=createdAt&direction=DESC` la public, tra `PageResponse<MentorReviewResponse>`. `size` toi da `20`; `sortBy` nhan `createdAt` hoac `rating`.
- `GET /api/mentors/recommendations?limit=12` yeu cau dang nhap. Moi item co `mentor`, `matchScore`, `matchReasons`.

```ts
interface MentorReviewResponse {
  reviewId: string;
  reviewerUserId: string;
  reviewerDisplayName: string;
  reviewerAvatarUrl: string | null;
  rating: number;
  comment: string | null;
  createdAt: string;
}
```

## 5. Lich truoc va sau dang nhap

### 5.1 Public availability preview

`GET /api/mentors/{mentorUserId}/availability-preview?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD`

Endpoint public chi cho xem tu **Thu Hai tuan hien tai** den **Chu Nhat tuan sau** theo `Asia/Ho_Chi_Minh`. Khong gui khoang ngay ngoai cua so nay; backend tra `400`. Bo ca hai query de backend dung toan bo cua so mac dinh.

```ts
interface MentorPublicAvailabilityPreviewResponse {
  timezone: string; // Hien tai: Asia/Ho_Chi_Minh
  isPublicOfferAvailable: boolean;
  nextAvailableAt: string | null;
  slots: Array<{
    startTime: string;
    endTime: string;
    services: Array<{
      serviceId: string;
      title: string;
      durationMinutes: number;
      isFree: boolean;
      priceScoin: number;
    }>;
  }>;
}
```

Response public khong co `slotId`, quota, request count hoac booking state. Neu user chon gio truoc login, chi luu `mentorUserId`, service muon chon va thoi gian trong state client; sau login phai tai lai du lieu authenticated.

### 5.2 Chon slot va candidate sau login

1. Goi `GET /api/mentors/{mentorUserId}/availability-slots?fromDate=...&toDate=...` voi Bearer token.
2. User chon mot service trong `slot.services`.
3. Goi `GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId={serviceId}`.
4. Chi cho chon candidate co `isSelectable = true`.
5. Truoc khi create booking, luon dung candidate vua tai lai; khong dung preview cu.

```ts
interface MentorAvailabilitySlotResponse {
  slotId: string;
  startTime: string;
  endTime: string;
  timezone: string;
  pendingRequestCount: number;
  acceptedSlotCount: number;
  services: AvailabilitySlotServiceBasicResponse[];
}

interface AvailabilitySlotServiceBasicResponse {
  serviceId: string;
  title: string;
  durationMinutes: number;
  isFree: boolean;
  priceScoin: number;
  bindingRemoval: {
    mode: "ALLOWED" | "REQUIRES_PENDING_REJECTION" | "BLOCKED_BY_LOCKING_BOOKING";
    restrictionCode: string | null;
    affectedPendingBookingCount: number;
  };
}

interface ServiceSlotCandidatesResponse {
  slotId: string;
  serviceId: string;
  serviceDurationMinutes: number;
  candidateServiceSlots: Array<{
    startTime: string;
    endTime: string;
    pendingCount: number;
    remainingPendingQuota: number;
    isSelectable: boolean;
    reasonIfBlocked: string | null;
    blockedByAcceptedBooking: boolean;
    blockingBookingId: string | null;
    blockingServiceId: string | null;
    blockingServiceTitle: string | null;
    blockedBySameService: boolean;
    blockedByDifferentService: boolean;
    bookingConflictNote: string | null;
  }>;
}
```

`404` nghia la mentor, slot hoac service khong con hop le. `409` nghia la service/slot vua thay doi hoac bi khoa. Trong ca hai truong hop, dong lua chon cu va tai lai availability thay vi retry mu.

## 6. Mentor Profile cua chinh minh

`GET /api/me/mentor-profile` va `PUT /api/me/mentor-profile` yeu cau Bearer token va role `MENTEE` hoac `MENTOR`.

`GET` luon tra `200`. Neu user chua tao profile, `data.exists = false`; day khong phai loi. `requiredFieldsCompleted` la co ho tro cho UI, nhung wizard verification van dung `GET /api/me/mentor-verification/progress` lam source of truth cho CTA nop ho so.

```ts
interface MentorProfileUpsertRequest {
  headline: string; // 1-200 ky tu
  expertiseDescription: string; // 1-1000 ky tu
  isAvailable?: boolean;
  subjectResults: Array<{
    subjectCode: string; // Toi da 80 ky tu, khong trung trong request
    subjectName?: string; // Toi da 200 ky tu
    scoreValue: number; // 0-10
  }>; // 1-20 item
  foundationSupportLevel: 1 | 2 | 3 | 4;
  outputReviewSupportLevel: 1 | 2 | 3 | 4;
  directionSupportLevel: 1 | 2 | 3 | 4;
  githubUrl?: string | null;
  portfolioUrl?: string | null;
  phoneNumber: string; // 10 so Viet Nam: 03/05/07/08/09...
  minimumBookingLeadTimeMinutes?: number;
  maximumBookingHorizonDays?: number;
  bookingTimezone?: string;
}

interface MentorProfileResponse {
  exists: boolean;
  requiredFieldsCompleted: boolean;
  userId: string;
  email: string | null;
  displayName: string | null;
  avatarUrl: string | null;
  mentorStatus: string | null;
  headline: string | null;
  expertiseDescription: string | null;
  isAvailable: boolean | null;
  bookingSuspendedUntil: string | null;
  lateCancellationPenaltyPoints: number;
  verifiedAt: string | null;
  minimumBookingLeadTimeMinutes: number;
  maximumBookingHorizonDays: number;
  bookingTimezone: string;
  subjectResults: MentorSubjectResult[];
  foundationSupportLevel: number | null;
  outputReviewSupportLevel: number | null;
  directionSupportLevel: number | null;
  featuredProjects: MentorFeaturedProject[];
  achievements: MentorAchievement[];
  githubUrl: string | null;
  portfolioUrl: string | null;
  phoneNumber: string | null;
  ratingAverage: number | null;
  reviewCount: number;
  completedSessions: number;
  createdAt: string | null;
  updatedAt: string | null;
}
```

`phoneNumber`, `email`, penalty va booking policy la du lieu rieng cua mentor. Khong lay response nay de hien thi trang public; dung `GET /api/mentors/{mentorUserId}` o phan 3.

### 6.1 Project va achievement

| Muc dich | Endpoint |
| --- | --- |
| List/create project | `GET`, `POST /api/me/mentor-projects` |
| Update/delete project | `PUT`, `DELETE /api/me/mentor-projects/{projectId}` |
| Upload anh project | `PUT /api/me/mentor-projects/{projectId}/picture` voi `multipart/form-data`, field `file` |
| List/create achievement | `GET`, `POST /api/me/mentor-achievements` |
| Update/delete achievement | `PUT`, `DELETE /api/me/mentor-achievements/{achievementId}` |

Project nhan `title` bat buoc; `content`, `projectDescription`, `liveDemoUrl` tuy chon. Achievement nhan `title` bat buoc; `awardDescription`, `achievedAt`, `productHeader`, `productDescription`, `demoUrl` tuy chon. Khi upload anh project thanh cong, dung `pictureUrl` backend tra ve; khong dung URL upload noi bo.

## 7. Error handling

| HTTP | FE can lam |
| --- | --- |
| `400` | Hien thi validation theo field hoac message; khong retry tu dong |
| `401` | Thuc hien refresh flow tu `identity.md`; neu refresh fail thi ve login |
| `403` | Khong hien thi action khong dung role |
| `404` | Mentor/slot/service khong con public; quay lai danh sach phu hop |
| `409` | Du lieu vua doi hoac slot bi khoa; tai lai resource truoc |
| `429` | Doc `retryAfterSeconds`, khoa nut va hien thi thoi gian cho |
