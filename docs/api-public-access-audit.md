# Public API Growth Gate Audit

Source of truth: controller/security code as of this phase.

## Policy

- Public APIs are limited to read-only marketing, SEO, catalog, and mentor discovery surfaces.
- Do not whitelist broad module paths such as `/api/blog/**`.
- Public write is only allowed for deduplicated low-risk telemetry such as blog view counting.
- Public APIs must not expose email, phone, student code, verification documents, admin notes, payment, booking, chat, notification inbox, or private user lifecycle data.
- Public API contracts are treated as externally consumed contracts. Breaking changes require versioning or a compatibility window.

## CORS

- Production must keep explicit allowed origins such as `https://skillswap.asia`.
- Do not use wildcard CORS with credentials.
- Staging origins must be listed explicitly and removed if no longer used.

## Public Endpoints

### Catalog / Academic

| Method | Endpoint | Decision | Notes |
| --- | --- | --- | --- |
| GET | `/api/campuses` | PUBLIC | Master data. `Cache-Control` + `ETag`. |
| GET | `/api/academic-programs` | PUBLIC | Master data. `Cache-Control` + `ETag`. |
| GET | `/api/academic-programs/{programId}/specializations` | PUBLIC | Master data. `Cache-Control` + `ETag`. |
| GET | `/api/specializations` | PUBLIC | Master data. `Cache-Control` + `ETag`. |
| GET | `/api/catalog/help-topics` | PUBLIC | Catalog data. `Cache-Control` + `ETag`. |
| GET | `/api/catalog/mentor-profile-options` | PUBLIC | Static form labels. `Cache-Control` + `ETag`. |

### Blog

| Method | Endpoint | Decision | Notes |
| --- | --- | --- | --- |
| GET | `/api/blog/posts` | PUBLIC | Only readable posts according to visibility. Anonymous sees `PUBLIC` only. |
| GET | `/api/blog/posts/{slug}` | PUBLIC | `MEMBERS_ONLY` and `MENTOR_ONLY` remain hidden from anonymous. |
| GET | `/api/blog/posts/{slug}/related` | PUBLIC | Same visibility rules as source post. |
| GET | `/api/blog/posts/{slug}/recommendations` | PUBLIC | Rule-based, not AI. |
| GET | `/api/blog/featured` | PUBLIC | Uses published + featured window. |
| GET | `/api/blog/trending` | PUBLIC | Lightweight cached ranking. |
| GET | `/api/blog/categories` | PUBLIC | Catalog-style read. |
| GET | `/api/blog/tags` | PUBLIC | Catalog-style read. |
| POST | `/api/blog/posts/{postId}/view` | PUBLIC_WRITE_LIMITED | Rate limited and deduped by backend user/IP/User-Agent fingerprint. FE `sessionId` is not trusted as the only dedupe key. |

Auth required:

- `PUT/DELETE /api/blog/posts/{postId}/like`
- `PUT/DELETE /api/blog/posts/{postId}/bookmark`
- `PUT/DELETE /api/blog/categories/{categoryId}/follow`
- `PUT/DELETE /api/blog/tags/{tagId}/follow`
- `POST /api/blog/posts/{postId}/notification-click`
- `POST /api/blog/posts/{postId}/recommendation-click`
- `/api/me/blog/**`

### Mentor Discovery

| Method | Endpoint | Decision | Notes |
| --- | --- | --- | --- |
| GET | `/api/mentors` | PUBLIC | Anonymous gets non-personalized discovery. No matching-profile personalization. |
| GET | `/api/mentors/{mentorUserId}` | PUBLIC | Only discoverable mentors. Response does not include email, phone, student code, proofs, or admin fields. |
| GET | `/api/mentors/{mentorUserId}/reviews` | PUBLIC | Public review surface for trust. |

Auth required in this phase:

- `GET /api/mentors/recommendations`
- `GET /api/mentors/{mentorUserId}/availability`
- `GET /api/mentors/{mentorUserId}/availability-slots`
- `GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates`

Rationale: detailed availability can reveal mentor schedule patterns. Public landing should show mentor trust/profile first; exact booking slot selection remains authenticated.

## Always Auth Required

- `/api/me/**`
- `/api/admin/**`
- booking write/read APIs
- payment, wallet, payout APIs
- chat/conversation APIs
- notification inbox APIs
- mentor verification/profile/service management APIs
- file upload/presigned upload APIs
- Google Calendar connect/disconnect APIs

## Sensitive Field Leak Checklist

Public responses must not contain:

- `email`
- `phone`
- `studentCode`
- `verificationDocument`
- `documentUrl`
- `adminNote`
- `internalStatus`
- payment/ledger/balance fields
- chat/notification private payloads

