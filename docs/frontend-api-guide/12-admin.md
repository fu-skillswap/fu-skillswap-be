# Admin & System Operations Service (`12-admin.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Quản trị Hệ thống và Kiểm duyệt Vận hành (Admin & System Operations Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend Admin, Mobile, QA và Operations.

---

## 1. Overview (Tổng quan)

**Admin & System Operations Service** quản lý toàn bộ bảng điều khiển vận hành trung tâm (`Admin Workbench & Operations Control Center`), giám sát chỉ số tổng quan hệ thống (`Dashboard Overview`), chuỗi thời gian 30 ngày (`Timeseries`), quản lý tài khoản & phân quyền `ADMIN` / `SYSTEM_ADMIN`, duyệt xác minh danh tính Mentor (`Mentor Verification`), xử lý tranh chấp khiếu nại (`Booking Dispute`), kiểm duyệt nội dung Forum & Chat, duyệt chi trả doanh thu (`Payout Requests`), và theo dõi nhật ký hệ thống (`Audit Logs & Email Outbox`).

### Trách nhiệm chính của Service
- **Giám sát & Báo cáo Chỉ số Vận hành (`Dashboard & Timeseries`)**: Cung cấp các snapshot tổng quan về người dùng, hồ sơ duyệt mentor, đơn đặt lịch, báo cáo vi phạm, yêu cầu rút tiền, và biểu đồ chuỗi thời gian 30 ngày theo múi giờ `Asia/Ho_Chi_Minh`.
- **Hàng chờ Xử lý Ưu tiên (`Operational Queue Cards & Drill-Down`)**: Tổ chức các nhóm công việc tồn đọng (Backlog) theo thẻ Queue Card ưu tiên, cho phép Admin click đi sâu vào từng case xử lý cụ thể (`/api/admin/dashboard/queue-items`).
- **Quản lý Tài khoản & Khóa Quyền Truy cập (`User Management & Banning`)**: Cho phép tìm kiếm người dùng, xem tóm tắt hoạt động (`User Summary`), khóa tài khoản vi phạm (`Ban`) hoặc mở lại (`Unban`).
- **Cấp Quyền Quản trị Viên Hệ thống (`System Admin Role Management`)**: Chỉ role `SYSTEM_ADMIN` mới được phép truy vấn và cấp/thu hồi quyền `ADMIN` cho người dùng hệ thống via `/api/system/users/admin-role/grant` & `revoke`.
- **Kiểm duyệt Đa Miền Vận hành (`Multi-Domain Moderation`)**: Duyệt hồ sơ Mentor, xử lý báo cáo vi phạm bài viết/chat, quản lý danh sách từ cấm (`Prohibited Phrases`), duyệt rút tiền ngân hàng, và xem nhật ký thay đổi dữ liệu (`Audit Logs`).

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Phân tách Tuyệt đối Ranh giới Người dùng & Vận hành**: Mọi API quản trị nằm dưới tiền tố `/api/admin/...` và `/api/system/...`, bắt buộc kiểm tra quyền `hasAnyRole('ADMIN', 'SYSTEM_ADMIN')`. Khớp nối bảo mật ngăn chặn hoàn toàn việc gọi nhầm từ giao diện người dùng thông thường.
2. **Quy trình Xử lý Case Theo Xung đột Đồng thời (Concurrent Case Handling)**: Khi nhiều Admin cùng vận hành một hàng chờ, hệ thống hỗ trợ cơ chế khóa/gán case (`Assign / Claim Case`) để tránh trường hợp 2 Admin cùng duyệt trùng một hồ sơ.
3. **Minh bạch Nhật ký Thao tác Quản trị (`Audit Trail & Administrative Notes`)**: Mọi hành động Ban người dùng, duyệt hồ sơ Mentor, từ chối rút tiền hay khóa phòng chat đều bắt buộc đính kèm ghi chú lý do (`AdminNoteRequest`), được ghi lại trực tiếp vào nhật ký `AuditLog`.
4. **An toàn Hệ thống Bằng Cấp Quyền Độc quyền (`System Admin Boundary`)**: Việc gán quyền Admin không thể thực hiện bởi các Admin thông thường; chỉ duy nhất tài khoản Quản trị Hệ thống tối cao (`SYSTEM_ADMIN`) mới có thẩm quyền thao tác.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng Admin)

```
+-------------------------------------------------------------------------------------------------------+
|                                    LUỒNG VẬN HÀNH DÀNH CHO ADMIN / OPERATIONS                         |
+-------------------------------------------------------------------------------------------------------+

  Admin / Operations User (Browser)            Backend (SkillSwap Admin API)           Database / Audit Log
          |                                                   |                                  |
   1. Đăng nhập Tài khoản Admin                               |                                  |
          |-- GET /api/admin/dashboard/overview ------------->|-- Thống kê số dư & backlog ----->|
          |-- GET /api/admin/dashboard/queues --------------->|-- Trả danh sách Queue Cards --->|
          |<-- 200 OK (Snapshot số liệu & Cards) -------------|                                  |
          |                                                   |                                  |
   2. Chọn 1 Queue Card (VD: Hồ sơ Mentor chờ duyệt)          |                                  |
          |-- GET /api/admin/dashboard/queue-items ---------->|-- Trả danh sách Item phân trang->|
          |<-- 200 OK (Danh sách Queue Items) ----------------|                                  |
          |                                                   |                                  |
   3. Mở Chi tiết 1 Case & Xem Minh chứng                     |                                  |
          |-- GET /api/admin/mentor-verifications/{id} ------>|                                  |
          |<-- Trả Chi tiết Hồ sơ, Bằng cấp & Lịch sử --------|                                  |
          |                                                   |                                  |
   4. Bấm "Phê duyệt Hồ sơ" / "Từ chối"                         |                                  |
          |-- POST /admin/mentor-verifications/{id}/approve ->|-- Đổi Status -> APPROVED ------->|
          |   (kèm adminNote)                                 |-- Gửi Email & Notification ---->|
          |                                                   |-- Ghi lại AuditLog ------------->|
          |<-- 200 OK (Cập nhật Status thành công) -----------|                                  |
          |                                                   |                                  |
   5. Reload lại Queue Items & Widget Dashboard -------------->|                                  |
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Bảng Cấu trúc Phân quyền Quản trị (`Admin Role Permission Matrix`)

| Nhóm API | Endpoint Prefix | Phân quyền Bắt buộc | Mục đích Vận hành |
| --- | --- | --- | --- |
| **Dashboard Overview** | `/api/admin/dashboard/...` | `ADMIN`, `SYSTEM_ADMIN` | Xem số liệu tổng quan, biểu đồ timeseries và danh sách queue |
| **Mentor Verification** | `/api/admin/mentor-verifications/...` | `ADMIN`, `SYSTEM_ADMIN` | Duyệt/Từ chối hồ sơ đăng ký làm Mentor của sinh viên |
| **Booking & Dispute** | `/api/admin/bookings/...` | `ADMIN`, `SYSTEM_ADMIN` | Can thiệp đơn hàng, xử lý tranh chấp khiếu nại vắng mặt |
| **Payout Requests** | `/api/admin/payout-requests/...` | `ADMIN`, `SYSTEM_ADMIN` | Duyệt chi trả tiền từ ví Mentor về tài khoản ngân hàng real |
| **Forum & Chat Moderation**| `/api/admin/forum/...`, `/api/admin/chat-reports/...` | `ADMIN`, `SYSTEM_ADMIN` | Khóa bài viết vi phạm, xử lý từ cấm, khóa cuộc trò chuyện |
| **User Ban / Unban** | `/api/admin/users/...` | `ADMIN`, `SYSTEM_ADMIN` | Cấm/Khôi phục tài khoản người dùng vi phạm quy định |
| **System Role Management** | `/api/system/users/...` | `SYSTEM_ADMIN` duy nhất | Trao/Thu hồi quyền Admin cho nhân sự vận hành |

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Header Bắt buộc | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/admin/dashboard/overview` | Admin / System Admin | Bearer | Lấy snapshot tổng quan số liệu người dùng & đơn hàng | Màn hình Dashboard Admin |
| `GET` | `/api/admin/dashboard/queues` | Admin / System Admin | Bearer | Lấy danh sách thẻ Queue Card theo thứ tự ưu tiên | Màn hình Dashboard Admin |
| `GET` | `/api/admin/dashboard/timeseries` | Admin / System Admin | Bearer | Lấy dữ liệu biểu đồ chuỗi thời gian 30 ngày | Biểu đồ biến động Dashboard |
| `GET` | `/api/admin/dashboard/queue-items` | Admin / System Admin | Bearer | Tải danh sách item chi tiết của một Queue cụ thể | Màn hình Queue Workbench |
| `GET` | `/api/admin/users` | Admin / System Admin | Bearer | Danh sách người dùng hệ thống (tìm kiếm, lọc status) | Trang Quản lý Người dùng |
| `POST` | `/api/admin/users/{userId}/ban` | Admin / System Admin | Bearer | Cấm tài khoản người dùng (truyền lý do ban) | Bấm "Ban User" |
| `POST` | `/api/admin/users/{userId}/unban` | Admin / System Admin | Bearer | Bỏ cấm tài khoản người dùng | Bấm "Unban User" |
| `GET` | `/api/system/users/admins` | System Admin | Bearer | Lấy danh sách tài khoản đang có quyền Admin | Trang Quản trị Nhân sự System |
| `POST` | `/api/system/users/admin-role/grant` | System Admin | Bearer | Cấp quyền Admin cho 1 người dùng | Bấm "Cấp quyền Admin" |
| `POST` | `/api/system/users/admin-role/revoke` | System Admin | Bearer | Thu hồi quyền Admin của 1 người dùng | Bấm "Thu hồi quyền Admin" |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `POST /api/admin/users/{userId}/ban`

#### Purpose
Cấm tài khoản người dùng vi phạm tiêu chuẩn cộng đồng hoặc có hành vi gian lận.

#### Request Body (`AdminBanUserRequest`)
```json
{
  "reason": "Phát hiện hành vi spa-link lừa đảo và gian lận thanh toán trên Forum",
  "sendEmailNotification": true
}
```

#### Response Body (`AdminUserResponse`)
```json
{
  "timestamp": "2026-08-04T10:17:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "publicId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "baduser@fpt.edu.vn",
    "fullName": "Nguyễn Văn X",
    "roles": ["MENTEE"],
    "status": "BANNED",
    "banned": true,
    "bannedAt": "2026-08-04T10:17:00Z",
    "banReason": "Phát hiện hành vi spa-link lừa đảo..."
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Duyệt Hồ Sơ Xác Minh Mentor (`Mentor Verification Flow`)

```
Admin (Operations)                     Backend Admin API                       Database & Email Service
        |                                       |                                           |
   1. Mở Queue "Chờ Duyệt Mentor" ------------->|                                           |
        |-- GET /admin/dashboard/queue-items?queueType=MENTOR_VERIFICATION                  |
        |<-- Trả danh sách Hồ sơ chờ -----------|                                           |
   2. Click xem Chi tiết 1 Hồ sơ                |                                           |
        |-- GET /admin/mentor-verifications/{id}|                                           |
        |<-- Trả Hồ sơ & Presigned Download URL |                                           |
   3. Kiểm tra Bằng cấp & Bấm "Duyệt"           |                                           |
        |-- POST /admin/mentor-verifications/{id}/approve --------------------------------->|
        |   { "adminNote": "Đã đối soát bằng cử nhân CNTT FPTU" }                          |
        |                                       |-- Cập nhật User Role -> MENTOR ----------->|
        |                                       |-- Gửi Email Thông báo Thành công --------->|
        |<-- 200 OK ----------------------------|                                           |
   4. Refetch lại Dashboard Widgets ----------->|                                           |
```

---

## 8. State Machine (Ma trận Trạng thái Moderation, User Status & Queue Health)

### 8.1 Trạng thái Tài khoản Người dùng (`UserAccountStatus`)

```
             +-----------------------+
             |        ACTIVE         | (Tài khoản đang hoạt động bình thường)
             +-----------------------+
                         |
            POST /admin/users/{id}/ban
                         |
                         v
             +-----------------------+
             |        BANNED         | (Tài khoản bị cấm truy cập)
             +-----------------------+
                         |
           POST /admin/users/{id}/unban
                         |
                         v
             +-----------------------+
             |        ACTIVE         | (Khôi phục quyền truy cập)
             +-----------------------+
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `403 FORBIDDEN` | `ACCESS_DENIED` | Tài khoản hiện tại không có quyền `ADMIN` hoặc gọi API System Admin bằng tài khoản Admin thường. | Ẩn các nút thao tác không thuộc thẩm quyền, báo lỗi phân quyền. |
| `409 RESOURCE_CONFLICT` | `CASE_ALREADY_PROCESSED` | Queue case này đã được một Admin khác xử lý xong trước đó. | Thông báo Toast *"Hồ sơ này đã được xử lý bởi Admin khác"*, tự động reload lại Queue. |
| `400 BAD_REQUEST` | `INVALID_NOTE` | Thực hiện hành động Reject/Ban mà không nhập ghi chú lý do `adminNote`. | Hiển thị cảnh báo màu đỏ tại ô nhập lý do. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Bảo vệ Phân quyền 2 Lớp (`PreAuthorize`)**: Tất cả các Controller Admin được bảo vệ bởi `@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")`. Mọi request không chứa JWT hợp lệ mang role Admin đều bị từ chối ngay từ Security Filter Chain.
2. **Kiểm tra Thẩm quyền Cấp Role Hệ thống**: API `/api/system/users/admin-role/...` được bảo vệ riêng biệt bởi `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Tách riêng toàn bộ mã nguồn ứng dụng Admin thành dự án/màn hình quản trị độc lập (`Admin Panel / Operations Portal`).
- Luôn thực hiện Tải lại dữ liệu (Refetch) sau mỗi thao tác Phê duyệt, Từ chối, Ban, Unban để cập nhật số liệu mới nhất.
- Bắt buộc hiển thị ô nhập lý do `adminNote` cho các thao tác ảnh hưởng lớn (Từ chối duyệt, Cấm tài khoản, Xóa bài viết).

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** nhúng mã nguồn hoặc gọi các API Admin `/api/admin/...` trong giao diện người dùng thông thường (`Client App`).
- **KHÔNG ĐƯỢC** tự giả định trạng thái case đã hoàn tất mà không chờ response 200 OK từ Backend.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Hai Admin Cùng Xử lý Một Case Đồng thời (`Concurrent Review`)**:
   - Nếu Admin A và Admin B cùng mở 1 hồ sơ Mentor chờ duyệt, khi Admin A bấm "Phê duyệt" trước, request của Admin B bấm sau sẽ nhận về lỗi `409 CASE_ALREADY_PROCESSED`. Frontend Admin B sẽ hiển thị Toast thông báo và cập nhật lại giao diện.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Mentor Service**: Tiếp nhận quyết định phê duyệt hồ sơ từ Admin để nâng cấp tài khoản người dùng thành Mentor.
- **Booking & Payment Services**: Tiếp nhận can thiệp xử lý khiếu nại tranh chấp và phê duyệt rút tiền Payout.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Trang Điều hành Vận hành Trung tâm (`AdminDashboardPage.tsx`)
- **React Components**: `AdminDashboardPage.tsx`, `OverviewMetricCards.tsx`, `PriorityQueueList.tsx`, `TimeseriesChart.tsx`
- **APIs Triggered**:
  1. `GET /api/admin/dashboard/overview` (Snapshot số liệu)
  2. `GET /api/admin/dashboard/queues` (Tải danh sách Queue Cards)
  3. `GET /api/admin/dashboard/timeseries` (Tải dữ liệu biểu đồ 30 ngày)
- **Expected Behavior**: Hiển thị bảng điều khiển trực quan. Cho phép Admin click vào từng Queue Card để điều hướng sang màn hình xử lý case.

#### B. Trình Xử lý Case Hàng chờ (`QueueWorkbenchPage.tsx`)
- **React Components**: `QueueWorkbenchPage.tsx`, `CaseFilterBar.tsx`, `CaseDetailModal.tsx`, `AdminNoteForm.tsx`
- **APIs Triggered**:
  1. `GET /api/admin/dashboard/queue-items` (Tải danh sách case)
  2. Domain Admin Action API (Phê duyệt/Từ chối case)
- **Expected Behavior**: Cho phép lọc case theo danh mục. Khi phê duyệt xong: Reload lại danh sách case.

---

### 14.2 Frontend Admin State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |    DASHBOARD_LOADED   | (Xem bảng điều khiển tổng quan)
                       +-----------------------+
                                   |
                          Chọn 1 Queue Card
                                   |
                                   v
                       +-----------------------+
                       |   WORKBENCH_ACTIVE    | (Xem & Lọc danh sách Queue Cases)
                       +-----------------------+
                                   |
                       Thực thi Action Duyệt/Ban
                                   |
                                   v
                       +-----------------------+
                       |    REFRESHING_QUEUE   | (Tải lại hàng chờ & Dashboard)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Admin Portal | Select Queue Card | Click Case Detail | Submit Approval/Ban | User Action |
| --- | --- | --- | --- | --- | --- |
| `GET /admin/dashboard/overview` | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /admin/dashboard/queue-items` | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| Domain Detail API | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG |
| Domain Action API | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (`adminNote`) | ✅ Bấm "Phê duyệt" / "Ban" |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Case Đã Được Xử lý Bởi Admin Khác (`HTTP 409`)
- **UI Component**: Toast Cảnh báo Vận hành (`OperationsToast.tsx`).
- **Visual State**: Toast màu vàng cam.
- **Message**: *"Hồ sơ này đã được phê duyệt hoặc xử lý bởi một Admin khác trong hệ thống."*
- **Action**: Tự động gọi refetch lại danh sách case.

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['admin', 'overview']` | 1 phút | 10 phút | `true` | Khi thực thi bất kỳ thao tác duyệt/ban nào |
| `['admin', 'queue-items', filter]` | 0 ms | 5 phút | `true` | Khi hoàn tất 1 case |
| `['admin', 'users', search]` | 1 phút | 10 phút | `true` | Ban hoặc Unban người dùng thành công |
