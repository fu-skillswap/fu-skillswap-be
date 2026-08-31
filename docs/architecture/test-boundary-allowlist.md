# Danh mục Phân loại & Allow-List Cross-Module Test Imports

Tài liệu này ghi nhận và phân loại toàn bộ **487 cross-module internal test import** hiện có trong `src/test`. Mục đích là kiểm soát chặt chẽ, ngăn ngừa việc phát sinh thêm coupling mới trong quá trình refactor, và xác định rõ lộ trình xóa bỏ tương ứng với từng Task refactor nghiệp vụ từ **Task 05** đến **Task 11**.

---

## 1. Nguyên tắc kiểm soát Test Boundary

1. **Unit Test**: Test của module nào chỉ được import internal types (`domain`, `repository`, `service`, `controller`, `dto`) của chính module đó.
2. **Consumer Contract Test**: Test của module consumer tương tác với module khác CHỈ được import `modules.<provider>.port` hoặc nhận data từ `modules.<provider>.support.<Provider>TestFixture`.
3. **Integration / Smoke Test**: Phải sử dụng orchestration fixture hoặc REST contracts, không được autowire chéo JPA repositories của nhiều module.
4. **Thời hạn tồn tại (Expiry)**: Các test hiện đang tạm thời coupling sẽ bị loại bỏ dần theo từng task tương ứng dưới đây.

---

## 2. Bảng phân loại và Lộ trình giải tỏa (Allow-List Matrix)

| Nhóm Consumer Test | Module bị phụ thuộc | Số lượng import | Lý do tạm thời & Bản chất | Target Task giải tỏa | Hạn mức / Milestone |
|---|---|---:|---|---|---|
| `smoke` | `admin`, `booking`, `identity`, `mentor`, `payment`, `feedback` | 43 | End-to-end smoke test tạo dữ liệu qua nhiều aggregate repositories | **Task 03 / Task 04** | Thay bằng Orchestration Fixture & Public API Commands |
| `booking` | `identity` | 66 | Booking tests khởi tạo `User`, `AcademicProgram`, `GoogleCalendarSyncStatus` | **Task 05 & Task 07** | Chuyển sang `IdentityTestFixture` & `UserQueryPort` |
| `booking` | `mentor` | 64 | Booking tests mock/setup `MentorProfile`, `MentorService`, availability rules | **Task 06** | Chuyển sang `MentorBookingCapability` & `MentorTestFixture` |
| `booking` | `payment` | 14 | Booking tests kiểm tra quote/settlement qua `PaymentOrderService` | **Task 08** | Chuyển sang Payment event listener |
| `booking` | `chat` | 9 | Booking tests thiết lập chat entitlement | **Task 10** | Chuyển sang Outbox Lifecycle Event |
| `booking` | `notification` | 9 | Booking tests verify email notification service calls | **Task 09** | Chuyển sang Event capture |
| `mentor` | `identity` | 55 | Mentor tests kiểm tra user profile, academic program, campus | **Task 05** | Chuyển sang `IdentityTestFixture` |
| `mentor` | `catalog` | 18 | Mentor discovery tests import catalog entities | **Task 11** | Chuyển sang `CatalogKeywordQueryPort` |
| `mentor` | `filestorage` | 8 | Mentor verification tests import `StoredFile` entity | **Task 05** | Chuyển sang `VerificationDocumentStoragePort` & `FileAssetSnapshot` |
| `mentor` | `feedback` | 4 | Mentor discovery mapper tests import review internal projection | **Task 11** | Chuyển sang `MentorReviewProjection` từ port |
| `admin` | `identity` | 18 | Admin moderation tests import `User` entity & identity services | **Task 05 & Task 11** | Chuyển sang `UserAdminPort` |
| `admin` | `mentor` | 20 | Admin verification tests import `MentorProfile` repository | **Task 06 & Task 11** | Chuyển sang `MentorVerificationAdminPort` |
| `admin` | `payment` | 14 | Admin financial tests import payment order repository | **Task 08 & Task 11** | Chuyển sang `PaymentAdminPort` |
| `admin` | `booking`, `forum`, `notification` | 27 | Admin case tests import provider repositories | **Task 11** | Chuyển sang Provider Admin Ports |
| `payment` | `booking` | 16 | Payment pricing tests nhận `Booking` & `BookingTime` entity | **Task 08** | Chuyển sang `BookingChargeSnapshot` |
| `payment` | `identity`, `mentor`, `chat`, `notification` | 10 | Payment checkout tests import user & mentor profile | **Task 08** | Chuyển sang scalar IDs & Audience snapshot |
| `forum` | `identity`, `notification` | 22 | Forum tests import `User` entity & notification service | **Task 10** | Chuyển sang Author projection & `NotificationCommandPort` |
| `identity` | `mentor`, `booking`, `catalog` | 14 | Onboarding & calendar fallback tests import mentor & booking services | **Task 07** | Chuyển sang lifecycle events & ports |
| `feedback` | `booking`, `identity`, `mentor`, `notification` | 7 | Feedback tests import booking entity for verification | **Task 11** | Chuyển sang Booking Completion Snapshot |
| `chat` | `identity`, `booking`, `mentor` | 4 | Chat access tests import user and booking entities | **Task 10** | Chuyển sang `ChatAccessSnapshotPort` |
| `blog` | `identity`, `booking`, `notification` | 5 | Blog tests import user and booking entitlement | **Task 11** | Chuyển sang `ContentEntitlementQuery` & `BlogAuthorQueryPort` |

---

## 3. Quy định chấp hành

- Tuyệt đối không thêm mới bất kỳ import `modules.<other_module>.(domain|repository|service|controller|dto)` nào trong các test mới.
- Mọi test mới phải sử dụng port (`modules.<owner>.port`) hoặc test fixture (`modules.<owner>.support`).
- Khi mỗi Task tương ứng hoàn thành (từ Task 05 đến Task 11), nhóm test thuộc Task đó sẽ được dọn dẹp sạch hoàn toàn về 0.
