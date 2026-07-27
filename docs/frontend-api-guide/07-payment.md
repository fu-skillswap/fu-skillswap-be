# Payment

## Mục tiêu
File này mô tả checkout, webhook semantics, wallet, payout và cách FE hiểu đúng ranh giới giữa:
- payment order
- booking payment state
- settlement state

## API inventory
### Checkout và payment order
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/me/payment-orders/checkout` | Authenticated | mentee | `PaymentCheckoutRequest` | `PaymentCheckoutResponse` | - | Tạo payment order + trả checkout link |
| GET | `/api/me/payment-orders/{bookingId}` | Authenticated | participant | path `bookingId` | `PaymentCheckoutResponse` | - | Poll trạng thái payment order |
| POST | `/api/payments/webhook/payos` | Public backend-only | - | `PaymentWebhookRequest` | `PaymentCheckoutResponse` | - | Webhook từ PayOS, FE không gọi |

### Wallet
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/credit-wallet` | Authenticated | mentee | - | `CreditWalletResponse` | - | Số dư Scoin của mentee |
| GET | `/api/me/mentor-wallet` | Authenticated | mentor | - | `MentorWalletResponse` | - | Settlement earnings của mentor |

### Payout
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/mentor/payout-requests` | Authenticated | mentor | `PayoutRequestCreateRequest` | `PayoutRequestResponse` | - | Tạo payout request |
| GET | `/api/mentor/payout-requests` | Authenticated | mentor | - | `List<PayoutRequestResponse>` | - | Lịch sử payout của mentor |
| GET | `/api/admin/payout-requests` | Authenticated | admin/system admin | `AdminPayoutRequestListRequest` | `PageResponse<PayoutRequestResponse>` | - | Queue payout cho admin |
| GET | `/api/admin/payout-requests/{payoutRequestId}` | Authenticated | admin/system admin | path `payoutRequestId` | `PayoutRequestResponse` | - | Chi tiết payout |
| POST | `/api/admin/payout-requests/{payoutRequestId}/approve` | Authenticated | admin/system admin | `AdminNoteRequest` | `PayoutRequestResponse` | - | Duyệt payout |
| POST | `/api/admin/payout-requests/{payoutRequestId}/reject` | Authenticated | admin/system admin | `AdminNoteRequest` | `PayoutRequestResponse` | - | Từ chối payout |
| POST | `/api/admin/payout-requests/{payoutRequestId}/mark-paid` | Authenticated | admin/system admin | `AdminNoteRequest` | `PayoutRequestResponse` | - | Đánh dấu đã chi trả |

## Call order chuẩn
### Checkout
1. FE chỉ gọi checkout khi booking đã vào trạng thái cần thanh toán.
2. FE gửi `bookingId` và `couponCode` nếu có.
3. Backend tính giá, áp coupon/credit và trả `checkoutUrl`.
4. FE redirect user sang provider checkout.

### Sau checkout
1. FE poll `/api/me/payment-orders/{bookingId}` nếu cần refresh trạng thái.
2. FE không tự kết luận payment thành công chỉ từ redirect callback.
3. Booking/payment state chốt theo webhook/provider sync từ backend.

### Wallet
1. FE dùng wallet screen để xem số dư hiện tại.
2. Mentee xem credit wallet.
3. Mentor xem settlement wallet.

### Payout
1. Mentor tạo payout request khi có balance đủ điều kiện.
2. Admin duyệt/reject/mark-paid trong admin panel.
3. FE mentor chỉ đọc lịch sử và trạng thái payout của mình.

## Ý nghĩa field quan trọng
### `PaymentCheckoutResponse`
- `paymentOrderId`
  - ID payment order nội bộ
- `orderCode`
  - mã đơn thanh toán
- `attemptNo`
  - số lần checkout/retry
- `basePriceScoin`
  - giá gốc
- `couponDiscountScoin`
  - giảm giá coupon
- `campaignCreditAppliedScoin`
  - credit từ campaign
- `userCreditAppliedScoin`
  - credit user dùng
- `remainingPayableScoin`
  - số Scoin còn phải trả
- `remainingPayableVnd`
  - số VND còn phải trả
- `status`
  - trạng thái payment order
- `paymentProvider`
  - provider hiện tại
- `checkoutUrl`
  - URL redirect ra provider
- `paymentLink`
  - link payment tương ứng
- `expiresAt`
  - thời điểm link hết hạn

### Payment lifecycle
- `PENDING`
  - chưa xong thanh toán
- `PAID`
  - provider đã xác nhận thanh toán
- `FAILED`
  - thanh toán thất bại
- `EXPIRED`
  - link/payment quá hạn
- `NOT_REQUIRED`
  - booking miễn phí hoặc không cần payment
- `REFUNDED`
  - đã hoàn tiền ở settlement

### Settlement
- `settlementStatus`
  - `HELD`, `RELEASED`, `REFUNDED`
- Payment success không đồng nghĩa settlement release.
- FE phải đọc settlement từ booking/payment summary, không suy luận từ payment provider.

## FE phải làm
- Chỉ redirect bằng `checkoutUrl`.
- Poll order detail nếu cần UI chờ trạng thái.
- Refresh booking detail sau khi payment xong.
- Dùng wallet/payout screen riêng cho ví và settlement.

## FE không được làm
- Không gọi webhook từ FE.
- Không coi `PAID` là đã release tiền.
- Không dùng checkout response để thay thế booking detail.
- Không retry checkout vô hạn nếu backend đã báo conflict/expires.
- Không trộn payout mentor với payment booking.

## Response JSON example
### Checkout
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 201,
  "code": "SUCCESS",
  "message": "Created",
  "data": {
    "paymentOrderId": "dddddddd-dddd-dddd-dddd-dddddddddddd",
    "orderCode": "SK-20260713-0001",
    "bookingId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "attemptNo": 1,
    "basePriceScoin": 100,
    "couponDiscountScoin": 0,
    "campaignCreditAppliedScoin": 0,
    "userCreditAppliedScoin": 0,
    "remainingPayableScoin": 100,
    "remainingPayableVnd": 0,
    "status": "PENDING",
    "paymentProvider": "PAYOS",
    "providerOrderCode": "PAYOS-123",
    "providerPaymentLinkId": "link_123",
    "providerStatus": "PENDING",
    "checkoutUrl": "https://pay.payos.vn/web/...",
    "paymentLink": "https://pay.payos.vn/web/...",
    "expiresAt": "2026-07-13T10:45:00"
  }
}
```

### Wallet
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "balanceScoin": 250,
    "recentTransactions": []
  }
}
```

## UI mapping
- Checkout modal/page:
  - hiển thị amount, coupon, credit, remaining payable
- Payment redirect page:
  - chỉ mở provider khi có `checkoutUrl`
- Wallet screen:
  - mentee xem credit balance
  - mentor xem settlement earnings
- Payout screen:
  - mentor theo dõi payout request

## API success/error behavior
- `checkout`
  - success: redirect provider
  - 400: booking không hợp lệ hoặc coupon sai
  - 409: booking đã được thanh toán / trạng thái không cho checkout
- `getByBookingId`
  - success: dùng để poll và refresh UI
  - 404: booking không có payment order
- `webhook`
  - backend-only; FE không gọi
- wallet/payout
  - success: render số dư / queue
  - 403: role không hợp lệ

## Ghi chú cho AI Agent và FE dev
- `PaymentOrderStatus.PAID` chỉ nghĩa là provider đã xác nhận, không phải settlement release.
- FE chỉ cần biết `checkoutUrl`, `status`, `expiresAt`, `remainingPayable...`.
- Payout là luồng khác với booking payment, không gộp chung screen.
