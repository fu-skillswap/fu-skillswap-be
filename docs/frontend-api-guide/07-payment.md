# Payment

## Mục tiêu
File này mô tả checkout, webhook semantics, wallet, payout và cách FE hiểu đúng ranh giới giữa:
- payment order
- booking payment state
- settlement state

## API inventory
### Discovery pricing estimate
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/mentor-services/{serviceId}/pricing-preview` | Authenticated | any logged-in user | path `serviceId` | `ServicePricingPreviewResponse` | Personalized campaign estimate only; no coupon, wallet or reservation. |

### Checkout và payment order
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/me/payment-orders/checkout` | Authenticated | mentee | `PaymentCheckoutRequest` | `PaymentCheckoutResponse` | - | Tạo payment order + trả checkout link |
| POST | `/api/me/bookings/{bookingId}/checkout-preview` | Authenticated | booking mentee | optional `PaymentCheckoutPreviewRequest` | `PaymentCheckoutPreviewResponse` | - | Estimate read-only; không tạo PayOS link hay reserve coupon/credit/campaign |
| GET | `/api/me/payment-orders/{bookingId}` | Authenticated | participant | path `bookingId` | `PaymentCheckoutResponse` | - | Poll trạng thái payment order |
| POST | `/api/payments/webhook/payos` | Public backend-only | - | `PaymentWebhookRequest` | `PaymentCheckoutResponse` | - | Webhook từ PayOS, FE không gọi |

### Wallet
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/credit-wallet` | Authenticated | mentee | - | `CreditWalletResponse` | - | Số dư Scoin của mentee |
| GET | `/api/me/mentor-wallet` | Authenticated | mentor | - | `MentorWalletResponse` | - | Settlement earnings của mentor |

### Mentor payout profiles
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/mentor/payout-profiles` | Authenticated | mentor | `MentorPayoutProfileUpsertRequest` | `MentorPayoutProfileResponse` | Tạo tài khoản nhận payout. |
| PUT | `/api/mentor/payout-profiles/{payoutProfileId}` | Authenticated | mentor | `MentorPayoutProfileUpsertRequest` | `MentorPayoutProfileResponse` | Cập nhật tài khoản đã lưu. |
| GET | `/api/mentor/payout-profiles` | Authenticated | mentor | - | `List<MentorPayoutProfileResponse>` | Danh sách tài khoản của mentor. |

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
1. Khi booking ở `ACCEPTED_AWAITING_PAYMENT`, FE có thể gọi `POST /api/me/bookings/{bookingId}/checkout-preview` với `couponCode` để hiện estimate và `paymentDeadlineAt`.
2. Preview chỉ là thông tin: không tạo payment order, PayOS link, coupon redemption, credit reservation hoặc campaign reservation.
3. FE chỉ gọi checkout khi booking vẫn cần thanh toán.
4. FE gửi `bookingId` và `couponCode` nếu có.
5. Backend tính lại giá trong transaction, áp coupon/credit và trả `checkoutUrl`.
6. FE redirect user sang provider checkout.

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

### `PaymentCheckoutPreviewResponse`
- `basePriceScoin`, `menteeSurchargeScoin`, `priceBeforeDiscountScoin`
  - giá service và surcharge đã tính cho mentee trước discount
- `couponDiscountScoin`, `campaignCreditAppliedScoin`, `userCreditAppliedScoin`
  - estimate các khoản giảm/credit tại thời điểm preview
- `estimatedFinalPayableScoin`
  - tổng estimate sau giảm giá; checkout tính lại và là nguồn sự thật
- `paymentDeadlineAt`
  - deadline server-enforced của booking; FE chỉ dùng để hiển thị countdown
- `isEstimate`, `disclaimer`
  - luôn nhắc rằng preview không phải reservation hay final price

### Payment lifecycle
- `PENDING`
  - payment order vừa tạo, chưa chốt cách thanh toán còn lại
- `PARTIALLY_COVERED_BY_CREDIT`
  - credit/campaign đã cover một phần; backend đang chuẩn bị phần provider payable còn lại
- `AWAITING_PROVIDER_PAYMENT`
  - có payment link/provider amount và đang chờ provider xác nhận
- `PAID`
  - provider đã xác nhận thanh toán
- `FAILED`
  - thanh toán thất bại
- `CANCELLED`
  - payment order bị hủy trước khi hoàn tất
- `EXPIRED`
  - link/payment quá hạn

Service miễn phí không nhất thiết tạo `PaymentOrder`; FE đọc `BookingResponse` để biết booking có còn yêu cầu thanh toán hay không. Hoàn tiền là payment/settlement outcome trên booking, không phải `PaymentOrderStatus` trong checkout response.

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
- Không dùng checkout preview như giá/credit được giữ chỗ.
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
- `checkout-preview`
  - success: render estimate và deadline, nhưng vẫn phải tính lại ở checkout
  - 400/409: refetch booking; không retry preview như một mutation
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
