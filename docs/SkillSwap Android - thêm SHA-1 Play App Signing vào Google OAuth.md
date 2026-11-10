# SkillSwap Android – thêm SHA-1 Play App Signing vào Google OAuth

Nov 4, 2026 · @no

## Vì sao cần

Cần thêm 1 Android OAuth client mới cho SHA-1 của Play App Signing, nếu không đăng nhập Google sẽ lỗi trên mọi bản cài từ Google Play.

Khi phát hành qua Play, Google ký lại app bằng khoá Play App Signing. SHA-1 của khoá này khác SHA-1 của bản build EAS đang được đăng ký. Google Sign-In so SHA-1 của app với OAuth client, không khớp thì trả lỗi `DEVELOPER_ERROR`. Đăng nhập bằng email/mật khẩu không bị ảnh hưởng.

## Cần làm gì

Làm trên Google Cloud Console, trong đúng project đang chứa Web client `1009522721292-e7fb6m9h3h62svo27d2s1f7jh5rdeoff.apps.googleusercontent.com`.

1. Vào **APIs & Services → Credentials** (hoặc **Google Auth Platform → Clients**).
2. Bấm **Create credentials → OAuth client ID**.
3. Điền theo bảng dưới rồi bấm **Create**.

| Trường | Giá trị |
| --- | --- |
| Application type | Android |
| Name | SkillSwap Android (Play App Signing) |
| Package name | `com.fptu.exe.skillswap` |
| SHA-1 certificate fingerprint | `DE:D7:D2:F4:35:0D:15:64:1A:B2:07:37:FC:47:8C:56:1C:E1:EB:A2` |

## Lưu ý

- **Giữ nguyên** Android client cũ có SHA-1 `76:42:A8:78:5C:33:A1:9B:1C:A9:F7:A0:55:FC:99:0E:BD:EC:22:6E` (bản EAS dùng cho test). Mỗi SHA-1 cần một client riêng, không sửa client cũ.
- **Không đổi** Web client ID và code BE. BE vẫn xác thực ID token theo Web client ID như hiện tại.
- Client mới có thể mất vài phút đến vài giờ mới có hiệu lực.
- Làm xong thì báo lại nhóm mobile. Nhóm mobile sẽ thử đăng nhập Google trên bản cài từ Play (closed testing) để xác nhận.
