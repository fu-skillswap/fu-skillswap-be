# `/api/auth/google/mobile` chưa được mở trong SecurityConfig

**Mức độ: chặn hoàn toàn.** Đăng nhập trên mobile không thể thành công cho tới
khi sửa. Sửa mất **một dòng**.

Phát hiện 2026-10-03 khi chạy app thật trên máy ảo Android.

---

## Triệu chứng

App lấy được Google ID Token hợp lệ, gửi lên `POST /api/auth/google/mobile`, và
nhận về:

```json
{"status":401,"code":"AUTH_1001","message":"Chưa xác thực người dùng"}
```

ID Token hoàn toàn hợp lệ — app tự kiểm trước khi gửi:

```
aud KHỚP web client id (aud=1009522721292-....apps.googleusercontent.com)
iss=https://accounts.google.com
sub có, exp=1791037078
KHÔNG có claim nonce (đúng như dự kiến cho đường mobile)
```

## Nguyên nhân

`infrastructure/config/SecurityConfig.java` mở các đường sau:

```java
"/api/auth/google",
"/api/auth/google/nonce",
"/api/auth/refresh",
"/api/auth/logout",
```

**Thiếu `/api/auth/google/mobile`.** `requestMatchers("/api/auth/google")` của
Spring Security khớp **đúng** đường đó, không khớp đường con, nên request rơi
xuống `auth.anyRequest().authenticated()` ở cuối chuỗi và bị chặn **trước khi**
tới `AuthController.loginWithGoogleMobile()`.

Controller (`AuthController.java:137`) và toàn bộ tầng dịch vụ
(`IdentityService.loginWithGoogleMobile`, `GoogleAuthService.verifyMobileToken`,
`GoogleMobileTokenReplayService`) đều đã có và đúng — chỉ mình lớp bảo mật chưa
biết tới đường này.

## Bằng chứng

Cùng một body rác, hai endpoint, hai kết quả khác hẳn:

```
POST /api/auth/google        -d '{"credential":"rac"}'
  -> 400 VAL_3001 "must not be blank" (field: nonce)
     => controller CÓ chạy, nó validate được body

POST /api/auth/google/mobile -d '{"credential":"rac"}'
  -> 401 AUTH_1001 "Chưa xác thực người dùng"
     => Spring Security chặn, controller KHÔNG chạy
```

Nếu controller có chạy, credential rác phải ra `400 AUTH_1006`
(`OAUTH_VERIFICATION_FAILED`) chứ không bao giờ ra `401 AUTH_1001` — toàn bộ
đường mobile chỉ có hai mã 401 là `UNAUTHENTICATED` và `SESSION_EXPIRED`, và
không nhánh nào trong `verifyMobileToken` ném chúng.

## Cách sửa

`infrastructure/config/SecurityConfig.java`, thêm một dòng vào danh sách
`permitAll()` đầu tiên:

```diff
                                 "/api/auth/google",
+                                "/api/auth/google/mobile",
                                 "/api/auth/google/nonce",
                                 "/api/auth/refresh",
                                 "/api/auth/logout",
```

## Cách kiểm sau khi deploy

```bash
curl -s -X POST https://api.skillswap.asia/api/auth/google/mobile \
  -H "Content-Type: application/json" -d '{"credential":"rac"}'
```

**Đạt** khi trả `400` với `AUTH_1006` (hoặc `VAL_3001`) — nghĩa là controller đã
chạy và từ chối credential rác.
**Chưa đạt** khi vẫn trả `401 AUTH_1001`.

## Một chỗ KHÔNG cần sửa, xin đừng đụng vào

`GoogleAuthService.resolveMobileClientId()` lấy `GOOGLE_MOBILE_CLIENT_ID`, và
nếu trống thì lùi về `GOOGLE_CLIENT_ID`. **Để trống là đúng.**

Thư viện `@react-native-google-signin/google-signin` đặt `aud` của ID Token bằng
**web client id**, không phải Android client id. Android OAuth client chỉ dùng
để Google đối chiếu chữ ký của app (package + SHA-1); nó **không bao giờ** xuất
hiện trong `aud`. Nếu đặt `GOOGLE_MOBILE_CLIENT_ID` thành Android client id thì
`expectedClientId.equals(payload.getAudience())` sẽ trượt và đăng nhập hỏng với
`400 AUTH_1006` — một lỗi khác, trông rất giống, nhưng ở chỗ khác.
