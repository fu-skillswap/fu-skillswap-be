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
                        .description("🖥️ Local Development Server"))
                .info(new Info()
                        .title("SkillSwap API")
                        .description("""
                            ## SkillSwap API Documentation - EXE101
                            
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
                                .name("Internal - EXE101 Project")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
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
                        new Tag()
                                .name("Xác thực")
                                .description("Đăng nhập Google, làm mới token, đăng xuất và xem thông tin tài khoản hiện tại"),
                        new Tag()
                                .name("Hồ sơ học thuật")
                                .description("Xem và cập nhật hồ sơ sinh viên FPT University (MSSV, cơ sở, ngành, chuyên ngành, ...)"),
                        new Tag()
                                .name("Danh mục học thuật")
                                .description("Tra cứu danh sách cơ sở, ngành học và chuyên ngành – không yêu cầu đăng nhập"),
                        new Tag()
                                .name("Hệ thống")
                                .description("Các API kiểm tra trạng thái hoạt động của server")));
    }
}
