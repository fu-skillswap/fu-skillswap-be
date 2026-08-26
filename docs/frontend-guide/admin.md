# Frontend Integration Guide — Cổng Quản Trị Hệ Thống (Admin Portal & Workbench)

> **Phân quyền truy cập:** Tất cả các API trong tài liệu này yêu cầu Header `Authorization: Bearer <accessToken>` của tài khoản có Role `ADMIN` hoặc `SYSTEM_ADMIN`. Người dùng thường (`MENTEE`, `MENTOR`) khi gọi các API này sẽ nhận lỗi `403 Forbidden`.

> **Chuẩn Envelope:** Tất cả phản hồi từ backend đều được bọc trong `ApiResponse<T>`. Vui lòng xem [identity.md](identity.md) để biết cấu trúc envelope, cơ chế refresh token và cách xử lý mã lỗi `429 Retry-After`.

---

## 1. Kiến Trúc Phân Quyền Quản Trị (Admin Security & Roles)

### 1.1 Nguyên Tắc Phân Quyền Quản Trị Viên
Hệ thống SkillSwap áp dụng kiến trúc **Tách biệt vai trò nghiêm ngặt (Strict Role Separation)**:
- Khi một tài khoản được cấp quyền `ADMIN`, backend sẽ tự động gỡ bỏ các Role `MENTEE` và `MENTOR` để tài khoản trở thành **Admin-Only**.
- Tài khoản Admin **không tham gia** vào các hoạt động người dùng thông thường (không đặt lịch, không tạo dịch vụ, không đăng bài forum client).
- **Phân cấp giữa ADMIN và SYSTEM_ADMIN**:
  - `ADMIN`: Quản trị viên vận hành hàng ngày (duyệt mentor, duyệt rút tiền, xử lý khiếu nại booking, kiểm duyệt nội dung, quản lý coupon/campaign).
  - `SYSTEM_ADMIN`: Quản trị viên cấp cao nhất (toàn quyền như Admin, cộng thêm quyền Cấp/Thu hồi Role Admin và Force Unassign case của Admin khác).

---

### 1.2 Nhóm API Quản Lý Quyền Quản Trị (`SYSTEM_ADMIN` Only)

| Thao tác | Endpoint | Method | Mô tả & Lưu ý |
|---|---|---|---|
| Cấp quyền Admin | `/api/system/users/admin-role/grant` | `POST` | Body `{ "email": string }`. Gỡ role Mentee/Mentor và gán `ADMIN`. |
| Thu hồi quyền Admin | `/api/system/users/admin-role/revoke` | `POST` | Body `{ "email": string }`. Thu hồi `ADMIN` và trả về `MENTEE` mặc định. |
| Danh sách Admin hiện tại | `/api/system/users/admins` | `GET` | Phân trang danh sách các tài khoản đang giữ quyền `ADMIN`. |
| Danh sách toàn bộ System Users | `/api/system/users` | `GET` | Xem danh sách tất cả tài khoản trong hệ thống ở cấp System. |

---

## 2. Bảng Điều Khiển & Hàng Đợi Vận Hành (Dashboard & Queue Workbench)

### 2.1 Snapshot Tổng Quan Vận Hành (`GET /api/admin/dashboard/overview`)
- **Endpoint**: `GET /api/admin/dashboard/overview`
- **Mục đích**: Trả về dữ liệu tổng hợp tức thời để dựng các Widget thống kê trên trang Dashboard chính của Admin.
- **Response**: `ApiResponse<AdminDashboardOverviewResponse>`

```typescript
interface AdminDashboardOverviewResponse {
  totalUsers: number;                   // Tổng số người dùng hệ thống
  activeUsers: number;                  // Số người dùng đang hoạt động
  bannedUsers: number;                  // Số người dùng đang bị khóa
  pendingMentorVerifications: number;   // Số hồ sơ mentor đang chờ duyệt
  activeMentors: number;                // Tổng số mentor đang hoạt động
  activeBookings: number;               // Số booking đang trong tiến trình
  disputedBookings: number;             // Số booking có khiếu nại (UNDER_REVIEW)
  pendingForumReports: number;          // Số báo cáo vi phạm forum chưa xử lý
  pendingPayoutRequests: number;        // Số yêu cầu rút tiền đang chờ duyệt
  pendingPayoutAmountScoin: number;     // Tổng số Scoin đang chờ chi trả
  failedPaymentOrders: number;          // Số đơn hàng thanh toán lỗi cần kiểm tra
}
```

---

### 2.2 Danh Sách Hàng Đợi Ưu Tiên (`GET /api/admin/dashboard/queues`)
- **Endpoint**: `GET /api/admin/dashboard/queues`
- **Mục đích**: Trả về danh sách thẻ công việc (Queue Cards) được sắp xếp theo mức độ khẩn cấp (SLA Priority) để Admin biết cần xử lý việc gì trước ngay khi đăng nhập.

```typescript
interface AdminDashboardQueuesResponse {
  queues: AdminQueueCardResponse[];
}

interface AdminQueueCardResponse {
  queueKey: 
    | "BOOKING_DISPUTE"             // Khiếu nại booking (SLA cao nhất)
    | "MENTOR_VERIFICATION"         // Duyệt hồ sơ mentor mới
    | "FORUM_REPORT"                // Báo cáo vi phạm cộng đồng
    | "PAYOUT_REQUEST"              // Yêu cầu rút tiền của mentor
    | "FAILED_PAYMENT_ORDER"        // Đơn thanh toán PayOS gặp sự cố
    | "EMAIL_OUTBOX_DEAD_LETTER";   // Email gửi thất bại
  title: string;
  description: string;
  pendingCount: number;             // Số lượng case đang tồn đọng
  oldestPendingAt: string | null;   // Thời gian của case tồn đọng lâu nhất
  slaBreachCount: number;           // Số case đã vượt quá thời hạn cam kết SLA
  priorityOrder: number;            // Thứ tự ưu tiên (1 là cao nhất)
}
```

---

### 2.3 Biểu Đồ Thống Kê 30 Ngày (`GET /api/admin/dashboard/timeseries`)
- **Endpoint**: `GET /api/admin/dashboard/timeseries`
- **Mục đích**: Trả về dữ liệu chuỗi thời gian 30 ngày gần nhất (theo múi giờ `Asia/Ho_Chi_Minh`) để vẽ biểu đồ tăng trưởng (Line/Bar Charts).

```typescript
interface AdminDashboardTimeseriesResponse {
  dailyMetrics: Array<{
    date: string;                   // Định dạng YYYY-MM-DD
    newUsersCount: number;          // Sinh viên đăng ký mới
    mentorVerificationSubmits: number;
    newBookingsCount: number;       // Số lượt đặt lịch mới
    paidPaymentsCount: number;      // Số đơn thanh toán thành công
    forumReportsCount: number;      // Số lượng báo cáo vi phạm
    payoutRequestsCount: number;    // Số yêu cầu rút tiền phát sinh
  }>;
}
```

---

### 2.4 Xem Chi Tiết Từng Hàng Đợi (`GET /api/admin/dashboard/queue-items`)
- **Endpoint**: `GET /api/admin/dashboard/queue-items`
- **Query Params**:
  - `queueKey` (Bắt buộc): Mã queue lấy từ mục 2.2
  - `assignedToMeOnly` (boolean, optional): `true` để chỉ lọc các case do chính tôi đang nhận xử lý
  - `unassignedOnly` (boolean, optional): `true` để lọc case chưa có ai nhận
  - `page`, `size` (phân trang chuẩn)

---

## 3. Cơ Chế Nhận & Điều Phối Case (Case Ownership & Locking)

Hệ thống cung cấp cơ chế **Nhận quyền xử lý (Case Ownership)** nhằm tránh tình trạng 2 Admin cùng thao tác đồng thời trên một hồ sơ:

| Thao tác | Endpoint | Method | Mô tả |
|---|---|---|---|
| Kiểm tra người đang giữ case | `/api/admin/cases/{caseType}/{caseId}/ownership` | `GET` | Xem ai đang phụ trách xử lý case này. |
| Nhận case cho chính mình | `/api/admin/cases/{caseType}/{caseId}/assign` | `POST` | Nếu case chưa có ai nhận: Gán cho caller. Nếu admin khác đang giữ: Nhận lỗi `409 Conflict`. |
| Nhả quyền xử lý (Unassign) | `/api/admin/cases/{caseType}/{caseId}/unassign` | `POST` | Admin hiện tại tự nhả case; `SYSTEM_ADMIN` có quyền Force Unassign case của người khác. |
| Lịch sử thao tác nội bộ | `/api/admin/cases/{caseType}/{caseId}/activity` | `GET` | Xem lịch sử ghi chú, thao tác đổi trạng thái của các admin trước đó. |

- **`caseType` hỗ trợ**: `"MENTOR_VERIFICATION"`, `"BOOKING"`, `"FORUM_REPORT"`, `"PAYOUT_REQUEST"`.

---

## 4. Quản Lý Người Dùng & Điều Phối Tài Khoản (User Moderation)

### 4.1 Danh Sách Người Dùng Khả Dụng (`GET /api/admin/users`)
- **Endpoint**: `GET /api/admin/users`
- **Query Params**: `keyword` (tìm tên/email/mã SV), `role` (`"MENTEE"` | `"MENTOR"`), `status` (`"ACTIVE"` | `"BANNED"`), `page`, `size`.
- **Response**: `ApiResponse<PageResponse<AdminUserListItemResponse>>`

> [!NOTE]
> Danh sách này chỉ hiển thị người dùng thông thường (`MENTEE`, `MENTOR`). Các tài khoản `ADMIN` và `SYSTEM_ADMIN` bị loại trừ khỏi danh sách này theo thiết kế an toàn.

---

### 4.2 Snapshot Toàn Diện Của Một Người Dùng (`GET /api/admin/users/{userId}/summary`)
- **Endpoint**: `GET /api/admin/users/{userId}/summary`
- **Mục đích**: Entrypoint duy nhất giúp Admin xem trọn vẹn thông tin User (Tài khoản Google, Hồ sơ sinh viên, Hồ sơ Mentor, Số buổi học đã tham gia, Tổng số báo cáo vi phạm) mà không cần gọi nhiều API rời rạc.

---

### 4.3 Khóa (Ban) & Mở Khóa (Unban) Tài Khoản

| Thao tác | Endpoint | Request Body |
|---|---|---|
| Khóa tài khoản (Ban) | `POST /api/admin/users/{userId}/ban` | `{ "reason": string }` (Bắt buộc, 1 - 1000 ký tự) |
| Mở khóa tài khoản (Unban) | `POST /api/admin/users/{userId}/unban` | `{ "reason": string }` (Bắt buộc, 1 - 1000 ký tự) |

---

## 5. Can Thiệp Vận Hành Booking & Xử Lý Khiếu Nại (Booking Dispute Operations)

### 5.1 Danh Sách & Chi Tiết Booking Hệ Thống
- **Danh sách toàn hệ thống**: `GET /api/admin/bookings?status=...&mentorUserId=...&menteeUserId=...&page=0&size=20`
- **Chi tiết booking**: `GET /api/admin/bookings/{bookingId}` (Trả về đầy đủ trạng thái thanh toán, participant, meeting link và lịch sử sự cố).
- **Queue ưu tiên dispute**: `GET /api/admin/dashboard/queue-items?queueKey=booking_under_review`. Queue ưu tiên case quá SLA, case sắp đến SLA, case đã đủ phản hồi hai phía, rồi mới tới case mới tạo. Với booking dispute, response trả `issueType`, các deadline, số reminder và mốc fallback để admin quyết định đúng thời hạn.

> Admin có 48 giờ từ khi case vào queue để resolve. Nếu quá hạn, hệ thống nhắc mỗi 24 giờ tối đa ba lần. Sau reminder thứ ba thêm 24 giờ mà không có quyết định, hệ thống tự release tiền cho mentor theo policy đã công bố và lưu audit event.

---

### 5.2 Phán Quyết & Xử Lý Khiếu Nại Buổi Học (`POST /api/admin/bookings/{bookingId}/resolve-issue`)
Khi booking rơi vào trạng thái có sự cố (`UNDER_REVIEW`), Admin tiến hành điều tra và đưa ra quyết định xử lý:

- **Endpoint**: `POST /api/admin/bookings/{bookingId}/resolve-issue`
- **Request Body (`AdminResolveBookingIssueRequest`)**:

```typescript
interface AdminResolveBookingIssueRequest {
  action: 
    | "FORCE_COMPLETE"        // Xác nhận buổi học đã diễn ra thành công (giải ngân cho mentor)
    | "FORCE_CANCEL_REFUND"    // Hủy buổi học và hoàn 100% tiền lại cho mentee (do mentor vắng mặt/lỗi)
    | "DISMISS_ISSUE";         // Bác bỏ khiếu nại không hợp lệ
  resolutionNote: string;      // Biên bản giải quyết của Admin (Bắt buộc, 1 - 2000 ký tự)
}
```

---

### 5.3 Đổi lịch

Tính năng đổi lịch chưa phát hành. Không có API đổi lịch cho mentee, mentor hoặc admin.

---

## 6. Duyệt Yêu Cầu Rút Tiền Của Mentor (Payout Moderation)

| Thao tác | Endpoint | Method | Request Body |
|---|---|---|---|
| Danh sách yêu cầu rút tiền | `/api/admin/payout-requests` | `GET` | Lọc theo `status`, `page`, `size` |
| Chi tiết yêu cầu rút tiền | `/api/admin/payout-requests/{payoutRequestId}` | `GET` | Snapshot thông tin ngân hàng & số dư |
| Phê duyệt yêu cầu rút tiền | `/api/admin/payout-requests/{payoutRequestId}/approve` | `POST` | `{ "note"?: string }` |
| Từ chối yêu cầu rút tiền | `/api/admin/payout-requests/{payoutRequestId}/reject` | `POST` | `{ "note": string }` (Hoàn lại Scoin về ví) |
| Đánh dấu đã chi trả thành công | `/api/admin/payout-requests/{payoutRequestId}/mark-paid` | `POST` | `{ "note"?: string }` (Sau khi đã chuyển khoản VietQR) |

---

## 7. Quản Lý Khuyến Mãi, Mã Giảm Giá & Chiến Dịch (Coupons & Campaigns)

### 7.1 Quản Lý Mã Giảm Giá (Coupons)
- **Danh sách Coupon**: `GET /api/admin/coupons` (Hỗ trợ lọc theo `status`, `discountType`, `keyword`).
- **Tạo Coupon mới**: `POST /api/admin/coupons`
- **Cập nhật Coupon**: `PUT /api/admin/coupons/{couponId}`
- **Đổi trạng thái**: `PATCH /api/admin/coupons/{couponId}/status` (Body: `{ "status": "ACTIVE" | "INACTIVE" | "SUSPENDED" }`).
- **Lịch sử đổi mã (Redemptions)**: `GET /api/admin/coupons/{couponId}/redemptions` (Xem ai đã dùng mã này).

```typescript
interface AdminCouponCreateRequest {
  code: string;                          // Mã code (ví dụ: "FPTU_BACK2SCHOOL")
  title: string;                         // Tiêu đề chương trình
  discountType: "PERCENTAGE" | "FIXED_AMOUNT";
  discountValue: number;                 // Tỷ lệ % (1-100) hoặc số điểm Scoin cố định
  maxDiscountScoin?: number;             // Giới hạn giảm tối đa
  minOrderValueScoin?: number;           // Giá trị đơn hàng tối thiểu
  usageLimitTotal?: number;              // Tổng lượt sử dụng tối đa của toàn hệ thống
  usageLimitPerUser?: number;            // Lượt sử dụng tối đa cho mỗi sinh viên
  validFrom: string;                     // ISO Timestamp
  validTo: string;                       // ISO Timestamp
}
```

---

### 7.2 Quản Lý Chiến Dịch Khuyến Mãi (Campaigns & Benefits)
- **Danh sách Campaign**: `GET /api/admin/campaigns`
- **Tạo Campaign mới**: `POST /api/admin/campaigns` (Trạng thái ban đầu: `DRAFT` hoặc `SCHEDULED`).
- **Cập nhật Campaign**: `PUT /api/admin/campaigns/{campaignId}`
- **Chuyển trạng thái**: `PATCH /api/admin/campaigns/{campaignId}/status` (`"ACTIVE"`, `"PAUSED"`, `"ENDED"`, `"ARCHIVED"`).
- **Thống kê hiệu quả**: `GET /api/admin/campaigns/{campaignId}/analytics` (Đo lường số booking phát sinh từ chiến dịch).

---

## 8. Kiểm Duyệt Nội Dung Diễn Đàn & Bộ Lọc Từ Cấm (Content Moderation)

### 8.1 Xử Lý Báo Cáo Vi Phạm (Forum Reports)
- **Hàng đợi báo cáo vi phạm**: `GET /api/admin/forum/reports?status=PENDING&page=0&size=20`
- **Xem chi tiết báo cáo**: `GET /api/admin/forum/reports/{reportId}`
- **Xử lý báo cáo**: `POST /api/admin/forum/reports/{reportId}/resolve`

```typescript
interface ForumReportResolveRequest {
  resolutionAction: 
    | "HIDE_CONTENT"          // Ẩn bài viết hoặc bình luận vi phạm khỏi cộng đồng
    | "DISMISS_REPORT"        // Bác bỏ báo cáo (nội dung hợp lệ)
    | "WARN_USER";            // Cảnh cáo người dùng
  adminNote?: string;
}
```

---

### 8.2 Quản Lý Danh Sách Bài Viết & Bình Luận Bị Ẩn (Moderation Posts & Comments)
- **Danh sách bài viết (Cursor Page)**: `GET /api/admin/forum/posts?status=HIDDEN&cursor=...`
- **Khôi phục bài viết**: `POST /api/admin/forum/posts/{postId}/restore`
- **Danh sách bình luận (Cursor Page)**: `GET /api/admin/forum/comments?status=HIDDEN&cursor=...`
- **Khôi phục bình luận**: `POST /api/admin/forum/comments/{commentId}/restore`

---

### 8.3 Quản Lý Bộ Lọc Cụm Từ Cấm (Prohibited Phrases)
Hệ thống tự động chặn người dùng đăng bài chứa các từ ngữ phản cảm hoặc vi phạm tiêu chuẩn cộng đồng.

- **Danh sách từ cấm**: `GET /api/admin/forum/prohibited-phrases?isActive=true`
- **Thêm từ cấm mới**: `POST /api/admin/forum/prohibited-phrases` (`{ "phrase": string, "category": string, "severity": "BLOCK" | "FLAG" }`)
- **Cập nhật / Bật tắt**: `PUT /api/admin/forum/prohibited-phrases/{ruleId}` và `PATCH .../active`

---

### 8.4 Kiểm Duyệt Báo Cáo Phòng Chat (Chat Reports)
- **Danh sách báo cáo chat**: `GET /api/admin/chat-reports?status=PENDING&page=0&size=20`
- **Xử lý báo cáo chat**: `PATCH /api/admin/chat-reports/{reportId}` (`{ "status": "RESOLVED_LOCKED" | "RESOLVED_NO_ACTION", "note": string }`)
- **Khóa cưỡng chế phòng chat**: `PATCH /api/admin/chat-reports/conversations/{conversationId}/lock` (`{ "locked": boolean, "reason": string }`)

---

## 9. Nhật Ký Kiểm Toán & Ghi Chú Vận Hành (Audit Logs & Notes)

### 9.1 Tra Cứu Nhật Ký Kiểm Toán Hệ Thống (`GET /api/admin/audit-logs`)
- **Endpoint**: `GET /api/admin/audit-logs`
- **Query Params**: `actorId`, `entityType`, `entityId`, `action`, `fromDate`, `toDate`, `page`, `size`.
- **Response**: Trả về dữ liệu chi tiết gồm `oldValue` và `newValue` dạng JSON raw để kiểm tra lịch sử can thiệp dữ liệu.

---

### 9.2 Ghi Chú Điều Phối Nội Bộ (`Admin Notes`)
Admin có thể ghi lại các ghi chú nội bộ (Append-Only) đính kèm vào User, Booking, Payout Request hoặc Forum Report để các Admin khác nắm bắt thông tin.

- **Lấy danh sách ghi chú**: `GET /api/admin/notes?targetType=BOOKING&targetId=<UUID>`
- **Tạo ghi chú mới**: `POST /api/admin/notes` (`{ "targetType": string, "targetId": string, "noteContent": string }`)

---

## 10. Code Mẫu Thực Chiến Next.js: Admin Queue Workbench Component

Component mẫu quản lý Hàng đợi xử lý công việc kèm cơ chế Nhận Case (Assign to me) và Phán quyết tức thời.

```tsx
'use client';

import React, { useState, useEffect } from 'react';
import { apiClient } from '@/lib/api-client';

interface QueueItem {
  caseId: string;
  caseType: string;
  title: string;
  submittedAt: string;
  assignedAdminEmail: string | null;
  slaRemainingMinutes: number;
}

export function AdminQueueWorkbench() {
  const [items, setItems] = useState<QueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'BOOKING_DISPUTE' | 'MENTOR_VERIFICATION' | 'PAYOUT_REQUEST'>('BOOKING_DISPUTE');

  const loadQueue = async () => {
    setLoading(true);
    try {
      const res: any = await apiClient.get('/api/admin/dashboard/queue-items', {
        params: { queueKey: activeTab, page: 0, size: 20 },
      });
      setItems(res.data?.data?.content || res.data?.content || []);
    } catch (err) {
      console.error('Lỗi tải queue:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadQueue();
  }, [activeTab]);

  // Nhận case cho chính mình
  const handleAssignToMe = async (caseId: string, caseType: string) => {
    try {
      await apiClient.post(`/api/admin/cases/${caseType}/${caseId}/assign`);
      alert('Đã nhận case thành công!');
      loadQueue();
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Không thể nhận case (có thể Admin khác vừa nhận)');
    }
  };

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <div className="flex justify-between items-center border-b pb-4">
        <h1 className="text-2xl font-bold text-gray-900">Admin Workbench — Hàng Đợi Xử Lý</h1>
        <button onClick={loadQueue} className="px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded-lg text-sm font-medium">
          🔄 Làm mới
        </button>
      </div>

      {/* Tabs chuyển đổi Queue */}
      <div className="flex gap-3">
        {(['BOOKING_DISPUTE', 'MENTOR_VERIFICATION', 'PAYOUT_REQUEST'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-5 py-2.5 rounded-xl font-semibold text-sm transition ${
              activeTab === tab ? 'bg-indigo-600 text-white shadow' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {tab === 'BOOKING_DISPUTE' && 'Khiếu nại Buổi học'}
            {tab === 'MENTOR_VERIFICATION' && 'Duyệt Mentor mới'}
            {tab === 'PAYOUT_REQUEST' && 'Yêu cầu Rút tiền'}
          </button>
        ))}
      </div>

      {/* Danh sách Case */}
      {loading ? (
        <div className="text-center py-12 text-gray-500">Đang tải danh sách hàng đợi...</div>
      ) : items.length === 0 ? (
        <div className="text-center py-12 text-green-600 font-medium">🎉 Hàng đợi hiện tại đã được xử lý hết!</div>
      ) : (
        <div className="bg-white border rounded-2xl shadow-sm overflow-hidden">
          <table className="w-full text-left text-sm text-gray-600">
            <thead className="bg-gray-50 border-b text-gray-900 font-semibold">
              <tr>
                <th className="p-4">Tiêu đề / Đối tượng</th>
                <th className="p-4">Thời gian gửi</th>
                <th className="p-4">Người đang xử lý</th>
                <th className="p-4">Hạn SLA</th>
                <th className="p-4 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {items.map((item) => (
                <tr key={item.caseId} className="hover:bg-gray-50/50">
                  <td className="p-4 font-medium text-gray-900">{item.title}</td>
                  <td className="p-4">{new Date(item.submittedAt).toLocaleString('vi-VN')}</td>
                  <td className="p-4">
                    {item.assignedAdminEmail ? (
                      <span className="bg-blue-50 text-blue-700 px-2.5 py-1 rounded-full text-xs font-semibold">
                        👤 {item.assignedAdminEmail}
                      </span>
                    ) : (
                      <span className="text-gray-400 italic">Chưa có ai nhận</span>
                    )}
                  </td>
                  <td className="p-4">
                    <span className={item.slaRemainingMinutes < 0 ? 'text-red-600 font-bold' : 'text-emerald-600'}>
                      {item.slaRemainingMinutes < 0 ? `Trễ ${Math.abs(item.slaRemainingMinutes)}p` : `Còn ${item.slaRemainingMinutes}p`}
                    </span>
                  </td>
                  <td className="p-4 text-right space-x-2">
                    {!item.assignedAdminEmail && (
                      <button
                        onClick={() => handleAssignToMe(item.caseId, item.caseType)}
                        className="px-3 py-1.5 bg-indigo-50 text-indigo-600 hover:bg-indigo-100 rounded-lg text-xs font-bold"
                      >
                        Nhận xử lý
                      </button>
                    )}
                    <a
                      href={`/admin/cases/${item.caseType.toLowerCase()}/${item.caseId}`}
                      className="px-3 py-1.5 bg-gray-900 text-white hover:bg-black rounded-lg text-xs font-bold inline-block"
                    >
                      Mở Case ➔
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
```
