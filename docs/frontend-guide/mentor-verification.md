# Frontend Guide - Mentor Verification va Admin Review

> **Quy tac URL:** URL upload presigned chi dung de gui file va co han. Khong hien thi, luu local storage hay tu ghep URL private. Chi dung `fileUrl` backend tra ve khi response cho phep hien thi.

> **Envelope chung:** Tat ca response nam trong `ApiResponse<T>`. Xem [identity.md](identity.md) de xu ly access token, refresh token, validation va `429 Retry-After`.

Guide nay co hai phan: wizard cho user dang ky mentor va Admin Workbench duyet ho so. Xem [mentor-discovery.md](mentor-discovery.md) cho Mentor Profile va evidence hien thi cong khai; xem [mentor-service.md](mentor-service.md) cho service/lich sau khi da duoc duyet.

## 1. Ai co the dang ky mentor

Tat ca API wizard dung Bearer token va chi chap nhan role `MENTEE` hoac `MENTOR`.

- User moi van co the mo ho so mentor sau khi onboarding va co role `MENTEE`.
- `ADMIN` va `SYSTEM_ADMIN` bi chan khoi flow nay. Neu admin muon mentor, dung tai khoan ca nhan khac.
- Tao Mentor Profile hoac nop verification khong tu cap role `MENTOR`.
- Service va lich ranh khong bat buoc de nop verification, nhung can co de nhan booking va xuat hien tren discovery sau khi duoc duyet.

```text
Academic Profile
-> Mentor Profile
-> Mo verification request
-> Upload minh chung
-> Dong y dieu khoan va submit
-> Admin review
-> APPROVED: co role MENTOR
-> Tao service va lich ranh
```

## 2. Wizard cua user

### 2.1 Mo va khoi phuc wizard

| Endpoint | Dung khi |
| --- | --- |
| `POST /api/me/mentor-verification/request` | Bat dau wizard; tra `201` neu tao moi, `200` neu da co request active |
| `GET /api/me/mentor-verification` | Khoi phuc request moi nhat, documents, checklist, timeline va action |
| `GET /api/me/mentor-verification/progress` | Quyết dinh CTA va tien do wizard |
| `GET /api/me/mentor-verification/timeline` | Hien lich su submit/revision/approve/reject/withdraw |

`GET /progress` la source of truth cho giao dien. Khong tu suy dien dieu kien tu nhieu API rieng le.

```ts
interface MentorVerificationProgressResponse {
  requestId: string | null;
  applicationStatus:
    | "NOT_STARTED" | "DRAFT" | "PENDING_REVIEW" | "NEEDS_REVISION"
    | "APPROVED" | "REJECTED" | "WITHDRAWN";
  submittedAt: string | null;
  estimatedReviewBy: string | null;
  reviewTargetHours: number | null;
  reviewOverdue: boolean;
  submissionSteps: VerificationStep[];
  activationSteps: VerificationStep[];
  nextAction: { code: string; actionPath: string | null; message: string };
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

`estimatedReviewBy` la thoi gian muc tieu, khong phai cam ket admin se duyet. Dung `reviewOverdue` de hien thi thong bao phu hop, khong tu doi request sang trang thai khac.

### 2.2 Hoan thien Mentor Profile truoc khi submit

`GET` va `PUT /api/me/mentor-profile` yeu cau role `MENTEE` hoac `MENTOR`. `GET` luon tra `200`; neu chua tao profile thi `data.exists = false`.

`requiredFieldsCompleted` chi la co ho tro. FE dung no de hien thi trang thai form, nhung van dung `GET /progress` lam source of truth cho nut submit va cac buoc con thieu.

Ngoai Mentor Profile, user co the quan ly project va achievement truoc khi duoc duyet:

| Muc dich | Endpoint |
| --- | --- |
| List/create project | `GET`, `POST /api/me/mentor-projects` |
| Update/delete project | `PUT`, `DELETE /api/me/mentor-projects/{projectId}` |
| Upload anh project | `PUT /api/me/mentor-projects/{projectId}/picture` voi `multipart/form-data`, field `file` |
| List/create achievement | `GET`, `POST /api/me/mentor-achievements` |
| Update/delete achievement | `PUT`, `DELETE /api/me/mentor-achievements/{achievementId}` |

Xem schema day du cua Mentor Profile, project va achievement trong [mentor-discovery.md](mentor-discovery.md). So dien thoai la thong tin rieng cua mentor, khong duoc dua vao public profile.

### 2.3 Upload minh chung private

Moi file chi nhan `image/jpeg`, `image/png` hoac `application/pdf`, toi da `15 MB`.

| Loai document | Enum khi confirm | So file active toi da |
| --- | --- | --- |
| Minh chung lien ket truong | `FPTU_AFFILIATION_PROOF` | 1 |
| Minh chung nang luc | `EXPERTISE_PROOF` | 3 |

Quy trinh cho moi file:

1. `POST /api/me/mentor-verification/documents/upload-intents` voi `{ filename, contentType, sizeBytes }`.
2. Browser `PUT` file truc tiep toi `uploadUrl`, giu nguyen `requiredHeaders` backend tra ve.
3. `POST /api/me/mentor-verification/documents` voi `{ documentType, uploadIntentId }`.
4. Tai lai progress/request de render checklist moi nhat.

```ts
interface MentorVerificationDocumentUploadIntentResponse {
  uploadIntentId: string;
  uploadUrl: string; // Chi dung de PUT file
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
  documentType: string;
  status: string;
  storageKind: string;
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

- Intent het han sau khoang 15 phut. Khong retry PUT bang URL cu.
- Dung `GET /documents/upload-intents/{uploadIntentId}` khi app mat mang hoac user quay lai wizard.
- Chi goi `POST /documents/upload-intents/{uploadIntentId}/retry` khi `canRetry = true`.
- Confirm lai cung `uploadIntentId` la idempotent neu response truoc do bi mat.
- `uploadUrl` va `private://...` khong phai URL hien thi anh.

### 2.4 Submit, sua va rut request

`POST /api/me/mentor-verification/submit` nhan:

```ts
interface MentorVerificationSubmitRequest {
  submitNote?: string; // Toi da 2000 ky tu
  termsAccepted: boolean;
}
```

Khong gui `termsVersion`: backend tu ghi nhan version dieu khoan hien hanh.

- Submit chi thanh cong khi Academic Profile, Mentor Profile, mot affiliation proof, mot expertise proof va dieu khoan da du.
- `DELETE /api/me/mentor-verification/documents/{documentId}` chi hoat dong o `DRAFT` hoac `NEEDS_REVISION`.
- `POST /api/me/mentor-verification/withdraw` hoat dong o `DRAFT`, `NEEDS_REVISION` hoac `PENDING_REVIEW`; co the bi `409` neu admin dang giu lock xu ly request.
- Khi `PENDING_REVIEW`, hien thi timeline va trang thai cho; khong hien thi nut sua document hoac submit lai.

Trang thai request:

```text
DRAFT -> submit -> PENDING_REVIEW
PENDING_REVIEW -> admin yeu cau sua -> NEEDS_REVISION -> submit -> PENDING_REVIEW
PENDING_REVIEW -> admin approve -> APPROVED
PENDING_REVIEW -> admin reject -> REJECTED
DRAFT / NEEDS_REVISION / PENDING_REVIEW -> withdraw -> WITHDRAWN
```

## 3. Admin Workbench review

Tat ca endpoint ben duoi yeu cau Bearer token va role `ADMIN` hoac `SYSTEM_ADMIN`. Khong dung chung UI hay token cua applicant voi admin review.

### 3.1 Queue va detail

| Endpoint | Muc dich |
| --- | --- |
| `GET /api/admin/mentor-verification/requests` | Danh sach queue |
| `GET /api/admin/mentor-verification/requests/{requestId}` | Mo detail va co the claim soft lock |
| `GET /api/admin/mentor-verification/requests/{requestId}/lock` | Kiem tra lock hien tai |
| `POST /api/admin/mentor-verification/requests/{requestId}/lock/refresh` | Gia han lock cua reviewer hien tai |
| `POST /api/admin/mentor-verification/requests/{requestId}/lock/release` | Reviewer release lock; `SYSTEM_ADMIN` co the force release |

Queue ho tro `status`, `keyword`, `submittedFrom`, `submittedTo`, pagination va sort. Mac dinh la `status=PENDING_REVIEW`, `sortBy=submittedAt`, `direction=ASC`, `size=20`.

Detail tra request, document, timeline, checklist, Mentor Profile va Academic Profile. Khi `canReview = false`, disable cac nut decision va hien thi `lockedByAdminEmail`, `lockExpiresAt` neu co. FE reviewer dang mo request phai goi refresh lock dinh ky va release lock khi roi man hinh.

```ts
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

### 3.2 Quyet dinh review

| Endpoint | Ket qua |
| --- | --- |
| `POST /{requestId}/request-revision` | Dua request ve `NEEDS_REVISION` |
| `POST /{requestId}/approve` | Duyet request; body co the bo trong |
| `POST /{requestId}/reject` | Dong flow hien tai voi `REJECTED` |

Base path cua ba endpoint la `/api/admin/mentor-verification/requests`.

```ts
interface AdminMentorVerificationReviewRequest {
  note: string; // Bat buoc voi revision/reject, toi da 2000 ky tu
}
```

Voi `approve`, body la tuy chon; neu gui body thi `note` van phai khong rong. Khong goi decision neu lock response cho thay `canReview = false`. Sau mot decision thanh cong, tai lai queue va dong detail cu thay vi cho phep reviewer gui quyet dinh lan hai.

## 4. Error handling

| HTTP | FE can lam |
| --- | --- |
| `400` | Hien validation/message; khong retry tu dong |
| `401` | Theo refresh flow trong [identity.md](identity.md) |
| `403` | An wizard voi admin; an Admin Workbench voi mentee/mentor |
| `404` | Request/document khong ton tai hoac khong thuoc user hien tai; quay lai man truoc |
| `409` | Request dang bi lock hoac trang thai vua doi; tai lai progress/detail/lock truoc |
| `429` | Doc `retryAfterSeconds`, khoa nut va hien thi thoi gian cho |
