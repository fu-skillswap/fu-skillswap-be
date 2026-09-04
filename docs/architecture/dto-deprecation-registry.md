# DTO Deprecation Registry

This registry tracks compatibility fields that are retained while public,
admin, and internal DTO boundaries are being separated. A field must not be
removed only because it is not used by the current UI; the listed consumers
and removal condition must be verified first.

## Rules

- Existing JSON fields remain available during the compatibility period.
- Provider identifiers and storage keys may remain in persistence, webhook,
  reconciliation, and storage services even after being removed from a public
  response.
- `Internal/System` schemas are for operations, provider callbacks, or backend
  tooling; they are not FE user contracts.
- Removal requires consumer verification, a replacement rollout, and one
  compatibility period unless a versioned contract is introduced.

## Registry

| Field | DTO | Module | Reason deprecated | Replacement | Current consumers | Removal condition |
|---|---|---|---|---|---|---|
| `bunnyLibraryId` | `CourseVideoUploadInitResponse` | Course | Provider-specific upload detail | `uploadMetadata` | Legacy FE payloads, video provider/webhook processing | FE uses neutral metadata; provider ID remains internal |
| `bunnyVideoId` | `CourseVideoUploadInitResponse` | Course | Provider-specific upload detail | `uploadMetadata` | Legacy FE payloads, video processing and reconciliation | Same as above |
| `storageProviderType` | `CourseMaterialSummaryResponse` | Course | Storage implementation detail | Provider-neutral asset/material metadata | Backend mapping and legacy schema consumers | FE confirms it is not read and neutral metadata is adopted |
| `objectKey` | `PresignedUploadResponse` | File Storage | Storage implementation detail | Asset/upload metadata | Local upload flow and legacy clients | All FE flows use asset/upload intent identifiers |
| `objectKey` | `ConfirmCoursePdfUploadRequest` | Course | Client currently confirms a storage key | Asset or upload intent ID | Current course PDF confirmation flow | Replacement confirmation endpoint/input is migrated |
| `providerOrderCode` | `PaymentCheckoutResponse` | Payment | Raw payment provider identifier | `paymentOrderId` / `orderCode` | Legacy FE and support/provider correlation | FE migration plus compatibility period; retain internally |
| `providerPaymentLinkId` | `PaymentCheckoutResponse` | Payment | Raw provider payment-link identifier | `checkoutUrl` and normalized payment state | Provider reconciliation and operational support | No public consumer; internal reconciliation replacement exists |
| `providerStatus` | `PaymentCheckoutResponse` | Payment | Raw provider status | normalized `status` | Legacy clients and webhook/reconciliation models | FE uses normalized status; raw value remains internal |
| `paymentLink` | `PaymentCheckoutResponse` | Payment | Legacy alias for checkout URL | `checkoutUrl` | Existing FE compatibility consumers | FE migration and one release grace period |
| `sourceType`, `sourceId` | `WalletTransactionResponse` | Payment | Internal ledger source reference | Public transaction reference or admin detail | Ledger, support, and admin views | Public/admin DTOs are split and support replacement is available |
| `coverImageObjectKey` | `AdminBlogPostDetailResponse` | Blog | Storage implementation detail | Asset ID/public asset URL | Admin editor and image URL fallback | Admin FE asset migration completed |
| `ogImageObjectKey` | `AdminBlogPostDetailResponse` | Blog | Storage implementation detail | Asset ID/public asset URL | Admin editor and image URL fallback | Same as above |
| `contentHash` | `AdminBlogPostDetailResponse` | Blog | CMS/cache/integrity detail | Internal diagnostics | CMS and operational debugging | Admin UI confirms no dependency or field moves to internal detail |
| `reportCount` | `ForumPostResponse`, `ForumCommentResponse` | Forum | Moderation counter mixed into public projection | Admin moderation projection | Moderation counters and existing shared responses | Admin DTOs are used by admin endpoints and public clients migrate |
| `unreadCount`, `realtimeEventKind` | `NotificationResponse` | Notification | Realtime-only fields mixed into REST model | `NotificationRealtimeEvent` / badge event | Existing realtime compatibility path | All realtime consumers use explicit event DTOs |

## Review ownership

Each removal proposal should identify the module owner, FE consumer status,
operational replacement, target release, and rollback plan before changing a
serialized contract.
