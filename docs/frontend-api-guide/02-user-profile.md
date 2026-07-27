# User Profile

## Mục tiêu
File này là guide cho luồng academic profile và onboarding.
FE phải hiểu rõ:
- dữ liệu nào là master data để render form
- dữ liệu nào là profile đã lưu
- field nào là derived state để dẫn hướng onboarding
- khi nào nên gọi lại status sau khi save

## API inventory
### Academic master data
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/campuses` | Public | - | - | `List<CampusResponse>` | - | Cache 24h |
| GET | `/api/academic-programs` | Public | - | - | `List<AcademicProgramResponse>` | - | Cache 24h |
| GET | `/api/academic-programs/{programId}/specializations` | Public | - | path `programId` | `List<SpecializationResponse>` | - | Specialization phải thuộc program |
| GET | `/api/specializations` | Public | - | - | `List<SpecializationResponse>` | - | Full catalog, chỉ dùng khi cần dataset toàn bộ |
| GET | `/api/catalog/help-topics` | Public | - | - | `List<HelpTopicResponse>` | - | Dùng cho mentor/profile/discovery form |
| GET | `/api/catalog/mentor-profile-options` | Public | - | - | `MentorProfileOptionsResponse` | - | Label mức support 1..4 |

### Student profile và onboarding
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/me/student-profile` | Authenticated | user | - | `StudentProfileResponse` | - | Lấy profile học thuật hiện tại |
| PUT | `/api/me/student-profile` | Authenticated | user | `StudentProfileRequest` | `StudentProfileResponse` | - | Tạo/cập nhật profile học thuật |
| GET | `/api/me/onboarding-status` | Authenticated | user | - | `OnboardingStatusResponse` | - | Trạng thái onboarding tổng hợp |

## Call order chuẩn
### Onboarding lần đầu
1. Gọi `/api/auth/me`.
2. Gọi `/api/me/onboarding-status`.
3. Nếu chưa hoàn thành student profile:
   - tải master data bằng `/api/campuses`, `/api/academic-programs`, `/api/academic-programs/{programId}/specializations`
   - nếu cần mentor form thì tải thêm `/api/catalog/help-topics`
4. FE render form theo dữ liệu master vừa lấy.
5. Gửi `PUT /api/me/student-profile`.
6. Gọi lại `/api/me/onboarding-status`.
7. Nếu profile đã xong, điều hướng theo `nextRecommendedAction`.

### Sửa profile
1. Gọi `GET /api/me/student-profile`.
2. Nếu form cần dropdown, preload master data.
3. Gửi `PUT /api/me/student-profile`.
4. Refresh lại `GET /api/me/student-profile` hoặc `/api/me/onboarding-status`.

## Ý nghĩa field quan trọng
### `StudentProfileRequest`
- `studentCode`
  - mã số sinh viên FPT
- `displayName`
  - tên hiển thị override Google
- `avatarUrl`
  - avatar override Google
- `campusId`
  - cơ sở FPT
- `programId`
  - ngành học
- `specializationId`
  - chuyên ngành, phải thuộc `programId`
- `semester`
  - học kỳ hiện tại
- `intakeYear`
  - năm nhập học
- `isAlumni`
  - cựu sinh viên hay chưa
- `graduationYear`
  - năm tốt nghiệp nếu là alumni
- `bio`
  - mô tả bản thân / mục tiêu trao đổi kỹ năng

### `StudentProfileResponse`
- `campus`, `program`, `specialization`
  - response đầy đủ object, không chỉ ID
- `isAlumni`
  - cờ trạng thái học thuật
- `createdAt`, `updatedAt`
  - phục vụ hiển thị và debug

### `OnboardingStatusResponse`
- `studentProfileCompleted`
  - đã có student profile hợp lệ hay chưa
- `mentorProfileCompleted`
  - đã hoàn thành mentor profile hay chưa
- `mentoringNeedsCompleted`
  - đã hoàn thành questionnaire/needs hiện tại hay chưa
- `mentorVerificationStatus`
  - trạng thái verification gần nhất
- `roles`
  - vai trò hiện tại
- `nextRecommendedAction`
  - action FE nên đưa user đi tiếp

## State / phase guide
### Student profile
- `missing`
  - chưa có profile, FE phải mở form
- `invalid`
  - backend trả 400, FE không tự sửa
- `saved`
  - profile hợp lệ, có thể vào onboarding tiếp theo

### Onboarding
- `COMPLETE_STUDENT_PROFILE`
  - chưa có hồ sơ học thuật
- `COMPLETE_MENTOR_PROFILE_OR_EXPLORE`
  - profile mentor chưa xong nhưng user vẫn có thể tiếp tục khám phá
- `SUBMIT_MENTOR_VERIFICATION_OR_EXPLORE`
  - profile mentor xong, có thể nộp verification
- `WAIT_FOR_APPROVE`
  - đang chờ admin duyệt verification
- `REVISE_MENTOR_VERIFICATION`
  - cần chỉnh lại hồ sơ xác thực
- `EXPLORE`
  - đã đủ điều kiện vào trải nghiệm chính

## FE không được làm
- Không gửi `programId` và `specializationId` lệch nhau.
- Không dùng `onboarding-status` như nguồn duy nhất để render student profile detail.
- Không đoán "đã hoàn thành onboarding" chỉ dựa vào `roles`.
- Không tự cache master data quá lâu nếu backend đã trả cache header.
- Không coi help topics là academic specialization.

## Error handling nhanh
| HTTP / code | Khi nào | FE nên làm |
| --- | --- | --- |
| `400 INVALID_INPUT` | `studentCode` sai format, cặp `programId/specializationId` không hợp lệ | hiển thị lỗi form, không retry |
| `401 UNAUTHENTICATED` | chưa login / token hết hạn | refresh hoặc về login |
| `404 NOT_FOUND` | profile chưa tạo / master data không tồn tại | refresh data hoặc mở flow tạo mới |
| `409 RESOURCE_CONFLICT` | dữ liệu đổi trong lúc save | refresh profile rồi thử lại |

## Response JSON example
### Student profile
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "nguyenvana@gmail.com",
    "studentCode": "SE192621",
    "displayName": "Nguyễn Văn A",
    "avatarUrl": "https://example.com/avatar.jpg",
    "campus": {
      "id": "11111111-1111-1111-1111-111111111111",
      "code": "FPTU_HCM",
      "nameVi": "FPT University Hồ Chí Minh",
      "nameEn": "FPT University Ho Chi Minh"
    },
    "program": {
      "id": "22222222-2222-2222-2222-222222222222",
      "code": "SE",
      "nameVi": "Kỹ thuật phần mềm",
      "nameEn": "Software Engineering"
    },
    "specialization": {
      "id": "33333333-3333-3333-3333-333333333333",
      "code": "SE_WEB",
      "nameVi": "Web Development",
      "nameEn": "Web Development"
    },
    "semester": 5,
    "intakeYear": 2019,
    "isAlumni": false,
    "graduationYear": null,
    "bio": "Sinh viên SE, thích React và Spring Boot.",
    "createdAt": "2026-06-22T10:15:30",
    "updatedAt": "2026-07-01T09:10:00"
  }
}
```

### Onboarding status
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "studentProfileCompleted": true,
    "mentorProfileCompleted": false,
    "mentoringNeedsCompleted": false,
    "mentorVerificationStatus": "NOT_STARTED",
    "roles": ["MENTEE"],
    "nextRecommendedAction": "COMPLETE_MENTOR_PROFILE_OR_EXPLORE"
  }
}
```

## UI mapping
- Onboarding form:
  - bind dropdown campus/program/specialization từ master data
- Profile screen:
  - render `StudentProfileResponse` để user thấy dữ liệu đã lưu
- Onboarding CTA:
  - dùng `nextRecommendedAction` để điều hướng nút chính

## API success/error behavior
- `GET /api/me/student-profile`
  - success: fill form hoặc profile view
  - 404: profile chưa tạo, FE mở form create
- `PUT /api/me/student-profile`
  - success: cập nhật UI và gọi lại onboarding status
  - 400: sai relation hoặc format, giữ form state để sửa
  - 409: dữ liệu bị đổi trong lúc submit, reload master/profile rồi thử lại
- `GET /api/me/onboarding-status`
  - success: dùng `nextRecommendedAction` làm action tiếp theo
  - 401: về login

## Ghi chú cho AI Agent và FE dev
- `OnboardingStatusResponse` là derived state, không phải data nhập tay.
- `profileCompleted` ở auth me là flag nhanh; nếu cần điều hướng thì đọc onboarding-status.
- `specializationId` phải thuộc `programId`, FE có thể validate sớm nhưng backend là nguồn cuối.
