# Admin Coupon & Campaign Management Service (`14-admin-coupon-campaign-management.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Quản trị Chiến dịch Khuyến mãi, Ngân sách Trợ giá và Mã Giảm giá (Admin Coupon & Campaign Management Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend Admin, Marketing, QA và Operations.

---

## 1. Overview (Tổng quan)

**Admin Coupon & Campaign Management Service** là trung tâm điều khiển tăng trưởng (`Growth Engine Control Center`) của SkillSwap, cung cấp toàn bộ API quản trị chiến dịch khuyến mãi (`Campaigns`), phân tập đối tượng hướng tới (`Audience Targeting`), quản lý loại quyền lợi (`Benefits`), tạo và quản lý mã giảm giá (`Coupons`), xem lịch sử đổi mã (`Redemptions`), báo cáo hiệu quả chiến dịch (`Campaign Analytics`) và tự động hóa vòng đời chiến dịch (`Campaign Lifecycle Scheduler`).

### Trách nhiệm chính của Service
- **Quản lý Chiến dịch Khuyến mãi (`Campaign Management`)**: Cho phép Admin tạo, sửa, đổi trạng thái và theo dõi ngân sách Scoin trợ giá của từng chiến dịch.
- **Tự động hóa Vòng đời Chiến dịch (`Campaign Lifecycle Automation`)**: Tự động chuyển chiến dịch sang trạng thái `ACTIVE` khi tới thời điểm `startAt` (nếu đặt ở dạng `SCHEDULED`), và chuyển sang `ENDED` khi đã quá `endAt`.
- **Tập Đối tượng Mục tiêu (`Audience Targeting Scoping`)**: Hỗ trợ khoanh vùng đối tượng theo `roleCodes` (STUDENT, MENTOR), `campusIds` (FPT HCM, FPT HN), `programIds`, `specializationIds`, và `helpTopicIds`.
- **Báo cáo Phân tích Hiệu quả Chiến dịch (`Campaign Analytics`)**: Cung cấp metrics ROI (Return on Investment), burn rate ngân sách, tổng số đơn đặt lịch tạo ra, tổng doanh thu thu về (Revenue Scoin) và lượt dùng coupon.
- **Quản lý Mã Giảm giá & Lịch sử Đổi mã (`Coupon & Redemption Governance`)**: Tạo mã giảm giá theo phần trăm (%) hoặc giá trị cố định (Scoin), kiểm soát quota tổng/mỗi user, xem lịch sử redemption của từng mã.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Phân tách Ranh giới Quản trị & Thanh toán (`Security & Domain Boundaries`)**: Mọi API nằm dưới `/api/admin/campaigns` và `/api/admin/coupons`, yêu cầu quyền `ADMIN` hoặc `SYSTEM_ADMIN`. Quá trình tính toán trợ giá checkout (Payment Order) chỉ gọi đọc thông tin khả dụng từ Campaign/Coupon, tuyệt đối không bị ảnh hưởng trực tiếp bởi các thao tác sửa đổi khi chiến dịch chưa công bố.
2. **Bảo toàn Ngân sách Hệ thống (`Budget Overburn Prevention`)**: Khi chỉnh sửa ngân sách `budgetScoin`, hệ thống kiểm tra ngân sách mới phải lớn hơn hoặc bằng lượng ngân sách đã thực sự giải ngân cho các giao dịch thành công.
3. **Mã Giảm giá Duy nhất & Toàn vẹn Tài chính (`Financial Record Integrity`)**: Mã coupon là duy nhất (`UNIQUE CODE`). Hệ thống tuyệt đối **không hard-delete** coupon đã từng phát sinh lịch sử sử dụng (Redemption History) mà chuyển sang cơ chế **soft-deactivate** (chuyển status sang `INACTIVE` hoặc `SUSPENDED`).

---

## 3. Campaign & Coupon Status Lifecycle (Vòng đời Trạng thái)

### 3.1. Campaign Status State Machine

```
              [ DRAFT ] ── (có startAt tương lai) ──> [ SCHEDULED ]
                  │                                         │
            (activate ngay)                          (scheduler auto
                  │                                   hoặc admin)
                  ▼                                         ▼
              [ ACTIVE ] <──────────────────────────────────┘
                 │  ▲
        (pause)  │  │ (resume)
                 ▼  │
              [ PAUSED ]
                 │
              (endAt đến hoặc admin end)
                 ▼
              [ ENDED ] ── (admin archive) ──> [ ARCHIVED ]
```

- **`DRAFT`**: Nháp ban đầu khi vừa khởi tạo.
- **`SCHEDULED`**: Đã đặt lịch trước, hệ thống tự động kích hoạt sang `ACTIVE` khi tới `startAt`.
- **`ACTIVE`**: Đang hoạt động, được áp dụng trợ giá trực tiếp khi người dùng tính giá booking.
- **`PAUSED`**: Tạm dừng chiến dịch (không áp dụng trợ giá cho booking mới).
- **`ENDED`**: Đã kết thúc (do hết hạn `endAt` hoặc admin chủ động kết thúc).
- **`ARCHIVED`**: Đã lưu trữ tài liệu.

### 3.2. Coupon Status

- **`ACTIVE`**: Mã đang có hiệu lực sử dụng.
- **`INACTIVE`**: Tạm tắt mã (không cho phép người dùng apply).
- **`SUSPENDED`**: Tạm khóa do nghi vấn lạm dụng.
- **`EXPIRED`**: Hết hạn sử dụng.

---

## 4. Endpoints Reference (Danh sách API)

### 4.1. Admin Campaign APIs (`/api/admin/campaigns`)

| HTTP Method | Endpoint Path | Mô tả | Authorization |
|-------------|---------------|-------|---------------|
| `GET` | `/api/admin/campaigns` | Lấy danh sách chiến dịch (phân trang + bộ lọc `status`, `fundingSource`, `keyword`) | `ADMIN`, `SYSTEM_ADMIN` |
| `GET` | `/api/admin/campaigns/{id}` | Xem chi tiết thông tin chiến dịch, ngân sách đã dùng và các tập đối tượng áp dụng | `ADMIN`, `SYSTEM_ADMIN` |
| `POST` | `/api/admin/campaigns` | Tạo chiến dịch mới (Status ban đầu: `DRAFT` hoặc `SCHEDULED`) | `ADMIN`, `SYSTEM_ADMIN` |
| `PUT` | `/api/admin/campaigns/{id}` | Cập nhật thông tin chiến dịch (chỉ khi `DRAFT`, `SCHEDULED` hoặc `PAUSED`) | `ADMIN`, `SYSTEM_ADMIN` |
| `PATCH` | `/api/admin/campaigns/{id}/status` | Chuyển đổi trạng thái chiến dịch | `ADMIN`, `SYSTEM_ADMIN` |
| `GET` | `/api/admin/campaigns/{id}/analytics` | Xem báo cáo chỉ số hiệu quả chiến dịch (ROI %, budget burn rate %, số đơn hàng, doanh thu) | `ADMIN`, `SYSTEM_ADMIN` |
| `GET` | `/api/admin/campaigns/{id}/benefits` | Lấy danh sách quyền lợi (Benefits) thuộc chiến dịch | `ADMIN`, `SYSTEM_ADMIN` |
| `POST` | `/api/admin/campaigns/{id}/benefits` | Thêm quyền lợi mới cho chiến dịch (`CREDIT_ISSUANCE` hoặc `COUPON_ISSUANCE`) | `ADMIN`, `SYSTEM_ADMIN` |
| `PUT` | `/api/admin/campaigns/{id}/benefits/{benefitId}` | Cập nhật thông tin quyền lợi | `ADMIN`, `SYSTEM_ADMIN` |
| `DELETE` | `/api/admin/campaigns/{id}/benefits/{benefitId}` | Xóa một quyền lợi khỏi chiến dịch | `ADMIN`, `SYSTEM_ADMIN` |

### 4.2. Admin Coupon APIs (`/api/admin/coupons`)

| HTTP Method | Endpoint Path | Mô tả | Authorization |
|-------------|---------------|-------|---------------|
| `GET` | `/api/admin/coupons` | Lấy danh sách mã giảm giá (phân trang + bộ lọc `status`, `discountType`, `keyword`) | `ADMIN`, `SYSTEM_ADMIN` |
| `GET` | `/api/admin/coupons/{id}` | Xem chi tiết mã giảm giá, quota và tập điều kiện áp dụng | `ADMIN`, `SYSTEM_ADMIN` |
| `POST` | `/api/admin/coupons` | Tạo mã giảm giá mới | `ADMIN`, `SYSTEM_ADMIN` |
| `PUT` | `/api/admin/coupons/{id}` | Cập nhật thông tin mã giảm giá | `ADMIN`, `SYSTEM_ADMIN` |
| `PATCH` | `/api/admin/coupons/{id}/status` | Chuyển trạng thái mã giảm giá (`ACTIVE`, `INACTIVE`, `SUSPENDED`) | `ADMIN`, `SYSTEM_ADMIN` |
| `GET` | `/api/admin/coupons/{id}/redemptions` | Lấy lịch sử đổi mã giảm giá (Redemptions) kèm tên người dùng | `ADMIN`, `SYSTEM_ADMIN` |

---

## 5. Sample Payloads & Data Models

### 5.1. Response Xem Chi tiết Campaign (`GET /api/admin/campaigns/{id}`)

```json
{
  "timestamp": "2026-08-04 11:50:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "id": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a1001",
    "name": "Back to School 2026",
    "description": "Trợ giá booking cho sinh viên đầu năm học mới",
    "status": "ACTIVE",
    "fundingSource": "APP_FUNDED",
    "startAt": "2026-08-01T00:00:00",
    "endAt": "2026-09-01T23:59:59",
    "budgetScoin": 100000,
    "budgetUsedScoin": 25000,
    "budgetRemainingScoin": 75000,
    "audienceRoleCodes": ["STUDENT"],
    "audienceCampusIds": ["0197e6b1-2d1a-7f0f-bc7a-9bc2e31a9001"],
    "audienceProgramIds": [],
    "audienceSpecializationIds": [],
    "audienceHelpTopicIds": [],
    "benefitCount": 1,
    "totalBookingsCreated": 15,
    "createdAt": "2026-07-28T10:00:00",
    "updatedAt": "2026-08-01T00:00:00"
  }
}
```

### 5.2. Response Campaign Analytics (`GET /api/admin/campaigns/{id}/analytics`)

```json
{
  "timestamp": "2026-08-04 11:50:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "campaignId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a1001",
    "campaignName": "Back to School 2026",
    "status": "ACTIVE",
    "budgetScoin": 100000,
    "budgetUsedScoin": 25000,
    "budgetRemainingScoin": 75000,
    "budgetBurnRate": 25.0,
    "totalBookingsCreated": 15,
    "totalCouponRedemptions": 8,
    "totalRevenueScoin": 150000,
    "totalCampaignCostScoin": 25000,
    "campaignRoiPercent": 500.0,
    "startAt": "2026-08-01T00:00:00",
    "endAt": "2026-09-01T23:59:59",
    "daysActive": 3
  }
}
```

### 5.3. Response Lịch sử Đổi mã Coupon (`GET /api/admin/coupons/{id}/redemptions`)

```json
{
  "timestamp": "2026-08-04 11:50:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "Thành công",
  "data": {
    "content": [
      {
        "id": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a5001",
        "couponId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a4001",
        "paymentOrderId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a6001",
        "redeemerUserId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a7001",
        "redeemerFullName": "Nguyen Van A",
        "status": "REDEEMED",
        "discountScoin": 20000,
        "createdAt": "2026-08-02T14:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

---

## 6. Guidance for Frontend Integration (Lưu ý cho Frontend)

1. **Mã Coupon Tự động Viết hoa**: Frontend nên auto-uppercase input mã coupon khi người dùng gõ, tuy nhiên Backend cũng đã xử lý tự động `.toUpperCase()`.
2. **Khóa Chỉnh sửa Campaign Active**: Giao diện Admin Wizard không cho phép sửa đổi thông tin chính (ngân sách, thời gian, audience) khi Campaign đang ở trạng thái `ACTIVE`. Phải hiển thị nút **"Tạm dừng" (PAUSE)** trước khi mở form sửa.
3. **Phân biệt 2 Loại Benefit**:
   - `CREDIT_ISSUANCE`: Trợ giá Scoin trực tiếp vào đơn đặt lịch khi mentee tạo booking.
   - `COUPON_ISSUANCE`: Phát coupon mã giảm giá cho mentee.
4. **Cập nhật Dashboard Overview**: Endpoint `GET /api/admin/dashboard/overview` hiện đã bổ sung thêm block `campaigns` chứa số liệu tổng quan ngân sách và số campaign đang hoạt động.
