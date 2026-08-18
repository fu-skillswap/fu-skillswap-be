# Frontend Guide - Mentor Service va Lich Ranh

> **Quy tac URL:** Chi dung URL backend tra ve. Khong tu tao URL CDN, `storageKey` hoac `objectKey`. Xem [identity.md](identity.md) cho envelope chung, refresh token va `429 Retry-After`.

Guide nay danh cho giao dien mentor sau khi da co role `MENTOR`: quan ly service `ONE_TO_ONE`, booking policy va lich ranh. Xem [mentor-discovery.md](mentor-discovery.md) cho card/profile cong khai va cach mentee chon candidate; xem [mentor-verification.md](mentor-verification.md) cho dieu kien de duoc duyet mentor.

## 1. Dieu kien va luong

Tat ca endpoint trong guide nay can Bearer token va role `MENTOR`.

```text
Mentor da duoc duyet
-> Tao service ONE_TO_ONE active
-> Tao direct slot hoac weekly template
-> Discovery hien mentor khi du dieu kien public
-> Mentee chon candidate va tao booking
```

Verification khong tu tao service hay slot. Service va availability khong phai dieu kien submit verification, nhung can thiet de mentor nhan booking va xuat hien trong discovery.

## 2. Quan ly service mentoring

1. Goi `GET /api/me/mentor-services/constraints` truoc khi mo form de lay duration va khoang gia do platform quy dinh.
2. Tao service bang `POST /api/me/mentor-services`.
3. Dung `GET /api/me/mentor-services?isActive=true|false` de quan ly danh sach. Bo `isActive` de lay toan bo.
4. Dung `PUT /api/me/mentor-services/{serviceId}` de sua va `PATCH /api/me/mentor-services/{serviceId}/active` de bat/tat.

```ts
interface CreateMentorServiceRequest {
  title: string; // Toi da 200 ky tu
  description: string; // Toi da 1000 ky tu
  expectedOutcome: string; // Toi da 1000 ky tu
  durationMinutes: number; // Phai thuoc allowedDurationMinutes
  isFree: boolean;
  priceScoin: number;
  maintainPostSessionChat?: boolean;
  deliveryMode?: string;
}

interface UpdateMentorServiceRequest {
  title: string;
  description: string;
  expectedOutcome: string;
  isFree: boolean;
  priceScoin: number;
  maintainPostSessionChat?: boolean;
  expectedVersion: number;
}

interface MentorServiceActiveRequest {
  isActive: boolean;
  expectedVersion: number;
  rejectPendingBookings?: boolean;
  pendingRejectionToken?: string;
}

interface MentorServiceManagementResponse {
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
```

`durationMinutes` va `deliveryMode` khong nam trong update request. Hien thi chung la field khong the sua sau khi tao. Khi response tra `409`, tai lai service roi yeu cau mentor xac nhan lai; khong gui lai `expectedVersion` cu.

Khong tu tao `pendingRejectionToken`. Neu backend yeu cau xu ly pending booking, hien thi xac nhan bang thong tin conflict/preview backend tra ve va dung du lieu do cho lan goi ke tiep.

## 3. Booking policy va scheduling constraints

| Endpoint | Muc dich |
| --- | --- |
| `GET /api/me/mentor-booking-policy` | Doc lead time, horizon va timezone hien tai |
| `PATCH /api/me/mentor-booking-policy` | Sua policy cua mentor |
| `GET /api/me/mentor-scheduling-constraints` | Lay gioi han platform truoc khi mo form lich |

Update policy bat buoc co `expectedVersion`. Khong hard-code gioi han platform; luon lay scheduling constraints truoc khi tao/sua lich.

```ts
interface UpdateMentorBookingPolicyRequest {
  minimumBookingLeadTimeMinutes?: number;
  maximumBookingHorizonDays?: number;
  timezone?: string;
  expectedVersion: number;
}
```

## 4. Direct availability slots

Direct slot la lich mot lan. Thoi gian request dung `Instant` UTC, vi du `2026-06-29T01:00:00Z`; UI doi gio nguoi dung nhap sang UTC truoc khi gui va hien thi theo timezone tu response/policy.

| Endpoint | Muc dich |
| --- | --- |
| `POST /api/me/availability-slots` | Tao slot mot lan |
| `GET /api/me/availability-slots?isActive=&fromDate=&toDate=` | Xem slot mentor quan ly |
| `PUT /api/me/availability-slots/{slotId}` | Sua direct slot chua bi khoa booking |
| `POST /api/me/availability-slots/{slotId}/deactivate` | Rut slot theo contract deactivation |

```ts
interface CreateAvailabilitySlotRequest {
  startAt: string; // ISO Instant UTC
  endAt: string; // ISO Instant UTC
  note?: string; // Toi da 200 ky tu
  serviceIds: string[];
  replaceGeneratedOccurrences?: boolean;
  rejectPendingBookings?: boolean;
  expectedTemplateVersions?: Array<{ templateId: string; expectedVersion: number }>;
}

interface UpdateAvailabilitySlotRequest extends CreateAvailabilitySlotRequest {
  expectedVersion: number;
  pendingRejectionToken?: string;
}

interface DeactivateAvailabilitySlotRequest {
  expectedVersion: number;
  rejectPendingBookings?: boolean;
  pendingRejectionToken?: string;
  expectedTemplateVersion?: number;
}
```

Neu direct slot overlap occurrence tu template, backend co the tra `409 GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED`. FE hien thi xac nhan, tai lai version hien tai va chi retry voi `replaceGeneratedOccurrences = true` cung day du `expectedTemplateVersions`.

Generated slot do template quan ly khong duoc sua nhu direct slot. Deactivate generated slot duoc backend xu ly theo exception cua template, vi vay co the can `expectedTemplateVersion`.

## 5. Availability templates

Template la lich lap hang tuan dung mui gio `Asia/Ho_Chi_Minh`; backend materialize thanh slots cu the cho booking. Khong doc template truc tiep de tao booking.

| Endpoint | Muc dich |
| --- | --- |
| `POST /api/me/availability-templates` | Tao template |
| `GET /api/me/availability-templates` | List cursor pagination |
| `GET /api/me/availability-templates/{templateId}` | Detail template |
| `PUT /api/me/availability-templates/{templateId}` | Sua template |
| `POST /{templateId}/pause`, `/resume`, `/archive` | Doi trang thai |
| `PUT /{templateId}/exceptions/{occurrenceDate}` | Bo mot ngay occurrence |
| `POST /{templateId}/exceptions/{occurrenceDate}/restore` | Khoi phuc ngay da bo |

List ho tro `configuredStatus`, `effectiveStatus`, `cursor`, `limit` va tra `CursorPageResponse`. Khong decode hoac tu tao `cursor`; chi gui lai nguyen `nextCursor` backend tra ve.

```ts
interface AvailabilityTemplateResponse {
  templateId: string;
  startTime: string; // LocalTime, vi du 09:00:00
  endTime: string;
  weekdays: string[]; // MONDAY ... SUNDAY
  effectiveFrom: string; // LocalDate
  effectiveTo: string | null;
  timezone: string;
  note: string | null;
  configuredStatus: "ACTIVE" | "PAUSED" | "ARCHIVED";
  effectiveStatus: "ACTIVE" | "PAUSED" | "EXPIRED" | "ARCHIVED";
  configVersion: number;
  services: AvailabilitySlotServiceBasicResponse[];
  generationBlockedReason: string | null;
  skippedDates: string[];
  blockedOccurrences: Array<{ date: string; reason: string; slotId: string | null }>;
  createdAt: string;
  updatedAt: string;
}
```

Moi mutation sau create dung `expectedVersion` hoac `configVersion`. Neu nhan `409`, tai lai template truoc khi cho mentor sua lai. Khong tu tang version o FE.

`skippedDates` la ngay mentor chu dong bo qua. `blockedOccurrences` la ngay backend chua the tao slot, vi du dang bi manual slot overlap; khong tu coi day la exception cua mentor.

## 6. Loi can xu ly

| HTTP | FE can lam |
| --- | --- |
| `400` | Hien validation theo field/message; khong retry tu dong |
| `401` | Theo refresh flow trong [identity.md](identity.md) |
| `403` | Day la API chi cho `MENTOR`; an action neu user chua duoc duyet |
| `404` | Service/slot/template khong con ton tai hoac khong thuoc mentor hien tai |
| `409` | Tai lai resource va dung version moi; khong ghi de thay doi cua mentor khac/booking |
| `429` | Doc `retryAfterSeconds`, khoa nut va hien thi thoi gian cho |
