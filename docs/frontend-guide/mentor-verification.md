# Frontend Guide — Xác Thực Mentor & Quản Trị Duyệt Hồ Sơ (Mentor Verification & Admin Review)

> **Quy tắc URL:** URL upload presigned chỉ dùng để đẩy file lên lưu trữ và có thời hạn nhất định. Tuyệt đối không hiển thị, không lưu vào local storage hay tự ghép nối các URL private. Chỉ sử dụng `fileUrl` do backend trả về khi response cho phép hiển thị.

> **Chuẩn Envelope:** Tất cả phản hồi từ backend đều được bọc trong `ApiResponse<T>`. Vui lòng xem [identity.md](identity.md) để biết cách xử lý Access Token, Refresh Token, Validation và mã lỗi `429 Retry-After`.

> **Tài liệu liên quan:**
> - Tài liệu này gồm 2 phần: **Wizard cho người dùng đăng ký mentor** và **Admin Workbench duyệt hồ sơ**.
> - Xem [mentor-discovery.md](mentor-discovery.md) cho Mentor Profile và các minh chứng hiển thị công khai.
> - Xem [mentor-service.md](mentor-service.md) để quản lý dịch vụ và lịch rảnh sau khi hồ sơ đã được duyệt.

---

## 1. Điều Kiện & Quy Trình Đăng Ký Mentor (Eligibility & Journey)

Tất cả API trong luồng Wizard yêu cầu Bearer token và chỉ chấp nhận Role `MENTEE` hoặc `MENTOR`.

- Người dùng mới sau khi hoàn thành onboarding sinh viên có Role `MENTEE` hoàn toàn có thể mở hồ sơ đăng ký làm mentor.
- Role `ADMIN` và `SYSTEM_ADMIN` **bị chặn hoàn toàn** khỏi luồng này. Nếu quản trị viên muốn làm mentor, cần sử dụng tài khoản cá nhân riêng.
- Việc tạo Mentor Profile hoặc nộp hồ sơ xác thực **không tự động cấp Role `MENTOR`**.
- Dịch vụ (Service) và lịch rảnh không bắt buộc khi nộp hồ sơ, nhưng cần thiết lập sau đó để nhận đặt lịch và xuất hiện trên trang tìm kiếm sau khi được phê duyệt.
- Google Calendar **không phải trường hoặc bước của đơn xác thực mentor**. Không yêu cầu người nộp đơn cấp quyền Calendar trong wizard này.

```text
Hồ sơ học thuật (Academic Profile)
 ➔ Tạo Hồ sơ Mentor (Mentor Profile)
 ➔ Khởi tạo yêu cầu xác thực (Open Verification Request)
 ➔ Upload các minh chứng (Affiliation Proof, Expertise Proof)
 ➔ Đồng ý điều khoản & Nộp hồ sơ (Submit Verification)
 ➔ Quản trị viên xét duyệt (Admin Review)
 ➔ APPROVED: Hệ thống tự động cấp Role MENTOR
 ➔ Kết nối Google Calendar
 ➔ Thiết lập Dịch vụ và Lịch rảnh để nhận booking
```

> [!IMPORTANT]
> Sau khi trạng thái thành `APPROVED`, CTA tiếp theo nên là **“Kết nối Google Calendar để tạo dịch vụ”**. FE chuyển mentor sang flow trong [mentor-service.md](mentor-service.md); không đưa bước Calendar vào lại wizard xác thực.

---

## 2. Luồng Wizard Đăng Ký Của Người Dùng (Applicant Wizard)

### 2.1 Mở & Khôi Phục Trạng Thái Wizard

| Endpoint | Khi nào sử dụng |
|---|---|
| `POST /api/me/mentor-verification/request` | Bắt đầu wizard; trả về `201 Created` nếu tạo mới, `200 OK` nếu đã có request đang active |
| `GET /api/me/mentor-verification` | Khôi phục thông tin request mới nhất, danh sách tài liệu, checklist, timeline và hành động tiếp theo |
| `GET /api/me/mentor-verification/progress` | **Source of truth** để quyết định nút Call-to-Action (CTA) và thanh tiến độ của wizard |
| `GET /api/me/mentor-verification/timeline` | Hiển thị lịch sử nộp/yêu cầu sửa/phê duyệt/từ chối/rút hồ sơ |

```typescript
interface MentorVerificationProgressResponse {
  requestId: string | null;
  applicationStatus:
    | "NOT_STARTED"       // Chưa bắt đầu
    | "DRAFT"             // Bản nháp, đang hoàn thiện
    | "PENDING_REVIEW"    // Đã nộp, đang chờ admin duyệt
    | "NEEDS_REVISION"    // Admin yêu cầu chỉnh sửa bổ sung
    | "APPROVED"          // Đã được phê duyệt
    | "REJECTED"          // Bị từ chối
    | "WITHDRAWN";        // Người dùng tự rút hồ sơ
  submittedAt: string | null;
  estimatedReviewBy: string | null;
  reviewTargetHours: number | null;
  reviewOverdue: boolean;
  submissionSteps: VerificationStep[];
  activationSteps: VerificationStep[];
  nextAction: {
    code: string;
    actionPath: string | null;
    message: string;
  };
}

interface VerificationStep {
  code: string;
  completed: boolean;
  requiredForSubmission: boolean;
  requiredForBookingOffer: boolean;
  actionPath: string;
  message: string;
}
```

#### Ma trận chuyển đổi trạng thái (`applicationStatus` Lifecycle & Triggers)

| Trạng thái hiện tại | Trạng thái tiếp theo | Tác nhân (Actor) | Hành động kích hoạt (Trigger Endpoint) | Điều kiện chuyển state |
|---|---|---|---|---|
| *(Chưa có)* | `NOT_STARTED` | Hệ thống | `GET /api/me/mentor-verification/progress` | Người dùng chưa từng gửi yêu cầu đăng ký mentor (`requestId === null`). |
| `NOT_STARTED` | `DRAFT` | Người dùng | `POST /api/me/mentor-verification/request` | Mở wizard đăng ký lần đầu hoặc bắt đầu một hồ sơ mới. |
| `DRAFT` | `PENDING_REVIEW` | Người dùng | `POST /api/me/mentor-verification/submit` | Hoàn thành checklist (Academic Profile, Mentor Profile, 1 Affiliation Proof, ≥ 1 Expertise Proof) và gửi `termsAccepted: true`. |
| `DRAFT` | `WITHDRAWN` | Người dùng | `POST /api/me/mentor-verification/withdraw` | Người dùng chủ động hủy/rút bản nháp hiện tại. |
| `PENDING_REVIEW` | `NEEDS_REVISION` | Admin | `POST /api/admin/mentor-verification/requests/{requestId}/request-revision` | Admin kiểm tra hồ sơ, yêu cầu bổ sung/sửa đổi minh chứng kèm lý do (`note`). |
| `PENDING_REVIEW` | `APPROVED` | Admin | `POST /api/admin/mentor-verification/requests/{requestId}/approve` | Admin duyệt hồ sơ. Hệ thống tự động cấp Role `MENTOR`. |
| `PENDING_REVIEW` | `REJECTED` | Admin | `POST /api/admin/mentor-verification/requests/{requestId}/reject` | Admin từ chối hồ sơ kèm lý do (`note`). Đơn bị đóng. |
| `PENDING_REVIEW` | `WITHDRAWN` | Người dùng | `POST /api/me/mentor-verification/withdraw` | Người dùng rút hồ sơ khi hồ sơ chưa bị Admin khóa xử lý (`hasActiveAdminLock === false`). Nếu Admin đang khóa xem xét, API trả `409 Conflict`. |
| `NEEDS_REVISION` | `PENDING_REVIEW` | Người dùng | `POST /api/me/mentor-verification/submit` | Người dùng cập nhật lại tài liệu/thông tin và bấm nộp lại (`revisionCount` tăng thêm 1). |
| `NEEDS_REVISION` | `WITHDRAWN` | Người dùng | `POST /api/me/mentor-verification/withdraw` | Người dùng không muốn tiếp tục bổ sung và chọn rút hồ sơ. |
| `REJECTED` / `WITHDRAWN` | `DRAFT` | Người dùng | `POST /api/me/mentor-verification/request` | Người dùng muốn tạo hồ sơ mới sau khi đơn trước đó bị từ chối hoặc đã rút. |
| `APPROVED` | *(Hoàn tất)* | Hệ thống | — | Trạng thái đích. Người dùng chuyển sang hoàn thiện dịch vụ và lịch rảnh ([mentor-service.md](mentor-service.md)). Không tạo lại request xác thực. |

#### Bảng Quyền Hạn UI Tương Ứng Với Từng Trạng Thái (UI & Action Permissions)

| Trạng thái (`applicationStatus`) | Upload / Xóa minh chứng | Sửa Mentor Profile | Nộp hồ sơ (`submit`) | Rút hồ sơ (`withdraw`) | Trạng thái hiển thị trên UI Frontend |
|---|:---:|:---:|:---:|:---:|---|
| `NOT_STARTED` | ❌ | ❌ | ❌ | ❌ | Hiển thị màn hình giới thiệu (Onboarding/Landing), nút CTA "Bắt đầu đăng ký" |
| `DRAFT` | ✅ | ✅ | ✅ (khi đủ checklist) | ✅ | Mở đầy đủ wizard, cho phép upload/xóa file, điền form profile, checkbox điều khoản |
| `PENDING_REVIEW` | ❌ | ❌ | ❌ | ✅ (khi chưa bị admin lock) | Khóa form, hiển thị timeline "Đang chờ Admin duyệt", hiển thị `estimatedReviewBy` |
| `NEEDS_REVISION` | ✅ | ✅ | ✅ (sau khi sửa) | ✅ | Mở lại form wizard, hiển thị lý do cần sửa (`reviewNote` từ timeline), cho phép xóa file cũ và upload lại |
| `APPROVED` | ❌ | ✅ (ở trang cá nhân) | ❌ | ❌ | Hiển thị thông báo chúc mừng, chuyển hướng sang CTA kết nối Calendar & tạo dịch vụ |
| `REJECTED` | ❌ | ❌ | ❌ | ❌ | Hiển thị lý do từ chối, nút "Tạo hồ sơ mới" để bắt đầu lại |
| `WITHDRAWN` | ❌ | ❌ | ❌ | ❌ | Hiển thị thông báo đã rút đơn, nút "Bắt đầu lại" |

> [!TIP]
> `estimatedReviewBy` là thời gian mục tiêu của hệ thống, không phải cam kết cứng. Sử dụng cờ `reviewOverdue` để hiển thị thông báo tiến độ phù hợp, **không tự ý đổi trạng thái request trên UI**.

---

### 2.2 Hoàn Thiện Hồ Sơ Mentor Trước Khi Nộp

- `GET /api/me/mentor-profile` và `PUT /api/me/mentor-profile` yêu cầu Role `MENTEE` hoặc `MENTOR`.
- `GET /api/me/mentor-profile` luôn trả `200 OK`; nếu chưa tạo profile thì `data.exists === false`.
- Cờ `requiredFieldsCompleted` hỗ trợ hiển thị trạng thái form, nhưng Frontend vẫn dùng `GET /progress` làm căn cứ chuẩn cho nút Nộp hồ sơ.

Ngoài Mentor Profile, người dùng có thể quản lý dự án nổi bật và thành tích trước khi được duyệt:

| Mục đích | Endpoint |
|---|---|
| Lấy danh sách / Tạo mới dự án | `GET`, `POST /api/me/mentor-projects` (Hỗ trợ `pictureAssetId` tùy chọn) |
| Cập nhật / Xóa dự án | `PUT`, `DELETE /api/me/mentor-projects/{projectId}` |
| Tạo Upload Intent cho ảnh dự án | `POST /api/me/mentor-projects/picture/upload-intents` hoặc `POST /api/me/mentor-projects/{projectId}/picture/upload-intents` (`PublicAssetUploadIntentRequest`) |
| Xác nhận ảnh dự án sau khi PUT lên R2 | `POST /api/me/mentor-projects/{projectId}/picture/confirm` (`{ uploadIntentId }`) |
| Gỡ ảnh khỏi dự án | `DELETE /api/me/mentor-projects/{projectId}/picture` |
| Lấy danh sách / Tạo mới thành tích | `GET`, `POST /api/me/mentor-achievements` (Hỗ trợ `pictureAssetId` tùy chọn) |
| Cập nhật / Xóa thành tích | `PUT`, `DELETE /api/me/mentor-achievements/{achievementId}` |
| Tạo Upload Intent cho ảnh thành tích | `POST /api/me/mentor-achievements/picture/upload-intents` hoặc `POST /api/me/mentor-achievements/{achievementId}/picture/upload-intents` (`PublicAssetUploadIntentRequest`) |
| Xác nhận ảnh thành tích sau khi PUT lên R2 | `POST /api/me/mentor-achievements/{achievementId}/picture/confirm` (`{ uploadIntentId }`) |
| Gỡ ảnh khỏi thành tích | `DELETE /api/me/mentor-achievements/{achievementId}/picture` |

---

### 2.3 Tải Lên Minh Chứng Riêng Tư (Private Document Upload)

Mỗi file chỉ chấp nhận định dạng `image/jpeg`, `image/png` hoặc `application/pdf`, dung lượng tối đa **15 MB**.

| Loại minh chứng | Enum `documentType` khi confirm | Ý nghĩa nghiệp vụ | Số file active tối đa |
|---|---|---|---|
| Minh chứng liên kết trường FPTU | `FPTU_AFFILIATION_PROOF` | Thẻ sinh viên, giấy xác nhận SV, bảng điểm, bằng tốt nghiệp | 1 |
| Minh chứng năng lực chuyên môn | `EXPERTISE_PROOF` | Chứng chỉ quốc tế, giải thưởng, bảng điểm môn chuyên ngành | 3 |

> [!WARNING]
> Phân biệt giữa **`contentType`** (MIME type của file: `"image/jpeg"`, `"image/png"`, `"application/pdf"`) và **`documentType`** (Loại minh chứng nghiệp vụ: `"FPTU_AFFILIATION_PROOF"` hoặc `"EXPERTISE_PROOF"`). Tuyệt đối không gửi MIME type vào trường `documentType`.

#### Quy trình 4 bước tải lên cho từng file:
1. `POST /api/me/mentor-verification/documents/upload-intents` với body `{ filename, contentType, sizeBytes }`.
2. Browser gửi HTTP `PUT` file trực tiếp đến `uploadUrl`, giữ nguyên các header trong `requiredHeaders` mà backend trả về.
3. `POST /api/me/mentor-verification/documents` với body `{ documentType, uploadIntentId }`.
4. Gọi lại API `GET /progress` để cập nhật checklist mới nhất.

```typescript
// Bước 1: Request tạo upload intent
interface MentorVerificationDocumentUploadIntentRequest {
  filename: string; // Tên file gốc (vd: "ConfirmationLetter_NhatTT.jpg")
  contentType: "image/jpeg" | "image/png" | "application/pdf";
  sizeBytes: number; // Tối đa 15MB = 15728640 bytes
}

// Bước 3: Request xác nhận tài liệu đã upload lên R2
interface MentorVerificationDocumentUploadRequest {
  documentType: "FPTU_AFFILIATION_PROOF" | "EXPERTISE_PROOF"; // 👈 KHÔNG truyền MIME type vào đây!
  uploadIntentId: string; // UUID trả về từ bước 1
}

interface MentorVerificationDocumentUploadIntentResponse {
  uploadIntentId: string;
  uploadUrl: string; // Chỉ dùng để PUT file trực tiếp lên storage
  expiresAt: string;
  requiredHeaders: Record<string, string>;
  status: "PENDING_UPLOAD";
}

interface MentorVerificationUploadIntentStatusResponse {
  uploadIntentId: string;
  status: "PENDING_UPLOAD" | "CONFIRMED" | "EXPIRED" | "REJECTED";
  expiresAt: string;
  canRetry: boolean;
  confirmedDocumentId: string | null;
}

interface MentorVerificationDocumentResponse {
  id: string;
  documentType: "FPTU_AFFILIATION_PROOF" | "EXPERTISE_PROOF";
  status: "UPLOADED" | "ACCEPTED" | "REJECTED" | "REMOVED";
  storageKind: "IMAGE" | "DOCUMENT";
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  fileUrl: string | null;
  isActive: boolean;
  version: number | null;
  reviewNote: string | null;
  rejectedReason: string | null;
  uploadedAt: string;
}
```

> [!NOTE]
> - Upload Intent sẽ hết hạn sau khoảng 15 phút. Không thử lại `PUT` bằng URL cũ nếu đã hết hạn.
> - Dùng `GET /documents/upload-intents/{uploadIntentId}` khi ứng dụng mất kết nối mạng hoặc người dùng quay lại wizard.
> - Chỉ gọi `POST /documents/upload-intents/{uploadIntentId}/retry` khi `canRetry === true`.
> - Xác nhận lại cùng một `uploadIntentId` có tính chất idempotent (an toàn nếu bị mất gói tin phản hồi trước đó).
> - `uploadUrl` và chuỗi `private://...` là đường dẫn lưu trữ nội bộ, không phải URL công khai để gắn thẻ `<img src>` hiển thị.

---

### 2.4 Nộp, Chỉnh Sửa & Rút Hồ Sơ

- **Nộp hồ sơ**: `POST /api/me/mentor-verification/submit`
```typescript
interface MentorVerificationSubmitRequest {
  submitNote?: string;    // Tùy chọn, tối đa 2000 ký tự
  termsAccepted: boolean; // Bắt buộc true
}
```

> [!IMPORTANT]
> - Frontend không gửi `termsVersion`: Backend tự động ghi nhận phiên bản điều khoản hiện hành.
> - Nộp hồ sơ chỉ thành công khi đã có đầy đủ: Academic Profile, Mentor Profile, 1 minh chứng trường (Affiliation Proof), ít nhất 1 minh chứng năng lực (Expertise Proof) và đã tích chọn đồng ý điều khoản.
> - Xóa minh chứng: `DELETE /api/me/mentor-verification/documents/{documentId}` chỉ hoạt động ở trạng thái `DRAFT` hoặc `NEEDS_REVISION`.
> - Rút hồ sơ: `POST /api/me/mentor-verification/withdraw` chỉ dùng khi ở trạng thái `DRAFT`, `NEEDS_REVISION` hoặc `PENDING_REVIEW` (có thể nhận lỗi `409` nếu Admin đang giữ lock duyệt hồ sơ).
> - Khi ở trạng thái `PENDING_REVIEW`: Hiển thị timeline chờ duyệt, ẩn nút chỉnh sửa tài liệu và nút nộp lại.

#### Vòng đời trạng thái yêu cầu xác thực:
```text
DRAFT ➔ Submit ➔ PENDING_REVIEW
PENDING_REVIEW ➔ Admin yêu cầu sửa ➔ NEEDS_REVISION ➔ Submit lại ➔ PENDING_REVIEW
PENDING_REVIEW ➔ Admin phê duyệt ➔ APPROVED (Tự động cấp quyền MENTOR)
PENDING_REVIEW ➔ Admin từ chối ➔ REJECTED
DRAFT / NEEDS_REVISION / PENDING_REVIEW ➔ Người dùng rút hồ sơ ➔ WITHDRAWN
```

---

## 3. Quản Trị Viên Xét Duyệt Hồ Sơ (Admin Workbench Review)

Tất cả các endpoint bên dưới yêu cầu Bearer token với Role `ADMIN` hoặc `SYSTEM_ADMIN`. Không sử dụng chung giao diện hoặc token của người nộp hồ sơ với giao diện quản trị.

### 3.1 Danh Sách Hàng Đợi & Chi Tiết Hồ Sơ (Queue & Detail)

| Endpoint | Mục đích |
|---|---|
| `GET /api/admin/mentor-verification/requests` | Lấy danh sách hàng đợi cần duyệt |
| `GET /api/admin/mentor-verification/requests/{requestId}` | Mở xem chi tiết và tự động nhận quyền khóa mềm (Claim Soft Lock) |
| `GET /api/admin/mentor-verification/requests/{requestId}/lock` | Kiểm tra trạng thái khóa hiện tại của request |
| `POST /api/admin/mentor-verification/requests/{requestId}/lock/refresh` | Gia hạn thời gian khóa của reviewer hiện tại |
| `POST /api/admin/mentor-verification/requests/{requestId}/lock/release` | Reviewer chủ động nhả khóa; `SYSTEM_ADMIN` có quyền Force Release |

- **Bộ lọc hàng đợi**: Hỗ trợ `status`, `keyword`, `submittedFrom`, `submittedTo`, phân trang và sắp xếp. Mặc định là `status=PENDING_REVIEW`, `sortBy=submittedAt`, `direction=ASC`, `size=20`.
- **Chi tiết hồ sơ**: Trả về thông tin request, danh sách minh chứng, timeline, checklist, Mentor Profile và Academic Profile.
- **Xử lý Khóa duyệt (Locking)**: Khi `canReview === false`, disable các nút ra quyết định và hiển thị thông tin người đang khóa (`lockedByAdminEmail`, `lockExpiresAt`). Frontend của reviewer đang mở xem hồ sơ cần gọi API refresh lock định kỳ và release lock khi thoát màn hình.

```typescript
interface AdminMentorVerificationLockResponse {
  requestId: string;
  locked: boolean;
  canReview: boolean;
  lockedByAdminId: string | null;
  lockedByAdminEmail: string | null;
  lockedByAdminFullName: string | null;
  lockedAt: string | null;
  lockExpiresAt: string | null;
  secondsRemaining: number;
}
```

---

### 3.2 Đưa Ra Quyết Định Xét Duyệt (Review Decisions)

| Thao tác | Endpoint | Kết quả |
|---|---|---|
| Yêu cầu chỉnh sửa | `POST /api/admin/mentor-verification/requests/{requestId}/request-revision` | Chuyển request về trạng thái `NEEDS_REVISION` |
| Phê duyệt hồ sơ | `POST /api/admin/mentor-verification/requests/{requestId}/approve` | Chuyển request sang `APPROVED`, tự động cấp Role `MENTOR` |
| Từ chối hồ sơ | `POST /api/admin/mentor-verification/requests/{requestId}/reject` | Đóng quy trình xét duyệt với trạng thái `REJECTED` |

```typescript
interface AdminMentorVerificationReviewRequest {
  note: string; // Bắt buộc đối với revision và reject (1 - 2000 ký tự)
}
```

> [!TIP]
> - Đối với hành động `approve`: Request body là tùy chọn; nếu có gửi body thì `note` không được để chuỗi rỗng.
> - Không cho phép bấm duyệt/từ chối nếu `canReview === false`.
> - Sau khi gửi quyết định thành công, tải lại danh sách hàng đợi và đóng màn hình chi tiết hiện tại.

---

## 4. Xử Lý Lỗi (Error Handling)

| HTTP Status | Hướng xử lý cho Frontend |
|---|---|
| `400 Bad Request` | Hiển thị thông báo lỗi hoặc lỗi validation theo trường dữ liệu. Không retry tự động. |
| `401 Unauthorized` | Thực hiện quy trình Refresh Token theo [identity.md](identity.md). |
| `403 Forbidden` | Ẩn wizard đối với tài khoản Admin; ẩn Admin Workbench đối với Mentee/Mentor. |
| `404 Not Found` | Request hoặc tài liệu không tồn tại hoặc không thuộc quyền sở hữu của user. Quay lại màn hình trước. |
| `409 Conflict` | Hồ sơ đang bị Admin khác khóa hoặc trạng thái vừa thay đổi. Tải lại progress/detail/lock trước khi thao tác tiếp. |
| `429 Too Many Requests` | Đọc trường `retryAfterSeconds` từ response, khóa nút và hiển thị đếm ngược thời gian chờ. |
