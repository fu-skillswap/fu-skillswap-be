# Kế hoạch: Settlement matrix có kiểm soát cho dispute booking 1:1

## Mục tiêu

Mở rộng quyết định của admin cho dispute chất lượng/kỹ thuật/khác mà vẫn bảo toàn ba nguyên tắc: tổng tiền luôn cân bằng, không sửa lịch sử settlement đã ghi, và người dùng nhận được kết quả dễ hiểu.

## Phạm vi

- Áp dụng cho booking mentoring 1:1 đã thanh toán và đang `UNDER_REVIEW`.
- Giữ nguyên xử lý no-show theo rule hiện có; partial settlement không áp dụng cho no-show.
- Không đưa AI, reschedule, hay cơ chế payout ngoài hệ thống vào đợt này.

## Luồng nghiệp vụ dự kiến

1. Mentee hoặc mentor tạo dispute kèm minh chứng; booking chuyển `UNDER_REVIEW` và tiền được giữ.
2. Counterparty phản hồi trong hạn; hệ thống chuyển case vào hàng đợi admin.
3. Admin xem hai bên, evidence, lịch sử booking và trạng thái settlement hiện tại.
4. Admin chọn quyết định chuẩn hoặc chia tiền một phần; reason code là bắt buộc.
5. Backend kiểm tra action hợp lệ, tổng tỷ lệ, trạng thái booking và số tiền ký quỹ.
6. Trong cùng transaction, backend lưu quyết định bất biến, tạo settlement tương ứng và chốt outcome booking/session.
7. Sau commit, outbox gửi thông báo in-app/email cho mentor và mentee với số tiền, kết quả và lý do dễ hiểu.
8. Nếu cần đảo quyết định, admin tạo reversal riêng; không được sửa hoặc xóa quyết định/settlement cũ.

## Settlement matrix

| Action | Áp dụng | Mentee | Mentor | Platform | Ghi chú |
| --- | --- | ---: | ---: | ---: | --- |
| `CONFIRM_SESSION` | Mọi dispute hợp lệ | 0% | tỷ lệ thông thường | phần nền tảng thông thường | Xác nhận buổi học đã diễn ra. |
| `RELEASE_AS_IS` | `QUALITY_ISSUE`, `TECHNICAL_PROBLEM`, `OTHER` | 0% | tỷ lệ thông thường | phần nền tảng thông thường | Bác dispute, giữ settlement gốc; reason code bắt buộc. |
| `PARTIAL_SETTLEMENT` | `QUALITY_ISSUE`, `TECHNICAL_PROBLEM`, `OTHER` | nhập tỷ lệ | nhập tỷ lệ | nhập tỷ lệ | Tổng đúng 100%; phản ánh bồi hoàn/chia lại có kiểm soát. |
| `CONFIRM_MENTOR_NO_SHOW` | `MENTOR_NO_SHOW` | 100% | 0% | 0% | Giữ rule no-show hiện có. |
| `CONFIRM_MENTEE_NO_SHOW` | `MENTEE_NO_SHOW` | 0% | tỷ lệ thông thường | phần nền tảng thông thường | Giữ rule no-show hiện có. |

## Task triển khai

### T1 — Chuẩn hóa action, reason code và request admin

**Lý do:** Admin hiện chỉ xử lý một số outcome cố định, không đủ cho tranh chấp chất lượng có hoàn một phần.

**Thực hiện:**

- Thêm action `RELEASE_AS_IS` và `PARTIAL_SETTLEMENT` vào contract admin.
- Thêm reason code bắt buộc; admin note bắt buộc với `PARTIAL_SETTLEMENT`, reversal và `OTHER`.
- Request partial nhận `menteeBps`, `mentorBps`, `platformBps`.

**Ràng buộc:** mỗi BPS trong 0–10.000; tổng phải bằng 10.000; partial chỉ cho `QUALITY_ISSUE`, `TECHNICAL_PROBLEM`, `OTHER`.

**Definition of Done:** validation unit test đủ cho action/issue type/reason/BPS; Swagger nêu rõ field và ví dụ; request sai trả lỗi dễ hiểu.

### T2 — Migration và audit record bất biến

**Lý do:** Quyết định tài chính cần truy vết được ai quyết định, lý do gì và phân bổ bao nhiêu.

**Thực hiện:**

- Tạo migration `EXPAND` cho bảng resolution/audit record: booking, admin, action, reason code, note, ba tỷ lệ BPS, snapshot số tiền, thời điểm, reversal reference.
- Thêm unique rule để một dispute chỉ có một quyết định cuối đang hiệu lực.
- Không update/xóa bản ghi đã quyết; reversal tạo record mới liên kết record cũ.

**Definition of Done:** Flyway migrate được trên database trống và database nâng cấp; migration có rollout policy; repository test chứng minh audit record không bị ghi đè.

### T3 — Settlement service dùng allocation snapshot

**Lý do:** Không được để controller/completion service tự tính tiền hoặc làm tổng tiền lệch.

**Thực hiện:**

- Bổ sung một entry point settlement dành riêng cho admin resolution.
- Tính theo toàn bộ tiền ký quỹ snapshot của booking; phần làm tròn thuộc platform để tổng luôn khớp 100%.
- Gắn operation key/idempotency theo resolution record.

**Ràng buộc:** command bị từ chối nếu booking không `UNDER_REVIEW`, payment chưa giữ thành công hoặc settlement cuối đã tồn tại.

**Definition of Done:** unit/integration test cho full refund, release as-is, ba case partial, retry cùng key, concurrent resolve; ledger tổng bằng số tiền ký quỹ.

### T4 — Chốt booking/session trong cùng transaction

**Lý do:** settlement, booking outcome và session không được chốt lệch nhau.

**Thực hiện:**

- `CONFIRM_SESSION`/`RELEASE_AS_IS` kết thúc session như delivered.
- `PARTIAL_SETTLEMENT` có outcome riêng, không tăng nhầm completed counter như full delivery.
- No-show tiếp tục đánh dấu session not delivered.

**Definition of Done:** integration test kiểm tra cùng transaction: decision, settlement, booking state, session status và mentor statistics nhất quán; rollback toàn bộ nếu một bước lỗi.

### T5 — Reversal theo bút toán bù trừ

**Lý do:** Khi quyết định đã ảnh hưởng số dư, sửa record cũ làm mất audit và không an toàn tài chính.

**Thực hiện:**

- Tạo action reversal riêng, tham chiếu resolution gốc và reason code bắt buộc.
- Sinh settlement adjustment event thay vì sửa settlement cũ.
- Nếu tiền đã payout/không đủ khả năng bù trừ, chuyển `MANUAL_FINANCE_REVIEW`; không tự tạo số dư âm không kiểm soát.

**Definition of Done:** test reversal thành công, duplicate reversal bị chặn, insufficient funds chuyển manual review, audit trail truy từ bản ghi mới về quyết định gốc.

### T6 — Response, notification và tài liệu FE/QA

**Lý do:** Hai bên cần biết kết quả chứ không cần thấy công thức nội bộ.

**Thực hiện:**

- Booking/dispute response trả action, reason code, số tiền mentee hoàn, mentor nhận, platform giữ và trạng thái xử lý đơn giản.
- Outbox gửi in-app + email sau commit cho mentor/mentee.
- Cập nhật frontend guide, Swagger và main-flow document.

**Definition of Done:** contract test OpenAPI; notification test không gửi khi transaction rollback; FE/QA có bảng mapping action → UI copy → expected settlement.

## Test release bắt buộc

- Partial với 50/35/15, 100/0/0 và 0/85/15; tổng luôn đúng số tiền ký quỹ.
- Sai tổng BPS, partial trên no-show, resolve booking không `UNDER_REVIEW`, resolve lặp, hai admin resolve đồng thời.
- Rollback khi lưu audit, settlement hoặc finalization lỗi.
- Reversal không sửa record gốc; retry notification không tạo settlement hai lần.
- Migration validation, API documentation validation và regression no-show hiện có.
