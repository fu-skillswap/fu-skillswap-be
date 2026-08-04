# File Storage Service (`11-storage.md`)

Tài liệu Hướng dẫn Tích hợp & Vận hành Dịch vụ Lưu trữ Tệp và Tải Lên Bảo mật (File Storage Service Usage & Frontend Integration Guide) dành cho Đội ngũ Phát triển Frontend, Mobile, QA và Product.

---

## 1. Overview (Tổng quan)

**File Storage Service** quản lý hạ tầng lưu trữ đám mây (Cloudflare R2 / AWS S3), quy trình tải file bảo mật 2 bước không qua server (`Presigned Upload Intent Flow`), kiểm tra khả năng hệ thống (`Storage Capabilities`), tải tài liệu riêng tư (`Private Presigned Download URL`), và phân tách ranh giới an toàn giữa tài sản công khai (`Public Assets`) và tài liệu mật (`Private Documents`).

### Trách nhiệm chính của Service
- **Kiểm tra Khả năng Hệ thống (`GET /api/files/capabilities`)**: Cung cấp các cờ boolean kiểm tra tính khả thi của tính năng upload/download file ở môi trường hiện tại. Không tiết lộ tên Bucket, Endpoint hay Object Key.
- **Vô hiệu hóa Endpoint Upload Chung ở Production (`Generic Upload Lockdown`)**: Endpoint upload chung `/api/files/upload-url` và `/api/files/local-upload` bị khóa cứng (`404 NOT_FOUND`) ở môi trường Production. Mọi thao tác upload trên Production bắt buộc phải sử dụng Upload Intent theo mục đích nghiệp vụ riêng (`Purpose-Scoped Upload Intent`).
- **Phân loại 4 Quy trình Upload Theo Nghiệp vụ (`Purpose-Scoped Upload Flows`)**:
  1. **Minh chứng Xác minh Mentor (`Mentor Verification`)**: Upload Intent riêng tư, chấp nhận `JPG`, `PNG`, `PDF` tối đa 15 MiB. Confirm via `uploadIntentId`.
  2. **Tài liệu Dịch vụ Mentoring (`Service Resources`)**: File tài liệu kèm bài học, chỉ cấp link tải thời gian ngắn 10 phút (`no-store, private`).
  3. **Ảnh Bài viết Blog (`Blog Public Assets`)**: Upload ảnh public, confirm trả về `assetId` và `publicUrl`. Mentor submit `coverAssetId` thay vì đường dẫn lưu trữ.
  4. **Tệp Đính kèm Chat (`Chat Private Attachments`)**: File đính kèm tin nhắn (`PNG`, `JPEG`, `PDF`, `DOCX` tối đa 10 MiB), xác minh Magic-Byte và cấu trúc ZIP.
- **Xác minh Đầu Server Trước Khi Khai Báo (`HEAD Object Verification`)**: Backend chủ động thực hiện lệnh HEAD sang Cloudflare R2 để đối soát sự tồn tại, dung lượng và định dạng file trước khi cho phép lưu metadata vào Database.

---

## 2. Business Purpose (Mục đích Nghiệp vụ)

1. **Bảo mật Đường dẫn Lưu trữ Thô (`Zero Exposure of Storage Keys`)**: Mã JavaScript ở Frontend **không bao giờ** được gửi, nhận hoặc lưu trữ `objectKey`, Bucket Name hay URL S3 thô. Mọi thao tác đều thông qua `uploadIntentId` hoặc `assetId`.
2. **Tối ưu Băng thông Backend (`Direct-to-R2 Upload`)**: Trình duyệt upload file binary trực tiếp lên Cloudflare R2 qua HTTP PUT Presigned URL. Server Backend không phải gánh luồng dữ liệu file dung lượng lớn.
3. **Ngăn Chặn Tải Trái Phép Tài liệu Riêng tư**: Các file bằng cấp, CCCD, minh chứng mentor hay tài liệu dịch vụ chỉ được truy cập qua Link tải hạn giờ (TTL 10-15 phút). Trình duyệt trả về header `Cache-Control: no-store, private` chống lưu cache local.
4. **Tránh Tập Tin Rác (`Orphaned File Cleanup`)**: Mỗi Presigned Upload Intent chỉ tồn tại 15 phút. Nếu Frontend không hoàn tất bước Confirm, Backend tự động dọn dẹp các tệp mờ rác trên Cloud storage.

---

## 3. User Journey / UX Flow (Luồng Trải nghiệm Người dùng)

```
+-------------------------------------------------------------------------------------------------------+
|                                    LUỒNG UPLOAD FILE BẢO MẬT BẰNG INTENT                              |
+-------------------------------------------------------------------------------------------------------+

  Frontend (Browser)                  Backend (SkillSwap API)                 Cloudflare R2 / S3 Storage
          |                                     |                                         |
   1. Chọn File (VD: Minh chứng CCCD.pdf)       |                                         |
          |-- 2. Xin Upload Intent ------------>|                                         |
          |    POST /upload-intents             |-- Kiểm tra Mime-type & User Role ----->|
          |<-- Trả uploadUrl & uploadIntentId --|                                         |
          |                                     |                                         |
   3. Direct HTTP PUT Binary File ------------------------------------------------------->|
      (Gửi đủ requiredHeaders & Content-Type)   |                                         |<-- 200 OK
          |                                     |                                         |
   4. Xác nhận hoàn tất upload                  |                                         |
          |-- POST /documents (uploadIntentId)->|-- Backend thực hiện HEAD Object Check ->|
          |                                     |-- Verify Owner, Size & Magic-Bytes --->|
          |<-- 200 OK (Xác minh Thành công) ----|                                         |
```

---

## 4. Service Concepts (Khái niệm Cốt lõi & Domain Model)

### 4.1 Bảng So sánh 4 Quy trình Upload (`Upload Purpose Matrix`)

| Phân loại | Intent Request Endpoint | Confirm Endpoint | Định dạng Hỗ trợ | Dung lượng Tối đa | Kết quả Trả về khi Confirm |
| --- | --- | --- | --- | --- | --- |
| **Mentor Verification** | `POST /api/me/mentor-verification/documents/upload-intents` | `POST /api/me/mentor-verification/documents` | `image/jpeg`, `image/png`, `application/pdf` | 15 MiB / file | `MentorVerificationRequestResponse` (Private document) |
| **Service Resource** | Presigned Intent Endpoint của Service | Confirm via Service Resource API | `PDF`, `ZIP`, `DOCX`, `PNG`, `JPEG` | 50 MiB / file | `uploadIntentId` (Private resource) |
| **Blog Asset** | `POST /api/me/blog/assets/upload-intents` | `POST /api/me/blog/assets/{intentId}/confirm` | `image/jpeg`, `image/png`, `image/webp` | 10 MiB / file | `assetId` + `publicUrl` |
| **Chat Attachment** | `POST /api/me/conversations/{id}/attachment-upload-intents` | Nằm trong `POST /messages` | `PNG`, `JPEG`, `PDF`, `DOCX` | 10 MiB / file | `attachmentId` |

---

## 5. API List (Danh sách API)

| Method | Endpoint | Quyền truy cập | Header Bắt buộc | Mục đích | Thời điểm/Đối tượng gọi |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/files/capabilities` | Authenticated | Bearer | Kiểm tra khả năng hệ thống lưu trữ hiện tại | Khi khởi chạy App / Trước khi mở Form Upload |
| `POST` | `/api/me/mentor-verification/documents/upload-intents` | Authenticated | Bearer | Xin Presigned URL upload tài liệu xác minh Mentor | Bấm chọn file xác minh |
| `POST` | `/api/me/mentor-verification/documents` | Authenticated | Bearer | Xác nhận hoàn tất upload tài liệu xác minh | Sau khi HTTP PUT sang R2 thành công |
| `POST` | `/api/me/blog/assets/upload-intents` | Mentor Role | Bearer | Xin Presigned URL upload ảnh cho bài viết Blog | Chọn ảnh bìa bài viết |
| `POST` | `/api/me/blog/assets/{intentId}/confirm` | Mentor Role | Bearer | Xác nhận upload ảnh Blog và nhận `assetId` | Sau khi HTTP PUT ảnh sang R2 thành công |
| `POST` | `/api/me/conversations/{id}/attachment-upload-intents` | Participant | Bearer | Xin Presigned URL upload file đính kèm chat | Chọn file đính kèm trong khung chat |
| `POST` | `/api/me/chat-attachments/{attachmentId}/download-url` | Entitled | Bearer | Xin link tải riêng tư thời hạn ngắn cho file chat | Bấm nút Xem/Tải file đính kèm |
| `GET` | `/api/files/upload-url` | Local Only | Bearer | Endpoint presigned upload dùng giả lập cho dev (Khóa ở Prod) | Chỉ dùng ở môi trường Local |

---

## 6. API Usage Guide (Hướng dẫn Sử dụng Chi tiết API)

### 6.1 `POST /api/me/mentor-verification/documents/upload-intents`

#### Purpose
Xin Presigned URL upload tài liệu riêng tư xác minh danh tính Mentor.

#### Request Body
```json
{
  "filename": "bang_tot_nghiep_fpt.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 2097152
}
```

#### Response Body
```json
{
  "timestamp": "2026-08-04T10:12:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "uploadUrl": "https://storage.skillswap.asia/upload/skillswap/verification-documents/users/123/bang_tot_nghiep_fpt.pdf?X-Amz-Algorithm=...",
    "uploadIntentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "expiresAt": "2026-08-04T10:27:00Z",
    "requiredHeaders": {
      "Content-Type": "application/pdf"
    }
  }
}
```

---

## 7. Common Flow Examples (Các Luồng Phối hợp API Thực tế)

### 7.1 Luồng Upload 2 Bước Trực tiếp lên Storage (`Direct Presigned Upload`)

```
Frontend (Browser File Picker)             Backend (SkillSwap API)                 Cloudflare R2 Storage
              |                                     |                                         |
   1. Chọn tệp PDF (2.0 MiB)                         |                                         |
   2. Request Xin Upload Intent ------------------->|                                         |
      POST .../upload-intents                       |-- Sinh Presigned PUT URL (TTL 15m) ---->|
   3. Nhận uploadUrl & requiredHeaders <------------|                                         |
   4. Thực thi HTTP PUT sang uploadUrl ------------------------------------------------------>|
      Headers: { Content-Type: "application/pdf" }  |                                         |<-- 200 OK
   5. Confirm Upload thành công                     |                                         |
      POST .../documents (uploadIntentId) --------->|-- Backend HEAD Object Check ------------>|
                                                    |-- Verify Size & MIME-Type ------------->|
   6. Nhận 200 OK xác nhận lưu DB <-----------------|                                         |
```

---

## 8. State Machine (Ma trận Trạng thái Upload Intent, Storage Object & Capabilities)

### 8.1 Vòng đời Upload Intent (`UploadIntentLifecycleState`)

```
             +-----------------------+
             |    PENDING_UPLOAD     | (Đã xin Presigned URL - TTL 15 phút)
             +-----------------------+
                         |
           +-------------+-------------+
           |                           |
    HTTP PUT thành công          Quá 15 phút
    & Confirm OK                  Không confirm
           |                           |
           v                           v
+-----------------------+   +-----------------------+
|       CONFIRMED       |   |        EXPIRED        |
+-----------------------+   +-----------------------+
(Tệp đã lưu vào DB)         (File rác tự dọn dẹp)
```

---

## 9. Error Handling (Quản lý và Xử lý Lỗi)

| HTTP Status | Backend Error Code | Nguyên nhân / Điều kiện phát sinh | Hành động Bắt buộc của Frontend |
| --- | --- | --- | --- |
| `400 BAD_REQUEST` | `INVALID_FILE_TYPE` | Định dạng tệp không được hỗ trợ cho mục đích nghiệp vụ đã chọn. | Hiển thị thông báo định dạng cho phép (`PNG`, `JPEG`, `PDF`, `DOCX`). |
| `400 BAD_REQUEST` | `STORAGE_OBJECT_MISSING` | Gọi API Confirm nhưng chưa thực hiện HTTP PUT hoặc upload bị thất bại giữa chừng. | Yêu cầu người dùng thực hiện upload lại từ đầu. |
| `404 NOT_FOUND` | `NOT_FOUND` | Cố tình gọi `/api/files/upload-url` ở môi trường Production. | Đảm bảo mã nguồn Client sử dụng đúng API Upload Intent theo nghiệp vụ. |
| `413 PAYLOAD_TOO_LARGE` | `FILE_TOO_LARGE` | Dung lượng tệp chọn lớn hơn hạn mức quy định (ví dụ > 15 MiB). | Kiểm tra dung lượng file ở Client trước khi xin Upload Intent. |

---

## 10. Permission & Security (Phân quyền và Bảo mật)

1. **Tuyệt đối Không Lưu Credentials ở Client**: Client chỉ nhận URL có thời hạn đếm ngược 15 phút.
2. **Kiểm tra Ma Trận Chữ Ký Tệp (`Magic-Byte Inspection`)**: Backend kiểm tra phần mở rộng file và cấu trúc binary (ví dụ `DOCX` phải là file ZIP hợp lệ) để phòng chống tải file độc hại.

---

## 11. Frontend Integration Rules (Quy tắc Tích hợp Cho Frontend)

### Frontend NÊN:
- Kiểm tra `GET /api/files/capabilities` khi vừa khởi chạy ứng dụng để bật/tắt các nút upload tệp.
- Truyền đúng các header yêu cầu trong `requiredHeaders` khi thực hiện lệnh HTTP PUT sang Cloudflare R2.
- Luôn thực hiện API Confirm ngay sau khi lệnh PUT thành công.

### Frontend KHÔNG ĐƯỢC:
- **KHÔNG ĐƯỢC** gọi API Confirm trước khi lệnh HTTP PUT hoàn tất.
- **KHÔNG ĐƯỢC** dùng endpoint generic `/api/files/upload-url` trên Production.
- **KHÔNG ĐƯỢC** lưu trữ hoặc hiển thị chuỗi `objectKey` thô ở giao diện người dùng.

---

## 12. Edge Cases (Các Kịch bản Biên & Xử lý Rủi ro)

1. **Intent Hết Hạn 15 Phút Khi Mạng Chậm**:
   - Nếu quá 15 phút người dùng mới upload xong, API Confirm trả về lỗi `400 STORAGE_INTENT_EXPIRED`. Frontend cần tự động tạo Upload Intent mới và thực hiện upload lại.

---

## 13. Related Services (Các Service Liên quan trong Hệ thống)

- **Mentor Service**: Gọi luồng Upload Intent xác minh tài liệu Mentor.
- **Blog Service**: Gọi luồng Upload Intent tải ảnh bài viết Blog.
- **Chat Service**: Gọi luồng Upload Intent đính kèm file trong tin nhắn.

---

## 14. Frontend Implementation Guide (Hướng dẫn Lập trình Frontend Chi tiết - Staff Engineer Level)

### 14.1 Screen Mapping (Sơ đồ Màn hình -> Component -> API -> Behavior)

#### A. Component Tải Tệp Đa Năng (`SecureFileUploader.tsx`)
- **React Components**: `SecureFileUploader.tsx`, `UploadProgressBar.tsx`, `FileDropzone.tsx`
- **APIs Triggered**:
  1. Purpose-scoped `POST .../upload-intents` (Khi người dùng thả/chọn file)
  2. Direct `PUT uploadUrl` (Tiến trình tải binary)
  3. Purpose-scoped `POST .../confirm` (Khi hoàn tất PUT)
- **Expected Behavior**: Hiển thị thanh tiến trình % upload. Chỉ đánh dấu thành công khi API Confirm trả về 200 OK.

---

### 14.2 Frontend Storage State Machine (Ma trận Trạng thái Client)

```
                       +-----------------------+
                       |      FILE_SELECTED    | (User chọn file ở máy tính)
                       +-----------------------+
                                   |
                       Xin Intent & HTTP PUT
                                   |
                                   v
                       +-----------------------+
                       |    UPLOADING_DIRECT   | (Đang PUT trực tiếp lên R2)
                                   |
                          PUT 200 & Confirm
                                   |
                                   v
                       +-----------------------+
                       |    CONFIRMED_READY    | (Backend xác minh OK -> Hoàn tất)
                       +-----------------------+
```

---

### 14.3 API Calling Lifecycle & Timing (Thời điểm Gọi API Tuyệt đối)

| API Endpoint | App Startup | Select File | Direct PUT | PUT Complete | User Action |
| --- | --- | --- | --- | --- | --- |
| `GET /api/files/capabilities` | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| Purpose `POST .../upload-intents` | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG |
| `PUT uploadUrl` (R2) | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG | ❌ KHÔNG |
| Purpose `POST .../confirm` | ❌ KHÔNG | ❌ KHÔNG | ❌ KHÔNG | ✅ CÓ | ❌ KHÔNG |

---

### 14.4 Error UX Mapping & UI Component Rules (Xử lý Lỗi Cấp độ UX)

#### A. Lỗi Tệp Upload Vượt Quá Hạn Mức Dung Lượng (`HTTP 413`)
- **UI Component**: Banner Thông báo Lỗi Form (`FileErrorBanner.tsx`).
- **Visual State**: Viền đỏ cảnh báo dưới ô chọn tệp.
- **Message**: *"Dung lượng tập tin vượt quá hạn mức tối đa cho phép (15 MiB). Vui lòng nén file hoặc chọn tệp nhỏ hơn."*

---

### 14.5 React Query / State Management Cache Rules (Chiến lược Cấu hình Cache)

| Query Key / Dữ liệu Cache | Stale Time | Garbage Collection (GC) Time | Refetch On Window Focus | Điều kiện Xóa Cache (Invalidation Triggers) |
| --- | --- | --- | --- | --- |
| `['storage', 'capabilities']` | 1 giờ | 24 giờ | `false` | Khởi chạy phiên ứng dụng mới |
