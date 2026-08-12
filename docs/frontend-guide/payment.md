# Frontend Integration Guide — Payment & Wallet Module

Tài liệu này hướng dẫn chi tiết cho các lập trình viên Frontend (FE) cách tích hợp với **Payment & Wallet Module** (Thanh toán đơn hàng qua PayOS, Ví S-Coin của Mentee, Ví Thu nhập Settlement của Mentor, và Quản lý Rút tiền Payouts) của SkillSwap Backend.

---

## 1. Kiến trúc Tổng quan & Quy tắc Bảo mật (Security & Payment Architecture)

### 1.1 Tích hợp Cổng Thanh toán PayOS (Hosted Checkout Link)
- Backend tích hợp cổng thanh toán trực tuyến **PayOS** (hỗ trợ chuyển khoản ngân hàng QR Code VietQR tự động).
- Khi mentee tạo checkout, backend tự động áp dụng mã giảm giá (Coupon), điểm thưởng (Credit), và gọi API PayOS để sinh link `checkoutUrl`.
- **Luồng điều hướng FE**: FE mở trang `checkoutUrl` cho người dùng chuyển khoản ➔ PayOS gửi Webhook chốt đơn ➔ Backend cập nhật trạng thái `PAID` và kích hoạt booking.

### 1.2 Chống Thao tác Lặp (Idempotency & Rate Limiting)
- API Checkout (`POST /api/me/payment-orders/checkout`) và Tạo Yêu cầu Rút tiền (`POST /api/mentor/payout-requests`) được bảo vệ bởi annotation `@Idempotent`.
- **Rate Limit**:
  - **Checkout**: Giới hạn **5 lần / 1 phút** (`payment:checkout:<userId>`).
  - **Payout**: Giới hạn **3 lần / 1 giờ** (`payment:payout-request:<userId>`).

---

## 2. Quy trình Thanh toán Đơn hàng (Payment Checkout Flow)

```text
FE Browser                      Backend API                     PayOS Gateway
    │                                │                                │
    ├── 1. POST /checkout-preview ──►│ (Tính thử giá, coupon, credit) │
    │◄── RemainingPayableVnd ────────┤                                │
    │                                │                                │
    ├── 2. POST /checkout ──────────►│ (Tạo order, gọi PayOS SDK)     │
    │◄── CheckoutUrl & PaymentId ────┤ ─── Create Payment Link ──────►│
    │                                │                                │
    ├── 3. Redirect user to CheckoutUrl ─────────────────────────────►│ (User quét QR VietQR)
    │                                │                                │
    │                                │◄── 4. Webhook (Signature verified)
    │                                │    Mark Order as PAID          │
    │                                │                                │
    ├── 5. Poll GET /payment-orders/{bookingId} ─────────────────────►│ (FE xác nhận thành công)
```

### 2.1 Xem trước Báo giá Ưu đãi (`GET /api/mentor-services/{serviceId}/pricing-preview`)
- **Endpoint**: `GET /api/mentor-services/{serviceId}/pricing-preview`
- **Header**: `Authorization: Bearer <accessToken>`
- Trả về báo giá cá nhân hóa dựa trên các chiến dịch khuyến mãi đang diễn ra.

### 2.2 Xem trước Checkout Booking (`POST /api/me/bookings/{bookingId}/checkout-preview`)
FE gọi API này để xem trước số tiền VND/S-Coin cần trả sau khi thử nhập mã Coupon hoặc điểm Credit tích lũy.

- **Endpoint**: `POST /api/me/bookings/{bookingId}/checkout-preview`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`PaymentCheckoutPreviewRequest`)**: `{ couponCode?: string }`

**Response Payload (`PaymentCheckoutPreviewResponse`)**:
```typescript
interface PaymentCheckoutPreviewResponse {
  bookingId: string;
  basePriceScoin: number;              // Giá gốc của booking theo điểm S-Coin
  couponDiscountScoin: number;         // Giảm giá từ mã Coupon
  campaignCreditAppliedScoin: number;  // Credit chiến dịch áp dụng
  userCreditAppliedScoin: number;      // Credit cá nhân sử dụng
  remainingPayableScoin: number;       // Số điểm S-Coin còn phải trả
  remainingPayableVnd: number;         // Quy đổi tiền mặt VND tương ứng cần chuyển khoản
  couponCodeApplied?: string | null;   // Mã coupon đã được chấp nhận
}
```

### 2.3 Tạo Payment Order & Lấy Link Thanh toán (`POST /api/me/payment-orders/checkout`)
- **Endpoint**: `POST /api/me/payment-orders/checkout`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`PaymentCheckoutRequest`)**:

```typescript
interface PaymentCheckoutRequest {
  bookingId: string;        // Bắt buộc. UUID của Booking
  couponCode?: string;      // Optional. Mã giảm giá nhập từ FE
}
```

**Response Payload (`PaymentCheckoutResponse`)**:
```typescript
interface PaymentCheckoutResponse {
  paymentOrderId: string;             // UUID của payment order
  orderCode: string;                  // Mã đơn hàng hệ thống (ví dụ: "ORD-2026-1002")
  bookingId: string;
  attemptNo: number;                  // Lần checkout thứ bao nhiêu
  basePriceScoin: number;
  couponDiscountScoin: number;
  remainingPayableScoin: number;
  remainingPayableVnd: number;        // Số tiền mặt VND mentee cần thanh toán
  status: "PENDING" | "PAID" | "EXPIRED" | "CANCELLED" | "FAILED";
  paymentProvider: "PAYOS";
  providerOrderCode?: string | null;  // Mã đơn hàng trên PayOS (chữ số)
  checkoutUrl?: string | null;        // URL trang thanh toán PayOS (FE mở link này)
  paymentLink?: string | null;        // Alias của checkoutUrl
  expiresAt: string;                  // Thời điểm hết hạn trang thanh toán
}
```

### 2.4 Truy vấn Trạng thái Payment Order (`GET /api/me/payment-orders/{bookingId}`)
FE dùng để poll kiểm tra xem đơn hàng đã được chốt trạng thái `PAID` hay chưa (ví dụ: 3 giây/lần trong màn chờ thanh toán).

- **Endpoint**: `GET /api/me/payment-orders/{bookingId}`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<PaymentCheckoutResponse>`

---

## 3. Hệ thống Ví S-Coin & Thu nhập Settlement (Wallets APIs)

### 3.1 Ví S-Coin của Mentee (`GET /api/me/credit-wallet`)
Cho phép Mentee xem số điểm S-Coin khả dụng và 15 giao dịch gần nhất.

- **Endpoint**: `GET /api/me/credit-wallet`
- **Header**: `Authorization: Bearer <accessToken>` (Role `MENTEE`)
- **Response Payload (`CreditWalletResponse`)**:

```typescript
interface CreditWalletResponse {
  availableScoin: number;              // Số dư S-Coin khả dụng
  recentTransactions: Array<{          // 15 giao dịch mới nhất
    transactionId: string;
    amountScoin: number;
    transactionType: "TOP_UP" | "BOOKING_PAYMENT" | "REFUND" | "BONUS";
    description: string;
    createdAt: string;
  }>;
}
```

### 3.2 Ví Settlement / Thu nhập của Mentor (`GET /api/me/mentor-wallet`)
Cho phép Mentor theo dõi thu nhập tích lũy sau khi hoàn thành các buổi mentoring.

- **Endpoint**: `GET /api/me/mentor-wallet`
- **Header**: `Authorization: Bearer <accessToken>` (Role `MENTOR`)
- **Response Payload (`MentorWalletResponse`)**:

```typescript
interface MentorWalletResponse {
  availableScoin: number;              // Số dư thu nhập khả dụng có thể rút
  recentTransactions: Array<{          // 15 giao dịch thu nhập gần nhất
    transactionId: string;
    amountScoin: number;
    transactionType: "SESSION_EARNING" | "PAYOUT_WITHDRAW" | "ADJUSTMENT";
    description: string;
    createdAt: string;
  }>;
}
```

---

## 4. Tài khoản Nhận tiền & Yêu cầu Rút tiền (Payouts Flow)

### 4.1 Tạo / Cập nhật Tài khoản Ngân hàng (`Mentor Payout Profiles`)
Dành cho Mentor lưu thông tin ngân hàng cá nhân để nhận tiền chi trả.

- **Tạo mới**: `POST /api/mentor/payout-profiles`
- **Cập nhật**: `PUT /api/mentor/payout-profiles/{payoutProfileId}`
- **Lấy danh sách**: `GET /api/mentor/payout-profiles`

**Request Body (`MentorPayoutProfileUpsertRequest`)**:
```typescript
interface MentorPayoutProfileUpsertRequest {
  accountHolderName: string;          // Bắt buộc. Tên chủ tài khoản (viết hoa không dấu)
  bankName: string;                   // Bắt buộc. Tên ngân hàng (ví dụ: "MBBank", "Vietcombank")
  accountNumber: string;              // Bắt buộc. Số tài khoản ngân hàng
  bankCode?: string;                  // Optional. Mã ngân hàng (ví dụ: "MB", "VCB")
  isDefault?: boolean;                // true nếu muốn đặt làm tài khoản mặc định
  isActive?: boolean;
}
```

### 4.2 Yêu cầu Rút tiền Payout (`Payout Requests`)
Mentor tạo yêu cầu rút số dư thu nhập trong `Mentor Wallet` về tài khoản ngân hàng.

- **Tạo yêu cầu rút tiền**: `POST /api/mentor/payout-requests`
  - Body (`PayoutRequestCreateRequest`):
    ```typescript
    interface PayoutRequestCreateRequest {
      amountScoin: number;            // Bắt buộc. Số S-Coin muốn rút (> 0)
      payoutProfileId?: string;       // Optional. UUID profile bank (Bỏ trống = lấy profile mặc định)
      note?: string;                  // Ghi chú cho Admin
    }
    ```
- **Lấy lịch sử rút tiền của tôi**: `GET /api/mentor/payout-requests`

**Response Payload (`PayoutRequestResponse`)**:
```typescript
interface PayoutRequestResponse {
  payoutRequestId: string;            // UUID yêu cầu rút tiền
  amountScoin: number;                // Số S-Coin yêu cầu rút
  equivalentVndAmount: number;        // Tiền mặt VND tương ứng
  status: "PENDING" | "APPROVED" | "REJECTED" | "PAID";
  accountHolderName: string;
  bankName: string;
  accountNumber: string;
  adminNote?: string | null;           // Ghi chú xử lý của Admin
  createdAt: string;
  processedAt?: string | null;
}
```

---

## 5. Bảng Mã Lỗi Thường Gặp (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho FE |
|---|---|---|
| `400` | `VAL_3001` | Sai thông tin ngân hàng hoặc số dư rút vượt quá giới hạn khả dụng. |
| `400` | `PAYMENT_5001` | Booking chưa sẵn sàng để checkout (ví dụ: booking không ở trạng thái PENDING/ACCEPTED). |
| `400` | `COUPON_4001` | Mã coupon không hợp lệ, hết hạn hoặc không áp dụng cho gói dịch vụ này. |
| `401` | `AUTH_1001` | Chưa xác thực người dùng. |
| `409` | `SYS_0007` | Đơn hàng checkout đã được thanh toán thành công trước đó. |
| `429` | `SYS_0010` | Rate limit thao tác checkout hoặc nộp yêu cầu rút tiền. Khóa nút đếm ngược. |

---

## 6. Ví dụ Code Tích hợp Next.js (PayOS Checkout Handler & Polling)

```typescript
import React, { useState, useEffect } from 'react';
import { apiClient } from '@/lib/api-client';

interface CheckoutProps {
  bookingId: string;
  onPaymentSuccess: () => void;
}

export const PayOSCheckoutButton: React.FC<CheckoutProps> = ({ bookingId, onPaymentSuccess }) => {
  const [loading, setLoading] = useState(false);
  const [polling, setPolling] = useState(false);

  const handleStartCheckout = async () => {
    setLoading(true);
    try {
      // 1. Gửi request tạo Payment Order với PayOS
      const res: any = await apiClient.post('/api/me/payment-orders/checkout', {
        bookingId,
      });

      const { checkoutUrl } = res.data;

      if (checkoutUrl) {
        // 2. Mở cửa sổ thanh toán PayOS hoặc chuyển hướng trang
        window.open(checkoutUrl, '_blank');
        setPolling(true);
      }
    } catch (err: any) {
      alert(err?.message || 'Không thể khởi tạo link thanh toán PayOS');
    } finally {
      setLoading(false);
    }
  };

  // 3. Poll kiểm tra trạng thái đơn hàng khi user đang mở trang thanh toán
  useEffect(() => {
    let timer: NodeJS.Timeout;

    if (polling) {
      timer = setInterval(async () => {
        try {
          const res: any = await apiClient.get(`/api/me/payment-orders/${bookingId}`);
          if (res.data?.status === 'PAID') {
            setPolling(false);
            onPaymentSuccess();
          }
        } catch (err) {
          console.error('Lỗi poll trạng thái thanh toán:', err);
        }
      }, 3000); // 3 giây poll 1 lần
    }

    return () => {
      if (timer) clearInterval(timer);
    };
  }, [polling, bookingId, onPaymentSuccess]);

  return (
    <div className="payment-container">
      <button onClick={handleStartCheckout} disabled={loading || polling} className="btn-payos">
        {loading ? 'Đang tạo link thanh toán...' : polling ? 'Đang chờ chuyển khoản...' : 'Thanh toán qua PayOS (VietQR)'}
      </button>

      {polling && <p className="polling-hint">Vui lòng hoàn tất chuyển khoản trên trang PayOS. Hệ thống sẽ tự động cập nhật...</p>}
    </div>
  );
};
```
