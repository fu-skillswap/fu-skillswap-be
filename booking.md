# Tài liệu Kỹ thuật SkillSwap - Beta Version 1.0

Tài liệu này tổng hợp chi tiết thiết kế hệ thống, luồng nghiệp vụ và danh sách API của 3 module lõi trong dự án SkillSwap:
1. **Mentor Service** (Dịch vụ Mentor)
2. **Mentor Booking & Session** (Đặt lịch & Buổi học)
3. **Conversation & Chat** (Hộp thư & Trò chuyện Realtime)

Mục tiêu giúp đội ngũ phát triển (đặc biệt là Frontend) hiểu đúng luồng vận hành thực tế của bản Beta V1.0 phục vụ 100–200 người dùng thật.

---

## 1. Tổng quan kiến trúc

### Vai trò các thành phần cốt lõi

*   **MentorService (Dịch vụ)**: Là các gói dịch vụ mentoring do Mentor cấu hình trong hồ sơ portfolio (ví dụ: *"Review CV Backend Intern"*, *"Định hướng học Spring Boot"*). Dịch vụ xác định rõ tiêu đề, mô tả, thời lượng cố định (15, 30, 60, 90 phút) và hình thức miễn phí hay có phí.
*   **AvailabilitySlot (Khung giờ rảnh)**: Là các block thời gian cụ thể (ví dụ: `2026-06-25 19:00 - 20:00`) được sinh ra từ các Rule lịch rảnh của Mentor. Mỗi Slot bắt buộc phải liên kết với một `MentorService` và thừa hưởng thời lượng của Service đó.
*   **Booking (Yêu cầu đặt lịch)**: Đại diện cho giao dịch/yêu cầu đặt chỗ từ Mentee gửi tới Mentor cho một Slot cụ thể. Booking đóng vai trò quản lý hàng đợi (Queue), xử lý các trạng thái thương lượng giữa hai bên trước khi buổi học chính thức diễn ra.
*   **Session (Buổi học thực tế)**: Buổi học thực tế được hệ thống tự động khởi tạo khi một Booking được Mentor chấp nhận (`ACCEPTED`). Session là thực thể chịu trách nhiệm lưu trữ link họp trực tuyến (`meetingLink`), nền tảng (`meetingPlatform`), và thời gian diễn ra thực tế.
*   **Conversation (Cuộc hội thoại)**: Kênh chat trực tiếp 1-1 giữa Mentor và Mentee, tự động tạo lập đồng thời với Session khi Booking được `ACCEPTED`.
*   **Message (Tin nhắn)**: Các tin nhắn lưu trong Conversation, phục vụ trao đổi thông tin trước và sau buổi học.

### Sự khác biệt cần lưu ý cho Frontend

*   **MentorService vs AvailabilitySlot**: Service định nghĩa *nội dung & thời lượng* của buổi học. Slot định nghĩa *thời gian* rảnh trên lịch. Mentee book một *Slot*, và Slot đó bắt buộc thuộc về một *Service* cụ thể.
*   **Booking vs Session**: Booking là trạng thái vòng đời của yêu cầu (PENDING/ACCEPTED/CANCELLED...). Session là thực thể chạy trực tiếp của buổi học.
    > [!IMPORTANT]
    > Trong Beta V1.0, Session **không có Controller public riêng**. Mọi thông tin của Session (nền tảng meeting, meeting link, actual times) được tích hợp trực tiếp vào trường dữ liệu của `BookingResponse` trả về cho Frontend.
*   **REST Chat vs WebSocket Push**: 
    - **REST API** là Source of Truth duy nhất để lưu trữ tin nhắn (`POST /api/me/conversations/{id}/messages`) và lấy lịch sử tin nhắn (`GET`).
    - **WebSocket** chỉ đóng vai trò **Push-only** (truyền tải sự kiện realtime từ Server tới các Client đang active qua kênh `/user/queue/messages`). Frontend tuyệt đối không gửi tin nhắn ngược lên qua WebSocket.

---

## 2. Luồng nghiệp vụ tổng thể (Beta V1.0)

Dưới đây là trình tự nghiệp vụ từ lúc Mentor hoạt động đến khi hoàn tất hoặc hết hạn lịch đặt:

```mermaid
sequenceDiagram
    autonumber
    actor Mentor
    actor Mentee
    participant BE as Backend Server
    participant DB as Database
    participant Scheduler

    Note over Mentor: 1. Mentor được Admin duyệt & Active hồ sơ
    Mentor->>BE: 2. Tạo MentorService (tiêu đề, thời lượng, topic...)
    BE->>DB: Lưu dịch vụ (isActive=true)
    Mentor->>BE: 3. Cấu hình Availability Rule (OPEN, WEEKLY, thứ/giờ)
    BE->>DB: Sinh tự động các AvailabilitySlot tương ứng
    
    Note over Mentee: 4. Mentee browse danh sách Mentor trên Discovery
    Mentee->>BE: 5. Xem chi tiết Mentor & lấy danh sách Slot rảnh
    BE-->>Mentee: Trả về danh sách Slot còn khả dụng (pendingCount < 3)
    
    Mentee->>BE: 6. Tạo Booking request (gửi kèm goal, serviceId, slotId)
    BE->>DB: Kiểm tra quota & Lưu Booking (Status = PENDING)
    BE-->>Mentor: Bắn notification "Yêu cầu đặt lịch mới"

    alt Mentor Từ Chối
        Mentor->>BE: 8a. Reject Booking (gửi kèm rejectReason)
        BE->>DB: Đổi Booking thành REJECTED. Cộng 1 điểm phạt reject của Mentor.
        BE-->>Mentee: Bắn notification "Lịch bị từ chối" + lý do
    else Mentor Đồng Ý (Chấp Nhận)
        Mentor->>BE: 8b. Accept Booking
        activate BE
        BE->>DB: Đổi Booking thành ACCEPTED, khóa Slot (isBooked = true)
        BE->>DB: Tự động đổi các Booking PENDING đồng thời khác của Slot này thành REJECTED (Auto-rejected)
        BE->>DB: Tạo Session (SCHEDULED) & Conversation (ACTIVE)
        BE-->>Mentee: Bắn notification "Lịch đã được chấp nhận"
        BE-->>Mentor: Trả về chi tiết Booking đã duyệt
        deactivate BE
    end

    Note over Mentor, Mentee: Trao đổi & chuẩn bị buổi học
    Mentor->>BE: 9. Cập nhật Meeting Link (Google Meet/Zoom)
    BE->>DB: Cập nhật link vào Session
    BE-->>Mentee: Bắn notification "Thông tin buổi học đã cập nhật"
    Mentee->>BE: 10. Chat trao đổi qua REST, nhận tin realtime qua WebSocket

    Note over Mentor, Mentee: 11. Diễn ra buổi học
    
    alt Hoàn thành buổi học
        Mentor/Mentee->>BE: 12a. Đánh dấu Complete + ghi completionNote
        BE->>DB: Đổi Booking & Session -> COMPLETED. Lưu note bên hoàn thành trước.
        Note over Mentor, Mentee: Bên còn lại vào ghi note bổ sung (chỉ ghi đè trường note của mình nếu đang trống)
    else Hủy lịch (Cancel)
        alt Mentee hủy lịch
            Mentee->>BE: 12b. Cancel Booking (gửi cancelReason)
            BE->>DB: Đổi Booking -> CANCELLED_BY_MENTEE, Session -> CANCELLED.
            Note over DB: Nếu hủy >= 8 tiếng: giải phóng Slot.<br/>Nếu hủy < 8 tiếng: hủy luôn Slot (deactivate).
        else Mentor hủy lịch
            Mentor->>BE: 12c. Cancel Booking (gửi cancelReason)
            BE->>DB: Đổi Booking -> CANCELLED_BY_MENTOR, Session -> CANCELLED, hủy Slot.
            Note over DB: Áp dụng phạt Mentor (hủy < 12h: phạt điểm; hủy < 6h: khóa tài khoản nhận lịch 3 ngày).
        end
    end

    Note over Scheduler: Định kỳ dọn dẹp hàng giờ
    Scheduler->>BE: 13. Quét các Booking PENDING quá thời gian bắt đầu
    BE->>DB: SQL Bulk Update Booking -> REJECTED (Reason = "Hết hạn"), không đụng Slot
```

---

## 3. State Machine (Thiết kế Trạng thái)

### A. BookingStatus

| Trạng thái | Ý nghĩa | Tác nhân chuyển đổi | API chuyển đổi | Ảnh hưởng Session | Ảnh hưởng Conversation | Hiển thị khuyến nghị trên FE |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`PENDING`** | Đang chờ Mentor phản hồi | Mentee tạo lịch | `POST /api/bookings` | Chưa tạo | Chưa tạo | Chờ xác nhận |
| **`ACCEPTED`** | Lịch đặt đã được đồng ý | Mentor duyệt | `POST /api/mentor/bookings/{id}/accept` | Tạo Session (`SCHEDULED`) | Tạo Conversation (`ACTIVE`) | Đã chấp nhận (Hiện link meeting & chat) |
| **`REJECTED`** | Bị từ chối hoặc Tự động từ chối | Mentor từ chối hoặc Hệ thống tự động từ chối (Sibling auto-reject / Scheduler expire) | `POST /api/mentor/bookings/{id}/reject` hoặc Auto-triggered | Không tạo | Không tạo | Đã từ chối (Hiển thị lý do từ chối/hết hạn) |
| **`CANCELLED_BY_MENTEE`** | Mentee chủ động hủy | Mentee hủy | `POST /api/me/bookings/{id}/cancel` | Đổi Session thành `CANCELLED` | Vẫn giữ nguyên | Mentee đã hủy |
| **`CANCELLED_BY_MENTOR`** | Mentor chủ động hủy | Mentor hủy | `POST /api/mentor/bookings/{id}/cancel` | Đổi Session thành `CANCELLED` | Vẫn giữ nguyên | Mentor đã hủy |
| **`COMPLETED`** | Buổi học đã hoàn thành | Mentor hoặc Mentee | `POST /api/me/bookings/{id}/complete` | Đổi Session thành `COMPLETED` | Vẫn giữ nguyên | Đã hoàn thành (Hiển thị Completion Note 2 chiều) |

> [!NOTE]
> Trạng thái `NO_SHOW` đã được định nghĩa trong Enum hệ thống nhưng hiện tại chưa được kích hoạt logic nghiệp vụ tự động trong các Controller chính của Beta V1.0.

### B. SessionStatus

*   **`SCHEDULED`**: Trạng thái ban đầu ngay khi Booking chuyển sang `ACCEPTED`.
*   **`COMPLETED`**: Đồng bộ chuyển sang khi Booking được đánh dấu hoàn thành (`completeBooking`).
*   **`CANCELLED`**: Đồng bộ chuyển sang khi Booking bị hủy bởi Mentee hoặc Mentor (`cancelBookingByMentee`/`cancelBookingByMentor`).
*   **Đồng bộ dữ liệu (Sync)**: Session đồng bộ 100% với Booking thông qua cơ chế xử lý Transaction nội bộ tại backend. Frontend không tương tác trực tiếp với Session ID hay Session Status riêng, tất cả đều được cấu trúc hóa trong phản hồi của Booking (`BookingResponse`).

### C. ConversationStatus

*   **`ACTIVE`**: Khởi tạo ngay khi booking được `ACCEPTED` để cho hai bên bắt đầu trao đổi.
*   **`LOCKED`**: Có trong enum thiết kế, nhưng **Beta V1.0 chưa áp dụng logic khóa chat**. Kể cả khi Booking bị Hủy (`CANCELLED`) hoặc Hoàn thành (`COMPLETED`), hai bên vẫn có thể gửi tin nhắn bình thường trong cuộc hội thoại đã tạo.

---

## 4. Hành trình tích hợp API của Frontend (User Journey)

Frontend nên gọi các API theo đúng thứ tự hành trình người dùng dưới đây để đảm bảo tính nhất quán dữ liệu:

### Nhóm A. Quản lý Mentor Service (Role: Mentor)

Cấu hình các dịch vụ mentoring trước khi mở lịch rảnh.

#### 1. Lấy danh sách dịch vụ của tôi
*   **Method + Path**: `GET /api/me/mentor-services`
*   **Ai được gọi**: Mentor
*   **Request body/query**: Không có
*   **Response DTO chính**: `List<MentorServiceResponse>`
*   **Business Rule**: Trả về các dịch vụ xếp theo ngày tạo tăng dần.
*   **Error case**: `403 Forbidden` (Nếu không có role `MENTOR`).

#### 2. Chi tiết một dịch vụ của tôi
*   **Method + Path**: `GET /api/me/mentor-services/{serviceId}`
*   **Ai được gọi**: Mentor
*   **Response DTO**: `MentorServiceResponse`

#### 3. Tạo dịch vụ mentoring mới
*   **Method + Path**: `POST /api/me/mentor-services`
*   **Ai được gọi**: Mentor
*   **Request body**: `MentorServiceUpsertRequest` (Xem mô tả trường ở mục 7).
*   **Response DTO**: `MentorServiceResponse`
*   **Business Rule**: 
    - Chỉ cho phép Mentor đã được phê duyệt (`verifiedAt != null` và status = `ACTIVE`).
    - Phải hoàn thiện profile (headline, expertiseDescription, teachingMode, sessionDuration) trước.
    - Thời lượng (`durationMinutes`) bắt buộc phải là một trong các giá trị: `15`, `30`, `60`, `90`.
    - Dịch vụ miễn phí (`isFree = true`) bắt buộc giá phải bằng `0`. Dịch vụ có phí phải có giá lớn hơn `0`. Tiền tệ mặc định luôn là `VND`.
*   **Error case**: `409 Conflict` (Chưa verify profile), `400 Bad Request` (Sai thời lượng/Giá không hợp lệ).

#### 4. Cập nhật dịch vụ mentoring
*   **Method + Path**: `PUT /api/me/mentor-services/{serviceId}`
*   **Request body**: `MentorServiceUpsertRequest`
*   **Response DTO**: `MentorServiceResponse`

#### 5. Bật/Tắt dịch vụ mentoring
*   **Method + Path**: `PATCH /api/me/mentor-services/{serviceId}/active`
*   **Request body**: `MentorServiceActiveRequest` (`{ "active": true/false }`)
*   **Response DTO**: `MentorServiceResponse`

#### 6. Xóa mềm dịch vụ mentoring
*   **Method + Path**: `DELETE /api/me/mentor-services/{serviceId}`
*   **Ai được gọi**: Mentor
*   **Business Rule**: Chuyển trạng thái `active = false`. Không xóa vật lý khỏi database để bảo toàn lịch sử booking cũ.

---

### Nhóm B. Quản lý Lịch rảnh - Availability (Role: Mentor & Mentee)

#### 1. Tạo mới Availability Rule (Tạo nguồn sinh slot)
*   **Method + Path**: `POST /api/mentor/availability-rules`
*   **Ai được gọi**: Mentor
*   **Request body**: `UpsertAvailabilityRuleRequest`
*   **Response DTO**: `AvailabilityRuleResponse` (Status code `201 Created`)
*   **Business Rule**: 
    - `serviceId` truyền vào phải hợp lệ và đang hoạt động (`active = true`).
    - Lịch sinh ra không được trùng lặp hoặc chồng lấn lên các rule có sẵn.
*   **Error case**: `409 Conflict` (Chồng lấn lịch), `404 Not Found` (Không thấy dịch vụ).

#### 2. Xem các Rule cấu hình của tôi
*   **Method + Path**: `GET /api/mentor/availability-rules`
*   **Ai được gọi**: Mentor
*   **Response DTO**: `List<AvailabilityRuleResponse>`

#### 3. Xóa mềm Rule lịch rảnh
*   **Method + Path**: `DELETE /api/mentor/availability-rules/{ruleId}`
*   **Ai được gọi**: Mentor
*   **Business Rule**: Vô hiệu hóa rule. Các Slot đã sinh và đã được đặt trước đó vẫn được giữ nguyên để bảo đảm trải nghiệm của Mentee.

#### 4. Lấy danh sách Slot rảnh công khai của Mentor (Dùng cho Discovery)
*   **Method + Path**: `GET /api/mentors/{mentorUserId}/availability`
*   **Ai được gọi**: Mentee (Hoặc bất kỳ User đã đăng nhập)
*   **Query Params**: `fromDate` (LocalDate), `toDate` (LocalDate).
*   **Response DTO**: `List<MentorAvailabilitySlotResponse>`
*   **Business Rule**: 
    - Ẩn các slot trong quá khứ.
    - Ẩn các slot đã có booking `ACCEPTED`.
    - Ẩn các slot đã đạt hàng đợi tối đa (`pendingRequestCount >= 3`).
*   **API tiếp theo**: `POST /api/bookings` để gửi yêu cầu đặt lịch.

---

### Nhóm C. Quản lý Booking (Role: Mentee & Mentor)

#### 1. Mentee tạo yêu cầu đặt lịch mới
*   **Method + Path**: `POST /api/bookings`
*   **Ai được gọi**: Mentee
*   **Request body**: `CreateBookingRequest`
*   **Response DTO**: `BookingResponse`
*   **Business Rule**:
    - Mentee không được tự book chính mình.
    - Tài khoản Mentee phải ở trạng thái hoạt động (`UserStatus.ACTIVE`) và đã hoàn tất hồ sơ học thuật (`StudentProfile`).
    - Mentee không được có quá **5 yêu cầu PENDING** trong toàn hệ thống cùng lúc.
    - Slot được chọn không được có quá **3 yêu cầu PENDING** cùng lúc.
    - Slot được chọn phải thuộc về đúng `serviceId` tương ứng.
    - Không cho phép book trùng giờ với một lịch học khác của Mentee đã được `ACCEPTED`.
*   **Error case**: `409 Conflict` (Vượt hạn mức pending, trùng lịch học, slot đã đầy hàng đợi).
*   **API tiếp theo**: `GET /api/me/bookings` để theo dõi trạng thái.

#### 2. Xem danh sách booking của tôi
*   **Method + Path**: `GET /api/me/bookings`
*   **Ai được gọi**: Mọi User
*   **Query Params**: 
    - `role` (Bắt buộc: `MENTEE` hoặc `MENTOR`)
    - `status` (Tùy chọn lọc theo trạng thái: `PENDING`, `ACCEPTED`, `COMPLETED`...)
    - `page`, `size`, `sortBy`, `sortDirection`
*   **Response DTO**: `PageResponse<BookingResponse>`

#### 3. Xem chi tiết một booking
*   **Method + Path**: `GET /api/me/bookings/{bookingId}`
*   **Ai được gọi**: Mentor hoặc Mentee sở hữu booking đó.
*   **Response DTO**: `BookingResponse`

#### 4. Mentor Chấp nhận yêu cầu đặt lịch
*   **Method + Path**: `POST /api/mentor/bookings/{bookingId}/accept`
*   **Ai được gọi**: Mentor sở hữu lịch
*   **Request body**: `AcceptBookingRequest` (`{ "mentorResponseNote": "..." }` - Tùy chọn)
*   **Response DTO**: `BookingResponse`
*   **Business Rule**: 
    - Đổi trạng thái Booking hiện tại thành `ACCEPTED`.
    - Đổi trạng thái Slot thành `isBooked = true`.
    - **Tự động từ chối (Auto-reject)** toàn bộ các booking `PENDING` khác đang xếp hàng cùng slot đó, ghi nhận lý do hệ thống: *"Mentor đã từ chối booking của bạn vì đã có lịch trình khác."*.
    - Tạo Session trực tuyến và Hộp thư chat 1-1.
*   **API tiếp theo**: Cập nhật link liên kết phòng học (`PATCH /api/mentor/bookings/{bookingId}/meeting-link`).

#### 5. Mentor Từ chối yêu cầu đặt lịch
*   **Method + Path**: `POST /api/mentor/bookings/{bookingId}/reject`
*   **Ai được gọi**: Mentor sở hữu lịch
*   **Request body**: `RejectBookingRequest` (`{ "rejectReason": "Lý do từ chối bắt buộc", "mentorResponseNote": "Ghi chú thêm" }`)
*   **Response DTO**: `BookingResponse`
*   **Business Rule**: Đổi trạng thái thành `REJECTED`, ghi nhận lý do. Điểm thống kê tỉ lệ từ chối của Mentor tăng 1.

#### 6. Mentee Hủy lịch hẹn
*   **Method + Path**: `POST /api/me/bookings/{bookingId}/cancel`
*   **Ai được gọi**: Mentee sở hữu lịch
*   **Request body**: `CancelBookingRequest` (`{ "cancelReason": "Lý do hủy bắt buộc" }`)
*   **Response DTO**: `BookingResponse`
*   **Business Rule (Giải phóng Slot)**:
    - Nếu Booking đang `PENDING`: Hủy tự do, không ảnh hưởng Slot rảnh của Mentor.
    - Nếu Booking đang `ACCEPTED`: 
        - Hủy **trước giờ học >= 8 tiếng**: Slot được giải phóng về trạng thái rảnh (`isBooked = false`), mentee khác có thể vào book tiếp.
        - Hủy **sát giờ học < 8 tiếng**: Hủy luôn Slot học đó (`isBooked = false`, `isActive = false`) để tránh ảnh hưởng lịch trình của Mentor.

#### 7. Mentor Hủy lịch hẹn (Đã ACCEPTED)
*   **Method + Path**: `POST /api/mentor/bookings/{bookingId}/cancel`
*   **Ai được gọi**: Mentor sở hữu lịch
*   **Request body**: `CancelBookingRequest` (`{ "cancelReason": "Lý do hủy bắt buộc" }`)
*   **Response DTO**: `BookingResponse`
*   **Business Rule (Áp phạt Mentor)**:
    - Chỉ được hủy lịch đã `ACCEPTED` khi chưa đến giờ học. Hủy xong slot đó sẽ bị đóng hoàn toàn.
    - Hủy **sát giờ từ 6h đến 12h trước khi bắt đầu**: Mentor bị cộng `0.5` điểm phạt hủy muộn (`lateCancellationPenaltyPoints`).
    - Hủy **cực sát giờ dưới 6h trước khi bắt đầu**: Tài khoản Mentor bị khóa nhận lịch mới tự động trong vòng 3 ngày (`bookingSuspendedUntil = now + 3 days`).

#### 8. Mentor Cập nhật thông tin phòng học trực tuyến (Meeting Link)
*   **Method + Path**: `PATCH /api/mentor/bookings/{bookingId}/meeting-link`
*   **Ai được gọi**: Mentor sở hữu lịch
*   **Request body**: `SaveMeetingLinkRequest` (Mô tả ở mục 7)
*   **Response DTO**: `BookingResponse`
*   **Business Rule**: 
    - Chỉ áp dụng khi lịch ở trạng thái `ACCEPTED`.
    - `meetingLink` phải là đường dẫn URL hợp lệ bắt đầu bằng `http://` hoặc `https://`.
*   **API tiếp theo**: Frontend reload lại chi tiết booking để lấy giá trị mới nhất.

#### 9. Đánh dấu hoàn thành buổi học (Note hoàn thành 2 chiều)
*   **Method + Path**: `POST /api/me/bookings/{bookingId}/complete`
*   **Ai được gọi**: Mentor hoặc Mentee sở hữu lịch
*   **Request body**: `CompleteBookingRequest` (`{ "completionNote": "Ghi chú phản hồi/tổng kết buổi học" }` - Tùy chọn)
*   **Response DTO**: `BookingResponse`
*   **Business Rule**:
    - **Lần gọi đầu tiên (Người thứ nhất bấm hoàn thành)**: Booking chuyển từ `ACCEPTED` sang `COMPLETED`. Session chuyển sang `COMPLETED`. Lưu note của người này vào trường tương ứng (`mentorNote` hoặc `menteeNote`). Tính toán chỉ số thống kê hoàn thành cho Mentor.
    - **Lần gọi thứ hai (Người còn lại bổ sung note)**: Cho phép gọi lại API này khi trạng thái đã là `COMPLETED`. Hệ thống chỉ lưu thêm note của người thứ hai vào trường tương ứng (với điều kiện trường note của họ đang trống). Không tăng chỉ số thống kê lần 2, không gọi lại logic Session, tránh xung đột dữ liệu.
*   **Error case**: `409 Conflict` (Nếu cố gắng sửa note hoàn thành khi đã ghi nhận trước đó).

---

### Nhóm D. Quản lý Session
*   **Chú ý**: Không có endpoint riêng cho Session. Frontend lấy toàn bộ trạng thái thực tế từ API Chi tiết Booking (`GET /api/me/bookings/{bookingId}`).

---

### Nhóm E. Tin nhắn & Hộp thư (Role: Mentor & Mentee)

#### 1. Lấy danh sách hộp thư thoại (Conversations)
*   **Method + Path**: `GET /api/me/conversations`
*   **Ai được gọi**: Mentor & Mentee đã đăng nhập
*   **Query Params**: `page`, `size` (Kế thừa từ BasePageRequest).
*   **Response DTO**: `PageResponse<ConversationResponse>`
*   **Business Rule**: Trả về danh sách hội thoại của User hiện tại. Danh sách được sắp xếp theo thời điểm có tin nhắn mới nhất giảm dần (`lastMessageAt DESC`). Trả về thông tin của đối phương (`otherUserId`, `otherUserName`, `otherUserAvatarUrl`).

#### 2. Lấy danh sách tin nhắn trong cuộc hội thoại (Message History)
*   **Method + Path**: `GET /api/me/conversations/{conversationId}/messages`
*   **Ai được gọi**: Chỉ các thành viên tham gia cuộc hội thoại.
*   **Query Params**: `page`, `size` (Kế thừa từ BasePageRequest).
*   **Response DTO**: `PageResponse<MessageResponse>`
*   **Business Rule**: 
    - Kết quả trả về danh sách tin nhắn sắp xếp theo thời gian tạo **giảm dần (Mới nhất ở đầu)**.
    - Trả về trường `isMine` (true/false) dựa vào token của người gọi để Frontend phân biệt bong bóng chat bên trái/phải.
*   **Error case**: `403 Forbidden` (Nếu User không thuộc conversation này).

#### 3. Gửi tin nhắn mới (REST API - Source of Truth)
*   **Method + Path**: `POST /api/me/conversations/{conversationId}/messages`
*   **Ai được gọi**: Chỉ các thành viên tham gia cuộc hội thoại.
*   **Request body**: `SendMessageRequest` (`{ "content": "Nội dung chat" }`)
*   **Response DTO**: `MessageResponse`
*   **Business Rule**: 
    - Chỉ cho phép gửi tin nhắn văn bản thuần túy (Độ dài tối đa 2000 ký tự).
    - Sau khi tin nhắn được lưu thành công vào Database và Transaction được commit, hệ thống sẽ tự động phát một sự kiện realtime qua WebSocket đến tất cả các thành viên đang kết nối.
*   **Error case**: `400 Bad Request` (Nội dung trống hoặc vượt quá 2000 ký tự).

#### 4. Kết nối Realtime WebSocket Push (STOMP Client)
*   **Endpoint kết nối**: `/ws` (Hỗ trợ dự phòng bằng SockJS)
*   **Cơ chế Authentication**: Truyền token JWT qua query parameter khi thiết lập handshake (ví dụ: `ws://domain/ws?token=JWT_TOKEN`).
*   **Đăng ký nhận tin (Subscribe Destination)**: `/user/queue/messages`
*   **Định dạng Event nhận về**: `ChatMessageEvent` (Mô tả chi tiết ở mục 7).
*   **Business Rule quan trọng cho FE**:
    - WebSocket **chỉ nhận tin (Push-only)**. Không gửi tin nhắn qua kênh này.
    - Sự kiện nhận về không chứa trường `isMine`. Frontend phải tự so sánh `senderId` trong Event với `currentUserId` của client để hiển thị bong bóng chat phù hợp.

---

## 5. Bảng API tổng hợp (Sắp xếp theo trình tự tích hợp)

| Order | Module | Method | Endpoint | Role | Purpose | Request DTO | Response DTO | Important Rules | Notes for FE |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | Mentor | `POST` | `/api/me/mentor-services` | Mentor | Tạo dịch vụ mentoring | `MentorServiceUpsertRequest` | `MentorServiceResponse` | Phải hoàn thành profile trước. Gói Free thì giá = 0. | Lưu lại `serviceId`. |
| **2** | Mentor | `GET` | `/api/me/mentor-services` | Mentor | Danh sách dịch vụ của tôi | N/A | `List<MentorServiceResponse>` | Sắp xếp theo ngày tạo tăng dần. | Dùng render quản lý dịch vụ. |
| **3** | Booking | `POST` | `/api/mentor/availability-rules` | Mentor | Tạo rule lịch rảnh để sinh slot | `UpsertAvailabilityRuleRequest` | `AvailabilityRuleResponse` | RuleType OPEN/CLOSED. Không chồng lấn lịch. | Rule chỉ cấu hình, Slot tự sinh ra. |
| **4** | Discovery | `GET` | `/api/mentors` | Mentee | Tìm kiếm & Duyệt Mentor | `MentorDiscoverySearchRequest` | `PageResponse<MentorDiscoveryCardResponse>` | Lọc theo topic, relevance, status. | Hiển thị danh sách trang chủ. |
| **5** | Discovery | `GET` | `/api/mentors/{mentorUserId}/availability` | Mentee | Lấy các Slot rảnh của Mentor | `AvailabilityQueryRequest` | `List<MentorAvailabilitySlotResponse>` | Ẩn slot quá khứ, slot đã đặt hoặc đầy hàng chờ (3 request). | Dùng hiển thị lịch để Mentee chọn. |
| **6** | Booking | `POST` | `/api/bookings` | Mentee | Đặt lịch hẹn | `CreateBookingRequest` | `BookingResponse` | Mentee không quá 5 pending. Slot không quá 3 pending. | Trạng thái ban đầu: `PENDING`. |
| **7** | Booking | `GET` | `/api/me/bookings` | Mọi User | Danh sách lịch hẹn | `BookingListRequest` (role, status) | `PageResponse<BookingResponse>` | Phân trang, sắp xếp theo thời gian học. | Cần truyền đúng query `role=MENTEE` hoặc `MENTOR`. |
| **8** | Booking | `POST` | `/api/mentor/bookings/{id}/accept` | Mentor | Phê duyệt lịch hẹn | `AcceptBookingRequest` | `BookingResponse` | Khóa slot. Tự động từ chối các yêu cầu pending khác cùng slot. | Sinh ra Session & Chat Conversation. |
| **9** | Booking | `POST` | `/api/mentor/bookings/{id}/reject` | Mentor | Từ chối lịch hẹn | `RejectBookingRequest` | `BookingResponse` | Lý do từ chối bắt buộc. Tăng chỉ số từ chối của Mentor. | |
| **10** | Booking | `PATCH` | `/api/mentor/bookings/{id}/meeting-link` | Mentor | Cập nhật link phòng học | `SaveMeetingLinkRequest` | `BookingResponse` | Chỉ cho phép khi trạng thái `ACCEPTED`. Link phải đúng định dạng URL. | FE lấy link phòng từ DTO này. |
| **11** | Chat | `GET` | `/api/me/conversations` | Mọi User | Xem danh sách hộp thư chat | `BasePageRequest` | `PageResponse<ConversationResponse>` | Sắp xếp theo tin nhắn cuối giảm dần (`lastMessageAt DESC`). | |
| **12** | Chat | `GET` | `/api/me/conversations/{id}/messages` | Mọi User | Xem lịch sử chat | `BasePageRequest` | `PageResponse<MessageResponse>` | Trả về sắp xếp tin nhắn mới nhất ở đầu (DESC). | FE cần đảo ngược (reverse) mảng để hiển thị từ cũ đến mới. |
| **13** | Chat | `POST` | `/api/me/conversations/{id}/messages` | Mọi User | Gửi tin nhắn mới | `SendMessageRequest` | `MessageResponse` | Gửi dạng REST thuần túy. Tối đa 2000 ký tự. | WebSocket sẽ tự động đẩy tin nhắn cho các bên. |
| **14** | Chat | `Connect` | `/ws` | Mọi User | Kết nối Socket realtime | N/A | N/A | Truyền token qua query handshake. Subscribe: `/user/queue/messages`. | Push-only. Reconnect phải gọi REST đồng bộ lại tin nhắn. |
| **15** | Booking | `POST` | `/api/me/bookings/{id}/cancel` | Mentee | Hủy lịch (Mentee) | `CancelBookingRequest` | `BookingResponse` | Hủy trước >= 8h: giải phóng Slot rảnh. Hủy dưới < 8h: đóng slot. | |
| **16** | Booking | `POST` | `/api/mentor/bookings/{id}/cancel` | Mentor | Hủy lịch (Mentor) | `CancelBookingRequest` | `BookingResponse` | Chỉ hủy lịch đã `ACCEPTED`. Áp dụng phạt nếu hủy muộn (<12h và <6h). | Slot bị đóng hoàn toàn. |
| **17** | Booking | `POST` | `/api/me/bookings/{id}/complete` | Mọi User | Đánh dấu hoàn thành | `CompleteBookingRequest` | `BookingResponse` | Người thứ 2 vào ghi note bổ sung nếu note của họ đang trống. | Cả hai bên đều có quyền gọi để ghi nhận note. |

---

## 6. Business Rules quan trọng (Beta V1.0)

1.  **Điều kiện quản lý của Mentor**: Tài khoản Mentor phải ở trạng thái hoạt động (`ACTIVE`), đã qua xác thực (`verifiedAt != null`), hoàn tất đầy đủ thông tin hồ sơ học thuật và hồ sơ năng lực thì mới có thể tạo dịch vụ hoặc cấu hình lịch rảnh.
2.  **Khống chế thời lượng**: Dịch vụ chỉ được phép cấu hình thời lượng là `15`, `30`, `60`, hoặc `90` phút. Lịch rảnh được sinh ra sẽ tự động chia theo các khung giờ bằng đúng thời lượng này.
3.  **Ràng buộc giá cả**: 
    - Gói miễn phí (`isFree = true`): Giá trị bắt buộc phải là `0 VND`.
    - Gói có phí (`isFree = false`): Giá trị bắt buộc phải lớn hơn `0 VND`.
4.  **Tự book chính mình**: Hệ thống chặn hoàn toàn việc Mentee đặt lịch với Mentor có cùng `userId`.
5.  **Quota hàng đợi Booking (Queue Limits)**:
    - Mỗi Slot rảnh chỉ chấp nhận tối đa **3 yêu cầu đặt lịch ở trạng thái `PENDING`** xếp hàng cùng lúc. Khi đạt giới hạn này, slot sẽ bị ẩn khỏi danh sách hiển thị trên Discovery.
    - Mỗi Mentee chỉ được phép sở hữu tối đa **5 yêu cầu đặt lịch ở trạng thái `PENDING`** đang chờ phản hồi trên toàn hệ thống.
6.  **Chống trùng lịch (Overlap Protection)**:
    - Chặn Mentee gửi yêu cầu đặt lịch nếu khung giờ đó trùng với một lịch học khác của Mentee đã được xác nhận (`ACCEPTED`).
    - Khi một Booking được Mentor `ACCEPTED`, hệ thống tự động đổi trạng thái của tất cả các Booking PENDING khác cùng slot đó sang `REJECTED` với lý do hệ thống rõ ràng.
7.  **Tự động hết hạn lịch hẹn (Scheduler Expire)**:
    - Định kỳ mỗi **30 phút**, `BookingCleanupScheduler` sẽ quét toàn bộ database.
    - Các Booking ở trạng thái `PENDING` có thời gian bắt đầu học nhỏ hơn thời gian hiện tại (`requestedStartTime < now`) sẽ bị tự động chuyển sang trạng thái `REJECTED` với lý do: *"Yêu cầu đặt lịch đã tự động hết hạn do vượt quá thời gian bắt đầu."*.
    - Thao tác này được tối ưu bằng câu lệnh SQL Bulk Update trực tiếp dưới DB để tránh xung đột dữ liệu (Lost Update) và cải thiện hiệu năng VPS yếu. Không đụng đến Slot rảnh hay Session liên quan.
8.  **Chính sách phạt hủy lịch của Mentor**:
    - Hủy sát giờ bắt đầu học từ **6h đến 12h**: Cộng `0.5` điểm phạt tích lũy.
    - Hủy cực sát giờ **dưới 6h**: Tự động khóa tính năng nhận lịch mới của Mentor trong vòng 3 ngày (`bookingSuspendedUntil = now + 3 days`).
9.  **Hủy lịch giải phóng slot của Mentee**:
    - Mentee hủy lịch đã được chấp nhận trước giờ học **>= 8 tiếng**: Slot rảnh được mở lại để người khác book.
    - Hủy **dưới 8 tiếng**: Hủy hoàn toàn slot học (chuyển Slot sang inactive) để đảm bảo quyền lợi cho Mentor.
10. **Ghi nhận hoàn tất 2 chiều**: Buổi học chỉ có thể bấm hoàn thành sau khi đã qua thời gian bắt đầu (`requestedStartTime`). Cho phép cả hai bên tự ghi nhận ý kiến tổng kết (`completionNote`) một cách độc lập thông qua API. Hệ thống bảo vệ dữ liệu bằng cách không cho phép ghi đè nếu note của bên đó đã tồn tại.

---

## 7. Các DTO quan trọng (Frontend cần lưu ý)

### 1. `CreateBookingRequest`
```json
{
  "mentorUserId": "UUID (Bắt buộc)",
  "availabilitySlotId": "UUID (Bắt buộc)",
  "serviceId": "UUID (Bắt buộc - Dùng để liên kết gói dịch vụ cụ thể)",
  "learningGoalTitle": "String (Tối đa 200 ký tự - Tiêu đề mục tiêu học tập)",
  "learningGoalDescription": "String (Tối đa 2000 ký tự - Mô tả chi tiết mong muốn hỗ trợ)"
}
```

### 2. `BookingResponse`
```json
{
  "bookingId": "UUID",
  "sessionId": "UUID (Bản Beta V1.0 đang trả về giá trị trùng khớp với bookingId)",
  "sessionStatus": "BookingStatus (Trạng thái buổi học đồng bộ với trạng thái booking)",
  "mentorUserId": "UUID",
  "mentorDisplayName": "String",
  "mentorAvatarUrl": "String (Có thể null)",
  "menteeUserId": "UUID",
  "menteeDisplayName": "String",
  "menteeAvatarUrl": "String (Có thể null)",
  "slotId": "UUID",
  "serviceId": "UUID",
  "serviceTitle": "String",
  "status": "BookingStatus (PENDING, ACCEPTED, REJECTED, CANCELLED_BY_MENTEE...)",
  "learningGoalTitle": "String",
  "learningGoalDescription": "String (Có thể null)",
  "mentorResponseNote": "String (Phản hồi của mentor khi duyệt)",
  "rejectReason": "String (Lý do từ chối/hết hạn)",
  "cancelReason": "String (Lý do hủy lịch)",
  "meetingPlatform": "GOOGLE_MEET hoặc ZOOM (Có thể null)",
  "meetingLink": "String (Link phòng học trực tuyến - Có thể null)",
  "location": "String (Địa điểm offline - Có thể null)",
  "requestedStartTime": "ISO-8601 DateTime",
  "requestedEndTime": "ISO-8601 DateTime",
  "actualStartTime": "ISO-8601 DateTime (Có thể null)",
  "actualEndTime": "ISO-8601 DateTime (Có thể null)",
  "acceptedAt": "ISO-8601 DateTime (Có thể null)",
  "rejectedAt": "ISO-8601 DateTime (Có thể null)",
  "cancelledAt": "ISO-8601 DateTime (Có thể null)",
  "completedAt": "ISO-8601 DateTime (Có thể null)",
  "mentorNote": "String (Ghi chú hoàn thành của mentor - Có thể null)",
  "menteeNote": "String (Ghi chú hoàn thành của mentee - Có thể null)",
  "createdAt": "ISO-8601 DateTime",
  "updatedAt": "ISO-8601 DateTime"
}
```

### 3. `UpsertAvailabilityRuleRequest`
```json
{
  "ruleType": "OPEN hoặc CLOSED",
  "serviceId": "UUID (Gói dịch vụ liên kết)",
  "repeatType": "NONE, DAILY, hoặc WEEKLY",
  "daysOfWeek": ["MONDAY", "WEDNESDAY"],
  "effectiveFrom": "YYYY-MM-DD",
  "effectiveTo": "YYYY-MM-DD (Có thể null)",
  "startTime": "HH:mm",
  "endTime": "HH:mm",
  "note": "String (Tối đa 200 ký tự)"
}
```

### 4. `ConversationResponse`
```json
{
  "id": "UUID (Mã cuộc hội thoại)",
  "sourceType": "BOOKING",
  "sourceId": "UUID (Liên kết trực tiếp với bookingId tương ứng)",
  "type": "DIRECT",
  "status": "ACTIVE hoặc LOCKED",
  "otherUserId": "UUID (userId của đối phương)",
  "otherUserName": "String",
  "otherUserAvatarUrl": "String",
  "lastMessageContent": "String (Tin nhắn mới nhất)",
  "lastMessageAt": "ISO-8601 DateTime",
  "createdAt": "ISO-8601 DateTime"
}
```

### 5. `MessageResponse`
```json
{
  "id": "UUID",
  "conversationId": "UUID",
  "senderId": "UUID (Có thể null nếu là tin nhắn hệ thống)",
  "senderName": "String (Trả về 'Hệ thống' nếu senderId null)",
  "messageType": "TEXT",
  "content": "String",
  "createdAt": "ISO-8601 DateTime",
  "isMine": "Boolean (Xác định tin nhắn này có phải của User hiện tại gửi hay không)"
}
```

### 6. `ChatMessageEvent` (WebSocket Payload)
```json
{
  "conversationId": "UUID",
  "messageId": "UUID",
  "senderId": "UUID",
  "senderName": "String",
  "messageType": "TEXT",
  "content": "String",
  "createdAt": "ISO-8601 DateTime"
}
```

---

## 8. Những điểm Frontend cần đặc biệt lưu ý

1.  **Duyệt lịch sử chat**: API lịch sử tin nhắn trả về danh sách sắp xếp theo thời gian giảm dần (`DESC`). Frontend cần **đảo ngược (reverse)** lại mảng tin nhắn trước khi hiển thị lên giao diện chat từ cũ đến mới.
2.  **So sánh senderId trên Socket**: Sự kiện `ChatMessageEvent` gửi từ WebSocket **không chứa trường `isMine`**. Frontend bắt buộc phải tự so sánh `senderId` trong payload nhận được với `currentUserId` của tài khoản hiện tại để quyết định đặt tin nhắn bên trái hay bên phải màn hình.
3.  **Lọc trùng tin nhắn (De-duplication)**: Khi gửi tin nhắn qua REST API, phản hồi trả về cũng chứa `MessageResponse`. Đồng thời hệ thống cũng bắn tin nhắn đó qua WebSocket. Để tránh tin nhắn bị hiển thị hai lần (duplicate), Frontend nên quản lý danh sách tin nhắn bằng một cấu trúc dữ liệu theo dõi theo `id` (hoặc kiểm tra nếu `messageId` đã tồn tại trong danh sách thì bỏ qua).
4.  **Khi mất kết nối Socket (Reconnect)**: Khi STOMP client bị ngắt kết nối và kết nối lại, Frontend phải **gọi lại REST API lấy lịch sử tin nhắn** để đồng bộ những tin nhắn bị bỏ lỡ trong khoảng thời gian offline. WebSocket không hỗ trợ gửi lại tin nhắn cũ tự động.
5.  **Xử lý hiển thị trạng thái "Hết hạn"**: Đối với các Booking bị Scheduler tự động hủy do quá hạn, trường `rejectReason` trả về sẽ chứa giá trị *"Yêu cầu đặt lịch đã tự động hết hạn do vượt quá thời gian bắt đầu."*. Giao diện Frontend nên bắt chuỗi này hoặc check `status = REJECTED` kết hợp `rejectedAt != null` và `rejectReason` để hiển thị nhãn thân thiện là *"Lịch hẹn hết hạn"* thay vì *"Mentor từ chối"*.
6.  **Cung cấp Meeting Link**: Trường `meetingLink` và `meetingPlatform` nằm trong `BookingResponse` nhưng thực chất được lưu tại `Session` phía backend. Mentor cập nhật link qua API `meeting-link` thành công thì Frontend nên chủ động reload lại chi tiết booking để lấy giá trị mới nhất.
7.  **Giới hạn số lượng Pending**: Trên màn hình chọn slot đặt lịch, Frontend nên chặn người dùng không cho phép bấm đặt lịch nếu phát hiện tài khoản Mentee đó đang có sẵn 5 yêu cầu `PENDING` (cần hiển thị thông báo yêu cầu họ chờ Mentor phản hồi hoặc hủy lịch cũ).

---

## 9. Gap / Risk còn lại (Kế hoạch defer)

### A. Must fix before Beta
*   **Chưa có cơ chế đếm tin nhắn chưa đọc (Unread Count)**: Database hiện chưa thiết kế bảng để lưu trạng thái đọc (Read receipt) hoặc số lượng tin nhắn chưa đọc của từng Participant.

### B. Should fix before Beta
*   **Chưa có Rate Limit cho Chat**: Người dùng có thể spam gửi hàng trăm tin nhắn REST liên tục gây tràn băng thông VPS yếu.
*   **Thiếu trạng thái `EXPIRED` riêng biệt**: Hiện tại lịch hết hạn vẫn dùng chung trạng thái `REJECTED` kèm text lý do hệ thống.

### C. Defer V1.1
*   **Khóa Chat sau khi hoàn thành**: Chưa tự động đóng hộp thoại chat khi lịch hẹn kết thúc (`CANCELLED` hoặc `COMPLETED`).
*   **Trình trạng thái `NO_SHOW`**: Chưa có logic phát hiện và xử lý tự động khi một trong hai bên không xuất hiện trong buổi học.
*   **Instant Session**: Chưa hỗ trợ kết nối dạy học tức thì không qua đặt lịch trước.
*   **ClassOffering**: Chưa có lớp học nhiều người tham gia.
*   **Thanh toán / Ví tiền (Payment / Wallet)**: Toàn bộ hệ thống Beta V1.0 chạy theo cơ chế cam kết trao đổi kỹ năng không dùng tiền mặt.

---

## 10. Cơ sở dữ liệu & Tối ưu hóa chỉ mục (Database & Indexing)

Để đảm bảo hiệu năng tối ưu trên môi trường VPS có tài nguyên giới hạn và tính chính xác của dữ liệu trong các luồng nghiệp vụ đồng thời của bản Beta V1.0, cấu trúc database đã được tinh chỉnh như sau:

### A. Tương thích ngược và Null-Safety trong kiểm thử (JPA Level)
*   Do tích hợp mô hình Service-Centric, các cột `service_id` đã được bổ sung vào các bảng `mentor_availability_slots`, `bookings`, và `sessions`.
*   Để không làm hỏng (break) các integration test di sản (vốn khởi tạo dữ liệu không có gói dịch vụ), quan hệ `service` trong các thực thể [Booking.java](file:///e:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/modules/booking/domain/Booking.java), [MentorAvailabilitySlot.java](file:///e:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/modules/booking/domain/MentorAvailabilitySlot.java) và [Session.java](file:///e:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/modules/session/domain/Session.java) được định nghĩa là `optional = true` và `nullable = true` ở mức độ Mapping JPA/Hibernate.
*   *Lưu ý*: Mọi API tạo mới Booking hoặc Slot từ Frontend gửi lên đều được kiểm tra nghiệp vụ nghiêm ngặt ở tầng Service, đảm bảo `serviceId` luôn hợp lệ và thuộc sở hữu của Mentor.

### B. Chỉ mục tối ưu hóa hiệu năng (Flyway Migrations)
1.  **Dọn dẹp Booking quá hạn (Scheduler)**:
    *   **Migration**: `V15__add_booking_expiry_indexes.sql`
    *   **Index 1**: `idx_bookings_status_requested_start_time` trên `bookings (status, requested_start_time)`. Giúp câu lệnh Bulk Update của `BookingCleanupScheduler` định kỳ 30 phút xác định nhanh các pending booking quá hạn mà không cần table scan toàn bộ bảng bookings.
    *   **Index 2**: `idx_bookings_mentee_status_requested_time` trên `bookings (mentee_user_id, status, requested_start_time, requested_end_time)`. Hỗ trợ tối ưu hóa kiểm tra trùng lặp thời gian học khi Mentee tạo yêu cầu đặt lịch mới.
2.  **Hộp thư và Lịch sử Chat (Conversations & Messages)**:
    *   **Migration**: `V16__add_chat_message_indexes.sql`
    *   **Index 1**: `idx_messages_conversation_created_at` trên `messages (conversation_id, created_at DESC)`. Tăng tốc truy vấn phân trang tin nhắn của một cuộc hội thoại từ cũ đến mới.
    *   **Index 2**: `idx_conversations_last_message_at` trên `conversations (last_message_at DESC)`. Tăng tốc truy vấn lấy danh sách hộp thư thoại của người dùng, sắp xếp các cuộc trò chuyện có tương tác gần nhất lên trên đầu giao diện.
*   *Lưu ý*: Các chỉ mục này đã được cập nhật đồng bộ trong annotations `@Table(indexes = ...)` của các thực thể Java tương ứng (bỏ qua từ khóa `DESC` trong annotation để đảm bảo tính di động/database-agnostic, SQL migration của Flyway chịu trách nhiệm thực thi index chính xác trên PostgreSQL).
