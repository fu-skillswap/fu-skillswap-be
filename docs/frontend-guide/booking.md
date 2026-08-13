# Frontend Integration Guide — Booking & Scheduling Module

Tài liệu này hướng dẫn chi tiết cho các lập trình viên Frontend (FE) cách tích hợp với **Booking & Scheduling Module** (Quản lý Đặt lịch Mentoring 1-on-1, Khung giờ Rảnh Availability Slots, Lịch lặp Hàng tuần Templates và Đổi lịch Reschedule) của SkillSwap Backend.

---

## 1. Nguyên tắc Bảo mật & Quy tắc Phân quyền (Security & Access Control)

### 1.1 Phân quyền Truy cập (Role Access Rules)
- **Role `MENTEE` & `MENTOR`**: Đều có quyền tạo booking request (`POST /api/bookings`) và xem báo giá (`POST /api/bookings/quote`). *Lưu ý*: Mentor hoàn toàn có thể đặt lịch với một mentor khác trên hệ thống để nâng cao kỹ năng.
- **Role `MENTOR` (Đã được cấp quyền Mentor)**: Bắt buộc để nhận/từ chối booking (`/api/mentor/bookings/{id}/accept`, `/reject`), quản lý slot rảnh (`/api/me/availability-slots/*`) và thiết lập lịch lặp (`/api/me/availability-templates/*`).
- **Role `ADMIN` & `SYSTEM_ADMIN`**: **Bị chặn tuyệt đối** khỏi các API đặt lịch trực tiếp (`403 Forbidden`).

### 1.2 Đảm bảo Idempotency (Chống Đặt Trùng / Gửi Lặp)
- API Tạo Booking (`POST /api/bookings`) và Tắt Slot (`POST /api/me/availability-slots/{id}/deactivate`) được bảo vệ bởi annotation `@Idempotent`.
- FE nên đính kèm header `X-Idempotency-Key: <unique-uuid>` nếu muốn đảm bảo không bao giờ bị trừ điểm/tạo trùng request ngay cả khi mạng chậm.

### 1.3 Rate Limiting
- **Tạo Booking**: Giới hạn **12 requests / 10 phút** (`booking:create:<userId>`).
- **Hủy Booking**: Giới hạn **3 requests / 1 giờ** (`booking:cancel:<userId>`).
- Nếu vượt giới hạn, backend trả về HTTP `429 Too Many Requests` kèm `retryAfterSeconds`.

---

## 2. Vòng đời Trạng thái Booking (Booking Lifecycle Diagram)

```text
               [MENTEE CREATES REQUEST]
                          │
                          ▼
                      [PENDING] ────► [REJECTED] / [EXPIRED] (Hết hạn pending)
                          │
                          ├───────────► [CANCELLED] (Mentee hủy trước khi Mentor accept)
                          │
                   Mentor Accept
                          │
                          ▼
                     [ACCEPTED] ◄───► [RESCHEDULED] (Đề xuất & Đồng ý đổi lịch)
                          │
             ┌────────────┴────────────┐
             ▼                         ▼
        [COMPLETED]               [CANCELLED] (Hủy kèm chính sách phạt / hoàn tiền)
             │                         │
             ▼                         ▼
       [CONFIRMED]            [ISSUE_REPORTED] (Báo sự cố trong 4h)
  (Mentee gửi feedback)
```

---

## 3. Tạo Booking & Xem trước Báo giá (Booking Creation & Quote)

### 3.1 Xem trước Báo giá Booking (`POST /api/bookings/quote`)
FE gọi trước khi hiển thị Modal xác nhận đặt lịch để lấy thông tin chi tiết về điểm S-Coin thực tế, phí phụ thu nền tảng và deadline phản hồi.

- **Endpoint**: `POST /api/bookings/quote`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`BookingQuoteRequest`)**:
```typescript
interface BookingQuoteRequest {
  slotId: string;       // UUID của Availability Slot rảnh
  serviceId: string;    // UUID của Service được chọn
  startAt: string;      // ISO UTC Instant (ví dụ: "2026-06-30T12:00:00Z")
}
```

**Response Payload (`BookingQuoteResponse`)**:
```typescript
interface BookingQuoteResponse {
  slotId: string;
  serviceId: string;
  startAt: string;
  endAt: string;                        // Tự động tính = startAt + durationMinutes
  durationMinutes: number;             // Thời lượng dịch vụ (30, 45, 60, 90)
  basePriceScoin: number;              // Giá gốc của dịch vụ
  surchargeScoin: number;             // Phụ phí nền tảng
  totalPriceScoin: number;             // Tổng giá S-Coin mentee cần trả
  isFree: boolean;                     // true nếu là session miễn phí
  pendingExpireDurationHours: number;  // Số giờ mentor có để phản hồi (ví dụ: 24h)
}
```

### 3.2 Tạo Booking Request chính thức (`POST /api/bookings`)
- **Endpoint**: `POST /api/bookings`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`CreateBookingRequest`)**:

```typescript
interface CreateBookingRequest {
  slotId: string;                 // Bắt buộc. UUID parent availability slot
  serviceId: string;              // Bắt buộc. UUID service được gắn trong slot
  startAt: string;                // Bắt buộc. ISO UTC Whole-minute Instant (Lấy từ candidate segment)
  learningGoalTitle: string;      // Bắt buộc. Tiêu đề mục tiêu (Tối đa 200 ký tự)
  learningGoalDescription?: string; // Optional. Mô tả chi tiết vấn đề cần hỗ trợ (Tối đa 2000 ký tự)
}
```

---

## 4. Quản lý Danh sách & Chi tiết Booking (`My Booking APIs`)

### 4.1 Lấy Danh sách Booking của Tôi (`GET /api/me/bookings`)
- **Endpoint**: `GET /api/me/bookings`
- **Header**: `Authorization: Bearer <accessToken>`
- **Query Parameters (`BookingListRequest`)**:
  - `role`: `"MENTEE"` (Lấy các booking tôi là người học) hoặc `"MENTOR"` (Lấy các booking gửi tới tôi)
  - `status`: `"PENDING"`, `"ACCEPTED"`, `"COMPLETED"`, `"CANCELLED"`, `"REJECTED"`, `"EXPIRED"`
  - `fromDate`: YYYY-MM-DD
  - `toDate`: YYYY-MM-DD
  - `page`: Số trang (Default: 0)
  - `size`: Kích thước (Default: 10)

**Response Item Nổi bật (`BookingResponse`)**:
```typescript
interface BookingResponse {
  bookingId: string;                  // UUID của booking
  actualSessionId?: string | null;    // UUID của session thật khi đã ACCEPTED
  mentorUserId: string;
  mentorDisplayName: string;
  mentorAvatarUrl?: string | null;
  menteeUserId: string;
  menteeDisplayName: string;
  menteeAvatarUrl?: string | null;
  serviceTitle: string;
  serviceDurationSnapshot: number;
  servicePriceScoinSnapshot: number;
  servicePriceWithSurchargeScoin: number;
  bookingStatus: "REQUESTED" | "ACCEPTED" | "CANCELLED" | "COMPLETED" | "REJECTED" | "EXPIRED";
  paymentStatus: "PENDING" | "PAID" | "REFUNDED";
  meetingPlatform?: "GOOGLE_MEET" | "ZOOM" | "TEAMS" | "OFFLINE" | null;
  meetingLink?: string | null;        // URL Google Meet hoặc địa điểm offline
  calendarSyncStatus?: string | null; // "SYNCED", "FAILED", ...
  selectedStartTime: string;          // ISO Timestamp bắt đầu
  selectedEndTime: string;            // ISO Timestamp kết thúc
  pendingExpireAt?: string | null;    // Deadline mentor phải bấm Accept/Reject
  conversationId?: string | null;     // ID cuộc hội thoại chat tự động kết nối giữa Mentee & Mentor
  canCancel: boolean;                 // true nếu user hiện tại có quyền bấm Hủy
  canComplete: boolean;               // true nếu user hiện tại có quyền bấm Hoàn tất
  canReschedule: boolean;             // true nếu user hiện tại có quyền Đổi lịch
  canSubmitFeedback: boolean;         // true nếu user hiện tại có thể viết đánh giá
}
```

### 4.2 Lấy Chi tiết một Booking (`GET /api/me/bookings/{bookingId}`)
- **Endpoint**: `GET /api/me/bookings/{bookingId}`

---

## 5. Thao tác của Mentor đối với Booking (Mentor Action APIs)

Yêu cầu role `MENTOR`.

### 5.1 Mentor Chấp nhận Booking (`POST /api/mentor/bookings/{bookingId}/accept`)
Chấp nhận request đang PENDING.
- **Endpoint**: `POST /api/mentor/bookings/{bookingId}/accept`
- **Request Body**: `{ mentorResponseNote?: string }`
- **Tác động Backend**:
  1. Chuyển `bookingStatus = ACCEPTED`.
  2. Tự động từ chối các booking request PENDING khác bị trùng khung giờ.
  3. Tự động tạo link Google Meet (nếu đã kết nối Google Calendar).
  4. Mở kênh Chat Conversation giữa Mentee và Mentor.

### 5.2 Mentor Từ chối Booking (`POST /api/mentor/bookings/{bookingId}/reject`)
- **Endpoint**: `POST /api/mentor/bookings/{bookingId}/reject`
- **Request Body**:
```typescript
interface RejectBookingRequest {
  rejectReason: string; // Bắt buộc. Lý do từ chối gửi cho mentee
}
```

### 5.3 Mentor Hủy Booking đã Chấp nhận (`POST /api/mentor/bookings/{bookingId}/cancel`)
- **Endpoint**: `POST /api/mentor/bookings/{bookingId}/cancel`
- **Request Body**: `{ cancelReason: string }` (Bắt buộc nhập lý do).

### 5.4 Mentor Cập nhật Meeting Link / Địa điểm (`PATCH /api/mentor/bookings/{bookingId}/meeting-link`)
- **Endpoint**: `PATCH /api/mentor/bookings/{bookingId}/meeting-link`
- **Request Body**:
```typescript
interface SaveMeetingLinkRequest {
  meetingPlatform: "GOOGLE_MEET" | "ZOOM" | "TEAMS" | "OFFLINE";
  meetingLink?: string;  // Bắt buộc nếu là online platform
  location?: string;     // Bắt buộc nếu meetingPlatform = OFFLINE
}
```

---

## 6. Hủy, Đổi lịch & Hoàn tất phía Mentee & Participant

### 6.1 Mentee Hủy Booking (`POST /api/me/bookings/{bookingId}/cancel`)
- **Endpoint**: `POST /api/me/bookings/{bookingId}/cancel`
- **Request Body**: `{ cancelReason: string }`

### 6.2 Đề xuất & Phản hồi Đổi lịch (Reschedule Requests Flow)
Cho phép Mentee hoặc Mentor đề xuất chuyển lịch hẹn sang một khung giờ mới:
- **Tạo đề xuất đổi lịch (Mentee)**: `POST /api/me/bookings/{bookingId}/reschedule-requests`
- **Tạo đề xuất đổi lịch (Mentor)**: `POST /api/mentor/bookings/{bookingId}/reschedule-requests`
  - Body: `{ newSlotId: string, newStartAt: string, reason: string }`
- **Xem danh sách đề xuất**: `GET /api/me/bookings/{bookingId}/reschedule-requests`
- **Đồng ý đổi lịch**: `POST /api/me/bookings/reschedule-requests/{requestId}/accept`
- **Từ chối đổi lịch**: `POST /api/me/bookings/reschedule-requests/{requestId}/reject`

### 6.3 Hoàn tất & Báo cáo Sự cố (Completion & Issues)
- **Đánh dấu hoàn thành**: `POST /api/me/bookings/{bookingId}/complete`
- **Participant xác nhận hoàn tất**: `POST /api/me/bookings/{bookingId}/confirm`
- **Báo cáo sự cố (trong vòng 4h)**: `POST /api/me/bookings/{bookingId}/issue`
  - Body: `{ issueType: "NO_SHOW" | "TECHNICAL_ISSUE" | "UNSATISFIED" | "OTHER", description: string }`
- **Phản hồi sự cố (trong vòng 24h)**: `POST /api/me/bookings/{bookingId}/issue/respond`

---

## 7. Quản lý Khung giờ Rảnh & Lịch lặp Hàng tuần của Mentor

### 7.1 Tạo Trực tiếp Slot Rảnh (`POST /api/me/availability-slots`)
- **Endpoint**: `POST /api/me/availability-slots`
- **Header**: `Authorization: Bearer <accessToken>` (Role `MENTOR`)
- **Request Body (`CreateAvailabilitySlotRequest`)**:
```typescript
interface CreateAvailabilitySlotRequest {
  startTime: string;        // Bắt buộc. ISO LocalDateTime (ví dụ: "2026-07-01T09:00:00")
  endTime: string;          // Bắt buộc. ISO LocalDateTime (ví dụ: "2026-07-01T12:00:00")
  serviceIds: string[];     // Danh sách UUID các gói dịch vụ được phép đăng ký trong slot này
  notes?: string;           // Ghi chú lịch rảnh
}
```

### 7.2 Xem & Quản lý Slots Rảnh (`GET /api/me/availability-slots`)
- **Endpoint**: `GET /api/me/availability-slots?fromDate=2026-07-01&toDate=2026-07-31&isActive=true`

### 7.3 Quản lý Lịch lặp Hàng tuần (Weekly Availability Templates)
Giúp Mentor tự động tạo khung giờ rảnh lặp lại theo thứ trong tuần mà không cần tạo thủ công từng ngày:
- **Tạo Template**: `POST /api/me/availability-templates`
- **Lấy danh sách**: `GET /api/me/availability-templates`
- **Cập nhật Template**: `PUT /api/me/availability-templates/{templateId}`
- **Tạm dừng / Mở lại**: `POST /api/me/availability-templates/{templateId}/pause` | `/resume`
- **Ngoại lệ (Bỏ qua 1 ngày cụ thể)**: `PUT /api/me/availability-templates/{templateId}/exceptions/{occurrenceDate}`

---

## 8. Bảng Mã Lỗi Thường Gặp (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho FE |
|---|---|---|
| `400` | `VAL_3001` | Tiêu đề mục tiêu hoặc mô tả quá độ dài quy định. |
| `401` | `AUTH_1001` | Chưa xác thực người dùng. |
| `403` | `AUTH_1002` | Admin/System Admin không được thực hiện đặt lịch. |
| `404` | `SYS_0003` | Slot rảnh hoặc Service không tồn tại. |
| `409` | `SYS_0007` | **Xung đột lịch**: Slot đã bị người khác book, slot đã bị deactivate, hoặc mentee đã vượt quota booking PENDING. |
| `429` | `SYS_0010` | Rate limit tạo/hủy booking. Khóa nút và đếm ngược theo `retryAfterSeconds`. |

---

## 9. Ví dụ Code Tích hợp Next.js (Booking Action Bar Component)

```typescript
import React, { useState } from 'react';
import { apiClient } from '@/lib/api-client';

interface BookingActionsProps {
  bookingId: string;
  status: string;
  isMentor: boolean;
  canCancel: boolean;
  onRefresh: () => void;
}

export const BookingActionBar: React.FC<BookingActionsProps> = ({
  bookingId,
  status,
  isMentor,
  canCancel,
  onRefresh,
}) => {
  const [loading, setLoading] = useState(false);

  const handleAccept = async () => {
    setLoading(true);
    try {
      await apiClient.post(`/api/mentor/bookings/${bookingId}/accept`, {
        mentorResponseNote: 'Tôi đã sẵn sàng cho buổi hẹn này!',
      });
      onRefresh();
    } catch (err) {
      alert('Không thể chấp nhận booking');
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async () => {
    const reason = prompt('Nhập lý do từ chối:');
    if (!reason) return;

    setLoading(true);
    try {
      await apiClient.post(`/api/mentor/bookings/${bookingId}/reject`, {
        rejectReason: reason,
      });
      onRefresh();
    } catch (err) {
      alert('Không thể từ chối booking');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async () => {
    const reason = prompt('Nhập lý do hủy booking:');
    if (!reason) return;

    setLoading(true);
    try {
      const endpoint = isMentor
        ? `/api/mentor/bookings/${bookingId}/cancel`
        : `/api/me/bookings/${bookingId}/cancel`;
      await apiClient.post(endpoint, { cancelReason: reason });
      onRefresh();
    } catch (err) {
      alert('Lỗi khi thực hiện hủy booking');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="action-bar">
      {isMentor && status === 'REQUESTED' && (
        <>
          <button onClick={handleAccept} disabled={loading} className="btn-success">
            Chấp nhận Booking
          </button>
          <button onClick={handleReject} disabled={loading} className="btn-danger">
            Từ chối
          </button>
        </>
      )}

      {canCancel && (
        <button onClick={handleCancel} disabled={loading} className="btn-warning">
          Hủy Booking
        </button>
      )}
    </div>
  );
};
```
