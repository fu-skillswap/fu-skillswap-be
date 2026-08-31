# Test boundary và fixture policy

Task 03 áp dụng quy tắc giống production cho `src/test`:

- Unit test của owner được phép dùng entity/repository/service của **chính
  owner**.
- Consumer test chỉ được mock `modules.<owner>.port`, consume immutable event,
  hoặc dùng `UUID`/snapshot từ `modules.<owner>.support`.
- Integration test của một owner có thể dùng database/repository của owner đó;
  dữ liệu module khác phải đi qua public REST hoặc port contract.
- Smoke test không được import entity/repository/service/controller của business
  module khác. Nếu test cũ cần setup sâu, tách provider fixture vào package
  `modules.<owner>.support` và expose snapshot/UUID thay vì persistence object.
- `architecture` negative fixture là ngoại lệ có chủ đích: nó phải import một
  internal type để chứng minh guardrail thực sự fail, không được dùng trong test
  nghiệp vụ.

## Audit command

Chạy từ repository root:

```powershell
pwsh -File scripts/check-test-boundary.ps1 -Strict
```

Baseline hiện tại là **456 import / 191 distinct edge**. Mỗi batch phải giảm
baseline hoặc giữ nguyên khi chỉ đổi test nội bộ cùng owner; không được thêm
cross-module internal import mới. Script không sửa test và không xoá assertion.

## Refactor order

1. Smoke và admin integration: chuyển setup liên module sang REST/port fixture.
2. Booking/payment/chat/notification: mock port record, không tạo entity owner
   khác trong test.
3. Mentor/identity/filestorage: dùng snapshot fixture, chỉ provider test mới
   chạm repository/entity.
4. Xoá allow-list tạm thời và đặt baseline về 0 khi migration source hoàn tất.
