# Payment & Wallet Service (`07-payment.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Thanh toán và Quản lý Ví SCoin (Payment & Wallet Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Payment & Wallet Service** quản lý toàn bộ hạ tầng thanh toán, ví điện tử SCoin, xem trước đơn hàng (`Checkout Preview`), khởi tạo liên kết thanh toán PayOS (`Payment Checkout Order`), xử lý Webhook bất đồng bộ, quản lý ví tín dụng Mentee (`Credit Wallet`), ví doanh thu Mentor (`Mentor Wallet`), và quy trình rút tiền (`Mentor Payout`).

### Trách nhiệm chính của Service
- **Xem trước Thanh toán Read-only (`Checkout Preview`)**: Ước tính số tiền SCoin thực trả (`finalPayableScoin`), áp dụng mã giảm giá (`couponCode`), sử dụng số dư ví tín dụng, và hiển thị đếm ngược thời gian hết hạn (`paymentDeadlineAt`). Hoàn toàn read-only, không tạo liên kết hay khóa tiền.
- **Khởi tạo Đơn Thanh toán Thanh khoản (`Payment Checkout Order`)**: Tính toán số tiền chính xác trong DB Transaction, khấu trừ số dư ví SCoin, áp mã giảm giá, và sinh link thanh toán PayOS Hosted Payment Link (`checkoutUrl`). Bắt buộc Header `Idempotency-Key` để chống trùng đơn.
- **Xử lý Webhook & Tự động Đồng bộ (`PayOS Webhook & Sync Fallback`)**: Nhận Webhook từ PayOS, xác minh chữ ký HMAC-SHA256 bảo mật, chuyển đơn hàng sang `PAID` và cập nhật Booking sang `CONFIRMED`. Hỗ trợ API polling fallback tự đồng bộ với cổng PayOS nếu Webhook đến chậm.
- **Quản lý Hệ thống Ví Đôi (Dual Wallet System)**: Tách biệt ví tín dụng học tập của Mentee (`GET /api/me/credit-wallet`) và ví doanh thu của Mentor (`GET /api/me/mentor-wallet`). Mỗi ví chỉ trả về số dư và 15 giao dịch gần nhất để giao diện tải nhẹ.
- **Quy trình Rút tiền của Mentor (`Mentor Payout`)**: Quản lý tài khoản ngân hàng nhận tiền (`Mentor Payout Profile`) và tạo/theo dõi các yêu cầu rút tiền về tài khoản ngân hàng thực.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Phân định Rõ ràng Ranh giới Thanh toán & Tạm giữ (Payment vs Settlement)**:
   - **Payment Status**: Xác nhận người dùng đã trả đủ tiền (`PAID`).
   - **Settlement Status**: Tiền được Backend tạm giữ (`HELD`), chỉ giải ngân sang ví Mentor (`RELEASED`) sau khi buổi học hoàn tất thành công, hoặc hoàn lại ví Mentee (`REFUNDED`) khi bị hủy/tranh chấp.
2. **Chống Tấn công Giả mạo Giao dịch**: Mọi request webhook PayOS bắt buộc phải kiểm tra chữ ký mã hóa (HMAC Signature Verification). Mã JavaScript ở Client **không bao giờ** có quyền tự cập nhật trạng thái đơn hàng thành đã thanh toán.
3. **An toàn Tài chính bằng Idempotent Checkout**: Bắt buộc gửi Header `Idempotency-Key` cho API `POST /api/me/payment-orders/checkout`. Nếu mạng bị ngắt và client retry, Backend trả về đúng link PayOS đã sinh trước đó mà không trừ tiền ví 2 lần.
4. **Trải nghiệm Thanh toán Linh hoạt bằng Ví & Mã Giảm Giá**: Tự động kết hợp điểm thưởng Campaign, số dư ví SCoin của người dùng, và mã giảm giá `couponCode` để tính ra số tiền VND còn phải trả qua cổng PayOS (`remainingPayableVnd`).

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                    LUỒNG THANH TOÁN & GIẢI NGÂN VÍ SCOIN                              |
+-------------------------------------------------------------------------------------------------------+

  Mentee (Browser)                    Backend API                             PayOS Gateway / Webhook
          |                                     |                                         |
   1. Mở màn hình Checkout Booking              |                                         |
          |-- POST /me/bookings/{id}/checkout-preview (?couponCode=...) ----------------->|
          |<-- 200 OK (finalPayableScoin, deadline) --------------------------------------|
          |                                     |                                         |
   2. Bấm "Thanh toán Ngay"                    |                                         |
          |-- POST /me/payment-orders/checkout (Idempotency-Key) ------------------------->|
          |                                     |-- Khấu trừ SCoin ví Mentee ------------>|
          |                                     |-- Tạo Order với PayOS API ------------->|
          |<-- 201 Created (checkoutUrl) -------|                                         |
          |                                     |                                         |
   3. Trình duyệt Redirect sang checkoutUrl --->|---------------------------------------->|
          | (User quét mã QR VietQR trên PayOS) |                                         |
          |                                     |<-- 4. PayOS gửi Webhook POST ----------|
          |                                     |-- Xác minh Chữ ký & Đổi status PAID -->|
          |                                     |-- Cập nhật Booking -> CONFIRMED ------->|
          |                                     |-- Tạm giữ tiền (Settlement = HELD) ---->|
   5. Poll GET /me/payment-orders/{bookingId} ->|                                         |
          |<-- 200 OK (Status: PAID) -----------|                                         |
          |                                     |                                         |
   6. [Sau khi xong Session] Mentor Complete -> Mentee Confirm                           |
          |                                     |-- Chuyển tiền sang Ví Mentor (RELEASE) ->|
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Hệ thống Trạng thái Đơn Thanh toán (`PaymentOrderStatus`)
- `PENDING`: Đơn hàng vừa khởi tạo, đang tính toán các khoản giảm giá.
- `PARTIALLY_COVERED_BY_CREDIT`: Đã trừ một phần số dư ví SCoin, còn lại cần thanh toán qua PayOS.
- `AWAITING_PROVIDER_PAYMENT`: Đã sinh link PayOS, đang chờ người dùng quét mã QR/chuyển khoản.
- `PAID`: Thanh toán thành công (Xác nhận via Webhook hoặc Polling Sync).
- `FAILED` / `CANCELLED` / `EXPIRED`: Giao dịch thất bại, bị hủy, hoặc quá thời gian đếm ngược.

### 4.2 Hệ thống Ví Đôi & Quy trình Giải ngân (`Dual Wallet & Settlement`)
- **Mentee Credit Wallet**: Ví điểm SCoin của Mentee dùng để đặt lịch học. Có thể nạp thêm hoặc nhận từ các chương trình khuyến mãi.
- **Mentor Settlement Wallet**: Ví chứa doanh thu của Mentor từ các buổi học đã hoàn thành. Tiền trong ví này có thể nộp yêu cầu rút về ngân hàng thực (`Payout Request`).

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Header Bắt buộc | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/me/bookings/{bookingId}/checkout-preview` | Mentee Role | Bearer | Tính toán ước tính số tiền, coupon & hạn thanh toán (Read-only) | Màn hình Checkout trước khi bấm trả tiền |
| `POST` | `/api/me/payment-orders/checkout` | Mentee Role | `Idempotency-Key` | Khởi tạo đơn thanh toán thật và lấy link PayOS | Khi bấm nút "Thanh toán" |
| `GET` | `/api/me/payment-orders/{bookingId}` | Participant | Bearer | Polling truy vấn trạng thái thanh toán theo Booking | Màn hình Chờ Thanh toán / Polling |
| `POST` | `/api/payments/webhook/payos` | Public (Backend) | Signature | Endpoint nhận Webhook từ PayOS (Chỉ dành cho PayOS server) | PayOS bắn khi giao dịch hoàn tất |
| `GET` | `/api/me/credit-wallet` | Mentee Role | Bearer | Xem số dư ví SCoin và 15 giao dịch gần nhất | Trang Ví cá nhân của Mentee |
| `GET` | `/api/me/mentor-wallet` | Mentor Role | Bearer | Xem doanh thu khả dụng và 15 giao dịch gần nhất | Trang Quản lý Doanh thu Mentor |
| `POST` | `/api/mentor/payout-requests` | Mentor Role | Bearer | Tạo yêu cầu rút tiền về tài khoản ngân hàng | Khi Mentor muốn rút tiền từ ví |
| `GET` | `/api/mentor/payout-requests` | Mentor Role | Bearer | Xem lịch sử các yêu cầu rút tiền | Trang Quản lý Rút tiền Mentor |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `POST /api/me/payment-orders/checkout`

#### Purpose
Khởi tạo đơn thanh toán giao dịch thật. Backend tự trừ ví SCoin, áp mã coupon và sinh link PayOS `checkoutUrl`.

#### Request Headers
- `Idempotency-Key` (`String`, Bắt buộc): Chuỗi UUIDv4 ngẫu nhiên chống trùng giao dịch.

#### Request Body (`PaymentCheckoutRequest`)
```json
{
  "bookingId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "couponCode": "SUMMER2026",
  "useCreditWallet": true
}
```

#### Response Body (`PaymentCheckoutResponse`)
```json
{
  "timestamp": "2026-08-04T09:55:00Z",
  "status": 201,
  "code": "SUCCESS",
  "message": "Created",
  "data": {
    "paymentOrderId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
    "orderCode": "SK-20260804-0001",
    "bookingId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "attemptNo": 1,
    "basePriceScoin": 100,
    "couponDiscountScoin": 10,
    "userCreditAppliedScoin": 20,
    "remainingPayableScoin": 70,
    "remainingPayableVnd": 70000,
    "status": "AWAITING_PROVIDER_PAYMENT",
    "paymentProvider": "PAYOS",
    "checkoutUrl": "https://pay.payos.vn/web/123456789",
    "expiresAt": "2026-08-04T10:10:00Z"
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Thanh toán Redirect & Polling Cập nhật Giao diện

```
Mentee (Frontend)                  Backend API                            PayOS Hosted Gateway
        |                                   |                                        |
   1. Bấm "Thanh toán"                     |                                        |
        |-- POST /me/payment-orders/checkout --------------------------------------->|
        |<-- 201 Created (checkoutUrl) -----|                                        |
   2. Window Redirect sang checkoutUrl --------------------------------------------->|
        |                                   | (User dùng app Ngân hàng quét QR)      |
   3. User chuyển tiền xong, PayOS redirect về returnUrl                              |
   4. Frontend trở lại App SkillSwap       |                                        |
        |-- GET /me/payment-orders/{bookingId} (Polling mỗi 3 giây) ---------------->|
        |<-- 200 OK (Status: PAID) ---------|                                        |
   5. Chuyển UI sang "Thanh toán Thành công! Buổi học đã được xác nhận"               |
```

---

## 8. State Machine (Ma trận Trạng thái Payment Order, Wallet & Settlement)

### 8.1 Vòng đời Đơn Thanh toán (`PaymentOrderStatus`)

```
             +-----------------------+
             |        PENDING        | (Vừa khởi tạo checkout)
             +-----------------------+
                         |
           +-------------+-------------+
           |                           |
   Đủ SCoin Ví               Thiếu SCoin -> Sinh PayOS Link
           |                           |
           v                           v
+-----------------------+   +-------------------------------+
|         PAID          |   |   AWAITING_PROVIDER_PAYMENT   |
+-----------------------+   +-------------------------------+
                                 /            |            \
                         PayOS Webhook    Quá 15 phút   User Hủy
                             PAID          Hết hạn     Giao dịch
                               /              |            \
                              v               v             v
                     +-----------------+ +----------+ +-----------+
                     |      PAID       | | EXPIRED  | | CANCELLED |
                     +-----------------+ +----------+ +-----------+
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `INVALID_COUPON` | Mã giảm giá hết hạn, không tồn tại hoặc không áp dụng cho gói dịch vụ này. | Báo lỗi ô nhập Coupon, giữ nguyên giá gốc. |
| `401 UNAUTHENTICATED` | `UNAUTHENTICATED` | Chưa đăng nhập hoặc token hết hạn. | Chuyển luồng Refresh Token. |
| `409 RESOURCE_CONFLICT` | `PAYMENT_ALREADY_COMPLETED` | Đơn hàng đã được thanh toán xong trước đó. | Chuyển thẳng người dùng về màn hình Chi tiết Đơn hàng. |
| `409 RESOURCE_CONFLICT` | `PAYMENT_EXPIRED` | Thời gian đếm ngược thanh toán đơn hàng đã kết thúc. | Báo lỗi đơn hết hạn, yêu cầu tạo lượt đặt mới. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Xác minh Webhook An toàn tuyệt đối (HMAC-SHA256)**:
   - Endpoint `POST /api/payments/webhook/payos` là public nhưng được bảo vệ bằng kiểm tra chữ ký mã hóa PayOS.
   - Frontend **tuyệt đối không gọi** API webhook này.
2. **Phân quyền Xem Ví Theo Role (`PreAuthorize`)**:
   - `/api/me/credit-wallet` chỉ dành cho Mentee (`hasRole('MENTEE')`).
   - `/api/me/mentor-wallet` chỉ dành cho Mentor (`hasRole('MENTOR')`).

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Chỉ chuyển hướng trình duyệt (redirect) khi Backend trả về `checkoutUrl` hợp lệ.
- Thực hiện Polling API `GET /api/me/payment-orders/{bookingId}` khi người dùng quay lại từ trang PayOS để kiểm tra trạng thái chốt từ Backend.
- Luôn truyền `Idempotency-Key` (UUIDv4) khi gọi API `POST /api/me/payment-orders/checkout`.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** tự xem việc quay lại từ URL return của PayOS là thanh toán thành công; bắt buộc phải chờ Backend chốt trạng thái `PAID`.
- **KHÔNG ĐƯỢC** dùng API Webhook ở Client.
- **KHÔNG ĐƯỢC** nhầm lẫn giữa `PaymentStatus == PAID` và `SettlementStatus == RELEASED`.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Thanh toán Bằng 100% Số dư Ví SCoin (`Fully Covered by Credit`)**:
   - Nếu số dư ví SCoin đủ trả toàn bộ tiền đơn hàng, Backend xử lý trừ ví và chốt `PAID` ngay lập tức mà không sinh `checkoutUrl` PayOS.
2. **Webhook PayOS Đến Chậm hoặc Bị Thất Lạc (`Delayed Webhook`)**:
   - Khi người dùng bấm "Tôi đã chuyển khoản thành công", Frontend gọi Polling `/api/me/payment-orders/{bookingId}`. Backend sẽ chủ động gọi API PayOS để đồng bộ tức thì nếu chưa nhận được Webhook.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Booking Service**: Cung cấp mã đơn đặt lịch `bookingId`, nhận tín hiệu `PAID` để chuyển `bookingStatus = CONFIRMED`.
- **Session Settlement Service**: Tiếp nhận khoản tiền tạm giữ `HELD` và tiến hành giải ngân `RELEASED` cho Mentor khi kết thúc buổi học.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Màn hình Thanh toán Checkout (`BookingCheckoutPage.tsx`)
- **React Components**: `BookingCheckoutPage.tsx`, `CouponApplyBox.tsx`, `WalletCreditToggle.tsx`, `PayOSPaymentButton.tsx`
- **APIs Triggered**:
  1. `POST /api/me/bookings/{bookingId}/checkout-preview` (Khi gõ mã Coupon)
  2. `POST /api/me/payment-orders/checkout` (Khi bấm nút Thanh toán)
- **Expected Behavior**: Hiển thị bảng chi tiết các khoản trừ (Coupon, Ví SCoin) và số tiền VND còn phải trả. Khi checkout 201: Redirect ngay sang `checkoutUrl`.

#### B. Màn hình Chờ Xác nhận Thanh toán (`PaymentSuccessPendingPage.tsx`)
- **React Components**: `PaymentSuccessPendingPage.tsx`, `PaymentPollingSpinner.tsx`
- **APIs Triggered**:
  1. `GET /api/me/payment-orders/{bookingId}` (Polling mỗi 3 giây, tối đa 10 lần)
- **Expected Behavior**: Hiển thị spinner kiểm tra giao dịch. Khi API trả `status = PAID`: Hiển thị icon xanh thành công và nút "Vào màn hình Lịch học".

#### C. Trang Quản lý Ví SCoin (`/wallet`)
- **React Components**: `CreditWalletView.tsx`, `MentorEarningsView.tsx`, `TransactionHistoryList.tsx`
- **APIs Triggered**:
  1. `GET /api/me/credit-wallet` (Dành cho Mentee)
  2. `GET /api/me/mentor-wallet` (Dành cho Mentor)
- **Expected Behavior**: Render thẻ số dư khả dụng nổi bật và danh sách 15 giao dịch biến động gần nhất.

---

### 14.2 Frontend Payment State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |    PREVIEWING_COST    | (Xem trước giảm giá & ví SCoin)
                       +-----------------------+
                                   |
                       POST /payment-orders/checkout
                                   |
                     +-------------+-------------+
                     |                           |
              Trả checkoutUrl              Trả PAID (Trừ 100% Ví)
                     |                           |
                     v                           |
         +-----------------------+               |
         |  REDIRECTED_TO_PAYOS  |               |
         +-----------------------+               |
                     |                           |
             Quay lại App & Poll                 |
                     |                           |
                     v                           v
         +-----------------------------------------------+
         |                PAYMENT_SUCCESS                | (Booking -> CONFIRMED)
         +-----------------------------------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Checkout Page | Apply Coupon Code | Click Pay Button | Return to App | User Action |
| --- | --- | --- | --- | --- | --- |
| `POST .../checkout-preview` | ✅ CÓ (Read-only) | ✅ CÓ (Khi thay đổi code) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `POST /me/payment-orders/checkout` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (`Idempotency-Key`) | ❌ KHÔNG | ✅ Bấm "Thanh toán" |
| `GET /me/payment-orders/{id}` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ (Polling khi trở lại App) | ❌ KHÔNG |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Mã Giảm giá Không Hợp lệ (`HTTP 400`)
- **UI Component**: Ô nhập Coupon Code (`CouponApplyBox.tsx`).
- **Visual State**: Viền đỏ + Thông báo lỗi bên dưới ô input.
- **Message**: *"Mã giảm giá không hợp lệ hoặc đã hết lượt sử dụng."*

#### B. Lỗi Đơn hàng Đã Được Thanh toán Trước Đó (`HTTP 409`)
- **UI Component**: Modal Thông báo Đơn hàng.
- **Message**: *"Đơn đặt lịch này đã được thanh toán thành công."*
- **Action**: Nút "Xem Chi tiết Đơn hàng".

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['checkout-preview', bookingId]` | 0 ms | 5 phút | `false` | Thay đổi mã coupon hoặc bật/tắt dùng ví SCoin |
| `['payment-order', bookingId]` | 0 ms | 5 phút | `true` | Khi Polling trạng thái thanh toán |
| `['wallet', 'credit']` | 1 phút | 15 phút | `true` | Thanh toán đơn hàng thành công, nạp điểm mới |
| `['wallet', 'mentor']` | 1 phút | 15 phút | `true` | Xử lý nộp yêu cầu rút tiền Payout |
