# Admin

## Mục tiêu
File này là guide cho admin/system-admin-only APIs.
FE user thường không được dùng file này để build UI sản phẩm.

## API inventory
### Dashboard & operations
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/admin/dashboard/overview` | Authenticated | admin/system admin | - | dashboard DTO | - | Overview số liệu |
| GET | `/api/admin/dashboard/queues` | Authenticated | admin/system admin | - | queue DTO | - | Queue health |
| GET | `/api/admin/dashboard/timeseries` | Authenticated | admin/system admin | - | timeseries DTO | - | Chart data |
| GET | `/api/admin/dashboard/queue-items` | Authenticated | admin/system admin | - | page DTO | - | Item trong queue |

### User & system roles
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/admin/users` | Authenticated | admin/system admin | filter request | page DTO | - | Danh sách user |
| GET | `/api/admin/users/{userId}/summary` | Authenticated | admin/system admin | path `userId` | summary DTO | - | Summary cua mot user |
| POST | `/api/admin/users/{userId}/ban` | Authenticated | admin/system admin | ban request | user DTO | - | Ban user |
| POST | `/api/admin/users/{userId}/unban` | Authenticated | admin/system admin | unban request | user DTO | - | Unban user |
| POST | `/api/system/users/admin-role/grant` | Authenticated | system admin | role request | user DTO | - | Grant admin role |
| POST | `/api/system/users/admin-role/revoke` | Authenticated | system admin | role request | user DTO | - | Revoke admin role |
| GET | `/api/system/users/admins` | Authenticated | system admin | - | list DTO | - | Danh sách admin |
| GET | `/api/system/users` | Authenticated | system admin | filter request | page DTO | - | Tất cả user |

### Domain admin actions
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Booking admin endpoints | `/api/admin/bookings/...` | Authenticated | admin/system admin | booking admin DTO | booking admin DTO | - | Resolve support / moderation |
| Forum admin endpoints | `/api/admin/forum/...` | Authenticated | admin/system admin | forum admin DTO | forum admin DTO | - | Moderate forum |
| Mentor verification admin endpoints | `/api/admin/mentor-verifications/...` | Authenticated | admin/system admin | verification admin DTO | verification admin DTO | - | Review mentor verification |
| Mentor admin endpoints | `/api/admin/mentors/...` | Authenticated | admin/system admin | mentor admin DTO | mentor admin DTO | - | Moderate mentor data |
| Audit log endpoints | `/api/admin/audit-logs/...` | Authenticated | admin/system admin | filter request | audit log DTO | - | Audit trail |
| Email outbox endpoints | `/api/admin/email-outbox/...` | Authenticated | admin/system admin | filter request | email outbox DTO | - | Queue / retry mail |
| Case / note endpoints | `/api/admin/cases/...`, `/api/admin/notes/...` | Authenticated | admin/system admin | case/note DTO | case/note DTO | - | Support workflow |
| Mentoring questionnaire admin endpoints | `/api/admin/mentoring-questionnaire/...` | Authenticated | admin/system admin | questionnaire DTO | questionnaire DTO | - | Manage matching questionnaire |
| Payout admin endpoints | `/api/admin/payout-requests/...` | Authenticated | admin/system admin | payout DTO | payout DTO | - | Duyệt payout |

### Chat moderation
| Method | Endpoint | Auth | Role | Request | Response | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/admin/chat-reports?status=&page=&size=` | Authenticated | admin/system admin | optional status/page/size | `Page<ChatReportResponse>` | Queue report chat. |
| PATCH | `/api/admin/chat-reports/{reportId}` | Authenticated | admin/system admin | `ChatReportResolveRequest` | `ChatReportResponse` | `RESOLVED_LOCKED` khoa conversation; `RESOLVED_NO_ACTION` chi dong report. |
| PATCH | `/api/admin/chat-reports/conversations/{conversationId}/lock` | Authenticated | admin/system admin | `ConversationLockRequest` | `Void` | Override moderation lock; unblock van ton trong booking/participant policy. |

## Call order chuẩn
### Admin panel
1. Load dashboard overview.
2. Load queues / pending items.
3. Drill down vào domain cần xử lý.
4. Thực hiện action.
5. Refresh lại list/detail sau action.

### System admin flow
1. Dùng các endpoint hệ thống để grant/revoke admin role.
2. Chỉ dùng khi cần quản trị hệ thống, không phải màn UI user thường.

## FE phải làm
- Xem admin endpoints như luồng vận hành riêng.
- Refresh lại item/list sau mỗi action.
- Hiển thị rõ note/audit của admin action nếu backend trả.

## FE không được làm
- Không gọi admin endpoint từ UI user thường.
- Không dùng system-admin endpoint cho admin thường nếu backend đã tách role.
- Không suy diễn action admin từ kết quả user flow.

## FE anti-patterns
- Không trộn admin state với user dashboard state.
- Không dùng admin list để render public view.

## Response JSON example
### Admin dashboard overview
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "totalUsers": 1200,
    "totalBookings": 560,
    "pendingVerification": 12,
    "pendingPayouts": 5
  }
}
```

### Admin user
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "publicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "nguyenvana@gmail.com",
    "roles": ["MENTEE"],
    "status": "ACTIVE",
    "banned": false
  }
}
```

## UI mapping
- Admin dashboard:
  - overview cards, queue widgets, timeseries charts
- Admin user management:
  - list/search, ban/unban action buttons
- Admin moderation:
  - drill-down queues cho booking/forum/verification/payout

## API success/error behavior
- success
  - refresh list/detail/dashboard widget sau mỗi action
- 403
  - role không đủ hoặc endpoint chỉ dành cho system admin
- 409
  - queue item đã được xử lý bởi người khác, phải reload
- 400
  - action sai trạng thái / sai input

## Ghi chú cho AI Agent và FE dev
- Admin endpoints chỉ dùng cho admin panel.
- System-admin endpoints chỉ dùng cho flow cấp hệ thống, không hiển thị trong UI user thường.
- Sau mỗi admin action phải refresh lại queue/detail vì item có thể bị người khác xử lý song song.

## Blog moderation boundary
- Admin fully edits only `PLATFORM` articles.
- `PATCH /api/admin/blog/posts/{postId}/moderation` is the mentor-article path: visibility, taxonomy, SEO overrides and archive only.
- Only admin can feature, soft-delete or restore Blog posts.
- Delete is separate from `DRAFT`/`PUBLISHED`/`ARCHIVED`. Admin list excludes deleted posts by default; use `deleted=true` for deleted history.
- Restore always returns an `ARCHIVED` post. Do not auto-publish restored content.
