# Discovery, Search & Recommendation Service (`04-discovery-search.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Tìm kiếm, Gợi ý và Khám phá Mentor (Discovery, Search & Recommendation Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**Discovery, Search & Recommendation Service** là công cụ tìm kiếm và gợi ý Mentor cá nhân hóa cho Mentee trên SkillSwap. Service bao gồm bộ lọc tìm kiếm đa tiêu chí (`/api/mentors`), thuật toán gợi ý cá nhân hóa dựa trên profile học thuật (`/api/mentors/recommendations`), trang thông tin chi tiết public của Mentor (`/api/mentors/{mentorUserId}`), quy trình truy vấn khung giờ rảnh 2 bước (`availability-slots` -> `candidates`), và hệ thống ghi nhận phễu hành vi (`Funnel Telemetry`).

### Trách nhiệm chính của Service
- **Tìm kiếm & Lọc Mentor Đa tiêu chí (`Search & Filter`)**: Tìm kiếm theo từ khóa (keyword), cơ sở (campus), ngành học (program), chuyên ngành (specialization), chủ đề hỗ trợ (help topics), điểm đánh giá tối thiểu (minRating), mức hỗ trợ 1..4, và sắp xếp theo độ phù hợp (`sortBy = relevance`), điểm số, hoặc giá.
- **Gợi ý Cá nhân hóa (`Personalized Recommendation Engine`)**: Phân tích hồ sơ sinh viên (`StudentProfile`) của Mentee đang đăng nhập để tự động tính điểm ghép nối (`matchScore` 0-100) và đưa ra danh sách Mentor phù hợp nhất kèm lý do gợi ý (`matchReasons`).
- **Trình diễn Thông tin Public của Mentor (`Public Mentor Detail`)**: Trả về 6 section thông tin công khai theo đúng thứ tự hiển thị chuẩn trên Frontend.
- **Chuẩn bị Đặt lịch 2 Bước (`Booking-Prep Availability & Candidates`)**: Phân tách rõ bước chọn khung giờ cha (`availability-slots`) và bước chọn khung giờ hẹp theo gói dịch vụ (`candidates`) trước khi tạo đơn đặt lịch.
- **Theo vết Phễu Khám phá (`Funnel Telemetry Analytics`)**: Thu thập các sự kiện hành vi `SERVICE_VIEWED`, `CANDIDATE_SELECTED`, `BOOKING_STARTED` dạng best-effort để tối ưu trải nghiệm người dùng.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Ghép nối Nhu cầu Mentoring Chính xác**: Sử dụng thuật toán Reranking & Recall ở Backend dựa trên tiêu chuẩn học thuật FPTU giúp Mentee tìm đúng Mentor có chuyên môn sâu trong thời gian ngắn nhất.
2. **Loại bỏ Tự Xếp hạng / Phân trang Client-side**: Phân trang (`PageResponse`) và tính toán `matchScore` hoàn toàn do Backend đảm nhiệm. Frontend không tự tính lại điểm hay sắp xếp lại kết quả trả về từ API.
3. **Bảo vệ Trạng thái Sẵn sàng Đặt lịch (`canRequestBooking`)**: Cung cấp cờ tổng hợp `canRequestBooking` trong chi tiết Mentor để Frontend chủ động bật/tắt nút "Đặt lịch" mà không phải tự tổng hợp từ nhiều nguồn.
4. **Phân tách Độc lập Giữa Tìm kiếm và Gợi ý**: Luồng Tìm kiếm (`Search`) dành cho khi người dùng chủ động gõ từ khóa. Luồng Gợi ý (`Recommendation`) dành cho Dashboard/Home khi người dùng chưa biết chính xác từ khóa cần tìm.
5. **Ghi nhận Phễu Bất đồng bộ Không Gây Trễ (Best-effort Telemetry)**: API ghi nhận phễu telemetry chạy theo cơ chế non-blocking, không bao giờ chặn điều hướng hay làm gián đoạn trải nghiệm người dùng khi sảy ra sự cố mạng.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                LUỒNG KHÁM PHÁ, TÌM KIẾM & ĐẶT LỊCH MENTOR                             |
+-------------------------------------------------------------------------------------------------------+

  Frontend (Browser)                  Backend (SkillSwap API)                 Database / Search Index
          |                                     |                                         |
   1. User vào Trang chủ / Dashboard            |                                         |
          |-- GET /api/mentors/recommendations >|-- Tính toán matchScore theo StudentProfile
          |<-- 200 OK (Danh sách gợi ý) --------|                                         |
          |                                     |                                         |
   2. User mở Trang Tìm kiếm (/explore)        |                                         |
          |-- GET /api/mentors?keyword=Spring ->|-- Recall Candidate Window & Rerank ---->|
          |<-- 200 OK (PageResponse<Card>) -----|                                         |
          |                                     |                                         |
   3. User click vào Mentor Card                |                                         |
          |-- GET /api/mentors/{mentorUserId} ->|-- Trả 6 section Public Detail --------->|
          |<-- 200 OK (MentorDiscoveryDetail) --|                                         |
          |                                     |                                         |
   4. User bấm "Xem lịch rảnh" (Nút Đặt lịch)  |                                         |
          |-- GET /mentors/{id}/availability-slots?from=...&to=... ---------------------->|
          |<-- 200 OK (Parent Slots & Active Services) -----------------------------------|
          |                                     |                                         |
   5. User chọn 1 Parent Slot & 1 Service       |                                         |
          |-- GET /availability-slots/{slotId}/candidates?serviceId=... ----------------->|
          |<-- 200 OK (Exact Candidate Segments) ----------------------------------------|
          |                                     |                                         |
   6. Điều hướng sang Màn hình Tạo Đơn Đặt lịch (Booking Checkout)
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Thuật toán Điểm Phù hợp (`matchScore` & Reranking)
- `matchScore`: Chỉ số phần trăm (0 - 100.0) đại diện cho độ khớp giữa yêu cầu của Mentee và hồ sơ Mentor.
- **Cơ chế `sortBy = relevance`**: Khi client không chỉ định kiểu sắp xếp hoặc truyền `sortBy = relevance`, Backend sẽ tự động lấy tập ứng viên (recall candidate window) dựa trên tiêu chí học thuật và thực hiện Rerank để đưa các Mentor phù hợp nhất lên đầu trang 1.

### 4.2 Cấu trúc Khung giờ 2 Bước (`Parent Slots & Candidates`)
- **Parent Slot (`Availability Slot`)**: Khung giờ rảnh lớn do Mentor tạo (Ví dụ: 08:00 - 12:00 Thứ Hai). Đi kèm danh sách các gói dịch vụ (`services`) mà Mentor gắn vào khung giờ này.
- **Candidate Segment (`Service Candidate`)**: Đoạn thời gian hẹp đúng bằng `durationMinutes` của gói dịch vụ đã chọn (Ví dụ: Dịch vụ 60 phút sẽ chia 08:00-12:00 thành 4 candidate segments: 08:00-09:00, 09:00-10:00, 10:00-11:00, 11:00-12:00).
- **Blocked Segment**: Candidate segment nào đã trùng với một Booking ở trạng thái `ACCEPTED` sẽ bị đánh dấu `isBlocked = true`.

### 4.3 Phễu Sự kiện Telemetry (`Funnel Telemetry`)
- `SERVICE_VIEWED`: Phát sinh khi user mở xem chi tiết một gói dịch vụ của Mentor.
- `CANDIDATE_SELECTED`: Phát sinh khi user chọn một candidate segment cụ thể trong lịch rảnh.
- `BOOKING_STARTED`: Phát sinh khi user bấm nút bắt đầu tạo đơn đặt lịch.
- Sources hợp lệ: `MENTOR_PROFILE`, `DISCOVERY_SEARCH`, `BLOG_ARTICLE`, `DIRECT_LINK`.

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Rate Limit | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/mentors/recommendations` | Authenticated | Không giới hạn | Lấy danh sách Mentor gợi ý cá nhân hóa | Màn hình Dashboard / Trang chủ |
| `GET` | `/api/mentors` | Public / Auth | Không giới hạn | Tìm kiếm và lọc danh sách Mentor discoverable | Màn hình Khám phá (/explore) |
| `GET` | `/api/mentors/{mentorUserId}` | Public / Auth | Không giới hạn | Lấy chi tiết công khai 6 phần của Mentor | Trang Profile Public của Mentor |
| `GET` | `/api/mentors/{mentorUserId}/reviews` | Authenticated | Không giới hạn | Lấy danh sách đánh giá công khai theo phân trang | Tab Review trên trang Mentor Detail |
| `GET` | `/api/mentors/{mentorUserId}/availability-slots` | Authenticated | Không giới hạn | Lấy các khung giờ rảnh cha (Parent Slots) | Khi mở Modal chọn lịch rảnh |
| `GET` | `/api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates` | Authenticated | Không giới hạn | Lấy các phân đoạn thời gian chính xác của 1 dịch vụ | Khi user chọn 1 Slot và 1 Service |
| `POST` | `/api/mentor-discovery/funnel-events` | Authenticated | Không giới hạn | Ghi nhận sự kiện phễu hành vi (Best-effort) | Khi user xem service/chọn slot/bấm book |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `GET /api/mentors`

#### Purpose
Tìm kiếm và lọc danh sách Mentor hỗ trợ phân trang chuẩn `PageResponse`.

#### Query Parameters (`MentorDiscoverySearchRequest`)
- `keyword` (`String`): Từ khóa tìm kiếm theo tên, headline, bio, kỹ năng.
- `campusId` (`UUID`): ID cơ sở FPT.
- `programId` (`UUID`): ID ngành học.
- `specializationId` (`UUID`): ID chuyên ngành.
- `helpTopicIds` (`List<UUID>`): Danh sách ID chủ đề hỗ trợ.
- `isAvailable` (`Boolean`): Lọc Mentor đang bật cờ sẵn sàng.
- `minRating` (`Double`): Điểm đánh giá tối thiểu (ví dụ: `4.0`, `4.5`).
- `sortBy` (`String`): `relevance` (mặc định), `rating`, `reviewCount`, `completedSessions`, `priceLowToHigh`, `priceHighToLow`.
- `page` (`int`): Trang hiện tại (0-indexed, mặc định 0).
- `size` (`int`): Số lượng phần tử mỗi trang (mặc định 20).

#### Response Body
```json
{
  "timestamp": "2026-08-04T09:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "items": [
      {
        "mentorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "displayName": "Nguyễn Văn A",
        "avatarUrl": "https://example.com/avatar.jpg",
        "headline": "Senior Backend Developer",
        "expertiseDescription": "Java, Spring Boot, Microservices",
        "campusName": "FPT University HCM",
        "programName": "Kỹ thuật phần mềm",
        "specializationName": "Web Development",
        "matchScore": 88.5,
        "isAvailable": true,
        "ratingAverage": 4.8,
        "reviewCount": 12,
        "completedSessions": 15,
        "helpTopicTags": ["SPRING_BOOT", "JAVA"]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 6.2 `GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates`

#### Purpose
Lấy danh sách các phân đoạn thời gian chính xác (candidate segments) của một gói dịch vụ `serviceId` bên trong một khung giờ cha `slotId`.

#### Query Parameters
- `serviceId` (`UUID`, Bắt buộc): ID gói dịch vụ đã chọn.

#### Response Body (`ServiceSlotCandidatesResponse`)
```json
{
  "timestamp": "2026-08-04T09:35:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "slotId": "6fa85f64-5717-4562-b3fc-2c963f66afa6",
    "serviceId": "55555555-5555-5555-5555-555555555555",
    "durationMinutes": 60,
    "candidates": [
      {
        "candidateId": "cand-001",
        "startTime": "2026-08-05T08:00:00Z",
        "endTime": "2026-08-05T09:00:00Z",
        "isBlocked": false
      },
      {
        "candidateId": "cand-002",
        "startTime": "2026-08-05T09:00:00Z",
        "endTime": "2026-08-05T10:00:00Z",
        "isBlocked": true
      }
    ]
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Chọn Lịch Rảnh & Candidate Segment Chuẩn bị Đặt Lịch

```
User (Modal Chọn Lịch)               Frontend Component                     Backend API
        |                                     |                                   |
   1. User chọn xem Lịch Rảnh Mentor -------->|-- GET /mentors/{id}/availability-slots?from=...&to=...
        |                                     |<-- Trả danh sách Parent Slots & Active Services
   2. Render danh sách Khung giờ Cha (Slots)  |                                   |
        |                                     |                                   |
   3. User chọn Slot "Thứ 4: 08:00-12:00"     |                                   |
   4. User chọn Service "Mock Interview 60m"  |                                   |
        |------------------------------------>|-- GET /availability-slots/{slotId}/candidates?serviceId=...
        |                                     |<-- Trả các Candidate Segments (60 phút/segment)
   5. Render các ô giờ: 08:00, 09:00 (Blocked), 10:00, 11:00
        |                                     |                                   |
   6. User chọn ô 08:00-09:00 và bấm "Tiếp tục"|                                   |
        |------------------------------------>|-- Chuyển sang Trang Đặt lịch (Checkout)
```

---

## 8. State Machine (Ma trận Trạng thái Discovery & Booking Eligibility)

### 8.1 Vòng đời Khả năng Đặt lịch của Mentor (`BookingEligibilityState`)

```
             +-----------------------+
             |   DISCOVERABLE_ONLY   | (Mentor xuất hiện trên danh sách nhưng canRequestBooking = false)
             +-----------------------+
                         |
      Cần thỏa mãn đồng thời:
      - isAvailable == true
      - bookingSuspendedUntil == null (hoặc đã quá hạn)
      - Có ít nhất 1 service isActive == true
      - Có lịch rảnh còn trống
                         |
                         v
             +-----------------------+
             |    BOOKING_ELIGIBLE   | (canRequestBooking = true -> Render Nút "Đặt Lịch")
             +-----------------------+
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `401 UNAUTHENTICATED` | `UNAUTHENTICATED` | Chưa đăng nhập khi truy cập `/recommendations` hoặc `/candidates`. | Đưa về luồng Đăng nhập hoặc ẩn module gợi ý cá nhân hóa. |
| `404 NOT_FOUND` | `NOT_FOUND` | Mentor ID không tồn tại hoặc đã bị ẩn khỏi danh sách public. | Thông báo "Mentor không tồn tại hoặc tạm ngưng hoạt động", chuyển về `/explore`. |
| `409 RESOURCE_CONFLICT` | `RESOURCE_CONFLICT` | Candidate segment đã bị mentee khác đặt trùng trong lúc đang chọn. | Thông báo "Khung giờ này vừa có người đặt", tự động reload danh sách candidates. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Bảo vệ Dữ liệu Khung giờ Cá nhân**: API danh sách kết quả tìm kiếm public chỉ trả thông tin tổng quan (`MentorDiscoveryCardResponse`). Chi tiết khung giờ cụ thể và candidate segment bắt buộc phải đăng nhập mới được truy vấn.
2. **Non-blocking Funnel Telemetry**: API `POST /api/mentor-discovery/funnel-events` chạy theo cơ chế async/best-effort. Frontend **nghiêm cấm** chặn luồng chuyển trang hoặc hiển thị lỗi UI nếu API này trả lỗi HTTP 5xx hay Timeout.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Sử dụng cờ `canRequestBooking` trong response `/api/mentors/{mentorUserId}` làm điều kiện duy nhất để bật/tắt nút "Đặt lịch".
- Gọi API `/candidates` ngay sau khi user chọn xong cả **Parent Slot** lẫn **Gói Dịch vụ**.
- Gửi sự kiện telemetry `POST /funnel-events` theo dạng ngầm (fire-and-forget).

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** tự sắp xếp lại kết quả tìm kiếm ở client khi backend trả về `sortBy = relevance`.
- **KHÔNG ĐƯỢC** tự hiển thị nút Đặt lịch nếu `canRequestBooking = false`.
- **KHÔNG ĐƯỢC** gọi API `/candidates` khi user mới chỉ chọn Slot mà chưa chọn Gói Dịch vụ.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Danh sách Tìm kiếm Rỗng (`Empty Search Result`)**:
   - Khi `totalElements = 0`, Frontend hiển thị màn hình Empty State cùng nút "Xóa bộ lọc" để đưa các tham số search request về mặc định.
2. **Candidate Segment Bị Khóa (`isBlocked = true`)**:
   - Các khung giờ có `isBlocked = true` phải được render dưới dạng ô xám bị vô hiệu hóa (disabled) kèm nhãn *"Đã có người đặt"*.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **User Profile Service**: Cung cấp thông tin chuyên ngành, campus của Mentee để phục vụ thuật toán Recommendation.
- **Mentor Service**: Cung cấp dữ liệu gói dịch vụ `MentorService` và thông tin hồ sơ công khai của Mentor.
- **Booking Service**: Tiếp nhận candidate segment đã chọn từ Discovery để tiến hành tạo đơn hàng và giữ chỗ.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Trang Khám phá & Tìm kiếm Mentor (`/explore`)
- **React Components**: `MentorExplorePage.tsx`, `MentorFilterBar.tsx`, `MentorGridCard.tsx`
- **APIs Triggered**:
  1. `GET /api/mentors` (Mỗi khi thay đổi filter, keyword, hoặc chuyển trang)
- **Expected Behavior**: Hiển thị kết quả dạng lưới. Mỗi card hiển thị `displayName`, `headline`, `avatarUrl`, `ratingAverage`, `reviewCount`, `matchScore` và danh sách `helpTopicTags`.

#### B. Màn hình Chi tiết Public của Mentor (`/mentors/:mentorUserId`)
- **React Components**: `PublicMentorDetailPage.tsx`, `MentorIdentityHeader.tsx`, `ServiceOptionCard.tsx`, `ReviewListTab.tsx`
- **APIs Triggered**:
  1. `GET /api/mentors/{mentorUserId}` (Khi mount trang)
  2. `GET /api/mentors/{mentorUserId}/reviews` (Khi chuyển sang tab Đánh giá)
- **Expected Behavior**: Render đúng 6 section dữ liệu theo hợp đồng. Nút "Đặt lịch ngay" chỉ sáng khi `canRequestBooking = true`.

#### C. Modal Chọn Lịch Rảnh Đặt Hàng (`BookingSlotPickerModal.tsx`)
- **React Components**: `BookingSlotPickerModal.tsx`, `ParentSlotCalendar.tsx`, `CandidateTimeGrid.tsx`
- **APIs Triggered**:
  1. `GET /api/mentors/{id}/availability-slots` (Khi mở modal)
  2. `GET /api/mentors/{id}/availability-slots/{slotId}/candidates?serviceId=...` (Khi chọn slot & service)
- **Expected Behavior**: Render danh sách các ô giờ candidate. Disable các ô có `isBlocked = true`. Khi bấm chọn 1 ô hợp lệ, lưu `candidateId` và chuyển sang checkout.

---

### 14.2 Frontend Discovery State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |    BROWSIING_SEARCH   | (Đang tìm kiếm / xem danh sách)
                       +-----------------------+
                                   |
                          Click chọn 1 Mentor
                                   |
                                   v
                       +-----------------------+
                       |    VIEWING_DETAIL     | (Đang xem thông tin public mentor)
                       +-----------------------+
                                   |
                        Click "Chọn Lịch Rảnh"
                                   |
                                   v
                       +-----------------------+
                       |    SELECTING_SLOT     | (Đang tải Parent Slots & Candidates)
                       +-----------------------+
                                   |
                         Chọn Candidate Segment
                                   |
                                   v
                       +-----------------------+
                       |    READY_TO_BOOK      | (Chuyển sang Booking Checkout)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | Open Explore Page | Change Filter/Sort | Open Mentor Detail | Open Slot Modal | Select Service & Slot | User Action |
| --- | --- | --- | --- | --- | --- | --- |
| `GET /api/mentors` | ✅ CÓ | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /api/mentors/recommendations` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG (Chỉ gọi ở Dashboard) |
| `GET /api/mentors/{mentorUserId}` | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET .../availability-slots` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG |
| `GET .../candidates` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ✅ Khi chọn Slot + Service |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Không Tìm thấy Mentor (`HTTP 404`)
- **Trang hiển thị**: `/404` hoặc Modal Cảnh báo.
- **Message**: *"Hồ sơ Mentor này không tồn tại hoặc đã tạm ngưng nhận yêu cầu."*
- **Action**: Nút "Quay lại Trang Khám phá".

#### B. Lỗi Segment Bị Đặt Trùng (`HTTP 409`)
- **UI Component**: `CandidateTimeGrid.tsx`.
- **Visual State**: Đổi ô giờ đang chọn sang trạng thái Đỏ/Disabled.
- **Toast Message**: *"Khung giờ này vừa được người khác đặt thành công. Vui lòng chọn khung giờ khác."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['discovery', 'search', queryParams]` | 2 phút | 10 phút | `false` | Thay đổi bộ lọc filter |
| `['discovery', 'recommendations']` | 5 phút | 15 phút | `false` | Mutation nộp khảo sát nhu cầu matching |
| `['public-mentor', mentorId]` | 5 phút | 30 phút | `false` | Tạo booking thành công |
| `['availability-slots', mentorId]` | 0 ms | 5 phút | `true` | Khi mở Modal chọn lịch |
| `['candidates', slotId, serviceId]` | 0 ms | 2 phút | `true` | Khi thay đổi lựa chọn service |
