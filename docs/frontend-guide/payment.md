# Frontend Integration Guide — Module Thanh Toán & Ví (Payment & Wallet)

Tài liệu này hướng dẫn chi tiết cho các lập trình viên Frontend (FE) cách tích hợp với **Payment & Wallet Module** (Thanh toán đơn hàng qua cổng PayOS, Ví S-Coin của Mentee, Ví Thu nhập Settlement của Mentor, và Quản lý Yêu cầu Rút tiền Payouts) của SkillSwap Backend.

---

## 1. Kiến Trúc Tổng Quan & Quy Tắc Bảo Mật (Security & Architecture)

### 1.1 Tích Hợp Cổng Thanh Toán PayOS (Hosted Checkout Link)
- Backend tích hợp cổng thanh toán trực tuyến **PayOS** (hỗ trợ chuyển khoản ngân hàng tự động qua VietQR).
- Khi Mentee thực hiện thanh toán, backend tự động tính toán và áp dụng mã giảm giá (Coupon), điểm thưởng (Credit), sau đó gọi PayOS để sinh đường dẫn thanh toán `checkoutUrl`.
- **Luồng điều hướng Frontend**: Frontend mở trang `checkoutUrl` để người dùng quét mã VietQR ➔ PayOS gửi Webhook chốt đơn ➔ Backend cập nhật trạng thái đơn hàng sang `PAID` và kích hoạt buổi học/khóa học.

### 1.2 Chống Thao Tác Lặp & Giới Hạn Tốc Độ (Idempotency & Rate Limiting)
- API Checkout (`POST /api/me/payment-orders/checkout`) và Tạo Yêu cầu Rút tiền (`POST /api/mentor/payout-requests`) được bảo vệ bởi cơ chế `@Idempotent`.
- **Giới hạn tốc độ (Rate Limit)**:
  - **Checkout**: Tối đa **5 lần / 1 phút** (`payment:checkout:<userId>`).
  - **Rút tiền (Payout)**: Tối đa **3 lần / 1 giờ** (`payment:payout-request:<userId>`).

---

## 2. Quy Trình Thanh Toán Đơn Hàng (Payment Checkout Flow)

```text
FE Browser                      Backend API                     PayOS Gateway
    │                                │                                │
    ├── 1. POST /checkout-preview ──►│ (Tính thử giá, coupon, credit) │
    │◄── RemainingPayableVnd ────────┤                                │
    │                                │                                │
    ├── 2. POST /checkout ──────────►│ (Tạo order, gọi PayOS SDK)     │
    │◄── CheckoutUrl & PaymentId ────┤ ─── Sinh link thanh toán ─────►│
    │                                │                                │
    ├── 3. Chuyển hướng sang CheckoutUrl ────────────────────────────►│ (Mentee quét mã VietQR)
    │                                │                                │
    │                                │◄── 4. Webhook (Xác thực chữ ký)│
    │                                │    Cập nhật Order sang PAID    │
    │                                │                                │
    ├── 5. Poll GET /payment-orders/{bookingId} ─────────────────────►│ (FE xác nhận thành công)
```

---

### 2.1 Xem Trước Báo Giá Dịch Vụ (`GET /api/mentor-services/{serviceId}/pricing-preview`)
- **Endpoint**: `GET /api/mentor-services/{serviceId}/pricing-preview`
- **Header**: `Authorization: Bearer <accessToken>`
- **Mục đích**: Trả về báo giá cá nhân hóa dựa trên các chiến dịch khuyến mãi đang diễn ra của hệ thống.

---

### 2.2 Xem Trước Checkout Booking (`POST /api/me/bookings/{bookingId}/checkout-preview`)
Frontend gọi API này để hiển thị trước số tiền VND / S-Coin cần thanh toán khi người dùng nhập thử mã giảm giá (Coupon). Hiện tại nền tảng quy đổi `1 SCoin = 1 VND`; FE luôn ưu tiên các trường tiền backend trả về thay vì tự quy đổi.

- **Endpoint**: `POST /api/me/bookings/{bookingId}/checkout-preview`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`PaymentCheckoutPreviewRequest`)**: `{ couponCode?: string }`

```typescript
interface PaymentCheckoutPreviewResponse {
  bookingId: string;
  basePriceScoin: number;              // Giá gốc của booking theo điểm S-Coin
  couponDiscountScoin: number;         // Số S-Coin được giảm từ mã Coupon
  campaignCreditAppliedScoin: number;  // Điểm credit từ chiến dịch áp dụng
  userCreditAppliedScoin: number;      // Điểm credit cá nhân sử dụng
  remainingPayableScoin: number;       // Số điểm S-Coin còn phải trả
  remainingPayableVnd: number;         // Số tiền mặt VND tương ứng cần chuyển khoản
  couponCodeApplied?: string | null;   // Mã coupon đã được chấp nhận
}
```

---

### 2.3 Tạo Đơn Hàng Thanh Toán & Lấy Link PayOS (`POST /api/me/payment-orders/checkout`)
- **Endpoint**: `POST /api/me/payment-orders/checkout`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`PaymentCheckoutRequest`)**:

```typescript
interface PaymentCheckoutRequest {
  bookingId: string;        // Bắt buộc. UUID của Booking
  couponCode?: string;      // Tùy chọn. Mã giảm giá nhập từ FE
}
```

```typescript
interface PaymentCheckoutResponse {
  paymentOrderId: string;             // UUID của đơn hàng thanh toán
  orderCode: string;                  // Mã đơn hàng hệ thống (ví dụ: "ORD-2026-1002")
  bookingId: string;
  attemptNo: number;                  // Lần checkout thứ bao nhiêu
  basePriceScoin: number;
  couponDiscountScoin: number;
  remainingPayableScoin: number;
  remainingPayableVnd: number;        // Số tiền VND mentee cần chuyển khoản qua VietQR
  status: "PENDING" | "PAID" | "EXPIRED" | "CANCELLED" | "FAILED";
  paymentProvider: "PAYOS";
  providerOrderCode?: string | null;  // Mã đơn hàng trên PayOS
  checkoutUrl?: string | null;        // URL trang thanh toán PayOS (FE mở link này)
  paymentLink?: string | null;        // Alias của checkoutUrl
  expiresAt: string;                  // Thời điểm hết hạn của phiên thanh toán
}
```

---

### 2.4 Truy Vấn Trạng Thái Thanh Toán (`GET /api/me/payment-orders/{bookingId}`)
Frontend sử dụng API này để thăm dò (Polling) kiểm tra xem đơn hàng đã được cập nhật trạng thái `PAID` hay chưa (khuyến nghị poll mỗi 3 giây/lần trong màn hình chờ chuyển khoản).

- **Endpoint**: `GET /api/me/payment-orders/{bookingId}`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<PaymentCheckoutResponse>`

---

## 3. Hệ Thống Ví S-Coin & Thu Nhập Settlement (Wallets APIs)

### 3.1 Ví Điểm S-Coin Của Mentee (`GET /api/me/credit-wallet`)
Cho phép Mentee theo dõi số dư S-Coin khả dụng và lịch sử 15 giao dịch gần nhất.

- **Endpoint**: `GET /api/me/credit-wallet`
- **Header**: `Authorization: Bearer <accessToken>` (Role `MENTEE`)
- **Response**: `ApiResponse<CreditWalletResponse>`

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

---

### 3.2 Ví Thu Nhập Của Mentor (`GET /api/me/mentor-wallet`)
Cho phép Mentor theo dõi tổng thu nhập tích lũy sau khi hoàn thành các buổi mentoring.

- **Endpoint**: `GET /api/me/mentor-wallet`
- **Header**: `Authorization: Bearer <accessToken>` (Role `MENTOR`)
- **Response**: `ApiResponse<MentorWalletResponse>`

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

## 4. Tài Khoản Ngân Hàng & Yêu Cầu Rút Tiền (Payouts Flow)

### 4.1 Quản Lý Tài Khoản Ngân Hàng Mentor (Payout Profiles)
Dành cho Mentor lưu thông tin tài khoản ngân hàng thụ hưởng để nhận tiền rút về.

- **Tạo mới**: `POST /api/mentor/payout-profiles`
- **Cập nhật**: `PUT /api/mentor/payout-profiles/{payoutProfileId}`
- **Lấy danh sách**: `GET /api/mentor/payout-profiles`

```typescript
interface MentorPayoutProfileUpsertRequest {
  accountHolderName: string;          // Bắt buộc. Tên chủ tài khoản (VIẾT HOA KHÔNG DẤU)
  bankName: string;                   // Bắt buộc. Tên ngân hàng (ví dụ: "MBBank", "Vietcombank")
  accountNumber: string;              // Bắt buộc. Số tài khoản ngân hàng
  bankCode?: string;                  // Tùy chọn. Mã ngân hàng (ví dụ: "MB", "VCB")
  isDefault?: boolean;                // true nếu muốn đặt làm tài khoản mặc định
  isActive?: boolean;
}
```

---

### 4.2 Tạo Yêu Cầu Rút Tiền (Payout Requests)
Mentor gửi yêu cầu rút số dư thu nhập từ `Mentor Wallet` về tài khoản ngân hàng đã cấu hình.

- **Tạo yêu cầu rút tiền**: `POST /api/mentor/payout-requests`
```typescript
interface PayoutRequestCreateRequest {
  amountScoin: number;            // Bắt buộc. Số S-Coin muốn rút (> 0)
  payoutProfileId?: string;       // Tùy chọn. UUID tài khoản ngân hàng (bỏ trống = lấy mặc định)
  note?: string;                  // Ghi chú cho Admin duyệt
}
```

- **Lấy lịch sử rút tiền**: `GET /api/mentor/payout-requests`
- **Response**: `ApiResponse<PayoutRequestResponse[]>`

```typescript
interface PayoutRequestResponse {
  payoutRequestId: string;            // UUID yêu cầu rút tiền
  amountScoin: number;                // Số S-Coin yêu cầu rút
  equivalentVndAmount: number;        // Tiền mặt VND tương ứng
  status: "PENDING" | "APPROVED" | "REJECTED" | "PAID";
  accountHolderName: string;
  bankName: string;
  accountNumber: string;
  adminNote?: string | null;          // Ghi chú phản hồi từ Quản trị viên
  createdAt: string;
  processedAt?: string | null;
}
```

---

## 5. Bảng Mã Lỗi Thường Gặp (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho Frontend |
|---|---|---|
| `400` | `VAL_3001` | Thông tin tài khoản ngân hàng không hợp lệ hoặc số dư rút vượt quá hạn mức khả dụng. |
| `502` | `PAY_5001` | Cổng thanh toán gặp sự cố khi tạo hoặc xử lý thanh toán. Thử lại theo chính sách của FE. |
| `502` | `PAY_5004` | Không thể tạo thanh toán. Hiển thị `message` và cho phép người dùng thử lại. |
| `409` | `PAY_5003` | Phiên thanh toán đã hết hạn; tạo checkout mới. |
| `400` | `COUPON_4001` | Mã coupon không hợp lệ, đã hết hạn hoặc không áp dụng cho dịch vụ này. |
| `401` | `AUTH_1001` | Chưa đăng nhập hoặc token đã hết hạn. |
| `409` | `SYS_0007` | Đơn hàng checkout này đã được thanh toán thành công từ trước. |
| `429` | `SYS_0010` | Thao tác checkout hoặc gửi yêu cầu rút tiền quá nhanh. Khóa nút đếm ngược. |

---

## 6. Code Mẫu Thực Chiến Next.js (PayOS Checkout & Polling)

```tsx
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

      const { checkoutUrl } = res.data?.data || res.data;

      if (checkoutUrl) {
        // 2. Mở tab thanh toán PayOS chứa mã VietQR
        window.open(checkoutUrl, '_blank');
        setPolling(true);
      }
    } catch (err: any) {
      alert(err?.message || 'Không thể khởi tạo link thanh toán PayOS');
    } finally {
      setLoading(false);
    }
  };

  // 3. Poll kiểm tra trạng thái đơn hàng khi user đang quét mã
  useEffect(() => {
    let timer: NodeJS.Timeout;

    if (polling) {
      timer = setInterval(async () => {
        try {
          const res: any = await apiClient.get(`/api/me/payment-orders/${bookingId}`);
          const order = res.data?.data || res.data;
          if (order?.status === 'PAID') {
            setPolling(false);
            onPaymentSuccess();
          }
        } catch (err) {
          console.error('Lỗi khi kiểm tra trạng thái thanh toán:', err);
        }
      }, 3000); // 3 giây poll 1 lần
    }

    return () => {
      if (timer) clearInterval(timer);
    };
  }, [polling, bookingId, onPaymentSuccess]);

  return (
    <div className="payment-container space-y-2">
      <button
        onClick={handleStartCheckout}
        disabled={loading || polling}
        className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg disabled:opacity-50"
      >
        {loading
          ? 'Đang tạo link thanh toán...'
          : polling
          ? 'Đang chờ chuyển khoản VietQR...'
          : 'Thanh toán qua PayOS (VietQR)'}
      </button>

      {polling && (
        <p className="text-sm text-gray-500">
          Vui lòng hoàn tất chuyển khoản trên trang PayOS. Hệ thống sẽ tự động kích hoạt buổi học ngay khi nhận được tiền...
        </p>
      )}
    </div>
  );
};
```
