# SkillSwap Frontend API Guide

## Mục tiêu
Guide này là tài liệu tích hợp cho FE, không phải API reference thuần.
Mục tiêu:
- gọi đúng API cho đúng flow
- hiểu đúng status/value/phase/outcome
- biết API nào gọi trước, API nào gọi sau
- không tự suy diễn legacy field thành logic mới
- giảm bug FE do gọi nhầm quyền hoặc nhầm nguồn sự thật

## Cách đọc guide
Đọc theo flow sản phẩm, không đọc theo package Java:
1. `01-authentication.md`
2. `02-user-profile.md`
3. `03-mentor.md`
4. `04-discovery-search.md`
5. `05-booking.md`
6. `06-calendar.md`
7. `07-payment.md`
8. `08-chat-notification.md`
9. `09-forum.md`
10. `10-blog.md`
11. `11-storage.md`
12. `12-admin.md`
13. `13-error-handling.md`

## Source of truth
- Source of truth là code hiện tại trong controller, DTO, service và OpenAPI annotations.
- Docs cũ chỉ để đối chiếu, không ưu tiên hơn code.
- Nếu response có canonical field và legacy field:
  - canonical field là nguồn sự thật
  - legacy field chỉ để tương thích ngược

## Quy ước dùng chung
- Tất cả business API đều trả `ApiResponse<T>`.
- List API có thể là:
  - `PageResponse<T>`: page/size cổ điển
  - `CursorPageResponse<T>`: cursor/keyset, `cursor` là **opaque string**
- FE chỉ được truyền lại `nextCursor` nguyên giá trị.
- Không decode, không sửa, không tự tạo cursor.
- Status/enum phải được hiểu theo nghiệp vụ của từng domain.

## Phân quyền khi đọc guide
- `Public`: không cần đăng nhập.
- `Authenticated`: cần access token hợp lệ.
- `Mentor-only`: cần role mentor hoặc điều kiện mentor hợp lệ theo domain.
- `Admin-only`: chỉ dùng trong admin panel.
- `System-admin-only`: chỉ dùng cho luồng cấp hệ thống.

## API inventory checklist
Mỗi file domain phải có ít nhất một bảng:

| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |

## FE anti-patterns bắt buộc tránh
- Không dùng legacy status khi backend đã có canonical status riêng.
- Không lọc/paginate lại toàn bộ data ở FE nếu backend đã expose filter/pagination.
- Không coi websocket/STOMP là source of truth khi REST đã có endpoint sync.
- Không upload file trực tiếp vào bucket public.
- Không gọi admin endpoint từ UI user thường.
- Không đoán flow từ tên field; luôn đọc phần call order và state mapping.

## Danh sách domain và phạm vi
| File | Phạm vi |
| --- | --- |
| `01-authentication.md` | Google login, refresh, logout, current user, session flags |
| `02-user-profile.md` | Student profile, onboarding, academic master data, catalog cho form |
| `03-mentor.md` | Mentor profile, services, verification, mentor readiness |
| `04-discovery-search.md` | Discovery, detail, recommendation, mentor availability view |
| `05-booking.md` | Booking lifecycle, reschedule, issue, availability slot CRUD |
| `06-calendar.md` | Google Calendar connect/disconnect/status và sync semantics |
| `07-payment.md` | Checkout, webhook, wallet, payout, settlement |
| `08-chat-notification.md` | Conversation, message, notification, STOMP contract |
| `09-forum.md` | Forum post/comment/reaction/report + moderation |
| `10-blog.md` | Public blog, feed, follow, bookmark, like, admin blog |
| `11-storage.md` | Presigned upload, local upload, file purpose, safety rules |
| `12-admin.md` | Dashboard, notes, cases, users, mentors, verification, outbox, roles |
| `13-error-handling.md` | ApiResponse, validation error, HTTP status, retry policy |

## Response JSON example
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "items": [],
    "nextCursor": null
  }
}
```

## UI mapping
- FE layout nên map theo domain file, không map theo package Java.
- Mỗi screen chỉ nên đọc 1-2 domain file liên quan trực tiếp.
- Khi một action làm đổi state, FE phải refresh lại detail/status endpoint của chính domain đó.

## API contract and OpenAPI
- Java controller, request/response DTO và runtime exception handler là source of truth. Frontend guide giải thích cách dùng, không thay thế runtime contract.
- OpenAPI được sinh code-first từ `/v3/api-docs`; CI xuất `target/openapi.json` làm artifact cho FE/QA. Không commit file này.
- Beta giữ path `/api/...`; `info.version` là SemVer của contract (`0.1.0-beta` mặc định), không suy ra version từ URL.
- Swagger production tắt mặc định. Khi được bật trong local/internal environment, Swagger UI chỉ dùng Bearer access token; refresh token vẫn chỉ là HttpOnly cookie do browser quản lý.

## API success/error behavior
- `200/201`
  - render data mới ngay.
- `400/409/422`
  - giữ form state nếu có, đồng thời hiển thị thông báo nghiệp vụ rõ nghĩa.
- `401`
  - refresh token 1 lần rồi mới quyết định redirect login.
- `403`
  - chặn hành động và hiển thị thiếu quyền / bị khóa.
- `500`
  - cho phép retry có kiểm soát với action idempotent.

## Ghi chú cho AI Agent và FE dev
- Mọi file domain đều phải trả lời được 5 câu:
  1. Ai gọi?
  2. Khi nào gọi?
  3. Điều kiện trước là gì?
  4. Response nào là nguồn sự thật?
  5. Nếu lỗi thì FE phải làm gì?
- Nếu chưa trả lời được 5 câu trên, chưa coi là đọc xong domain đó.
