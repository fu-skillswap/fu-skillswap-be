# Kế hoạch cải tiến và tối ưu hóa Database & Nghiệp vụ SkillSwap

Tài liệu này trình bày giải pháp kỹ thuật, đánh giá hiệu quả và phương án thực hiện cho 7 mục yêu cầu cải tiến cấu trúc database, ràng buộc và xử lý bất đồng bộ cho SkillSwap.

---

## 1. Đồng bộ trạng thái khóa tài khoản sang hồ sơ Mentor

### Cách giải quyết
*   Trong service [AdminUserService.java](file:///E:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/modules/system/service/AdminUserService.java#L33-L91), tại method `changeUserStatus`:
    *   Khi biến `ban = true`, sau khi chuyển trạng thái của `User` sang `BANNED`, hệ thống sẽ thực hiện truy vấn hồ sơ `MentorProfile` tương ứng của user đó.
    *   Nếu tồn tại `MentorProfile`, thực hiện cập nhật `isAvailable = false` và cập nhật status của MentorProfile về trạng thái tạm ngưng (hoặc giữ nguyên trạng thái nhưng tắt cờ nhận lịch).
*   Đồng thời, vô hiệu hóa (set `isActive = false`) cho tất cả các slot rảnh tương lai (`start_time > now()`) của mentor này trong bảng `mentor_availability_slots` chưa được book.

### Độ hiệu quả
*   **Cao**: Đảm bảo tính toàn vẹn dữ liệu. Bị ban đồng nghĩa với việc không thể nhận thêm lịch mới ngay lập tức.
*   **Tối ưu**: Tránh được việc thực hiện các câu query kiểm tra lồng nhau phức tạp ở tầng hiển thị.

---

## 2. Đánh giá cấu trúc thực thể `UserRole` & `UserRoleId`

### Phân tích nghiệp vụ
*   **Có hỗ trợ thêm Role mới không?**
    *   **Có**: Cấu trúc này dễ dàng mở rộng khi có Role tĩnh mới (ví dụ: `ALUMNI`, `STUDENT_LEADER`). Chỉ cần bổ sung giá trị vào Enum [RoleCode](file:///E:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/shared/constant/RoleCode.java) và insert bản ghi mới vào bảng `user_roles`.
*   **Có hỗ trợ phân quyền động (Dynamic Authorization) không?**
    *   **Không**: Cấu trúc này không hỗ trợ phân quyền động (tạo role mới từ UI, gán các permission động như `VIEW_REPORT`, `EDIT_POST`). Vì `RoleCode` là một Enum cứng ở tầng Java. Nếu muốn phân quyền động, Role phải là một bảng thực thể riêng biệt (`roles` với các trường `id`, `code` kiểu String, `name`) và liên kết nhiều-nhiều với `permissions`.
*   **Đề xuất gộp / Đơn giản hóa**:
    *   Vì quy mô dự án hiện tại chỉ dùng các role tĩnh cố định (`MENTEE`, `MENTOR`, `ADMIN`, `SYSTEM_ADMIN`), cấu trúc thực thể `UserRole` hiện tại đang bị **phức tạp hóa quá mức (over-engineered)** do lưu thêm thông tin kiểm toán (`assigned_by`, `assigned_at`) cho riêng quyền admin.
    *   **Giải pháp gộp**: 
        1. Giữ nguyên bảng vật lý `user_roles (user_id, role)` trong DB.
        2. Ở code Java, thay thế thực thể `UserRole` và `UserRoleId` phức tạp bằng cách sử dụng `@ElementCollection` trực tiếp trong thực thể `User`:
           ```java
           @ElementCollection(targetClass = RoleCode.class, fetch = FetchType.EAGER)
           @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
           @Column(name = "role")
           @Enumerated(EnumType.STRING)
           private Set<RoleCode> roles = new HashSet<>();
           ```
        3. Lịch sử gán quyền Admin sẽ được ghi nhận tập trung qua bảng `audit_logs` (hiện tại đã có sẵn cơ chế log), không cần thiết phải lưu trực tiếp trường `assigned_by` trên bảng mapping quyền nữa.

### Độ hiệu quả
*   **Rất cao**: Loại bỏ được 2 file class dư thừa (`UserRole`, `UserRoleId`), giảm thiểu boilerplate code, đơn giản hóa logic kiểm tra quyền trong Spring Security, giảm số lượng câu lệnh insert/query và tối ưu hóa hiệu năng khởi tạo session.

---

## 3. Cải tiến cơ chế lưu trữ Avatar và giữ cấu trúc `StoredFile`

### Phân tích & Giải pháp cải tiến Avatar
*   Do `StoredFile` cần giữ lại làm nền tảng cho việc tải file/ảnh ở tính năng forum/chat sau này, chúng ta sẽ tối ưu cơ chế lưu Avatar như sau:
    *   **Hiện trạng**: Trường `avatar_url` trong `users` lưu text URL trực tiếp (từ Google OAuth hoặc Cloudinary URL). Điều này dẫn đến khó quản lý nếu người dùng upload ảnh lên Cloudinary mà sau đó thay đổi ảnh khác (gây rác trên Cloudinary do không biết ảnh cũ nào đã bị bỏ).
    *   **Giải pháp cải tiến**: 
        1. Khi người dùng tải lên avatar mới, hệ thống sẽ thực hiện upload qua `CloudinaryService`.
        2. Tạo một bản ghi trong thực thể `StoredFile` với mục đích `FilePurpose.AVATAR`, lưu đầy đủ public ID và URL.
        3. Cập nhật trường `avatar_url` của `User` tham chiếu tới public URL của file vừa tạo.
        4. Thiết lập quan hệ liên kết (hoặc ghi nhận ownership) để khi người dùng thay đổi avatar mới, hệ thống tự động tìm file ảnh cũ từ `StoredFile` và gọi Cloudinary xóa ảnh cũ đi.

### Độ hiệu quả
*   **Cao**: Đảm bảo tài nguyên lưu trữ Cloudinary luôn được giải phóng, tránh lãng phí dung lượng và dễ dàng quản lý vòng đời của tất cả các file phương tiện (media) tải lên hệ thống.

---

## 4. Đồng bộ logic Soft-delete (Xóa mềm)

### Cách giải quyết
*   Khi thực hiện soft-delete tài khoản `User` (cập nhật `deleted_at = NOW()`):
    *   Sử dụng một cơ chế đồng bộ trong Service (hoặc thông qua Spring Application Events để đảm bảo nguyên tắc phân tách module của Spring Modulith).
    *   Khi có sự kiện `UserDeletedEvent` phát ra:
        1. **Module Academic**: Thực hiện ẩn/xóa mềm `StudentProfile`.
        2. **Module Mentor**: Cập nhật trạng thái `MentorProfile` sang `DRAFT`, set `isAvailable = false`.
        3. **Module Booking**: Vô hiệu hóa toàn bộ slot rảnh trong tương lai (`isActive = false` cho tất cả slots của mentor có `start_time > now()` và `is_booked = false`). Đối với các booking đang ở trạng thái `PENDING`, tự động chuyển sang `REJECTED` hoặc `CANCELLED` để hoàn trả slot.

### Độ hiệu quả
*   **Rất cao**: Triệt tiêu hoàn toàn dữ liệu rác (zombie data) hiển thị trên giao diện người dùng sau khi tài khoản liên kết đã bị xóa mềm.

---

## 5. Ngăn chặn trùng đè lịch học một phần (Overlapping Slots)

### Cách giải quyết
*   **Tầng Java Application**: Trước khi tạo mới một slot trong `generateSlotsForOpenRule` hoặc khi mentor tạo slot thủ công, thực hiện kiểm tra xem có slot nào đang hoạt động trùng đè thời gian hay không.
    *   Công thức kiểm tra overlap giữa slot mới `(S_new, E_new)` và slot đã có `(S_exist, E_exist)` của cùng một mentor:
        `S_new < E_exist AND E_new > S_exist`
    *   Viết query trong [MentorAvailabilitySlotRepository.java](file:///E:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/modules/booking/repository/MentorAvailabilitySlotRepository.java):
        ```java
        @Query("""
            SELECT EXISTS (
                SELECT 1 FROM MentorAvailabilitySlot slot
                WHERE slot.mentorProfile.userId = :mentorId
                  AND slot.isActive = true
                  AND slot.startTime < :endTime
                  AND slot.endTime > :startTime
            )
        """)
        boolean hasOverlappingActiveSlot(
            @Param("mentorId") UUID mentorId, 
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime
        );
        ```
*   **Tầng Database (Khuyến nghị cho PostgreSQL)**: Tạo một chỉ mục loại trừ (Exclusion Constraint) sử dụng phần mở rộng `btree_gist` để chặn cứng việc chèn hoặc cập nhật các dòng trùng đè thời gian của cùng một mentor.

### Độ hiệu quả
*   **Tuyệt đối**: Ngăn chặn 100% tình trạng Mentor bị book 2 ca học chồng chéo giờ lên nhau, giải quyết triệt để lỗi logic xung đột lịch hẹn.

---

## 6. Ràng buộc bảo vệ lịch rảnh và đặt lịch ở mức Database

### Cách giải quyết
*   **Tìm kiếm Mentor**: Sửa đổi câu query JPQL tìm kiếm trong [MentorProfileRepository.java](file:///E:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/modules/mentor/repository/MentorProfileRepository.java#L26-L476), bổ sung điều kiện lọc trạng thái hoạt động của User:
    ```sql
    WHERE mp.status = :mentorStatus
      AND u.status = 'ACTIVE' -- << THÊM ĐIỀU KIỆN NÀY để lọc bỏ các user bị Banned/Inactive
      AND mp.verifiedAt IS NOT NULL
    ```
*   **Ràng buộc tạo lịch rảnh & đặt lịch**:
    *   Viết trigger hoặc bổ sung ràng buộc kiểm tra trước khi ghi nhận Booking: Trạng thái MentorProfile bắt buộc phải là `ACTIVE` và có `verified_at is not null`.

### Độ hiệu quả
*   **Cao**: Đảm bảo an toàn tuyệt đối trước các lỗi bảo mật vòng qua API kiểm tra hoặc lỗi logic tầng ứng dụng. Người dùng bị ban hoặc chưa kích hoạt mentor sẽ lập tức biến mất khỏi trang tìm kiếm.

---

## 7. Đổi cơ chế sinh slot sang Bất đồng bộ (Async Batch Job)

### Cách giải quyết
*   Thay vì chạy một tiến trình tuần tự đơn luồng đồng bộ đè nặng lên một transaction duy nhất, chúng ta sẽ chuyển sang xử lý bất đồng bộ sử dụng Spring `@Async` và bộ xử lý ThreadPool chuyên dụng:
    1. Định nghĩa một ThreadPool TaskExecutor trong file cấu hình hạ tầng.
    2. Trong `MentorAvailabilityService`, tạo method bất đồng bộ chuyên biệt để sinh slot cho riêng một mentor:
       ```java
       @Async("slotGenerationExecutor")
       @Transactional
       public void generateSlotsForMentorAsync(UUID mentorUserId, LocalDate startDate, LocalDate endDate) {
           // Thực hiện sinh slot độc lập cho một mentor
       }
       ```
    3. Trong Scheduler [MentorSlotGenerationScheduler.java](file:///E:/FPTU/SU26/EXE201/project/src/main/java/com/fptu/exe/skillswap/modules/booking/scheduler/MentorSlotGenerationScheduler.java), thay vì gọi trực tiếp xử lý đồng bộ, scheduler chỉ thực hiện truy vấn danh sách ID của các active mentor, sau đó gửi các task bất đồng bộ vào Executor.
    4. Quá trình xử lý sẽ chạy song song trên nhiều luồng, giải phóng Scheduler thread ngay lập tức và tăng tốc độ xử lý lên gấp nhiều lần.

### Độ hiệu quả
*   **Cực kỳ cao**: Rút ngắn thời gian chạy tác vụ hàng ngày từ vài phút xuống vài giây. Giảm thiểu nguy cơ nghẽn kết nối database và cô lập lỗi (nếu một mentor gặp lỗi sinh lịch, các mentor khác vẫn được xử lý bình thường mà không bị rollback toàn bộ tác vụ).

---

## Kế hoạch kiểm thử & Xác minh

### Kiểm thử tự động (Automated Tests)
1.  **Test Overlap**: Viết unit test trong `MentorAvailabilityServiceTest` kiểm tra việc tạo các slot đè nhau 1 phần (ví dụ 09:00 - 10:00 và 09:30 - 10:30) sẽ bị từ chối hoặc bỏ qua.
2.  **Test Ban Sync**: Viết integration test thực hiện Ban một mentor, sau đó kiểm tra xem cờ `isAvailable` trong profile của họ có tự động chuyển về `false` và các slot rảnh chưa book tương lai có bị tắt (`isActive = false`) hay không.
3.  **Test Async Scheduler**: Chạy thử test tích hợp giả lập scheduler hoạt động để xác nhận các luồng chạy song song và ghi nhận đúng nhật ký log hoạt động.

### Xác minh thủ công (Manual Verification)
*   Thực hiện ban tài khoản qua admin API, sau đó kiểm tra xem tài khoản đó có biến mất khỏi danh sách tìm kiếm mentor tại `/api/mentor/discovery` hay không.
