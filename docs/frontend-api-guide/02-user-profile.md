# User Profile & Academic Catalog Service (`02-user-profile.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Hồ sơ Học thuật, Danh mục Học thuật và Tiến độ Onboarding (User Profile & Academic Catalog Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**User Profile & Academic Catalog Service** quản lý hồ sơ sinh viên FPT (`StudentProfile`), danh mục dữ liệu học thuật tĩnh (`Campus`, `AcademicProgram`, `Specialization`), danh mục định hướng mentor (`HelpTopic`, `MentorProfileOptions`), bảng khảo sát nhu cầu kết nối mentee (`MentoringMatchProfile`), và tính toán tiến độ onboarding tổng hợp của người dùng trong hệ thống SkillSwap.

### Trách nhiệm chính của Service
- **Quản lý Danh mục Học thuật Tĩnh (Academic Master Data)**: Cung cấp API công khai hỗ trợ HTTP Cache (`Cache-Control: public, max-age=86400`, `ETag`) cho danh sách cơ sở (Campus), ngành học (Program), chuyên ngành (Specialization) và chủ đề hỗ trợ (Help Topic) để đổ dữ liệu dropdown/chips trên Frontend.
- **Quản lý Hồ sơ Học thuật Sinh viên (`StudentProfile`)**: Tạo mới và cập nhật thông tin MSSV, khóa học, học kỳ, cựu sinh viên, bio và tên hiển thị/avatar ghi đè. Validate chặt chẽ regex MSSV FPT, niên khóa, và quan hệ giữa ngành học với chuyên ngành.
- **Quản lý Khảo sát Nhu cầu Mentoring (Smart Matching Profile)**: Cho phép Mentee làm bài trắc nghiệm 5 câu hỏi nhu cầu trao đổi kỹ năng để phục vụ thuật toán Smart Matching.
- **Hợp nhất Tiến độ Onboarding (`/api/me/onboarding-status`)**: Hợp nhất cờ hoàn thành hồ sơ sinh viên, hồ sơ mentor, nhu cầu kết nối và trạng thái duyệt mentor thành mã hành động gợi ý duy nhất (`nextRecommendedAction`).

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Chuẩn hóa Dữ liệu Học thuật Sinh viên FPT**: Đảm bảo tất cả người dùng trong hệ thống có MSSV hợp lệ (chuẩn format FPTU như `SE192621`, `HE170001`), thuộc đúng cơ sở, đúng ngành và chuyên ngành tương ứng.
2. **Kiểm soát Logic Ràng buộc Học thuật**:
   - Ngăn chặn lỗi chọn sai chuyên ngành không thuộc ngành học đã chọn (Backend trả về `400 BAD_REQUEST` nếu `specializationId` không thuộc `programId`).
   - Tự động gán học kỳ = 9 đối với cựu sinh viên (`isAlumni = true`) và yêu cầu năm tốt nghiệp phải lớn hơn năm nhập học ít nhất 2 năm.
3. **Tối ưu Hiệu năng với HTTP & Server Cache**: Các danh mục học thuật tĩnh được đánh chỉ mục cache bộ nhớ server (`catalog:campuses`, `catalog:programs`, `catalog:specializations`) và gửi kèm HTTP Header `Cache-Control: public, max-age=86400` giúp Frontend cache dữ liệu tại trình duyệt 24 giờ.
4. **Hướng dẫn Luồng Onboarding Tự động (Backend-Driven Routing)**: Loại bỏ hoàn toàn việc Frontend phải tự viết logic điều hướng rắc rối bằng cách sử dụng cờ `nextRecommendedAction` từ Backend làm nguồn sự thật duy nhất.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                  LUỒNG ĐIỀN HỒ SƠ HỌC THUẬT & ONBOARDING                              |
+-------------------------------------------------------------------------------------------------------+

  Frontend (Browser)                  Backend (SkillSwap API)                 Database / Cache
          |                                     |                                     |
   1. Đăng nhập Google thành công               |                                     |
          |                                     |                                     |
   2. GET /api/me/onboarding-status             |                                     |
          |------------------------------------>|-- Kiểm tra studentProfileCompleted -|
          |<------------------------------------| 200 OK                              |
          | { nextRecommendedAction:            |                                     |
          |   "COMPLETE_STUDENT_PROFILE" }      |                                     |
          |                                     |                                     |
   3. Điều hướng sang màn /onboarding/student-profile                                 |
          |                                     |                                     |
   4. Tải danh mục master data (Parallel Calls)  |                                     |
          |-- GET /api/campuses --------------->|-- Đọc từ Cache Server ------------->|
          |-- GET /api/academic-programs ------>|                                     |
          |<-- 200 OK (campuses, programs) -----|                                     |
          |                                     |                                     |
   5. User chọn Program -> Tải Specializations |                                     |
          |-- GET /api/academic-programs/{id}/specializations ----------------------->|
          |<-- 200 OK (specializations) --------|                                     |
          |                                     |                                     |
   6. User điền MSSV, Học kỳ, Bio -> Bấm Lưu    |                                     |
          |                                     |                                     |
   7. PUT /api/me/student-profile ------------->|-- Validate Regex MSSV FPT --------->|
          |                                     |-- Validate Relation Program/Spec -->|
          |                                     |-- Save User & StudentProfile ------>|
          |<-- 200 OK (StudentProfileResponse) -|                                     |
          |                                     |                                     |
   8. Gọi lại GET /api/me/onboarding-status ---->|                                     |
          |<-- 200 OK { nextRecommendedAction:  |                                     |
          |    "COMPLETE_MENTOR_PROFILE_OR_EXPLORE" }                                 |
          |                                     |                                     |
   9. Điều hướng User sang bước Onboarding tiếp theo hoặc Trang khám phá (/explore)
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Master Data Danh mục Học thuật
- **Campus**: Cơ sở học tập của FPT University (Ví dụ: `FPTU_HCM` - FPT University Hồ Chí Minh, `FPTU_HN` - FPT University Hà Nội).
- **AcademicProgram**: Ngành học chính (Ví dụ: `SE` - Kỹ thuật phần mềm, `IA` - An toàn thông tin, `BA` - Quản trị kinh doanh).
- **Specialization**: Chuyên ngành hẹp thuộc ngành học chính (Ví dụ: `SE_WEB` - Web Development thuộc ngành `SE`). Bắt buộc `specialization.programId == program.id`.

### 4.2 Định dạng Mã số Sinh viên FPT (`studentCode`)
- Quy tắc Regex Backend: `^(?i)[HSDQC][ESA](0[1-9]|1[0-9]|2[0-2])\d{4}$`
- **Giải mã Định dạng**:
  - Chữ cái 1 (Cơ sở): `H` (Hà Nội), `S` (Hồ Chí Minh), `D` (Đà Nẵng), `Q` (Quy Nhơn), `C` (Cần Thơ).
  - Chữ cái 2 (Hệ đào tạo): `E` (Kỹ thuật/Kinh tế), `S`, `A`.
  - 2 chữ số tiếp theo (Khóa tuyển sinh): `01` đến `22` (Ví dụ: `19` = Khóa K19).
  - 4 chữ số cuối: Số thứ tự sinh viên (Ví dụ: `2621`).
  - *Ví dụ hợp lệ*: `SE192621`, `HE170001`, `DE181234`, `QE160099`.
  - Backend luôn tự động chuẩn hóa chuỗi MSSV sang chữ hoa (`trim().toUpperCase()`).

### 4.3 Ràng buộc Niên khóa & Học kỳ (`AcademicTimeline`)
- **Semester (Học kỳ)**:
  - `0`: Tiếng Anh dự bị (Preparation English).
  - `1` đến `9`: Học kỳ chuyên ngành chính thức.
  - Nếu `isAlumni = true`, `semester` tự động chuyển thành `9`.
- **IntakeYear (Năm nhập học)**: Giới hạn từ năm `2000` đến năm hiện tại.
- **GraduationYear (Năm tốt nghiệp)**: Bắt buộc điền nếu `isAlumni = true`. Phải thỏa mãn: `2000 <= graduationYear <= currentYear` VÀ `graduationYear >= intakeYear + 2`.

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Header Cache | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/campuses` | Public | `max-age=86400` | Lấy danh sách cơ sở FPTU đang hoạt động | Khi mở form điền/sửa hồ sơ học thuật |
| `GET` | `/api/academic-programs` | Public | `max-age=86400` | Lấy danh sách ngành học đang hoạt động | Khi mở form điền/sửa hồ sơ học thuật |
| `GET` | `/api/academic-programs/{programId}/specializations` | Public | `max-age=86400` | Lấy chuyên ngành theo ngành học | Khi user chọn/thay đổi ngành học trong dropdown |
| `GET` | `/api/specializations` | Public | `max-age=86400` | Lấy toàn bộ chuyên ngành hệ thống | Khi cần full catalog chuyên ngành |
| `GET` | `/api/catalog/help-topics` | Public | `max-age=86400` | Lấy danh sách chủ đề hỗ trợ (Help Topics) | Form mentor profile / bộ lọc discovery |
| `GET` | `/api/catalog/mentor-profile-options` | Public | `max-age=86400` | Lấy label option hỗ trợ 1..4 của mentor | Form cấu hình mentor profile |
| `GET` | `/api/me/student-profile` | Authenticated | Private | Lấy hồ sơ học thuật hiện tại của caller | Trang cá nhân / Pre-fill form sửa profile |
| `PUT` | `/api/me/student-profile` | Authenticated | Private | Tạo mới hoặc cập nhật hồ sơ học thuật | Bấm nút "Lưu hồ sơ" ở bước Onboarding/Profile |
| `GET` | `/api/me/onboarding-status` | Authenticated | Private | Truy vấn trạng thái onboarding tổng hợp | App Startup / Sau khi lưu profile |
| `GET` | `/api/me/matching-profile` | Authenticated | Private | Lấy trạng thái nhu cầu mentoring | Màn hình Smart Matching |
| `GET` | `/api/me/matching-profile/questionnaire` | Authenticated | Private | Lấy 5 câu hỏi trắc nghiệm matching | Màn hình làm khảo sát nhu cầu |
| `PUT` | `/api/me/matching-profile` | Authenticated | Private | Nộp 5 câu trả lời nhu cầu mentoring | Nộp bài khảo sát matching |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `GET /api/campuses`
- **Mục đích**: Lấy danh sách các cơ sở FPT University (`FPTU_HCM`, `FPTU_HN`, `FPTU_DN`,...).
- **Response Body**:
```json
{
  "timestamp": "2026-08-04T09:00:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "FPTU_HCM",
      "name": "FPT University Hồ Chí Minh",
      "city": "Hồ Chí Minh"
    }
  ]
}
```

---

### 6.2 `GET /api/academic-programs/{programId}/specializations`
- **Mục đích**: Lấy danh sách chuyên ngành hẹp thuộc về ngành học `programId`.
- **Preconditions**: User đã chọn một `programId` trong dropdown Ngành học.
- **Response Body**:
```json
{
  "timestamp": "2026-08-04T09:00:02Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "id": "4ba85f64-5717-4562-b3fc-2c963f66afa7",
      "programId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "SE_WEB",
      "nameVi": "Web Development",
      "nameEn": "Web Development",
      "isExpected": false,
      "isOther": false
    }
  ]
}
```

---

### 6.3 `GET /api/me/student-profile`
- **Mục đích**: Lấy thông tin chi tiết hồ sơ học thuật đã lưu của người dùng hiện tại.
- **Phản hồi Lỗi**: Trả về `404 NOT_FOUND` với tin nhắn `"Hồ sơ học thuật chưa được tạo"` nếu người dùng mới đăng nhập chưa hoàn thành onboarding.

---

### 6.4 `PUT /api/me/student-profile`
- **Mục đích**: Tạo mới hoặc cập nhật hồ sơ học thuật. Đồng thời cập nhật `fullName` và `avatarUrl` vào bảng `users` nếu có truyền.

#### Request Body (`StudentProfileRequest`)
```json
{
  "studentCode": "SE192621",
  "displayName": "Nguyễn Văn A",
  "avatarUrl": "https://example.com/avatar.jpg",
  "campusId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "programId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "specializationId": "4ba85f64-5717-4562-b3fc-2c963f66afa7",
  "semester": 5,
  "intakeYear": 2019,
  "isAlumni": false,
  "graduationYear": null,
  "bio": "Sinh viên SE năm 4, đam mê React và Spring Boot."
}
```

#### Response Body (`StudentProfileResponse`)
```json
{
  "timestamp": "2026-08-04T09:05:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "student@fpt.edu.vn",
    "studentCode": "SE192621",
    "displayName": "Nguyễn Văn A",
    "avatarUrl": "https://example.com/avatar.jpg",
    "campus": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "FPTU_HCM",
      "name": "FPT University Hồ Chí Minh",
      "city": "Hồ Chí Minh"
    },
    "program": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "SE",
      "nameVi": "Kỹ thuật phần mềm",
      "nameEn": "Software Engineering"
    },
    "specialization": {
      "id": "4ba85f64-5717-4562-b3fc-2c963f66afa7",
      "programId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "SE_WEB",
      "nameVi": "Web Development",
      "nameEn": "Web Development",
      "isExpected": false,
      "isOther": false
    },
    "semester": 5,
    "intakeYear": 2019,
    "isAlumni": false,
    "graduationYear": null,
    "bio": "Sinh viên SE năm 4, đam mê React và Spring Boot.",
    "createdAt": "2026-08-04T09:05:00",
    "updatedAt": "2026-08-04T09:05:00"
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Tải Master Data Động Khi Chọn Ngành Học

```
User (Dropdown FE)                    Frontend Component                     Backend API
        |                                     |                                   |
   1. Mở màn điền Profile ------------------->|-- GET /api/campuses ------------->|
        |                                     |-- GET /api/academic-programs ---->|
        |                                     |<-- Trả về Campuses & Programs ----|
   2. Render 2 Dropdown (Campus & Program)    |                                   |
        |                                     |                                   |
   3. User chọn Program "Software Engineering"|                                   |
        |------------------------------------>|-- GET /academic-programs/{id}/specializations
        |                                     |<-- Trả về danh sách Specializations
   4. Render Dropdown Specialization tương ứng |                                   |
```

---

## 8. State Machine (Ma trận Trạng thái Student Profile & Onboarding)

### 8.1 Vòng đời Hồ sơ Học thuật (`StudentProfileState`)

```
             +-----------------------+
             |        MISSING        | (Hồ sơ chưa tạo - GET /student-profile trả 404)
             +-----------------------+
                         |
                Gửi PUT /student-profile
                         |
           +-------------+-------------+
           |                           |
    Backend Validate 400        Backend Save 200
           |                           |
           v                           v
+-----------------------+   +-----------------------+
|     INVALID_FORM      |   |        SAVED          | (studentProfileCompleted = true)
+-----------------------+   +-----------------------+
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `BAD_REQUEST` | MSSV không đúng định dạng regex (Ví dụ: `SE192621`), hoặc `specializationId` không thuộc `programId`. | Hiển thị lỗi đỏ trực tiếp tại ô input MSSV / Chuyên ngành. Giữ nguyên form state. |
| `400 BAD_REQUEST` | `BAD_REQUEST` | Học kỳ `< 0` hoặc `> 9`, năm tốt nghiệp nhỏ hơn năm nhập học + 2 năm. | Hiển thị lỗi validation thời gian học thuật dưới trường input tương ứng. |
| `401 UNAUTHENTICATED` | `UNAUTHENTICATED` | Bearer token hết hạn hoặc chưa đăng nhập. | Kích hoạt luồng refresh token qua Interceptor. |
| `404 NOT_FOUND` | `NOT_FOUND` | Không tìm thấy Campus ID, Program ID, hoặc Specialization ID trong DB. | Thông báo dữ liệu danh mục không hợp lệ, tải lại Master Data. |
| `404 NOT_FOUND` | `RESOURCE_NOT_FOUND` | Hồ sơ học thuật chưa được tạo (khi gọi `GET /api/me/student-profile`). | Mở form khởi tạo mới (`Create Student Profile Mode`). |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Ràng buộc Quyền Sở hữu (Ownership Control)**: API `PUT /api/me/student-profile` chỉ cho phép người dùng đăng nhập cập nhật hồ sơ của chính mình dựa vào `principal.getPublicId()`. Không thể sửa hồ sơ của người khác.
2. **Chuẩn hóa Xử lý Dữ liệu Đầu vào**:
   - Backend tự động trim và viết hoa `studentCode` (`trim().toUpperCase()`).
   - Tên hiển thị (`displayName`) và `avatarUrl` nếu được truyền sẽ tự động ghi đè thông tin mặc định lấy từ Google OAuth vào bảng `users`.
3. **HTTP Caching**: Các API danh mục public (`/api/campuses`, `/api/academic-programs`, `/api/specializations`, `/api/catalog/help-topics`) đặt header `Cache-Control: public, max-age=86400` cho phép Browser & CDN cache trong 24 giờ.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Pre-load danh mục `Campuses` và `Academic Programs` song song (`Promise.all`) khi mở màn hình Onboarding/Profile.
- Khi người dùng chọn Ngành học (`Program`), lập tức xóa giá trị Chuyên ngành (`Specialization`) đã chọn trước đó và gọi API lấy danh sách Specializations mới theo `programId`.
- Gọi lại `GET /api/me/onboarding-status` ngay sau khi `PUT /api/me/student-profile` thành công để nhận `nextRecommendedAction` mới.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** cho phép người dùng chọn Chuyên ngành trước khi chọn Ngành học.
- **KHÔNG ĐƯỢC** tự ghép nối `programId` và `specializationId` thủ công ở client mà không thông qua API `/api/academic-programs/{programId}/specializations`.
- **KHÔNG ĐƯỢC** dùng `GET /api/specializations` (lấy toàn bộ) để lọc client-side nếu không thực sự cần thiết; hãy dùng API lọc theo `programId`.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Người dùng là Cựu sinh viên (`isAlumni = true`)**:
   - Trường `graduationYear` trở thành bắt buộc. Bắt buộc `graduationYear >= intakeYear + 2`.
   - Trường `semester` được backend tự động chuyển thành `9` (bất kể client gửi bao nhiêu).
2. **Học kỳ Tiếng Anh Dự bị (`semester = 0`)**:
   - Giá trị `0` là hoàn toàn hợp lệ đối với tân sinh viên đang học tiếng Anh dự bị tại FPTU. Frontend không được chặn `val === 0`.
3. **Thay đổi Ngành học Giữa chừng**:
   - Khi người dùng sửa hồ sơ và chọn lại Ngành học mới, Frontend phải bắt buộc user chọn lại Chuyên ngành thuộc ngành mới đó trước khi bấm Lưu.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Authentication Service**: Đọc cờ `profileCompleted` / `hasStudentProfile` từ `AcademicService` để trả về trong `/api/auth/me`.
- **Mentoring Match Profile Service**: Sử dụng thông tin chuyên ngành và học kỳ của `StudentProfile` để làm đầu vào gợi ý kết nối Smart Matching.
- **Mentor Profile Service**: Yêu cầu `studentProfileCompleted = true` làm điều kiện tiên quyết trước khi cho phép đăng ký làm Mentor.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Màn hình Điền Hồ sơ Học thuật Onboarding (`/onboarding/student-profile`)
- **React Components**: `StudentProfileOnboardingPage.tsx`, `CampusSelect.tsx`, `ProgramSpecCascadeSelect.tsx`, `StudentCodeInput.tsx`
- **APIs Triggered**:
  1. `GET /api/campuses` & `GET /api/academic-programs` (Tải song song khi mount component)
  2. `GET /api/academic-programs/{programId}/specializations` (Khi chọn Program)
  3. `PUT /api/me/student-profile` (Khi bấm "Hoàn tất Hồ sơ")
  4. `GET /api/me/onboarding-status` (Sau khi PUT thành công)
- **Expected Behavior**:
  - Disable nút "Hoàn tất" nếu chưa chọn đủ Campus, Program, Specialization hoặc MSSV sai format.
  - Khi PUT 200 thành công: Gọi lại onboarding-status, tự động chuyển vùng sang bước tiếp theo dựa vào `nextRecommendedAction`.

#### B. Màn hình Chỉnh sửa Profile Cá nhân (`/profile/edit`)
- **React Components**: `EditProfilePage.tsx`, `AcademicTab.tsx`
- **APIs Triggered**:
  1. `GET /api/me/student-profile` (Pre-fill dữ liệu form)
  2. `GET /api/campuses`, `GET /api/academic-programs`
  3. `GET /api/academic-programs/{programId}/specializations` (Dựa trên programId đã lưu)
  4. `PUT /api/me/student-profile` (Khi bấm "Cập nhật")
- **Expected Behavior**:
  - Pre-fill đầy đủ các ô input. Nếu người dùng chọn lại Program khác, clear ô Specialization và fetch danh sách mới.
  - Sau khi lưu 200: Invalidate React Query cache `['student-profile', 'me']` và hiển thị Toast "Cập nhật hồ sơ học thuật thành công".

---

### 14.2 Frontend Profile & Onboarding State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |      UNINITIALIZED    | (Mới đăng nhập / chưa có profile)
                       +-----------------------+
                                   |
                     GET /api/me/student-profile
                                   |
                     +-------------+-------------+
                     |                           |
                  Trả 404                     Trả 200
                     |                           |
                     v                           v
         +-----------------------+   +-----------------------+
         |    PROFILE_MISSING    |   |     PROFILE_LOADED    |
         +-----------------------+   +-----------------------+
                     |                           |
           Bấm "Hoàn tất Hồ sơ"            Bấm "Cập nhật"
                     |                           |
                     +-------------+-------------+
                                   |
                       PUT /api/me/student-profile
                                   |
                     +-------------+-------------+
                     |                           |
                  Trả 400                     Trả 200
                     |                           |
                     v                           v
         +-----------------------+   +-----------------------+
         |     VALIDATION_ERROR  |   |     PROFILE_SAVED     |
         +-----------------------+   +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | App Startup | Open Form | Select Program | Form Submit | After Save | User Action |
| --- | --- | --- | --- | --- | --- | --- |
| `GET /api/campuses` | ❌ KHÔNG | ✅ CÓ (Nếu chưa Cache) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /api/academic-programs` | ❌ KHÔNG | ✅ CÓ (Nếu chưa Cache) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /api/academic-programs/{id}/specializations` | ❌ KHÔNG | ✅ CÓ (Nếu sửa form) | ✅ CÓ (Khi đổi Program) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `GET /api/me/student-profile` | ❌ KHÔNG | ✅ CÓ (Pre-fill form) | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `PUT /api/me/student-profile` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ✅ Khi bấm nút "Lưu" |
| `GET /api/me/onboarding-status` | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Format MSSV Khôn Đúng Regex (`HTTP 400`)
- **UI Component**: Ô Input `studentCode` trong form.
- **Visual State**: Trạng thái Border đỏ + Icon cảnh báo.
- **Error Message Under Input**: *"Mã số sinh viên không đúng định dạng FPTU (Ví dụ hợp lệ: SE192621, HE170001)."*

#### B. Lỗi Chuyên ngành Không thuộc Ngành học (`HTTP 400`)
- **UI Component**: Dropdown `specializationId`.
- **Visual State**: Reset value về rỗng + Border đỏ.
- **Error Message Under Input**: *"Chuyên ngành đã chọn không thuộc Ngành học này. Vui lòng chọn lại."*

#### C. Lỗi Niên khóa Cựu Sinh viên (`HTTP 400`)
- **UI Component**: Ô Input `graduationYear`.
- **Error Message Under Input**: *"Cựu sinh viên bắt buộc phải nhập Năm tốt nghiệp (lớn hơn Năm nhập học ít nhất 2 năm)."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['catalog', 'campuses']` | 24 giờ (`24 * 60 * 60 * 1000`) | 48 giờ | `false` | Thay đổi phiên bản ứng dụng |
| `['catalog', 'programs']` | 24 giờ (`24 * 60 * 60 * 1000`) | 48 giờ | `false` | Thay đổi phiên bản ứng dụng |
| `['catalog', 'specializations', programId]` | 24 giờ (`24 * 60 * 60 * 1000`) | 48 giờ | `false` | Thay đổi phiên bản ứng dụng |
| `['student-profile', 'me']` | 10 phút | 60 phút | `false` | `PUT /api/me/student-profile` thành công |
| `['auth', 'onboarding-status']` | 0 ms | 10 phút | `true` | `PUT /api/me/student-profile` thành công |
