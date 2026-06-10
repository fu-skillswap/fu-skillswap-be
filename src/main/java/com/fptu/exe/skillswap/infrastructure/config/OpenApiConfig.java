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
                            ## Tài liệu API – SkillSwap (EXE201)
                            
                            Nền tảng **trao đổi kỹ năng** dành riêng cho sinh viên FPT University.
                            
                            ### Luồng xác thực
                            1. Client gọi `POST /api/v1/auth/google` với Google ID Token
                            2. Server trả về `accessToken` và `refreshToken`
                            3. Thêm header `Authorization: Bearer {accessToken}` vào mọi request tiếp theo
                            4. Khi `accessToken` hết hạn, gọi `POST /api/v1/auth/refresh` để lấy token mới
                            
                            ### Luồng đăng nhập lần đầu
                            Sau khi nhận token, FE gọi `GET /api/v1/auth/me` và kiểm tra `profileCompleted`:
                            - `false` → Chuyển hướng đến trang **điền hồ sơ học thuật** (`PUT /api/me/student-profile`)
                            - `true` → Vào **dashboard** bình thường
                            """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("SkillSwap Team – FPT University")
                                .email("skillswap@fptu.edu.vn"))
                        .license(new License()
                                .name("Internal – EXE201 Project")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
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
