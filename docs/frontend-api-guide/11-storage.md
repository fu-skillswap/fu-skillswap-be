# Storage

## Mục tiêu
File này mô tả flow upload file theo purpose an toàn cho SkillSwap.
Mục tiêu là:
- FE upload đúng cách
- backend kiểm tra object tồn tại trước khi confirm metadata
- private object không được biến thành public URL
- local upload chỉ dùng cho local profile

## API inventory
| Method | Endpoint | Auth | Role | Request DTO | Response DTO | Deprecated/Legacy | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/me/mentor-verification/documents/upload-intents` | Authenticated | mentee or mentor | `MentorVerificationDocumentUploadIntentRequest` | `MentorVerificationDocumentUploadIntentResponse` | - | Private verification upload |
| POST | `/api/me/mentor-verification/documents` | Authenticated | mentee or mentor | `MentorVerificationDocumentUploadRequest` | `MentorVerificationRequestResponse` | - | Confirm private verification object |
| GET | `/api/files/upload-url` | Local profile only | authenticated | query theo runtime | `PresignedUploadResponse` | production-disabled | Không dùng cho bất kỳ production flow nào |
| POST/PUT | `/api/files/local-upload` | Local profile only | authenticated | multipart/raw bytes | `PresignedUploadResponse` | local only | Mô phỏng PUT URL cho dev/test |
| GET | `/api/files/capabilities` | Authenticated | any logged-in user | - | `FileStorageCapabilityResponse` | - | Runtime booleans cho FE bật/tắt file controls |

## Call order chuẩn
### Verification production flow
1. FE chọn `JPG`, `PNG` hoặc `PDF`, kích thước không quá 15 MiB.
2. FE gọi `POST /api/me/mentor-verification/documents/upload-intents` với `filename`, `contentType`, `sizeBytes`.
3. Backend trả `{ uploadIntentId, uploadUrl, expiresAt, requiredHeaders }`; URL sống tối đa 15 phút.
4. FE PUT trực tiếp file tới `uploadUrl` với toàn bộ `requiredHeaders`.
5. FE gọi `POST /api/me/mentor-verification/documents` với `documentType` và `uploadIntentId`.
6. Backend lock intent, HEAD object private, đối chiếu owner/content type/size rồi mới tạo document reference.

### Local upload flow
1. Chỉ dùng khi chạy local profile.
2. FE gọi `POST` hoặc `PUT /api/files/local-upload`.
3. Backend lưu file tạm vào thư mục local.
4. FE chỉ dùng flow này để dev/test local, không dùng ở prod.

## Ý nghĩa field quan trọng
### Verification upload intent response
- `uploadUrl`
  - private URL upload trực tiếp tới storage
- `uploadIntentId`
  - opaque ID duy nhất FE dùng khi confirm; không reuse sau confirm/expiry
- `expiresAt`
  - hết hạn thì tạo intent mới
- `requiredHeaders`
  - header bắt buộc cho PUT, hiện có `Content-Type`

### Policy quan trọng
- TTL presigned hiện tại: 15 phút
- content type verification cho phép: `image/jpeg`, `image/png`, `application/pdf`
- giới hạn verification: 15 MiB/document
- verification object là private; client không nhận public URL, storage key hoặc bucket path

## FE phải làm
- Gọi `GET /api/files/capabilities` sau auth trước khi hiển thị flow chat attachment, mentor-service resource hoặc Blog asset upload. Response không tiết lộ endpoint, bucket hay object key.
- Luôn dùng endpoint upload intent theo đúng purpose.
- Với verification, confirm bằng `uploadIntentId` sau khi PUT hoàn tất.
- Nếu intent hết hạn hoặc confirm fail, tạo intent mới; không retry bằng object key cũ.

## FE không được làm
- Không upload trực tiếp vào bucket public.
- Không gửi `objectKey`, bucket URL, `publicUrl`, filename/MIME/size client claim lại trong request confirm.
- Không dùng local-upload ở production.
- Không gửi multipart lên backend cho flow verification prod.

## FE anti-patterns
- Không confirm metadata trước khi object upload xong.
- Không cho phép người dùng tin rằng file đã được lưu nếu chỉ mới có presigned URL.
- Không dùng generic `/api/files/upload-url` cho production verification/resource/chat/blog flow.

## Response JSON example
### Verification private upload intent
```json
{
  "timestamp": "2026-07-13T10:30:00Z",
  "status": 200,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "uploadUrl": "https://storage.example.com/upload/...",
    "uploadIntentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "expiresAt": "2026-07-27T05:15:00Z",
    "requiredHeaders": { "Content-Type": "application/pdf" }
  }
}
```

## UI mapping
- File picker:
  - chọn file -> xin purpose-scoped upload intent -> PUT -> confirm bằng intent ID
- Verification wizard:
  - không preview qua public storage URL; dùng document metadata server trả về
- Local dev upload:
  - chỉ hiển thị ở profile local, không có ở prod UI

## API success/error behavior
- `POST /api/me/mentor-verification/documents/upload-intents`
  - success: PUT ngay trước `expiresAt`
  - 400/413: filename, content type hoặc size không hợp lệ
- `POST /api/me/mentor-verification/documents`
  - success: refresh verification request canonical state
  - 400: intent expired/object missing/metadata storage mismatch
  - 404: intent không thuộc user hoặc không tồn tại
- `POST/PUT /api/files/local-upload`
  - success: chỉ phục vụ local dev/test
  - 415/413: sai mime type hoặc file quá lớn

## Ghi chú cho AI Agent và FE dev
- `uploadIntentId` là reference duy nhất FE gửi lúc confirm verification.
- Không confirm metadata nếu upload chưa xong hoặc HEAD object chưa pass.
- Storage key chỉ được backend giữ và không được log ở FE telemetry.

## Private mentor-service resources
Tài liệu service là private object: reader không nhận `objectKey`, `publicUrl` hoặc bucket URL. Upload response của resource dùng `{ uploadIntentId, uploadUrl, expiresAt, requiredContentType }` (không phải `requiredHeaders`); FE PUT bytes với `Content-Type` đúng giá trị đó, rồi confirm bằng `uploadIntentId`. FE dùng `POST /api/mentor-service-resources/{resourceId}/download-url` sau khi load metadata; response trả signed URL sống tối đa 10 phút. Không dùng generic `/api/files/upload-url` cho resource này.

Download credential là bearer URL trong TTL 10 phút và response luôn `Cache-Control: no-store, private`. FE không cache URL. Local/test dùng `/api/private-download/{token}` tương đương presigned GET; token không chứa object key.

## Public Blog image assets
Blog images use a different public-asset intent flow; do not reuse verification uploads or private service-resource uploads.

1. `POST /api/me/blog/assets/upload-intents` with `filename`, `contentType` (`image/png`, `image/jpeg`, or `image/webp`).
2. Upload with the returned `uploadUrl` and `requiredHeaders` before `expiresAt`.
3. `POST /api/me/blog/assets/{intentId}/confirm` returns `{ assetId, publicUrl, contentType, sizeBytes }`.
4. Mentor Blog post writes submit `coverAssetId`/`ogAssetId`, never an object key or bucket URL.

The public URL is display data. `assetId` is the only accepted write reference. Private mentor-service resources remain private and must never use this flow.
# Chat private attachments

Chat dùng upload intent và storage key riêng. Client không gửi object key khi confirm/send message. Upload intent hết hạn sau 15 phút; object orphan được cleanup. Download URL là bearer credential TTL ngắn, không phải DRM. Attachment chat hết hạn cấp URL sau 90 ngày và chỉ được xóa vật lý sau 7 ngày grace, trừ dispute hoặc admin hold.

Chat attachment validation accepts only PNG, JPEG, PDF and DOCX with file-name, declared MIME, object-size and magic-byte verification. DOCX must be a valid Office ZIP in the runtime validation layer. Deleted messages revoke attachment access immediately without bypassing retention holds.
