# Frontend Integration Guide — Mentor & Verification Module

Tài liệu này hướng dẫn chi tiết cho các lập trình viên Frontend (FE) cách tích hợp với **Mentor Module** (bao gồm Hồ sơ Mentor, Quy trình Xác thực Mentor Verification, Quản lý Dịch vụ Mentoring, Tài nguyên Dịch vụ, và Tìm kiếm Mentor Discovery) của SkillSwap Backend.

---

## 1. Kiến trúc Tổng quan & Phân quyền (Architecture & Authorization Rules)

### 1.1 Phân quyền Truy cập (Access Control)
- **Role `MENTEE` & `MENTOR`**: Cả hai vai trò đều có quyền truy cập các API tạo/sửa hồ sơ mentor (`/api/me/mentor-profile`), dự án/thành tích (`/api/me/mentor-projects`, `/api/me/mentor-achievements`) và nộp hồ sơ xác minh (`/api/me/mentor-verification/*`). Quy trình này mở cho mọi sinh viên muốn đăng ký trở thành mentor.
- **Role `MENTOR` (Đã được duyệt)**: Bắt buộc để truy cập các API quản lý dịch vụ (`/api/me/mentor-services/*`), quản lý tài nguyên đính kèm (`/api/me/mentor-services/{serviceId}/resources/*`) và chính sách đặt lịch (`/api/me/mentor-booking-policy`).
- **Role `ADMIN` & `SYSTEM_ADMIN`**: **Bị chặn hoàn toàn** khỏi các API nộp hồ sơ mentor để tránh xung đột lợi ích (conflict-of-interest) và bảo đảm tính toàn vẹn audit.

### 1.2 Pattern Upload Minh chứng / Tài nguyên Riêng tư (Private Presigned Upload Pattern)
Đối với các tài liệu xác minh (bằng cấp, chứng chỉ, thẻ sinh viên, bảng điểm) hoặc tài liệu đính kèm dịch vụ, backend sử dụng pattern 3 bước bảo mật với Cloudflare R2 / AWS S3:

```text
FE Browser                      Backend API                   Storage Provider (R2/S3)
    │                                │                                    │
    ├── 1. POST /upload-intents ────►│ (Kiểm tra định dạng, kích thước)   │
    │◄── UploadIntentId & PutUrl ────┤                                    │
    │                                                                     │
    ├── 2. HTTP PUT Binary File ─────────────────────────────────────────►│ (Upload trực tiếp)
    │◄── 200 OK ──────────────────────────────────────────────────────────┤
    │                                │                                    │
    ├── 3. POST /documents ─────────►│ (Backend HEAD object xác minh)     │
    │    { uploadIntentId, ... }     │                                    │
    │◄── 201 Created + Metadata ─────┤                                    │
```

---

## 2. Quản lý Hồ sơ Mentor (Mentor Profile APIs)

### 2.1 Lấy Hồ sơ Mentor của tôi (`GET /api/me/mentor-profile`)
- **Endpoint**: `GET /api/me/mentor-profile`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: `ApiResponse<MentorProfileResponse>`

*Lưu ý cho FE*: Nếu user chưa từng tạo profile mentor, backend vẫn trả về HTTP 200 với `data.exists = false`. FE đọc cờ `exists` để chuyển hướng sang form tạo mới thay vì coi đây là lỗi.

**Response Payload (`MentorProfileResponse`)**:
```typescript
interface MentorProfileResponse {
  exists: boolean;                    // true nếu đã có bản ghi profile
  mentorUserId: string;               // UUID công khai của mentor
  headline: string;                   // Tiêu đề ngắn (ví dụ: "Senior Java & Spring Boot Developer")
  expertiseDescription: string;       // Mô tả chi tiết kinh nghiệm và lĩnh vực chuyên môn
  phoneNumber?: string | null;        // Số điện thoại liên hệ
  githubUrl?: string | null;          // Link GitHub cá nhân
  portfolioUrl?: string | null;       // Link Portfolio / LinkedIn
  isAvailable: boolean;               // true nếu sẵn sàng nhận lịch booking mới
  helpTopics: Array<{                 // Danh sách các chủ đề hỗ trợ (Help Topics)
    id: string;
    code: string;
    nameVi: string;
    nameEn: string;
  }>;
  subjectResults: Array<{             // Bảng điểm môn học xuất sắc (FPTU Subjects)
    subjectCode: string;
    grade: number;
  }>;
  supportLevel1: string;              // Mức độ hỗ trợ 1 (ví dụ: "Giải đáp thắc mắc bài tập")
  supportLevel2: string;              // Mức độ hỗ trợ 2 (ví dụ: "Review code & Fix bug")
  supportLevel3: string;              // Mức độ hỗ trợ 3 (ví dụ: "Định hướng đồ án tốt nghiệp")
  updatedAt?: string | null;
}
```

### 2.2 Tạo / Cập nhật Hồ sơ Mentor (`PUT /api/me/mentor-profile`)
FE dùng ở bước chuẩn bị trước khi người dùng nộp hồ sơ xác minh mentor verification.

- **Endpoint**: `PUT /api/me/mentor-profile`
- **Header**: `Authorization: Bearer <accessToken>`
- **Request Body (`MentorProfileUpsertRequest`)**:

```typescript
interface MentorProfileUpsertRequest {
  headline: string;                   // Bắt buộc. Max 150 ký tự
  expertiseDescription: string;       // Bắt buộc. Max 2000 ký tự
  helpTopicIds: string[];             // Bắt buộc. Danh sách UUID của help topics
  subjectResults: Array<{             // Bắt buộc. Danh sách môn học đạt điểm cao
    subjectCode: string;              // Regex: ^[A-Z]{3}\d{3}$ (ví dụ: "PRN231", "SWP391")
    grade: number;                    // Điểm số từ 0.0 đến 10.0
  }>;
  supportLevel1: string;              // Bắt buộc. Mô tả hỗ trợ level 1
  supportLevel2: string;              // Bắt buộc. Mô tả hỗ trợ level 2
  supportLevel3: string;              // Bắt buộc. Mô tả hỗ trợ level 3
  phoneNumber?: string;               // Optional. Regex số điện thoại VN: ^(0[3|5|7|8|9])+([0-9]{8})$
  githubUrl?: string;                 // Optional. URL hợp lệ
  portfolioUrl?: string;              // Optional. URL hợp lệ
  isAvailable: boolean;               // Bắt buộc. Trạng thái bật/tắt nhận booking
}
```

---

## 3. Dự án Tiêu biểu & Bằng cấp/Giải thưởng (Profile Items)

### 3.1 Dự án Tiêu biểu (Featured Projects)
- **Lấy danh sách**: `GET /api/me/mentor-projects`
- **Tạo dự án mới**: `POST /api/me/mentor-projects`
  - Body: `{ title: string, description: string, projectUrl?: string, role?: string, technologies: string[] }`
- **Cập nhật dự án**: `PUT /api/me/mentor-projects/{projectId}`
- **Upload ảnh dự án**: `PUT /api/me/mentor-projects/{projectId}/picture`
  - Header: `Content-Type: multipart/form-data`
  - Body: Form field `file` (Image file format)
- **Xóa dự án**: `DELETE /api/me/mentor-projects/{projectId}`

### 3.2 Học vấn & Giải thưởng (Achievements & Certifications)
- **Lấy danh sách**: `GET /api/me/mentor-achievements`
- **Tạo mới**: `POST /api/me/mentor-achievements`
  - Body: `{ title: string, issuer: string, issueDate?: string, certificateUrl?: string, achievementType: "CERTIFICATION" | "AWARD" | "DEGREE" }`
- **Cập nhật**: `PUT /api/me/mentor-achievements/{achievementId}`
- **Xóa**: `DELETE /api/me/mentor-achievements/{achievementId}`

---

## 4. Quy trình Xác thực Mentor (Mentor Verification Wizard)

Vòng đời của một Mentor Verification Request bao gồm các trạng thái:
`NOT_STARTED` ➔ `DRAFT` ➔ `PENDING_REVIEW` ➔ `NEEDS_REVISION` (Yêu cầu sửa đổi) ➔ `APPROVED` (Được duyệt) / `REJECTED` (Từ chối).

```text
[BẮT ĐẦU WIZARD]
       │
       ▼
1. POST /api/me/mentor-verification/request (Tạo/Mở request)
       │
       ▼
2. GET /api/me/mentor-verification (Kiểm tra tiến độ & Checklist)
       │
       ▼
3. POST /documents/upload-intents  ➔ Upload binary to S3/R2 ➔  POST /documents (Gắn minh chứng)
       │
       ▼
4. POST /api/me/mentor-verification/submit (Nộp hồ sơ cho Admin review)
```

### 4.1 Khởi tạo / Mở Verification Request (`POST /api/me/mentor-verification/request`)
- **Endpoint**: `POST /api/me/mentor-verification/request`
- **Header**: `Authorization: Bearer <accessToken>`
- **Response**: HTTP 201 (nếu tạo mới) hoặc HTTP 200 (nếu trả về request draft hiện tại).

### 4.2 Lấy thông tin Verification Request mới nhất (`GET /api/me/mentor-verification`)
FE gọi API này để kiểm tra xem user đã nộp hồ sơ chưa, trạng thái hiện tại là gì, danh sách tài liệu đã upload và danh sách các điều kiện trong checklist.

**Response Payload (`MentorVerificationRequestResponse`)**:
```typescript
interface MentorVerificationRequestResponse {
  requestId: string;                  // UUID của verification request
  status: "DRAFT" | "PENDING_REVIEW" | "APPROVED" | "NEEDS_REVISION" | "REJECTED" | "WITHDRAWN";
  studentProfileCompleted: boolean;   // Đã hoàn thành student profile chưa
  mentorProfileCompleted: boolean;    // Đã hoàn thành mentor profile chưa
  termsAccepted: boolean;             // Đã đồng ý điều khoản mentor chưa
  rejectionReason?: string | null;    // Lý do từ chối (nếu bị REJECTED)
  reviewerNote?: string | null;       // Ghi chú của Admin (nếu yêu cầu NEEDS_REVISION)
  documents: Array<{                  // Danh sách tài liệu xác minh đã upload
    documentId: string;
    documentType: "STUDENT_CARD" | "ACADEMIC_TRANSCRIPT" | "CERTIFICATE" | "OTHER";
    fileName: string;
    fileSize: number;
    contentType: string;
    status: "PENDING" | "APPROVED" | "REJECTED";
    createdAt: string;
  }>;
  checklist: {                        // Checklist điều kiện nộp hồ sơ
    canSubmit: boolean;               // true nếu ĐỦ ĐIỀU KIỆN bấm Submit
    missingRequirements: string[];    // Danh sách các điều kiện còn thiếu
  };
  submittedAt?: string | null;
  reviewedAt?: string | null;
}
```

### 4.3 Xem Timeline Tiến độ Xác thực (`GET /api/me/mentor-verification/timeline`)
- **Endpoint**: `GET /api/me/mentor-verification/timeline`
- Trả về danh sách lịch sử sự kiện: `REQUEST_CREATED`, `DOCUMENT_UPLOADED`, `SUBMITTED`, `REVISION_REQUESTED`, `APPROVED`, `REJECTED`, `WITHDRAWN`.

### 4.4 Khởi tạo Upload Intent cho Minh chứng (`POST /api/me/mentor-verification/documents/upload-intents`)
- **Endpoint**: `POST /api/me/mentor-verification/documents/upload-intents`
- **Request Body**:
```typescript
interface MentorVerificationDocumentUploadIntentRequest {
  documentType: "STUDENT_CARD" | "ACADEMIC_TRANSCRIPT" | "CERTIFICATE" | "OTHER";
  fileName: string;         // Ví dụ: "the_sinh_vien.jpg"
  fileSize: number;         // Kích thước tính theo bytes (Tối đa 15MB)
  contentType: string;      // "image/jpeg", "image/png", hoặc "application/pdf"
}
```
**Response**: Trả về `uploadIntentId` và `uploadUrl` (Presigned PUT URL của Cloudflare R2 / S3).

### 4.5 Xác nhận tài liệu đã Upload (`POST /api/me/mentor-verification/documents`)
Sau khi FE gửi file binary thành công lên `uploadUrl` ở bước trên:
- **Endpoint**: `POST /api/me/mentor-verification/documents`
- **Request Body**:
```typescript
interface MentorVerificationDocumentUploadRequest {
  uploadIntentId: string;   // ID nhận được từ bước tạo intent
  documentType: "STUDENT_CARD" | "ACADEMIC_TRANSCRIPT" | "CERTIFICATE" | "OTHER";
}
```

### 4.6 Nộp Hồ sơ Xác thực cho Admin Duyệt (`POST /api/me/mentor-verification/submit`)
FE gọi sau khi user đã xác nhận đồng ý điều khoản và checklist `canSubmit = true`.

- **Endpoint**: `POST /api/me/mentor-verification/submit`
- **Request Body**:
```typescript
interface MentorVerificationSubmitRequest {
  acceptTerms: boolean;               // Bắt buộc phải = true
  termsVersion: string;               // Phiên bản điều khoản (mặc định: "SKILLSWAP_MENTOR_TERMS_V1")
}
```

### 4.7 Xóa tài liệu minh chứng (`DELETE /api/me/mentor-verification/documents/{documentId}`)
Dùng khi hồ sơ ở trạng thái `DRAFT` hoặc `NEEDS_REVISION` và user muốn thay thế tài liệu khác.

### 4.8 Rút hồ sơ xác minh (`POST /api/me/mentor-verification/withdraw`)
Dùng khi user muốn hủy yêu cầu xác minh mentor đang chờ duyệt.

---

## 5. Quản lý Dịch vụ Mentoring 1-on-1 (Mentor Services)

*Yêu cầu quyền*: User phải có vai trò `MENTOR`.

### 5.1 Lấy giới hạn cấu hình Service (`GET /api/me/mentor-services/constraints`)
- **Endpoint**: `GET /api/me/mentor-services/constraints`
- **Response**: Trả về danh sách thời lượng hợp lệ (`durationsMinutes`: `[30, 45, 60, 90]`) và hạn mức giá (`minPrice`, `maxPrice`). FE không được hard-code các giá trị này.

### 5.2 Lấy danh sách Dịch vụ của tôi (`GET /api/me/mentor-services`)
- **Endpoint**: `GET /api/me/mentor-services?isActive=true|false|all`

### 5.3 Tạo Dịch vụ Mentoring mới (`POST /api/me/mentor-services`)
- **Endpoint**: `POST /api/me/mentor-services`
- **Request Body (`CreateMentorServiceRequest`)**:
```typescript
interface CreateMentorServiceRequest {
  title: string;                      // Bắt buộc. Max 150 ký tự (ví dụ: "Review CV & Mock Interview SE")
  description: string;                // Bắt buộc. Max 2000 ký tự
  expectedOutcome: string;            // Bắt buộc. Kết quả cam kết đạt được sau session
  durationMinutes: number;            // Bắt buộc. Phải thuộc danh sách constraints (30, 45, 60, 90)
  price: number;                      // Giá tiền tính theo điểm S-Coin (0 nếu isFree = true)
  isFree: boolean;                    // true nếu là dịch vụ miễn phí
  helpTopicIds: string[];             // Danh sách chủ đề hỗ trợ liên kết với service
}
```

### 5.4 Cập nhật Dịch vụ (`PUT /api/me/mentor-services/{serviceId}`)
- **Endpoint**: `PUT /api/me/mentor-services/{serviceId}`

### 5.5 Đổi trạng thái Bật/Tắt Dịch vụ (`PATCH /api/me/mentor-services/{serviceId}/active`)
- **Endpoint**: `PATCH /api/me/mentor-services/{serviceId}/active`
- **Request Body**: `{ active: boolean }`

---

## 6. Tìm kiếm & Đặt lịch Mentor (Mentor Discovery & Availability)

Dành cho Mentee khi xem và chọn Mentor trên ứng dụng.

### 6.1 Lấy danh sách Mentor gợi ý (`GET /api/mentors/recommendations?limit=12`)
Hiển thị danh sách mentor phù hợp trên Trang chủ / Dashboard.

### 6.2 Tìm kiếm & Lọc Mentor (`GET /api/mentors`)
Hỗ trợ tìm kiếm phân trang với các bộ lọc:
- **Query Params**:
  - `keyword`: Từ khóa tìm kiếm full-text search (tên, headline, môn học)
  - `campusId`: UUID của cơ sở FPT
  - `programId`: UUID ngành học
  - `specializationId`: UUID chuyên ngành
  - `helpTopicId`: UUID chủ đề hỗ trợ
  - `minRating`: Số sao tối thiểu (1.0 đến 5.0)
  - `sortBy`: `"REPUTATION"`, `"RATING"`, `"BOOKINGS_COUNT"`, `"PRICE_ASC"`, `"PRICE_DESC"`
  - `page`: Số trang (mặc định: 0)
  - `size`: Kích thước trang (mặc định: 10)

### 6.3 Xem Chi tiết Public Profile của Mentor (`GET /api/mentors/{mentorUserId}`)
Trả về đầy đủ thông tin profile, điểm uy tín (reputation), danh sách services công khai và thống kê đánh giá.

### 6.4 Quy trình 2 bước Chọn Slot & Candidate Segment cho Booking:

#### Bước 1: Lấy các Khung giờ rảnh tổng quát (`GET /api/mentors/{mentorUserId}/availability-slots`)
- **Query Params**: `startDate` (ISO-8601 Date), `endDate` (ISO-8601 Date).
- **Response**: Trả về danh sách các parent slot còn rảnh, kèm theo danh sách các `services` được gắn vào slot đó.

#### Bước 2: Lấy khoảng giờ chính xác cho Service đã chọn (`GET /api/mentors/{mentorUserId}/availability-slots/{slotId}/candidates?serviceId=...`)
Sau khi user chọn 1 Service trong parent slot ở Bước 1, FE gọi API này để nhận các candidate segment vừa vặn với `durationMinutes` của Service đó, kèm thông tin segment nào khả dụng và segment nào bị trùng lịch.

---

## 7. Bảng Mã Lỗi Chi Tiết (Error Codes Reference)

| HTTP Status | Error Code | Ý nghĩa & Hướng xử lý cho FE |
|---|---|---|
| `400` | `VAL_3001` | Dữ liệu form không hợp lệ (sai regex MSSV, điểm số ngoài khoảng 0-10, ...). |
| `400` | `SYS_0008` | Dung lượng file upload vượt quá giới hạn (Tối đa 15MB). |
| `400` | `SYS_0009` | Định dạng file upload không hỗ trợ (chỉ chấp nhận JPEG, PNG, PDF). |
| `401` | `AUTH_1001` | Chưa xác thực người dùng. Chuyển sang trang Login. |
| `403` | `AUTH_1002` | Admin/System Admin không được quyền thực hiện thao tác nộp verification. |
| `409` | `SYS_0007` | Xung đột dữ liệu (ví dụ: Nộp hồ sơ mới khi hồ sơ cũ đang chờ PENDING_REVIEW). |
| `429` | `SYS_0010` | Rate limit upload / submit. Đọc `retryAfterSeconds` để thông báo cho user. |

---

## 8. Ví dụ Code Tích hợp React / Next.js (Upload Verification Document Component)

```typescript
import React, { useState } from 'react';
import { apiClient } from '@/lib/api-client';

interface UploadProps {
  documentType: 'STUDENT_CARD' | 'ACADEMIC_TRANSCRIPT' | 'CERTIFICATE' | 'OTHER';
  onSuccess: () => void;
}

export const MentorDocumentUploader: React.FC<UploadProps> = ({ documentType, onSuccess }) => {
  const [uploading, setUploading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validate size client-side (Max 15MB)
    if (file.size > 15 * 1024 * 1024) {
      setErrorMsg('Dung lượng tệp không được vượt quá 15MB');
      return;
    }

    setUploading(true);
    setErrorMsg(null);

    try {
      // 1. Tạo Upload Intent với Backend
      const intentRes: any = await apiClient.post('/api/me/mentor-verification/documents/upload-intents', {
        documentType,
        fileName: file.name,
        fileSize: file.size,
        contentType: file.type,
      });

      const { uploadIntentId, uploadUrl } = intentRes.data;

      // 2. Upload file trực tiếp lên Storage Provider (Cloudflare R2 / AWS S3)
      const uploadRes = await fetch(uploadUrl, {
        method: 'PUT',
        headers: {
          'Content-Type': file.type,
        },
        body: file,
      });

      if (!uploadRes.ok) {
        throw new Error('Tải tệp lên bộ nhớ lưu trữ thất bại');
      }

      // 3. Xác nhận Upload thành công với Backend
      await apiClient.post('/api/me/mentor-verification/documents', {
        uploadIntentId,
        documentType,
      });

      onSuccess();
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err?.message || 'Có lỗi xảy ra trong quá trình tải tệp lên');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="upload-container">
      <input
        type="file"
        accept="image/jpeg,image/png,application/pdf"
        onChange={handleFileChange}
        disabled={uploading}
      />
      {uploading && <p>Đang tải tệp lên và xác minh...</p>}
      {errorMsg && <p className="error">{errorMsg}</p>}
    </div>
  );
};
```
