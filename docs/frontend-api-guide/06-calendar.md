# Google Calendar Integration Service (`06-calendar.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Kết nối Google Calendar (Google Calendar Integration Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Google Calendar Integration Service** quản lý quá trình liên kết tài khoản Google Calendar của người dùng (cả Mentor và Mentee) với SkillSwap via OAuth 2.0 PKCE. Service bao gồm kiểm tra trạng thái kết nối (`/api/me/google-calendar/status`), trao đổi authorization code lấy refresh token liên kết (`/api/me/google-calendar/connect`), ngắt kết nối (`/api/me/google-calendar/disconnect`), và hỗ trợ tự động tạo link họp Google Meet cùng sự kiện lịch khi buổi học chuyển sang trạng thái `CONFIRMED`.

### Trách nhiệm chính của Service
- **Khởi tạo & Duy trì Liên kết OAuth 2.0 Calendar**: Trao đổi Authorization Code + PKCE Code Verifier lấy Refresh Token có scope `https://www.googleapis.com/auth/calendar` để lưu trữ an toàn mã hóa ở Backend.
- **Theo dõi Trạng thái Kết nối & Đồng bộ (`Status Tracking`)**: Cung cấp các cờ `connected`, `syncEnabled`, `needsReconnect`, email Google liên kết, danh sách scopes đã cấp, và lịch sử đồng bộ gần nhất (`lastSyncStatus`, `lastSyncAt`, `lastSyncErrorCode`, `lastSyncErrorMessage`).
- **Đồng bộ Sự kiện Bất đồng bộ (`Outbox-Driven Event Sync`)**: Tự động tạo sự kiện trên Google Calendar của cả Mentor và Mentee khi đơn đặt lịch được xác nhận (`CONFIRMED`).
- **Tự động Cấp phát Link Google Meet (`Auto-Provisioning Google Meet`)**: Tự động sinh link họp mặt trực tuyến Google Meet gắn vào thông tin chi tiết đơn đặt lịch.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Đồng bộ Lịch học Tự động Tránh Trùng Lịch**: Giúp sinh viên và mentor FPTU tự động thấy các buổi học SkillSwap xuất hiện trên ứng dụng Google Calendar cá nhân (điện thoại/máy tính).
2. **Loại bỏ Thao tác Tạo Link Meeting Thủ công**: Mentor không cần phải tự tạo link Google Meet hay Zoom ngoài hệ thống. Ngay khi đơn được thanh toán/xác nhận, Backend tự động tạo sự kiện và cấp link Google Meet chính thức.
3. **Cảnh báo Thông minh khi Mất Quyền Đột xuất (`needsReconnect`)**: Tự động phát hiện khi Refresh Token của Google bị thu hồi hoặc hết hạn, trả về cờ `needsReconnect = true` để Frontend hiển thị banner yêu cầu người dùng kết nối lại.
4. **Giới hạn Phạm vi Đồng bộ 1 Chiều (One-Way Outbox Sync Boundary)**: Hệ thống chỉ đồng bộ các đơn đặt lịch đã xác nhận (`CONFIRMED`) từ SkillSwap sang Google Calendar. Hệ thống **không** import sự kiện cá nhân từ Google Calendar về SkillSwap và **không** ảnh hưởng đến các khung giờ rảnh định kỳ (`Availability Templates`).

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                LUỒNG KẾT NỐI GOOGLE CALENDAR & ĐỒNG BỘ BUỔI HỌC                       |
+-------------------------------------------------------------------------------------------------------+

  Frontend (Settings Page)             Backend (SkillSwap API)                 Google OAuth & Calendar API
          |                                     |                                         |
   1. User bấm "Kết nối Google Calendar"        |                                         |
          |                                     |                                         |
   2. Tạo PKCE codeVerifier & challenge        |                                         |
          |                                     |                                         |
   3. Chuyển hướng sang Google Consent URL với scope https://www.googleapis.com/auth/calendar----->|
          |<------------------------------------------------------------------------------|
   4. User đồng ý cấp quyền; Google redirect về Callback mang theo Authorization Code    |
          |                                     |                                         |
   5. POST /api/me/google-calendar/connect ---->|-- Trao đổi code lấy Refresh Token ------>|
      { authorizationCode, redirectUri,         |-- Lưu mã hóa connection vào DB -------->|
        codeVerifier }                          |                                         |
          |<-- 200 OK (GoogleCalendarStatusResponse)                                      |
          |    { connected: true, syncEnabled: true }                                     |
          |                                     |                                         |
   6. [Background Event] Booking chuyển sang CONFIRMED                                    |
          |                                     |-- Outbox Worker sinh Google Event ----->|
          |                                     |-- Tự động sinh link Google Meet ------->|
   7. Hiển thị Link Meet trên Booking Detail <--|                                         |
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Cờ Trạng thái Kết nối & Đồng bộ
- `connected` (`Boolean`): `true` nếu người dùng đã hoàn tất liên kết tài khoản Google Calendar.
- `syncEnabled` (`Boolean`): `true` nếu kết nối đang hoạt động tốt và có đủ quyền ghi sự kiện (`https://www.googleapis.com/auth/calendar`).
- `needsReconnect` (`Boolean`): `true` khi Google báo lỗi Refresh Token hết hạn/bị thu hồi hoặc bị thiếu Scope. Frontend **bắt buộc** phải hiển thị Banner màu vàng/đỏ yêu cầu người dùng kết nối lại.
- `lastSyncStatus`: Trạng thái đồng bộ gần nhất (`SYNCED`, `FAILED`, `PENDING`).

### 4.2 Ranh giới Đồng bộ Đặt lịch (`Calendar Sync Boundary`)
- **Dữ liệu ĐƯỢC đồng bộ**: Chỉ các đơn đặt lịch có `bookingStatus == CONFIRMED` mới được tạo sự kiện trên Google Calendar.
- **Dữ liệu KHÔNG đồng bộ**: Khung giờ rảnh định kỳ (`Availability Templates`), Yêu cầu mới chưa duyệt (`REQUESTED`), Đơn chờ thanh toán (`WAITING_PAYMENT`), và các đơn bị hủy/từ chối.

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Header Bắt buộc | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/me/google-calendar/status` | Authenticated | Bearer | Lấy trạng thái kết nối Google Calendar | Khi mở trang Settings / Sau khi đăng nhập |
| `POST` | `/api/me/google-calendar/connect` | Authenticated | Bearer | Trao đổi OAuth code lấy kết nối Calendar | Trang Callback sau khi cấp quyền Google |
| `POST` | `/api/me/google-calendar/disconnect` | Authenticated | Bearer | Ngắt kết nối Google Calendar | Bấm nút "Ngắt kết nối" trong Settings |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `GET /api/me/google-calendar/status`
- **Mục đích**: Truy vấn trạng thái liên kết và đồng bộ Google Calendar hiện tại của người dùng.
- **Response Body (`GoogleCalendarStatusResponse`)**:
```json
{
  "timestamp": "2026-08-04T09:50:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "connected": true,
    "syncEnabled": true,
    "email": "mentor@fpt.edu.vn",
    "grantedScopes": [
      "https://www.googleapis.com/auth/calendar"
    ],
    "needsReconnect": false,
    "lastSyncStatus": "SYNCED",
    "lastSyncAt": "2026-08-04T09:45:00",
    "lastSyncErrorCode": null,
    "lastSyncErrorMessage": null
  }
}
```

---

### 6.2 `POST /api/me/google-calendar/connect`
- **Mục đích**: Gửi Authorization Code thu được từ luồng PKCE Consent của Google để Backend thiết lập liên kết.

#### Request Body (`GoogleCalendarConnectRequest`)
```json
{
  "authorizationCode": "4/0AQSTgQF...",
  "redirectUri": "https://skillswap.asia/settings/google-calendar/callback",
  "codeVerifier": "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
}
```

#### Response Body
```json
{
  "timestamp": "2026-08-04T09:51:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "connected": true,
    "syncEnabled": true,
    "email": "mentor@fpt.edu.vn",
    "grantedScopes": [
      "https://www.googleapis.com/auth/calendar"
    ],
    "needsReconnect": false,
    "lastSyncStatus": "PENDING",
    "lastSyncAt": null,
    "lastSyncErrorCode": null,
    "lastSyncErrorMessage": null
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Kết nối Google Calendar từ Màn hình Cài đặt (`Settings Flow`)

```
User (Settings Page)                  Frontend Component                     Backend API
        |                                     |                                   |
   1. Bấm "Kết nối Google Calendar" --------->|-- Tạo PKCE Code Verifier --------->|
   2. Trình duyệt chuyển hướng Consent Google |                                   |
   3. User đồng ý cấp quyền Calendar         |                                   |
   4. Google redirect về Callback kèm Code --->|                                   |
        |------------------------------------>|-- POST /me/google-calendar/connect
        |                                     |<-- Trả GoogleCalendarStatusResponse
   5. Cập nhật Badge "Đã kết nối" trên UI ---->|-- Invalidate cache `['google-calendar', 'status']`
```

---

## 8. State Machine (Ma trận Trạng thái Kết nối & Đồng bộ Calendar)

### 8.1 Vòng đời Kết nối Google Calendar (`GoogleCalendarConnectionState`)

```
             +-----------------------+
             |     DISCONNECTED      | (Chưa kết nối - connected = false)
             +-----------------------+
                         |
         POST /me/google-calendar/connect
                         |
                         v
             +-----------------------+
             |       CONNECTED       | (Đã kết nối thành công - connected = true)
             +-----------------------+
              /                     \
      Google Revoke Scope         Bấm Disconnect
            /                         \
           v                           v
+-----------------------+   +-----------------------+
|    NEEDS_RECONNECT    |   |     DISCONNECTED      |
+-----------------------+   +-----------------------+
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `BAD_REQUEST` | Thiếu `authorizationCode`, `redirectUri`, hoặc `codeVerifier`. | Báo lỗi phiên xác thực, yêu cầu kết nối lại từ đầu. |
| `401 UNAUTHENTICATED` | `UNAUTHENTICATED` | Chưa đăng nhập hoặc token hệ thống hết hạn. | Chuyển luồng Refresh Token. |
| `409 RESOURCE_CONFLICT` | `OAUTH_VERIFICATION_FAILED` | Code verifier không khớp hoặc authorization code của Google đã quá hạn. | Hiển thị Toast "Mã xác thực Google hết hạn", bắt đầu lại luồng connect. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Không Lưu Credentials ở Client**: Mọi Refresh Token và Access Token thu được từ Google Calendar API đều được mã hóa và lưu trữ an toàn trong DB Backend. Frontend tuyệt đối không lưu các token này.
2. **Tách biệt Độc lập Với Đăng nhập System**: Ngắt kết nối Google Calendar (`disconnect`) chỉ làm ngắt tính năng đồng bộ lịch, **không** làm ngắt phiên đăng nhập của người dùng vào hệ thống SkillSwap.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Kiểm tra cờ `needsReconnect`: Nếu là `true`, hiển thị Banner màu vàng ở đầu màn hình Settings và Booking Detail khuyến nghị người dùng bấm "Kết nối lại".
- Re-fetch thông tin `/api/auth/me` hoặc `/api/me/google-calendar/status` ngay sau khi gọi API `connect` hoặc `disconnect` thành công.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** tự xem `connected = true` từ thông tin auth cơ bản để mặc định tính năng đồng bộ luôn chạy; bắt buộc phải dựa vào `syncEnabled = true` và `needsReconnect = false`.
- **KHÔNG ĐƯỢC** hiển thị lỗi đăng nhập khi người dùng bấm ngắt kết nối Google Calendar.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Mất Quyền Truy Cập Google Calendar (`Token Revoked`)**:
   - Khi người dùng vào trang Google Account Settings và chủ động xóa quyền truy cập của SkillSwap, Backend sẽ phát hiện khi Outbox Worker đi đồng bộ và tự động đổi `needsReconnect = true`. Frontend sẽ nhận được cờ này trong lần fetch status tiếp theo.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Authentication Service**: Cung cấp thông tin cờ `googleCalendarConnected` trong `GET /api/auth/me`.
- **Booking Service**: Tạo sự kiện lịch và tạo link Google Meet khi đơn đặt lịch đạt trạng thái `CONFIRMED`.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Tab Cài đặt Kết nối Lịch (`/settings/integrations`)
- **React Components**: `CalendarIntegrationCard.tsx`, `GoogleConnectButton.tsx`, `ReconnectBanner.tsx`
- **APIs Triggered**:
  1. `GET /api/me/google-calendar/status` (Mount trang)
  2. `POST /api/me/google-calendar/disconnect` (Khi bấm Ngắt kết nối)
- **Expected Behavior**: Hiển thị nhãn *"Đã kết nối với email..."* kèm cờ xanh `syncEnabled`. Nếu `needsReconnect = true`, hiển thị cảnh báo đỏ và nút "Kết nối lại".

#### B. Màn hình Handling Callback OAuth (`/settings/google-calendar/callback`)
- **React Components**: `CalendarCallbackHandler.tsx`, `LoadingSpinner.tsx`
- **APIs Triggered**:
  1. `POST /api/me/google-calendar/connect` (Truyền code, redirectUri, codeVerifier)
- **Expected Behavior**: Hiển thị hiệu ứng loading. Khi 200 OK: Chuyển hướng quay lại màn hình `/settings/integrations` kèm Toast "Kết nối Google Calendar thành công!".

---

### 14.2 Frontend Calendar State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |      UNLINKED         | (Chưa kết nối Calendar)
                       +-----------------------+
                                   |
                      Bấm "Kết nối Google Calendar"
                                   |
                                   v
                       +-----------------------+
                       |    CONNECTING_OAUTH   | (Đang xử lý ở Callback Handler)
                       +-----------------------+
                                   |
                       POST /google-calendar/connect
                                   |
                     +-------------+-------------+
                     |                           |
                Connect 200                 Connect Error
                     |                           |
                     v                           v
         +-----------------------+   +-----------------------+
         |     SYNC_ACTIVE       |   |    RECONNECT_NEEDED   |
         +-----------------------+   +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Settings Page | Consent Redirect Callback | Click Disconnect | User Action |
| --- | --- | --- | --- | --- |
| `GET /api/me/google-calendar/status` | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `POST /api/me/google-calendar/connect` | ❌ KHÔNG | ✅ CÓ (Tự động tại Callback) | ❌ KHÔNG | ❌ KHÔNG |
| `POST /api/me/google-calendar/disconnect` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ✅ Bấm "Ngắt kết nối" |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Mất Scope / Cần Kết nối Lại (`needsReconnect = true`)
- **UI Component**: `ReconnectBanner.tsx` trên Màn hình Settings & Booking Detail.
- **Visual State**: Banner viền cam/đỏ.
- **Message**: *"Kết nối Google Calendar của bạn cần được cấp lại quyền để tiếp tục tự động tạo link họp. Vui lòng kết nối lại."*
- **Action**: Nút "Kết nối lại ngay".

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['google-calendar', 'status']` | 5 phút | 30 phút | `true` | `POST /connect` hoặc `POST /disconnect` thành công |
