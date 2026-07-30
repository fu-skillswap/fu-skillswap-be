# Error Handling

## Mục tiêu
File này là bảng quy chiếu lỗi cho FE:
- status HTTP nào có thể retry
- business code nào cần chặn flow
- lỗi nào phải refresh data
- lỗi nào phải đưa user về login hoặc blocked screen

## Response envelope
Tất cả API business dùng `ApiResponse<T>`:
- `timestamp`
- `status`
- `code`
- `message`
- `data`

### Cách đọc
- `status`
  - HTTP status code
- `code`
  - business code để FE map nghiệp vụ
- `message`
  - message an toàn để hiển thị cho user trong luồng bình thường
- `data`
  - payload typed; error response thường không có data

## Validation error
### `ValidationErrorResponse`
- `field`
  - field bị lỗi
- `message`
  - mô tả lỗi cụ thể
- `rejectedValue`
  - giá trị FE gửi lên nhưng bị từ chối

## HTTP status map
| HTTP | Nghĩa | FE nên làm |
| --- | --- | --- |
| 200 | Thành công | Render data |
| 201 | Tạo mới thành công | Update UI, có thể chuyển màn |
| 400 | Input/validation sai | Highlight form, không retry tự động |
| 401 | Chưa đăng nhập / token hết hạn | Refresh token một lần; nếu fail thì về login |
| 403 | Không có quyền / tài khoản bị khóa | Chặn action, show forbidden/blocked |
| 404 | Không tìm thấy resource | Ẩn item, redirect hợp lý |
| 405 | Method không hợp lệ | Sửa call sai, không retry |
| 409 | Xung đột nghiệp vụ / idempotency conflict | Show conflict message, refresh data |
| 413 | Payload quá lớn | Bảo user giảm kích thước file/body |
| 415 | Unsupported media type | Sửa content type |
| 422 | Input đúng format nhưng không thể xử lý theo business rule tĩnh | Giữ form, sửa lựa chọn/giá trị theo message; không retry tự động |
| 429 | Thao tác quá nhanh | Backoff và retry sau |
| 500 | Lỗi hệ thống | Show fallback, retry có kiểm soát |

## Deploy And Network Recovery
- Một lỗi network/`5xx` ngay lúc deploy không chứng minh request write đã thất bại. FE không tự gửi lại `POST`/`PUT`/`PATCH` mutation ngay lập tức.
- Với booking, payment, feedback, upload confirm hoặc mutation có idempotency key: giữ nguyên key/body, refetch canonical resource trước; chỉ retry theo contract của endpoint khi backend cho phép.
- Với mutation không có idempotency key: refetch resource/list trước khi cho user bấm gửi lại. Không tự tạo duplicate booking, payment checkout hoặc feedback.

## Error codes thực tế
### System
- `UNCATEGORIZED_EXCEPTION`
- `INVALID_KEY`
- `BAD_REQUEST`
- `CONFIGURATION_ERROR`
- `STORAGE_ERROR`
- `DATABASE_ERROR`
- `RESOURCE_CONFLICT`
- `PAYLOAD_TOO_LARGE`
- `UNSUPPORTED_MEDIA_TYPE`
- `TOO_MANY_REQUESTS`
- `METHOD_NOT_ALLOWED`
- `UNPROCESSABLE_ENTITY`

### Auth
- `UNAUTHENTICATED`
- `UNAUTHORIZED`
- `ACCESS_DENIED`
- `SESSION_EXPIRED`
- `USER_BANNED`
- `USER_INACTIVE`
- `OAUTH_VERIFICATION_FAILED`

### Business
- `USER_EXISTED`
- `EMAIL_EXISTED`
- `USER_NOT_FOUND`
- `NOT_FOUND`

### Payment
- `PAYMENT_PROVIDER_ERROR`
- `INSUFFICIENT_BALANCE`

### Blog editor concurrency
- `BLOG_POST_VERSION_CONFLICT`
  - HTTP `409`.
  - `data` contains `postId`, `expectedVersion`, and optionally `currentVersion`.
  - Preserve the local unsaved editor draft, refetch `/api/admin/blog/posts/{postId}`, show a conflict UI, and require explicit resubmission. Do not automatically overwrite server content.

### Forum prohibited phrases
- `FORUM_CONTENT_PROHIBITED`
  - HTTP `400`.
  - A post title/content or comment content matched an active admin phrase rule after normalization.
  - Keep the local draft, ask the user to edit it, and never reveal the matched phrase, rule ID, or match position.
- `FORUM_PROHIBITED_PHRASE_DUPLICATE`
  - HTTP `409`.
  - An admin attempted to create or rename a prohibited phrase to a value that normalizes to an existing rule.
  - Refetch the rule list and choose a distinct phrase.

### Validation
- `INVALID_INPUT`

## Retry policy cho FE
- `401`
  - refresh 1 lần, không loop vô hạn
- `429`
  - backoff
- `409`
  - refresh data rồi thử lại nếu business cho phép
- `500`
  - retry có kiểm soát nếu action idempotent hoặc user chủ động bấm retry
- `400/403/404`
  - không retry tự động
- `422`
  - sửa input/business selection, không retry tự động

## Error matrix theo hành vi sản phẩm
| Nhóm flow | Lỗi hay gặp | FE nên làm |
| --- | --- | --- |
| Auth | `SESSION_EXPIRED`, `USER_BANNED`, `USER_INACTIVE` | về login hoặc blocked screen |
| Booking / payment | `RESOURCE_CONFLICT`, `PAYMENT_PROVIDER_ERROR`, `INSUFFICIENT_BALANCE` | refresh booking/payment detail, hiển thị conflict rõ ràng |
| Storage | `STORAGE_ERROR`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE` | đổi file/loại file, không retry máy móc |
| Mentor / discovery | `NOT_FOUND`, `ACCESS_DENIED` | ẩn dữ liệu, quay lại list |
| Forum / blog | `RESOURCE_CONFLICT`, `INVALID_INPUT` | refresh bài/post, giữ draft nếu có |
| Forum phrase moderation | `FORUM_CONTENT_PROHIBITED` | giữ draft, yêu cầu người dùng sửa nội dung; không retry tự động |
| Blog editor | `BLOG_POST_VERSION_CONFLICT` | giữ Markdown/form local, refetch admin detail, yêu cầu admin xác nhận trước khi gửi lại |
| Scheduling version | `RESOURCE_CONFLICT` với message `MENTOR_SERVICE_VERSION_CONFLICT`, `AVAILABILITY_SLOT_VERSION_CONFLICT`, hoặc `MENTOR_BOOKING_POLICY_VERSION_CONFLICT` | Reload resource, giữ form local, dùng version server trả về rồi yêu cầu user xác nhận gửi lại |
| Slot/service mutation | `RESOURCE_CONFLICT` với `SLOT_HAS_PENDING_BOOKINGS`, `SERVICE_HAS_PENDING_BOOKINGS`, `SLOT_HAS_LOCKING_BOOKINGS`, `SLOT_ALREADY_INACTIVE` | `PENDING` có thể yêu cầu confirm/retry; `LOCKING`/already inactive phải refresh và chặn action |
| Booking candidate | `RESOURCE_CONFLICT` với `SLOT_NO_LONGER_AVAILABLE`, `SERVICE_NO_LONGER_AVAILABLE`, `CANDIDATE_NO_LONGER_AVAILABLE`, `OUTSIDE_MENTOR_BOOKING_POLICY`, `CANDIDATE_LOCKING_BOOKING_OVERLAP`, `CANDIDATE_PENDING_QUOTA_REACHED` | Refetch candidates/booking context; không retry body cũ mù quáng |

## FE implementation rules
- Refresh token một lần duy nhất khi gặp `401`.
- Nếu request mutate trả `409`, luôn reload detail trước khi cho user retry.
- Nếu backend trả `400`, ưu tiên sửa form data thay vì retry.
- Nếu backend trả `422`, giữ form data và yêu cầu user chọn lại dữ liệu/điều kiện hợp lệ; không coi đó là race condition.
- Khi `500` xảy ra ở action idempotent, có thể cho user retry thủ công.
- Với `POST /api/bookings`, giữ nguyên `Idempotency-Key` khi retry cùng một request. Với một booking mới, sinh key mới.

## FE anti-patterns
- Không hiển thị raw stacktrace hoặc exception kỹ thuật cho user.
- Không dùng `code` để đoán domain ngoài phạm vi guide nếu message/field đã đủ.
- Không retry liên tục khi request đang bị `400`, `403`, `404`, `409`.
- Không dùng `422` thay cho `409`: slot, optimistic version, duplicate request và booking state conflict vẫn phải refetch theo luồng `409`.
- Không coi `401` nào cũng là hết session; có thể là cookie/token issue, nhưng xử lý đầu tiên vẫn là refresh một lần.

## Response JSON example
### Business error
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 409,
  "code": "RESOURCE_CONFLICT",
  "message": "Booking đã được xử lý trước đó",
  "data": null
}
```

### Validation error
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "Dữ liệu không hợp lệ",
  "data": [
    {
      "field": "specializationId",
      "message": "Chuyên ngành không thuộc ngành đã chọn",
      "rejectedValue": "33333333-3333-3333-3333-333333333333"
    }
  ]
}
```

## UI mapping
- Form validation:
  - highlight field và show message ngay cạnh input
- Conflict:
  - refresh detail rồi cho user thử lại
- Forbidden/blocked:
  - chuyển sang màn lỗi tương ứng, không retry

## API success/error behavior
- `400`
  - sửa form
- `401`
  - refresh token một lần
- `403`
  - chặn action và show reason
- `409`
  - reload data rồi mới cho retry
- `500`
  - fallback UI / retry có kiểm soát

## Ghi chú cho AI Agent và FE dev
- Nếu có thể xử lý bằng UI state thì không nên biến nó thành retry loop.
- Mọi lỗi validation nên được map ra field cụ thể.
- Error message nên ngắn, rõ, và hành động được.

## Mentor service resources
- `404` khi resource missing, đã xóa hoặc viewer không có entitlement; FE không suy đoán nguyên nhân từ 404.
- `409` khi upload intent đã confirm/expired hoặc resource version stale; refresh quản lý resource trước khi retry.
- `413` cho file vượt quota, `415` cho extension/signature/Office structure không hợp lệ.
- `429` khi tạo download URL quá 12 lần/phút/user hoặc 60 lần/phút/resource; chờ window tiếp theo rồi retry.

## Mentor Blog errors
- `404` on reader Blog detail can mean missing, unpublished, archived, deleted, suspended-author or inaccessible premium content. Do not expose a distinct UI reason.
- `409 BLOG_POST_VERSION_CONFLICT`: preserve the editor draft, fetch latest detail, compare and require explicit resubmission.
- `409 RESOURCE_CONFLICT` while following a mentor/category can mean the server follow cap was reached; render a remove-follow CTA rather than retrying.
- `400` when publishing `BOOKED_MEMBERS` means the article has no entitled service or invalid Markdown/asset reference. Fix the form first.
- `409 BLOG_FOLLOW_LIMIT_REACHED` means the current user already follows 20 mentors or 20 categories. Remove a follow, then retry.
- `400`/`415` during Blog asset intent/confirm means invalid image type, expired intent or asset ownership mismatch; request a new intent instead of reusing a stale one.

## Conversion telemetry
- `POST /api/mentor-discovery/funnel-events` is best-effort. FE must not block a page transition or booking action on a failed telemetry request.
- Invalid event type/source or mismatched mentor/service/slot is intentionally ignored by the server and returns normal success. The payload has no free-form metadata field.

## Booking pricing and deadlines
- Discovery pricing, `POST /api/bookings/quote`, and `POST /api/me/bookings/{bookingId}/checkout-preview` are informational estimates. A `200` response never reserves a candidate, campaign budget, coupon, wallet credit, or payment order.
- If booking creation or checkout later returns `400`/`409`, FE must fetch the canonical candidate or booking state and show the newly calculated amount/deadline. Do not retry a mutation automatically after a network/deploy error.
- `pendingExpireAt` and payment `actionDeadlineAt` are server-enforced. On expiry, refresh booking detail; do not keep a locally counted-down booking actionable.
# Chat errors

- `CHAT_4101` / `CHAT_CLIENT_MESSAGE_CONFLICT` (`409`): cùng `clientMessageId` được gửi lại với payload khác. FE giữ message local, tạo client ID mới chỉ khi người dùng thực sự soạn message khác.
- `ACCESS_DENIED` (`403`): conversation đang read-only hoặc viewer không phải participant. FE refetch conversation detail để lấy `readOnlyReason` và permissions canonical.
- `CHAT_MESSAGE_CURSOR_INVALID` (`400`): FE sent both `beforeSequence` and `afterSequence`; retry with one direction only.
- `CHAT_MESSAGE_NOT_EDITABLE`, `CHAT_MESSAGE_EDIT_WINDOW_EXPIRED`, `CHAT_MESSAGE_VERSION_CONFLICT`: refetch message/thread and do not overwrite a tombstone or newer revision.
- `CHAT_REPLY_TARGET_INVALID`, `CHAT_ATTACHMENT_INVALID`, `CHAT_ATTACHMENT_QUOTA_EXCEEDED`, `CHAT_ATTACHMENT_EXPIRED`, `CHAT_ATTACHMENT_REVOKED`, `CHAT_UPLOAD_INTENT_INVALID`: discard the affected local attachment intent and let the user create a new valid upload.
