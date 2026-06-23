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
                        new Tag().name("Authentication").description("Sign-in, token refresh, logout, and current user profile check"),
                        new Tag().name("Academic Profile").description("User FPT academic profile management (MSSV, campus, major, specialization)"),
                        new Tag().name("Academic Catalog").description("Lookups for campus list, academic programs, specializations, and help topics"),
                        new Tag().name("Mentor Verification").description("Flow for mentees submitting portfolios and documentation to become verified mentors"),
                        new Tag().name("Mentor Profile").description("Mentor portfolio management (available slots, headline, expertise description, service offerings)"),
                        new Tag().name("Mentor Discovery").description("Discovering mentors, listing availability slots, and browsing mentor public profiles"),
                        new Tag().name("Booking & Session").description("Requesting mentoring slots, managing booking queue, tracking sessions, and meeting links"),
                        new Tag().name("Review & Rating").description("Mentee reviews and ratings for completed mentoring sessions"),
                        new Tag().name("Notification").description("In-app notification center for booking updates and alert logs"),
                        new Tag().name("Admin - Users").description("Admin user listing, status configuration (lock/unlock), and role assignment"),
                        new Tag().name("Admin - Mentors").description("Admin list and details view for system mentors"),
                        new Tag().name("Admin - Mentor Verification").description("Admin approval workflow for pending mentor verification requests"),
                        new Tag().name("Admin - Bookings").description("System-wide operational monitoring of bookings and mentoring session records"),
                        new Tag().name("System").description("Server health check and diagnostic tools")
                ));
    }
}
