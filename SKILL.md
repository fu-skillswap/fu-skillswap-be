# SkillSwap Backend AI Working Guide

## 1. Mission
- Đây là backend Spring Boot cho SkillSwap.
- Mục tiêu hiện tại là xây một codebase sạch, ổn định, dễ mở rộng, ưu tiên hiệu năng và độ an toàn khi traffic tăng.
- Không biến base project thành monolith business phức tạp quá sớm.

## 2. Mandatory Working Rules
- Chỉ xử lý file, package, module liên quan trực tiếp đến yêu cầu hiện tại.
- Tuyệt đối không tự động quét/review toàn bộ project để tiết kiệm quota, trừ khi người dùng ghi rõ: `Review toàn bộ`.
- Mọi config hoặc thông tin cần người dùng tự cung cấp phải được hỏi rõ; không tự bịa env, key, domain, credential hoặc business rule.
- Khi sửa code phải bám sát kiến trúc và style hiện có, không refactor lan rộng nếu chưa thật sự cần.
- Mọi business logic mới phải ưu tiên cho khả năng chịu tải cao, giảm query thừa, giảm giữ transaction lâu, giảm object load không cần thiết.
- Tất cả lỗi trả ra cho FE phải rõ nghĩa, tiếng Việt UTF-8, không dùng message chung chung kiểu `An unexpected error occurred` nếu có thể xác định đúng nguyên nhân nghiệp vụ.

## 3. Tech Stack Standards
- Java 17+
- Spring Boot 3.x
- Spring Security 6.x
- Spring Data JPA / Hibernate
- PostgreSQL
- Docker / docker-compose
- OAuth2 Google login + JWT nội bộ của hệ thống
- ID chuẩn toàn hệ thống: UUID v7, không dùng mix `Long` và `UUID`

## 4. Architecture Rules
- Ưu tiên Layered Architecture:
  - Controller
  - Service
  - Repository
- Service chứa business logic.
- Repository chỉ giữ query/persistence logic.
- Controller mỏng, chỉ validate input, gọi service, trả response.
- Không đẩy business logic xuống controller.
- Không nhồi query hoặc mapping phức tạp vào controller.

## 5. Coding Style
- Code phải sạch, defensive, production-ready.
- Tên class, method, field rõ nghĩa theo domain.
- Tránh method quá dài; nếu method bắt đầu ôm nhiều nhánh logic thì tách private method.
- Ưu tiên early return / guard clause để giảm nesting.
- Comment chỉ viết ở chỗ logic khó; không comment kiểu mô tả điều hiển nhiên.
- Hạn chế tạo abstraction mới nếu project hiện tại chưa cần.
- Bám pattern đang dùng trong repo trước khi nghĩ ra pattern mới.

## 6. Entity and Persistence Rules
- Mọi entity mới phải dùng UUID v7.
- Kiểm tra kỹ mapping `@OneToOne`, `@ManyToOne`, `@OneToMany`, `@MapsId`, cascade, orphanRemoval, nullable.
- Không `save`/`merge` entity detached bừa bãi.
- Khi tạo entity liên quan tới `User`, ưu tiên fetch managed entity từ DB rồi mới gán quan hệ.
- Không truyền `null` vào repository query method.
- Chỉ dùng `saveAndFlush()` khi có lý do rõ ràng; mặc định ưu tiên `save()`.
- Tránh EAGER nếu không thật sự bắt buộc.
- Phải chú ý N+1 query khi trả list hoặc detail có quan hệ.
- Với dữ liệu đọc nhiều, ít đổi, cân nhắc cache.

## 7. API Design Rules
- API response phải nhất quán theo format chung của project.
- HTTP status phải đúng semantics:
  - `200 OK` cho read/update thành công
  - `201 Created` cho create resource thật sự
  - `204 No Content` chỉ dùng khi thật sự không trả body
  - `400 Bad Request` cho input sai/thiếu
  - `401 Unauthorized` khi chưa xác thực
  - `403 Forbidden` khi không đủ quyền
  - `404 Not Found` khi resource không tồn tại
  - `409 Conflict` khi xung đột trạng thái/nghiệp vụ
- `PUT` dùng cho thay thế/cập nhật đầy đủ theo resource contract.
- `PATCH` dùng cho cập nhật một phần hoặc chuyển trạng thái nhẹ.
- Swagger/OpenAPI mô tả phải chuyên nghiệp, rõ cho FE cách login, authorize và dùng token.

## 8. Validation and Exception Rules
- Request DTO phải dùng `@Valid`.
- Field bắt buộc phải có `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Email`... phù hợp.
- Service phải có guard clause cho các case null/empty/invalid state.
- Không để NPE, Hibernate `null identifier`, hoặc lỗi framework tự nổ thành `500`.
- Dùng exception nghiệp vụ rõ ràng, message tiếng Việt rõ trường hợp.
- Không log lộ token, secret, credential, hoặc nội dung nhạy cảm.

## 9. Security Rules
- Xác thực hiện tại dựa trên Google OAuth2 login và JWT nội bộ.
- Không thêm flow password local nếu chưa có yêu cầu rõ.
- Kiểm tra chặt quyền theo role và ownership.
- Cẩn thận IDOR: endpoint nhận `id`, `uuid`, `userId` phải check đối tượng đó có được user hiện tại truy cập không.
- Tránh SQL Injection: chỉ dùng JPA/parameterized query an toàn.
- Cẩn thận XSS stored/reflected ở các field text do user nhập; backend cần validate và chặn input nguy hiểm ở mức phù hợp.

## 10. Performance Rules
- Ưu tiên tối ưu cho high traffic ngay từ lúc chọn business logic.
- Tránh query trong loop.
- Tránh load full entity graph nếu response chỉ cần vài field.
- Dùng projection/DTO query nếu phù hợp.
- Với catalog ít đổi như campus/program/specialization/tag, ưu tiên cache.
- Index phải đi kèm các cột filter/search/join quan trọng.
- Không giữ transaction mở khi gọi external service.
- Với login Google, ưu tiên verify local nếu flow hiện tại đã hỗ trợ, giảm external call trong request path.
- Cân nhắc RAM VPS nhỏ, không thiết kế flow đòi giữ list lớn trong memory.

## 11. Testing Rules
- Mặc định chỉ test phần vừa sửa hoặc module liên quan trực tiếp.
- Chỉ khi người dùng yêu cầu mới chạy unit/integration test rộng hơn hoặc toàn project.
- Nếu chỉ sửa docs/README/SKILL thì không cần chạy test.
- Khi báo kết quả test phải nói rõ:
  - test nào đã chạy
  - test nào chưa chạy
  - build có pass không

## 12. Git Rules
- Branch prefix phải theo đúng rule user đã chốt:
  - `feat/`
  - `fix/`
  - `hotfix/`
  - `refactor/`
  - `docs/`
  - `chore/`
- Commit message format:
  - `<type>(<scope>): <description>`
- `description` viết bằng động từ ở thì hiện tại, chữ đầu không viết hoa, không chấm cuối câu.
- Không tự merge lên `main` nếu yêu cầu chưa thật rõ; nếu user nói merge `main`, nên xác nhận lại vì đây là thao tác nhạy cảm.

## 13. Project-Specific Business Notes
- Project này dùng cho SkillSwap, bối cảnh FPT University.
- Swagger mô tả cần chuyên nghiệp, có hướng dẫn FE:
  - lấy `idToken` từ Google phía FE
  - gọi `POST /api/auth/google`
  - nhận access token / refresh token
  - bấm `Authorize` trong Swagger bằng access token để test API protected
- `SYSTEM_ADMIN` và `ADMIN` là hai vai trò khác nhau:
  - `SYSTEM_ADMIN` chỉ quản trị hệ thống
  - không mặc định ôm toàn bộ quyền vận hành như `ADMIN` nếu điều đó làm rối UX và boundary
- `STAFF` đã bị loại khỏi hệ thống, không tái thêm lại nếu chưa có yêu cầu mới.

## 14. Mentor Verification Domain Rules
- Flow user hiện tại xoay quanh:
  - request to become mentor
  - upload verification documents
  - submit verification request
  - withdraw request
  - view detail / timeline / documents
- Flow admin hiện tại xoay quanh:
  - view queue
  - view request detail
  - request revision
  - approve
  - reject
  - soft lock review request
- Trạng thái quan trọng:
  - `DRAFT`
  - `PENDING_REVIEW`
  - `NEEDS_REVISION`
  - `APPROVED`
  - `REJECTED`
  - `WITHDRAWN`
- Rule đã chốt:
  - `NEEDS_REVISION`: sửa trên request cũ
  - `REJECTED`: request cũ bị khóa, muốn nộp lại phải tạo request mới
  - `WITHDRAWN`: request cũ bị khóa, muốn làm lại phải tạo request mới
  - `APPROVED`: flow hoàn tất, request khóa
- Document rule:
  - có `documentType`
  - mỗi loại có thể nhiều file
  - chỉ 1 file `isPrimary = true` cho mỗi loại
  - nếu thay file chính thì file cũ thành inactive/version cũ
- File upload hiện tại:
  - ảnh dùng Cloudinary
  - tài liệu như PDF dùng Cloudflare R2
- Nếu nghiệp vụ yêu cầu file limit, phải enforce rõ ở BE; ví dụ ảnh upload qua BE tối đa 1MB nếu user đã chốt rule đó.

## 15. Mentor Discovery Domain Rules
- Discovery phải kết hợp:
  - search/filter
  - smart matching / reranking
- Recommendation chỉ lấy mentor:
  - đã active
  - hồ sơ hiển thị đủ dữ liệu theo rule hiện tại
- Search flow:
  - query theo keyword + filter trước
  - sau đó dùng smart matching để rerank kết quả
- Response cần đủ cho FE render card/list:
  - avatar
  - headline
  - current position/company nếu có
  - expertise tags
  - rating
  - completed sessions
  - availability signal nếu có

## 16. Documentation Rules
- `README.md` và docs chính nên viết tiếng Việt, chỉ giữ lại keyword kỹ thuật bằng tiếng Anh khi cần.
- Nếu tạo tài liệu tổng hợp API cho FE, có thể dùng `apidocument.md`.
- Tài liệu phải bám đúng code đang chạy, không ghi wishful thinking.

## 17. Response Style for Future AI
- Trả lời ngắn gọn, thẳng, kỹ thuật.
- Không giải thích khái niệm cơ bản nếu người dùng không hỏi.
- Nếu request thiếu thông tin quan trọng và việc tự đoán có rủi ro cao, phải dừng và hỏi rõ.
- Khi sửa code, nên nói:
  - bối cảnh kỹ thuật ngắn
  - file sửa
  - lý do chọn cách làm
  - rủi ro còn lại hoặc thứ người dùng cần tự can thiệp

## 18. Definition of Done
Chỉ coi là xong khi:
- Code compile được
- Luồng chính không còn lỗi hiển nhiên
- Exception trả ra rõ nghĩa
- Không phá outcome cũ của API
- Test liên quan đã chạy nếu thay đổi code
- Nếu còn phần người dùng phải tự làm (env, deploy, secret, VPS), phải ghi rõ
