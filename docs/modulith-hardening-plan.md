# Kế hoạch hardening Spring Modulith — baseline audit 2026-08-31

## Mục tiêu và nguồn sự thật

Mục tiêu không phải chỉ là Maven compile được. Mục tiêu là không còn
MODULITH_TYPE_REF_VIOLATION trong VS Code và không còn production module nào
phụ thuộc vào implementation của module khác.

Lệnh authoritative cho mọi task:

    mvn "-Dtest=ModulithArchitectureAuditTest" test

Baseline đã đo ngày 2026-08-31:

| Chỉ số | Giá trị hiện tại | Ý nghĩa |
|---|---:|---|
| Violation location | 5.366 | Một type tạo nhiều call-site/field/method diagnostic |
| Distinct (consumer, owner, type) | 149 | Đơn vị giảm dần, mục tiêu cuối là 0 |
| Cross-module internal import trong src/test | 487 | Test đang copy implementation graph của production |
| Flyway version cao nhất | 123 | Chưa có migration cho snapshot field verification mới |
| Java compile | Pass | Không chứng minh Modulith xanh |

Nhóm edge lớn nhất: booking → mentor (16 type), payment → booking (17),
booking → payment (8), identity → booking (7), mentor → identity (8) và
booking → identity (6).

Lưu ý: ModulithTest hiện chỉ kiểm tra taxonomy/named-interface, không gọi
ApplicationModules.verify(). Nó có thể pass trong khi audit authoritative fail.

## Kiến trúc đích và quy tắc bất biến

Mỗi feature module sở hữu entity, repository, service implementation, web DTO
và mapper của chính nó. Chỉ package modules.<owner>.port là public module API
trong đợt này, với NamedInterface("api").

    consumer → owner.port (command/query + immutable record)
    consumer → owner.event (immutable event, after-commit/outbox)
    consumer → shared (ID, Instant, PageResponse, value object)

    consumer -X→ owner.domain / repository / service / controller / dto / JPA entity

Áp dụng cho mọi task:

1. Không expose domain, service, repository, controller, web dto hay package
   infrastructure bằng NamedInterface.
2. API public chỉ dùng primitive/JDK type, UUID, Instant, immutable record,
   contract enum, shared value type và PageResponse; không dùng entity/JPA
   proxy, Page/Pageable, controller DTO, Lombok builder type hay Spring type.
3. Không đổi URL, authorization, state machine, money calculation, idempotency,
   timeout hay JSON semantics chỉ để refactor.
4. Không dùng ObjectProvider, reflection, service locator hay wildcard để che
   dependency liên module.
5. Entity chỉ association tới state của owner; tham chiếu owner khác là UUID
   cộng snapshot cần thiết, không lazy-load aggregate khác.
6. Mỗi batch phải giảm distinct edge; không merge batch nếu audit tăng.

## Inventory phải bị triệt tiêu

| Consumer | Owner | Type nội bộ đang bị dùng | Hướng thay thế |
|---|---|---|---|
| booking | mentor | MentorProfile, MentorService, policy, availability DTO/event | MentorBookingQueryPort với slot/service/policy snapshot; mentor public event |
| booking | identity | User, UserStatus, Google Calendar event/status | identity eligibility/read port; booking calendar command/event |
| booking | payment | PaymentOrder, status, pricing/settlement service | payment quote/checkout/settlement port + payment event |
| booking | notification | notification/email/template service | booking event/intent; notification là consumer |
| payment | booking | Booking, Session, transition executor, BookingTime/status/event | booking snapshot/query + command/event; shared time |
| payment | mentor/identity/chat | mentor service/profile, student/user, conversation service | immutable profile/audience snapshot + events |
| mentor | booking/identity/filestorage/feedback/payment | availability service/DTO, User/academic entity, StoredFile, review DTO, pricing policy | owner query/command records và scalar ID |
| blog | identity/mentor/filestorage | User, MentorProfile/Service, StoredFile/public upload DTO/service | author/share/asset ports |
| chat | booking/course/identity/mentor | Booking, Course, User, MentorProfile | chat access snapshot + public enrollment/booking events |
| forum | identity/notification | User/student/academic entity, NotificationService | forum author projection + notification event/intent |
| identity | booking/mentor/notification | Session/Booking, mentor service/profile, email/template | owner calendar/onboarding ports và events |
| filestorage | identity | StoredFile.owner: User, direct user loads | scalar ownerUserId; identity query only at command boundary |
| admin/catalog/feedback/course | provider internals | provider DTO/entity/repository/service | provider-specific admin/query contracts |

## Task 01 — Chuyển audit thành hard gate duy nhất

**Nội dung**

- Giữ ModulithArchitectureAuditTest là test kiến trúc authoritative và chạy
  ApplicationModules.of(ProjectApplication.class).verify() ở default CI
  architecture job, không chỉ profile tùy chọn.
- Sinh target/modulith-audit-edges.csv có consumer, owner, type, source file và
  source line; file target không commit.
- Sửa ModulithTest để gọi verify(), hoặc đổi tên thành ModuleTaxonomyTest để
  không tạo cảm giác audit đã pass.
- Thêm negative fixture/ArchUnit assertion: class cố ý phụ thuộc provider
  internal phải fail rule.

**Ràng buộc**

- Không bỏ verify(), exclude class khỏi scanner hay filter edge để làm số liệu
  đẹp.

**Definition of done**

- Fixture cố ý fail, fixture bỏ đi pass.
- CI chạy audit trước unit/integration test.
- Report ghi baseline và delta distinct edge cho từng PR.

## Task 02 — Chuẩn hoá surface API và module detection

**Nội dung**

- Xác nhận 13 business module được ExplicitApplicationModuleDetectionStrategy
  phát hiện; infrastructure/bootstrap/demo, SEO và telemetry không phải module.
- Chỉ giữ NamedInterface("api") ở modules.*.port. Gỡ
  NamedInterface("infrastructure") khỏi infrastructure.config,
  infrastructure.realtime, storage/bunny/websocket để chúng không thành public
  business API giả.
- Lập docs/architecture/module-contracts.md: owner, consumer hợp lệ,
  command/query/event, transactional expectation và DTO mapping của từng port.
- Đổi contract public đang lẫn implementation, gồm nested
  MentorContentAccessPort.MentorBlogAuthorSummary, MentorReviewProjection,
  Lombok builder generated type và enum domain, thành top-level immutable
  record/contract enum trong port owner.

**Ràng buộc**

- Không public nguyên dto package để controller khác compile.
- API record không trả User, Booking, MentorService, StoredFile, Page,
  Pageable hay type annotated JPA/Spring.

**Definition of done**

- Mọi package public được document và có package-info.
- ArchUnit fail nếu public API phụ thuộc Spring/JPA/web DTO/entity.
- Không còn named interface trong infrastructure.

**Trạng thái thực hiện (2026-08-31)**

- Hoàn tất: taxonomy 13 business module và `NamedInterface("api")` chỉ còn ở
  `modules.*.port`; infrastructure không còn named interface.
- Hoàn tất: `BlogQueryPort` không còn trả DTO của `mentor`; contract mới là
  immutable `blog.port.BlogMentorArticlePreview`, phía Mentor tự map sang web
  response của Mentor.
- Hoàn tất: loại bỏ `EmailOutboxPort`/adapter persistence không có consumer;
  admin outbox tiếp tục dùng `EmailOutboxAdminPort` projection-only.
- Hoàn tất phần cross-owner: ArchUnit guardrail kiểm tra mọi class trong
  `modules.*.port` (bỏ qua `package-info`) không được phụ thuộc JPA/Spring hoặc
  implementation của module khác. Các persistence port cũ còn trả entity của
  chính owner (`BookingQueryPort`, `UserQueryPort`, `MentorQueryPort`) được giữ
  tạm để không làm vỡ use-case; chúng là đầu vào bắt buộc của Task 05–08 và
  chưa được coi là đạt DoD cuối cùng của toàn bộ kế hoạch.
- Production compile đã xác nhận bằng `mvn -Dmaven.test.skip=true compile`.
  Full clean test-compile vẫn đang bị chặn bởi các fixture cũ dùng builder và
  constructor đã đổi trong các task migration tiếp theo; không dùng lỗi đó để
  nới lỏng boundary của Task 2.

## Task 03 — Refactor test boundary và fixture trước source migration

**Nội dung**

- Phân loại 487 cross-module internal test import: unit cùng owner, integration
  setup, consumer contract, hoặc obsolete test.
- Tạo fixture theo owner trong modules.<owner>.support. Fixture cross-module
  chỉ nhận UUID/snapshot/port, không inject provider repository.
- Chuyển smoke test sang REST/public contract hoặc orchestration fixture, không
  import entity/repository booking, mentor, identity, payment.
- Consumer test mock port record; provider test kiểm chứng implementation và
  persistence của chính owner. Cập nhật constructor tests cùng lúc, không để
  bridge constructor/deprecated path là expected behavior.

**Ràng buộc**

- Không xoá assertion nghiệp vụ chỉ để test qua.
- Repository test được phép dùng repository cùng module, không được thành
  integration coupling với module khác.

**Definition of done**

- Cross-module internal import trong src/test về 0; nếu bắt buộc tạm thời phải
  có allow-list, comment lý do và ngày hết hạn.
- mvn clean -DskipTests test-compile pass sau mỗi batch.
- Mỗi public port có provider contract test và consumer mapping test.

**Trạng thái thực hiện (2026-08-31)**

- Đã có owner fixtures dưới `src/test/.../modules/*/support` với snapshot và
  UUID, cùng negative fixture riêng cho ArchUnit.
- Đã thêm `scripts/check-test-boundary.ps1` và
  `docs/architecture/test-boundary.md`; baseline hiện tại 456 import / 191
  distinct edge, script strict chặn regression.
- Dead `EmailOutboxPort` consumer test đã được loại bỏ cùng port persistence.
- DoD cuối (cross-module internal test import = 0 và clean test-compile pass)
  chưa đạt: 456 import còn lại nằm trong các smoke/integration fixture cũ và
  phải migrate theo thứ tự ở tài liệu test-boundary; không dùng allow-list để
  giả lập đã hoàn thành.

## Task 04 — Cắt infrastructure và bootstrap khỏi business internals

**Nội dung**

- Audit DevDemoDataSeeder, storage/realtime/security/SEO. Seeder gọi owner
  setup port hoặc dev fixture, không inject entity/repository/service liên module.
- SEO chỉ dùng BlogShareQueryPort và MentorShareQueryPort; realtime chỉ consume
  immutable outbox payload, không gọi Chat/Booking service.
- Tách PayOS/S3/Bunny client adapter ở infrastructure/integration. Business
  service chỉ biết port owner khai báo.

**Ràng buộc**

- Infrastructure không phải super-module.
- Seeder không được là lý do giữ DTO/domain cũ public.

**Definition of done**

- Rule infrastructure→module service pass và audit không có edge do
  infrastructure/package annotation.
- Demo seeder và storage integration test dùng owner port/fixture.

**Trạng thái thực hiện (2026-08-31)**

- Hoàn tất SEO boundary: `SitemapController` dùng
  `BlogShareQueryPort.findPublicPublishedSlugs()` và
  `MentorShareQueryPort.findPublicMentorUserIds()`, không còn inject
  `EntityManager` hoặc query entity của Blog/Mentor.
- Hoàn tất realtime boundary ở infrastructure: `RealtimeFanoutService` không
  import Chat/Booking/Notification DTO/event; payload được chuyển vào từ owner
  outbox consumer và gửi nguyên trạng qua STOMP.
- Bunny đã dùng `course.port.CourseVideoProvider`; không có named interface
  trong infrastructure.
- Phần seeder demo và các business service còn inject `StorageGateway` vẫn là
  migration còn lại: cần tách owner setup/storage capability port trước khi
  đánh dấu Task 4 đạt DoD tuyệt đối. Không xoá seeder hoặc nới rule để che
  dependency này.

## Task 05 — Cắt JPA cross-owner: identity và filestorage

**Nội dung**

- Thay mọi ManyToOne User ngoài identity bằng scalar userId: StoredFile.owner,
  MentorVerificationDocument.uploadedBy và các entity còn lại trong inventory.
  Provider validate actor/existence qua identity query port tại command boundary.
- Thay StoredFile leak trong mentor/blog bằng
  FileAssetMetadata(fileId, ownerUserId, originalFilename, contentType,
  sizeBytes, url) từ filestorage port; caller không được nhận entity để gán
  relation.
- Chuyển MentorProfileItemService và public asset flow sang asset ID + ownership
  proof, không gọi PublicAssetUploadService hoặc StoredFile.
- Tách identity read record active user/academic/audience khỏi User,
  StudentProfile, Campus, AcademicProgram và Specialization entity.

**Ràng buộc**

- Có thể giữ FK database để integrity, nhưng Java entity không giữ association
  JPA tới aggregate owner khác.
- Không copy PII không cần thiết vào snapshot.

**Definition of done**

- Edge filestorage→identity, blog/mentor→filestorage và entity-level identity
  leaks của mentor biến mất.
- Asset authorization, private URL expiration và cleanup vẫn có test.

**Trạng thái thực hiện (2026-08-31)**

- Hoàn tất phần `blog/mentor → filestorage`: thêm
  `filestorage.port.PublicAssetUploadPort` với request/intent/metadata record;
  controller và service consumer không còn import DTO, service hoặc entity của
  filestorage.
- Hoàn tất phần mentor asset aggregate: `MentorAchievement` và
  `MentorFeaturedProject` chỉ lưu `pictureFileId`; URL được resolve qua port
  sau khi kiểm tra ownership. Database FK `picture_file_id` được giữ nguyên.
- Hoàn tất phần blog identity scalar-ID: `BlogPost` lưu `authorUserId`, các
  aggregate like/bookmark/follow lưu `userId`/`mentorUserId`; các truy vấn không
  còn dereference `identity.User`. `PublicUserQueryPort` cung cấp read summary
  immutable cho mapper và policy.
- Chưa đạt DoD cuối: một số module ngoài blog (forum, booking, mentor
  verification) vẫn còn entity-level `User` association; test fixture cũ còn
  dựng entity cross-module. Schema blog không cần migration vì tên/type các
  cột UUID hiện hữu được giữ nguyên; cần bổ sung test fixture ID-only trước khi
  đánh dấu Task 5 hoàn thành tuyệt đối.
- Kiểm chứng: `mvn -B -ntp clean compile -Dmaven.test.skip=true` đạt. `clean
  test-compile` hiện vẫn fail ở fixture legacy của nhiều task trước (builder
  `authorUser`/`mentorProfile`, constructor cũ); các fixture blog asset cũng
  còn gọi DTO/service filestorage nội bộ và phải migrate sang
  `PublicAssetUploadPort` ở phần test-hardening kế tiếp.

## Task 06 — Booking owns booking state; mentor chỉ cung cấp capability snapshot

**Nội dung**

- Refactor MentorAvailabilityService và booking services để không nhận
  MentorProfile, MentorService hay mentor DTO. Tạo MentorBookingQueryPort trả
  MentorBookingCapability, ServiceSlotCandidate, EffectiveBookingPolicy và
  MentorPublicAvailability bằng UUID, text, money integer, duration, mode,
  zone và version.
- Booking lưu service/mentor policy snapshot khi quote/create; validate version
  và active state tại command boundary, không dereference mentor entity trong
  availability, reschedule, completion hoặc mapper.
- MentorAvailabilityChangedEvent trở thành public immutable event; booking
  listener nhận UUID/version/range, idempotent và after-commit.
- MentorViolationService được thay bằng MentorDisciplineCommand qua port;
  booking không import mentor violation domain enum.

**Ràng buộc**

- Giữ overlap/lock order, time-zone, capacity, cancellation/refund, security và
  booking state machine.
- Instant/shared time là canonical; BookingTime không được lộ ra liên module.

**Definition of done**

- Toàn bộ 16 booking→mentor type-edge bằng 0.
- Quote/create/reschedule/availability concurrency, policy-version, duplicate
  event và timezone integration test pass.

## Task 07 — Booking ↔ identity/calendar qua command/query/event

**Nội dung**

- Booking dùng identity port cho active user, eligibility và calendar
  capability; không dùng User, UserStatus, GoogleCalendarSyncStatus hay event
  nội bộ.
- Booking publish public lifecycle event (created/accepted/cancelled/rescheduled)
  qua outbox. Identity calendar adapter consume và sở hữu sync state; không
  gọi SessionService trực tiếp.
- Identity dùng booking read model/command cho calendar/session summary, không
  import Session, Booking, MeetingPlatform, BookingStatus hay BookingTime.

**Ràng buộc**

- Payload không có meeting link/token/PII dư, được version và có eventId +
  consumer idempotency.
- Calendar side effect chỉ after commit và replay-safe.

**Definition of done**

- booking→identity (6 types) và identity→booking (7 types) bằng 0.
- Create/cancel/update calendar duplicate/retry test pass.

## Task 08 — Payment owns money; booking owns lifecycle

**Nội dung**

- Refactor BookingPricingPreviewService, CampaignService, CouponService,
  PaymentOrderService, settlement/reconciliation để không nhận Booking,
  BookingIssueResolution, MentorService, User, StudentProfile hay gọi booking
  transition/session service.
- Booking gửi BookingChargeSnapshot vào payment command: bookingId, participant
  IDs, service snapshot, amount basis, quote version, coupon/campaign candidate
  và idempotency key. Payment trả CheckoutQuote/CheckoutSession; payment
  transition phát event để booking apply bằng listener.
- Thay chat/calendar/email direct call từ payment bằng public payment event.
- PaymentGatewayProvider không import booking.domain.BookingTime hoặc payment
  web DTO. Dùng Instant và provider-specific adapter record; HTTP webhook map ở
  inbound adapter rồi phát provider-neutral event.
- Campaign audience dùng identity AudienceSnapshot; coupon dùng
  BookingDiscountContext, không entity.

**Ràng buộc**

- Preserve money integer scale, surcharge/discount ordering, idempotency,
  unique constraints, provider signature verification và retry.
- Payment không synchronous mutate Booking, booking không synchronous settle
  payment aggregate.

**Definition of done**

- payment→booking (17), booking→payment (8), payment→identity (6),
  payment→mentor (2), payment→chat (1), payment→notification (1) bằng 0.
- Checkout, webhook replay/out-of-order, refund/settlement và concurrency pass
  trên PostgreSQL.

## Task 09 — Notification/email trở thành event consumer

**Nội dung**

- Thay NotificationService, EmailDispatchService và HtmlEmailTemplate dependency
  từ booking, mentor, identity, forum, payment bằng versioned domain event hoặc
  narrow NotificationIntentPort. Notification tự resolve recipient/template/
  deep-link/email-outbox.
- Bỏ identity User relation khỏi notification persistence, dùng recipient UUID.
- Tách REST notification response khỏi realtime envelope.

**Ràng buộc**

- Template rendering không được chuyển sang producer.
- Listener transactional-after-commit, deduplicated; email fail không rollback
  business state.

**Definition of done**

- Mọi notification edge bằng 0.
- Outbox/email retry/realtime có duplicate event, failed delivery và
  authorization test.

## Task 10 — Chat/course và forum access contract

**Nội dung**

- Chat thay Booking, Course, User, MentorProfile read bằng ChatAccessSnapshotPort
  hoặc booking/course public event. Chat sở hữu membership, booking/course chỉ
  announce entitlement lifecycle.
- Course event chat consume là public immutable event, không course.domain.Course.
- Forum dùng identity author/program projection và notification event/intent;
  không query User/StudentProfile hoặc NotificationService.
- Report/abuse/discipline dùng mentor command port với actor/target UUID và
  evidence reference.

**Ràng buộc**

- Giữ chat sequence/idempotency, read cursor, safety lock, attachment auth;
  giữ forum cursor/reaction uniqueness/abuse throttle.

**Definition of done**

- chat→booking/course/identity/mentor và forum→identity/notification bằng 0.
- Direct/group entitlement và forum moderation integration test pass.

## Task 11 — Blog, mentor, feedback, catalog và admin contracts

**Nội dung**

- Blog dùng BlogAuthorQueryPort, ContentEntitlementQuery, PublicAssetPort; không
  User/MentorProfile/MentorService/StoredFile/upload service.
- Mentor dùng identity academic record, feedback review page record, catalog
  option record, booking availability port, filestorage asset port, payment
  pricing port; không trả DTO của provider khác.
- Feedback consume booking completion snapshot/event và mentor command/query,
  không booking/mentor entity.
- Admin chỉ consume provider-specific AdminPort command/query model, không copy
  old admin web DTO.
- Catalog/course residual edge sang public option/query contract.

**Ràng buộc**

- Reader response không leak CMS/storage/review/account internal.
- Admin authorization vẫn provider-owned, AdminPort không phải blanket data access.

**Definition of done**

- Toàn bộ residual edge admin, blog, mentor, feedback, catalog, course và
  filestorage bằng 0.
- SEO share endpoint chỉ phụ thuộc share query port.

## Task 12 — Mapper, enum và legacy bridge cleanup

**Nội dung**

- Mọi cross-module mapping chuyển về provider adapter implementing port;
  consumer không gọi provider mapper/builder.
- Response import enum foreign owner (ví dụ BookingResponse →
  PaymentSettlementStatus) đổi sang owner contract enum hoặc value thích hợp.
- Xoá legacy dual-time/DTO bridge sau compatibility period:
  CreateBookingRequest.legacySelectedEndTime, deprecated LocalDateTime
  constructors/accessors và legacy response aliases.

**Ràng buộc**

- Không làm field nullable chỉ để old test compile.
- JSON rename/remove phải có deprecation date hoặc compatibility adapter.

**Definition of done**

- Audit không còn compiler-generated builder type.
- Controller mapper và API contract tests pass.

## Task 13 — DTO request/response review tập trung

Tạo docs/api/contract-matrix.md cho từng endpoint: path/method, actor, request
field, authority/source, validation, stored field, response consumer,
deprecation decision và OpenAPI test. Đây là review có chủ đích, không auto-xóa
field.

### Booking

- Create/quote chỉ giữ client intent: slotId, serviceId, startAt, learning goal
  title/description. End time, mentor/mentee, price, state, capability, session,
  calendar và payment là server-derived.
- Eliminate HTTP use của legacy selectedEndTime và LocalDateTime bridge sau
  documented migration. End time derive từ service/slot snapshot.
- Split BookingResponse rất lớn thành list item, participant detail, admin
  dispute detail và capability block. Chỉ xoá aliases sessionId/sessionStatus,
  status và canComplete sau FE migration; ordinary participant không nhận
  admin settlement/SLA/evidence field.

### Payment

- Checkout request chỉ booking reference và coupon/credit intent. Client không
  gửi price, payable VND, order code, status, provider link, expiry,
  participant hay settlement result.
- PaymentWebhookRequest là inbound adapter DTO, không là payment port/business
  contract. Verify signature trước khi map sang provider-neutral event.
- Tách checkout preview, checkout session và admin financial detail. Review
  checkoutUrl/paymentLink duplicate qua compatibility release + OpenAPI snapshot.

### Chat và notification

- Path/authentication cấp conversation/actor khi phù hợp; reject duplicated body
  ID như ChatTypingRequest.conversationId nếu endpoint path đã có ID. Giữ
  clientMessageId làm idempotency key; content/reply/attachment intent là input.
- Split ConversationResponse thành inbox summary và detail/access block nếu
  consumer khác nhau; read cursor/count/capability do server derive.
- REST NotificationResponse không chứa realtime-only unreadCount và
  realtimeEventKind; tạo realtime envelope riêng.

### Forum và blog

- Forum request không mang author, status, count, reaction/report state hoặc raw
  storage URL. Đổi imageUrls sang confirmed asset IDs + ownership proof qua port,
  trừ compatibility adapter có expiry.
- Forum public feed không leak report/moderation count; reaction state chỉ reader
  authenticated variant.
- Blog author/publish status server-owned. Title/content/category/tag/asset là
  intent; entitledServiceIds validate qua booking contract, asset ID qua
  filestorage contract. Reader detail không dùng CMS analytics/internal field.

**Ràng buộc**

- Frontend/API owner review matrix trước remove field.
- Mỗi rename/remove có JSON deserialize, serialize, auth và OpenAPI snapshot test.

**Definition of done**

- Không request nào hỏi client server-derived state.
- Không response nào đồng thời là public, participant, admin và realtime model.
- Mỗi field còn lại có named consumer và authority.

## Task 14 — Repair Flyway schema trước khi source dùng field mới

**Nội dung**

- Add forward-only migration từ V124; không sửa V1–V123.
- V124 bắt buộc expand mentor_verification_documents với original_filename,
  content_type, size_bytes, file_url đúng MentorVerificationDocument. Backfill
  từ files qua stored_file_id, report rows không backfill được, rồi mới validate
  nullability/length/index sau deployment data hợp lệ.
- Audit scalar-ID conversion Tasks 05–11: preserve UUID/FK nếu cần integrity,
  backfill snapshot, thêm check/index/unique chỉ sau dual-read/write. Theo
  expand → deploy dual read/write → backfill → validate → contract.
- Review V111–V117 booking/payment/notification timestamp/status migration và
  entity schema. Mọi mismatch mới nằm ở V124+, không dùng Hibernate auto-DDL.

**Ràng buộc**

- Production ddl-auto=validate. H2 create-drop không phải schema proof.
- Migration append-only, safe trên database có dữ liệu; không drop/rename trước
  khi app không còn read field cũ.

**Definition of done**

- Fresh PostgreSQL Flyway migrate pass.
- Upgrade fixture pre-V124 giữ ID, URL, content metadata và document access.
- Hibernate validate migrated PostgreSQL pass, không auto-DDL.

## Task 15 — PostgreSQL migration/persistence regression

**Nội dung**

- FlywayPostgresSchemaValidationTest bắt buộc trong CI với PostgreSQL
  service/Testcontainers. Local có thể skip khi không Docker nhưng report phải ghi.
- Test profile đang flyway.enabled=false + H2 ddl-auto=create-drop: chỉ dùng cho
  fast test và ghi rõ non-authoritative.
- Thêm migration data tests V124+, booking/payment unique/money, chat
  cursor/sequence, notification dedupe, forum reaction/report và blog
  asset/entitlement.
- Refactored aggregate integration chạy trên migrated PostgreSQL; chỉ mock
  external gateway/S3/mail, không mock schema.

**Ràng buộc**

- Không chấp nhận test pass do Hibernate tạo cột Flyway thiếu.
- CI không được skip Testcontainers.

**Definition of done**

- Flyway migrate + Hibernate validate + targeted Postgres integration pass trên
  DB mới và upgrade fixture DB.
- Report chỉ rõ migration test executed/skipped.

## Task 16 — Behavioral và contract regression theo module

**Nội dung**

- Booking: quote/create, lock, reschedule, cancellation, attendance, completion,
  dispute/SLA, calendar, payment event replay.
- Payment: quote/coupon/campaign/credit, checkout, webhook signature/replay,
  refund, settlement, payout, concurrency.
- Chat: entitlement event, direct/group membership, idempotent send, read cursor,
  attachment, safety lock.
- Notification: outbox dedupe, unread/read, email retry, realtime envelope.
- Forum: post/comment/reaction/report/moderation, author projection, asset auth.
- Blog: author entitlement, asset confirmation, publish/moderate, reader safety,
  follow/engagement/share metadata.

**Ràng buộc**

- Test input/expected response dùng public HTTP DTO hoặc owner port, không removed
  entity/service signature.
- Assert money/time/auth/idempotency, không chỉ status 200.

**Definition of done**

- Mỗi port có provider implementation test, consumer contract test, negative auth
  test và duplicate-event test nếu async.
- Full mvn test pass với CI test configuration.

## Task 17 — Final CI, IDE và release gate

**Nội dung**

- CI order: clean test-compile → Modulith audit → ArchUnit → PostgreSQL Flyway
  validate → migration integration → full test → OpenAPI/JSON diff.
- Build check fail khi production import module khác domain/service/repository/
  controller/dto; report riêng, stricter cho test source.
- Chỉ sau toàn bộ refactor mới clean workspace:
  mvn clean -DskipTests test-compile, VS Code Java: Clean Java Language Server
  Workspace, rồi Reload Window.

**Ràng buộc**

- Không baseline suppression/frozen ArchUnit store, failed migration ignored hay
  allowedDependencies wildcard.

**Definition of done**

- ModulithArchitectureAuditTest: 0 violation / 0 distinct edge.
- ArchUnit và compile pass với target sạch.
- PostgreSQL Flyway+validate và full test pass.
- OpenAPI diff/contract matrix approved; VS Code hết Modulith diagnostics sau
  fresh workspace reload.

## Thứ tự thực hiện bắt buộc

1. Task 01–03: đo đúng lỗi và ngăn test duy trì graph implementation cũ.
2. Task 04–05: bỏ technical/public interface giả và JPA cross-owner root cause.
3. Task 06–09: giải booking–mentor, booking–identity, booking–payment,
   producer–notification.
4. Task 10–12: hoàn tất chat/forum/blog/mentor/admin residual và API shape.
5. Task 13–16: freeze DTO behavior, schema và behavioral regression trước khi
   xoá compatibility path.
6. Task 17: bật gate không thể bypass.

Không task nào được đánh dấu hoàn thành từ incremental compile. Mỗi Definition
of Done phải được chứng minh từ target sạch và audit inventory phải giảm.
