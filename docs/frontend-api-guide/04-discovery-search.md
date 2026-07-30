# Discovery, Search & Recommendation

## Mục tiêu
File này là guide cho:
- browse mentor
- search mentor
- recommendation mentor
- xem detail mentor
- xem review và availability trước booking

FE phải hiểu:
- search = người dùng chủ động tìm
- recommendation = hệ thống gợi ý
- detail = trang profile public của mentor
- availability/candidate = bước trước booking

## API inventory
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/mentors` | Public or Authenticated | - | `MentorDiscoverySearchRequest` | `PageResponse<MentorDiscoveryCardResponse>` | - | Search/browse mentor |
| GET | `/api/mentors/recommendations` | Authenticated | any logged-in user | `limit` | `List<MentorRecommendationResponse>` | - | Gợi ý mentor cá nhân hóa |
| GET | `/api/mentors/{mentorUserId}` | Public or Authenticated | - | path `mentorUserId` | `MentorDiscoveryDetailResponse` | - | Public mentor detail |
| GET | `/api/mentors/{mentorUserId}/reviews` | Authenticated | any logged-in user | `BasePageRequest` | `PageResponse<MentorReviewResponse>` | - | Review công khai |
| GET | `/api/mentors/{mentorUserId}/availability-slots` | Authenticated | any logged-in user | `AvailabilityQueryRequest` | `List<MentorAvailabilitySlotResponse>` | - | Parent slots để chọn |
| GET | `/api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates` | Authenticated | any logged-in user | `serviceId` | `ServiceSlotCandidatesResponse` | - | Exact candidates của 1 service |

## Khi nào dùng API nào

### Funnel telemetry
`POST /api/mentor-discovery/funnel-events` la best-effort telemetry cho authenticated user. Body dung `MentorFunnelEventRequest`; event client hop le la `SERVICE_VIEWED`, `CANDIDATE_SELECTED`, `BOOKING_STARTED` va source la enum contract. FE khong duoc block navigation, discovery hay booking neu request nay fail.
### Search
Use khi user:
- nhập keyword
- filter theo campus/program/specialization/help topic
- sort theo relevance/price/quality

### Recommendation
Use khi user:
- vào dashboard/home
- chưa có keyword rõ ràng
- muốn danh sách mentor phù hợp ngay

### Detail
Use khi user:
- click vào mentor card
- muốn xem profile public, service, review trước booking

### Availability/candidates
Use khi user:
- đã chọn mentor
- đã chọn service
- cần slot cụ thể để đi tiếp sang booking

## Call order chuẩn
### Search flow
1. FE load filter UI.
2. FE gọi `/api/mentors` với `MentorDiscoverySearchRequest`.
3. Nếu `sortBy = relevance`, backend sẽ recall candidate window rồi rerank.
4. FE render card theo `PageResponse`.
5. Khi user đổi keyword/filter/sort, FE gọi lại API thay vì tự filter client-side.

### Recommendation flow
1. FE gọi `/api/mentors/recommendations`.
2. FE render danh sách gợi ý.
3. Nếu user click card, FE chuyển sang detail.

### Detail flow
1. FE gọi `/api/mentors/{mentorUserId}`.
2. FE đọc `canRequestBooking`, `hasCompletedProfile`, `hasActiveServices`.
3. Nếu muốn đặt lịch, FE lấy tiếp availability slots.

### Booking-prep flow
1. FE gọi `/api/mentors/{mentorUserId}/availability-slots`.
2. User chọn parent slot.
3. User chọn service trong slot.
4. FE gọi `/api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId=...`.
5. FE chỉ dùng candidate hợp lệ để đi sang booking.

## Ý nghĩa field quan trọng
### `MentorDiscoveryCardResponse`
- `matchScore`
  - độ phù hợp theo search hiện tại, đã quy đổi 0-100
- `isAvailable`
  - mentor đang mở lịch hay không
- `ratingAverage`, `reviewCount`, `completedSessions`
  - tín hiệu chất lượng
- `helpTopicTags`
  - chủ đề mentor hỗ trợ
- `foundationSupportLevel`, `outputReviewSupportLevel`, `directionSupportLevel`
  - tín hiệu matching chủ đề
- `campus/program/specialization`
  - metadata học thuật để FE hiển thị/so khớp nhanh

### `MentorDiscoveryDetailResponse`
- `canRequestBooking`
  - mentor có đủ điều kiện để mentee gửi booking hay không
- `hasCompletedProfile`
  - mentor đã đủ profile public chưa
- `hasActiveServices`
  - mentor có ít nhất một service active hay chưa

### `MentorRecommendationResponse`
- `matchScore`
  - điểm gợi ý
- `matchReasons`
  - lý do hệ thống gợi ý mentor này

## State / behavior guide
- `sortBy=relevance`
  - backend ưu tiên heuristic recall + rerank, FE chỉ hiển thị kết quả cuối
- `sortBy` khác relevance
  - backend vẫn quyết định ranking, FE không tự xếp lại
- `canRequestBooking=false`
  - FE phải ẩn/disable CTA booking
- `hasActiveServices=false`
  - FE không cho đi tiếp sang booking

## FE không được làm
- Không paginate rồi rerank ở FE.
- Không gọi candidate API trước khi chọn mentor + service + slot cha.
- Không hiển thị booking CTA nếu `canRequestBooking=false`.
- Không coi recommendation là cùng logic với search; recommendation có thể khác danh sách search.
- Không suy luận mentor phù hợp chỉ từ `ratingAverage`.

## FE anti-patterns
- Không thay `PageResponse` bằng infinite scroll tự chế nếu backend chưa hỗ trợ cursor ở endpoint đó.
- Không dùng detail API làm danh sách browse.
- Không dùng review count để thay thế `matchScore`.

## Response JSON example
### Search result
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
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
        "expertiseDescription": "Java, Spring Boot, System Design",
        "subjectResults": [],
        "foundationSupportLevel": 4,
        "outputReviewSupportLevel": 4,
        "directionSupportLevel": 3,
        "featuredProjects": [],
        "achievements": [],
        "isAvailable": true,
        "ratingAverage": 4.8,
        "reviewCount": 12,
        "completedSessions": 15,
        "verifiedAt": "2026-05-15T10:00:00",
        "campusId": "11111111-1111-1111-1111-111111111111",
        "campusName": "FPT University Hồ Chí Minh",
        "programId": "22222222-2222-2222-2222-222222222222",
        "programName": "Kỹ thuật phần mềm",
        "specializationId": "33333333-3333-3333-3333-333333333333",
        "specializationName": "Web Development",
        "matchScore": 87.5,
        "helpTopicTags": []
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Recommendation
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "mentor": {
        "mentorUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "displayName": "Nguyễn Văn A",
        "avatarUrl": "https://example.com/avatar.jpg",
        "headline": "Senior Backend Developer",
        "isAvailable": true,
        "matchScore": 92.0
      },
      "matchScore": 92.0,
      "matchReasons": ["Cùng chuyên ngành", "Có support level phù hợp"]
    }
  ]
}
```

## UI mapping
- Search page:
  - filter bar -> request params
  - card list -> `PageResponse.items`
- Detail page:
  - CTA booking chỉ bật nếu `canRequestBooking=true`
- Recommendation widget:
  - card nhỏ hơn search card, dùng trên dashboard/home
- Availability section:
  - dùng sau khi user đã chọn mentor, không dùng làm browse list chính

## API success/error behavior
- `GET /api/mentors`
  - success: render list
  - empty result: show empty state và gợi ý đổi filter
- `GET /api/mentors/recommendations`
  - success: render gợi ý
  - 401: nếu chưa login thì ẩn module hoặc chuyển sang search public
- `GET /api/mentors/{mentorUserId}`
  - 404: mentor không còn public/discoverable
- availability/candidates
  - 409: slot/service không còn khả dụng, phải reload detail/slot

## Ghi chú cho AI Agent và FE dev
- `matchScore` là output của backend, FE không tự tính lại.
- Search và recommendation là hai pipeline khác nhau.
- `availability-slots` là bước trước booking, không thay search/detail.
