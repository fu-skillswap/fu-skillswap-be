package com.fptu.exe.skillswap.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .addServersItem(new Server()
                        .url("/")
                        .description("Default Server (Current Environment)"))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("🖥️ Local Development Server"))
                .addServersItem(new Server()
                        .url("https://api.skillswap.asia")
                        .description("🌐 Production API Server"))
                .info(new Info()
                        .title("SkillSwap API")
                        .description("""
                            ## SkillSwap API Documentation - EXE201
                            
                            SkillSwap là nền tảng mentoring giữa sinh viên và cựu sinh viên trong phạm vi Đại học FPT.
                            Backend cung cấp REST API cho xác thực Google, hồ sơ học thuật, hồ sơ mentor,
                            danh mục kỹ năng và các luồng mentoring sẽ được phát triển tiếp theo.
                            
                            ### Cách FE đăng nhập với Google
                            1. FE tích hợp Google Identity Services hoặc thư viện Google Login tương đương.
                            2. Sau khi người dùng đăng nhập Google thành công, FE lấy `idToken` từ Google.
                            3. FE gửi `idToken` vào `POST /api/auth/google`.
                            4. Backend xác thực `idToken` với Google, tự tạo hoặc liên kết tài khoản người dùng.
                            5. Backend trả về `accessToken` và `refreshToken`.
                            6. FE dùng `accessToken` cho các API cần xác thực bằng header:
                               `Authorization: Bearer {accessToken}`.
                            7. Khi `accessToken` hết hạn, FE gọi `POST /api/auth/refresh` bằng `refreshToken`.
                            8. Khi đăng xuất, FE gọi `POST /api/auth/logout` để thu hồi `refreshToken`.
                            
                            ### Cách test API xác thực trên Swagger UI
                            1. Gọi `POST /api/auth/google` với Google `idToken` hợp lệ.
                            2. Copy giá trị `accessToken` trong response.
                            3. Bấm nút **Authorize** ở góc trên bên phải Swagger UI.
                            4. Dán `accessToken` vào ô `bearerAuth` (không cần nhập tiền tố `Bearer `).
                            5. Bấm **Authorize**, sau đó có thể gọi các API như `GET /api/auth/me`
                               hoặc `GET /api/me/student-profile`.
                            
                            ### Luồng đăng nhập lần đầu
                            Sau khi nhận token, FE gọi `GET /api/auth/me` và kiểm tra `profileCompleted`:
                            - `false` → Chuyển hướng đến trang **điền hồ sơ học thuật** (`PUT /api/me/student-profile`)
                            - `true` → Vào **dashboard** bình thường
                            """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Quang Tam")
                                .email("quangtam2005.lttg@gmail.com"))
                        .license(new License()
                                .name("Internal - EXE201 Project")))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .in(SecurityScheme.In.HEADER)
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT Access Token vào đây (không cần tiền tố 'Bearer '). Ví dụ: `eyJhbGci...`")))
                .tags(List.of(
                        new Tag().name("Authentication").description("Nhóm API dùng cho đăng nhập Google, làm mới token, đăng xuất và lấy thông tin user hiện tại. FE dùng nhóm này ở đầu luồng onboarding và khi cần khôi phục phiên đăng nhập."),
                        new Tag().name("Academic Catalog").description("Nhóm API trả dữ liệu danh mục campus, program và specialization để điền form onboarding hoặc form cập nhật hồ sơ học thuật. FE dùng các API này để đổ dropdown trước khi lưu Academic Profile."),
                        new Tag().name("Help Topic Catalog").description("Nhóm API trả danh sách help topics dùng trong mentor profile, mentor services và bộ lọc discovery. FE dùng khi cần danh sách chủ đề hỗ trợ để chọn trong form hoặc filter."),
                        new Tag().name("Academic Profile").description("Nhóm API tạo và cập nhật hồ sơ học thuật của user hiện tại. FE dùng ở bước onboarding và ở những luồng mà việc hoàn thành profile ảnh hưởng đến quyền sử dụng tính năng."),
                        new Tag().name("Mentor Profile").description("Nhóm API tạo và duy trì hồ sơ mentor nền tảng, quyết định mentor đã đủ dữ liệu để verification hoặc xuất hiện trên discovery hay chưa. FE dùng trước khi user nộp mentor verification hoặc trước khi hiển thị mentor public."),
                        new Tag().name("Mentor Services").description("Nhóm API để mentor tạo, cập nhật, bật tắt hoặc lưu trữ các dịch vụ mentoring cụ thể. FE dùng nhóm này để quản lý các gói/dịch vụ mà mentee có thể chọn khi booking."),
                        new Tag().name("Mentor Verification").description("Nhóm API mở, chỉnh sửa, nộp và theo dõi hồ sơ mentor verification cùng các minh chứng liên quan. FE dùng trong wizard xác thực mentor trước khi admin review."),
                        new Tag().name("Mentor Discovery").description("Nhóm API để khám phá mentor, tìm kiếm/lọc kết quả discovery và xem thông tin public cùng review của mentor. FE dùng khi mentee đang tìm mentor trước khi tạo booking."),
                        new Tag().name("Mentor Availability").description("Nhóm API để mentor quản lý availability rules và để mentee đọc các slot còn hiển thị có thể booking. FE dùng sau khi mentor đã có service và trước khi tạo booking request."),
                        new Tag().name("Mentor Booking").description("Nhóm API cho toàn bộ vòng đời booking: mentee tạo request, hai bên xem chi tiết, mentor accept/reject, hai bên cancel/complete và mentor cập nhật meeting info. FE dùng nhóm này sau khi mentee đã chọn mentor, service và slot."),
                        new Tag().name("Conversation").description("Nhóm API lấy danh sách conversation và gửi/đọc tin nhắn gắn với booking đã được accept. FE dùng sau khi hệ thống đã tự tạo conversation cho booking hợp lệ."),
                        new Tag().name("Notification").description("Nhóm API đọc danh sách thông báo, unread count và cập nhật trạng thái đã đọc của user hiện tại. FE dùng để dựng badge, dropdown và trang notification history."),
                        new Tag().name("Review & Rating").description("Nhóm API để mentee gửi feedback sau buổi mentoring và để hệ thống hiển thị dữ liệu review của mentor. FE dùng sau khi booking đã hoàn thành."),
                        new Tag().name("Admin - Mentor Verification").description("Nhóm API cho admin review hồ sơ mentor verification, xem chi tiết request và xử lý quyết định theo cơ chế soft lock. FE admin dùng trong queue review và màn hình xử lý hồ sơ."),
                        new Tag().name("Admin - Mentors").description("Nhóm API vận hành nội bộ để xem danh sách mentor và chi tiết mentor. FE admin dùng trong các màn hình quản trị mentor."),
                        new Tag().name("Admin - Users").description("Nhóm API vận hành nội bộ để xem danh sách user visible và thay đổi trạng thái tài khoản như ban hoặc unban. FE admin dùng trong các màn moderation user."),
                        new Tag().name("Admin - Bookings").description("Nhóm API vận hành nội bộ để theo dõi booking và session toàn hệ thống. FE admin dùng trong dashboard vận hành hoặc khi cần kiểm tra sự cố booking."),
                        new Tag().name("System Admin - Roles").description("Nhóm API cấp hệ thống để cấp/thu hồi quyền ADMIN và xem danh sách tài khoản quản trị. Chỉ FE dành cho SYSTEM_ADMIN mới nên dùng nhóm API này."),
                        new Tag().name("System").description("Nhóm API kỹ thuật để kiểm tra sức khỏe dịch vụ và chẩn đoán cơ bản. FE hoặc đội vận hành dùng để smoke check theo đúng cấu hình security hiện tại.")
                ));
    }
}
