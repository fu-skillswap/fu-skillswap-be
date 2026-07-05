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
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                            Backend hiện cung cấp REST API cho xác thực, hồ sơ, questionnaire nhu cầu mentoring,
                            mentor service, availability, booking, notification và chat; realtime dùng raw WebSocket theo scope nhỏ.
                            
                            ### Realtime guide cho Frontend
                            - Dùng **REST API** để:
                              - gửi chat message
                              - load conversation list
                              - load message history
                              - load notification list và unread count
                            - Dùng **raw WebSocket** chỉ để nhận realtime push event.
                            - FE connect websocket bằng:
                              - `wss://api.skillswap.asia/ws?token=<accessToken>`
                            - Chỉ dùng **access token**. Không dùng refresh token trong websocket URL.
                            - Sau khi reconnect, FE nên resync dữ liệu cần thiết qua REST.
                            - Scope websocket hiện tại được giữ nhỏ, chỉ gồm:
                              - push chat message mới
                              - push notification quan trọng
                              - push badge update cho notification unread count
                            
                            ### Cách dùng token trên Swagger UI
                            1. Lấy `accessToken` hợp lệ từ flow login hiện tại.
                            2. Bấm **Authorize** ở góc trên bên phải Swagger UI.
                            3. Dán `accessToken` vào ô `bearerAuth` (không cần nhập tiền tố `Bearer `).
                            4. Bấm **Authorize** để gọi các API cần xác thực.
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
                        new Tag().name("Mentee Matching Profile").description("Nhóm API lấy 5 câu hỏi nhu cầu mentoring và lưu câu trả lời flat của mentee để phục vụ Smart Matching."),
                        new Tag().name("Mentor Profile").description("Nhóm API tạo và duy trì hồ sơ peer mentor: headline, mô tả, help topics, môn - điểm, 3 mức support, GitHub/portfolio và các mục project/achievement optional."),
                        new Tag().name("Mentor Services").description("Nhóm API để mentor tạo, cập nhật, bật tắt hoặc lưu trữ các dịch vụ mentoring cụ thể. FE dùng nhóm này để quản lý các gói/dịch vụ mà mentee có thể chọn khi booking."),
                        new Tag().name("Mentor Verification").description("Nhóm API mở, chỉnh sửa, nộp và theo dõi hồ sơ mentor verification cùng các minh chứng liên quan. FE dùng trong wizard xác thực mentor trước khi admin review."),
                        new Tag().name("Mentor Discovery").description("Nhóm API để khám phá mentor, tìm kiếm/lọc kết quả discovery và xem thông tin public cùng review của mentor. FE dùng khi mentee đang tìm mentor trước khi tạo booking."),
                        new Tag().name("Mentor Availability Slot").description("Nhóm API để mentor quản lý trực tiếp các slot rảnh (CRUD) và gắn các service có thể nhận mentoring trên từng slot."),
                        new Tag().name("Mentor Booking").description("Nhóm API cho toàn bộ vòng đời booking: mentee tạo request, hai bên xem chi tiết, mentor accept/reject, hai bên cancel/complete và mentor cập nhật meeting info. FE dùng nhóm này sau khi mentee đã chọn mentor, service và slot."),
                        new Tag().name("Conversation").description("Nhóm API lấy danh sách conversation và gửi/đọc tin nhắn gắn với booking đã được accept. FE dùng sau khi hệ thống đã tự tạo conversation cho booking hợp lệ."),
                        new Tag().name("Notification").description("Nhóm API đọc danh sách thông báo, unread count và cập nhật trạng thái đã đọc của user hiện tại. FE dùng để dựng badge, dropdown và trang notification history."),
                        new Tag().name("Wallet").description("Nhóm API xem ví SCoin của mentee và ví settlement của mentor. FE dùng cho màn số dư, giao dịch gần nhất và trạng thái earnings."),
                        new Tag().name("Payment Orders").description("Nhóm API tạo checkout, poll trạng thái payment theo booking và nhận webhook PayOS. FE chỉ gọi các endpoint /api/me, webhook dành cho provider."),
                        new Tag().name("Payout Requests").description("Nhóm API mentor tạo payout request và admin duyệt/từ chối/mark-paid. FE mentor và FE admin dùng ở các màn tài chính beta."),
                        new Tag().name("Mentor Payout Profiles").description("Nhóm API mentor quản lý tài khoản nhận tiền payout. FE dùng để tạo, cập nhật và chọn payout profile trước khi tạo payout request."),
                        new Tag().name("Forum").description("Nhóm API forum nội bộ cho người dùng đăng bài, bình luận, thả reaction và report nội dung theo help topic. FE dùng để xây forum text-only MVP cho cộng đồng SkillSwap."),
                        new Tag().name("Review & Rating").description("Nhóm API để mentee gửi feedback sau buổi mentoring và để hệ thống hiển thị dữ liệu review của mentor. FE dùng sau khi booking đã hoàn thành."),
                        new Tag().name("Admin - Dashboard").description("Nhóm API snapshot, queue cards, queue drill-down và timeseries dành cho admin dashboard/workbench. FE admin dùng để hiển thị tổng quan vận hành, backlog cần xử lý và mở từng queue case cụ thể."),
                        new Tag().name("Admin - Audit Logs").description("Nhóm API read-only để admin duyệt audit logs nội bộ theo actor, entity và action mà không cần truy vấn trực tiếp database."),
                        new Tag().name("Admin - Notes").description("Nhóm API nội bộ để admin ghi chú vận hành lên user, booking, report, payout và các target moderation khác."),
                        new Tag().name("Admin - Email Outbox").description("Nhóm API để admin xem email outbox nội bộ, chẩn đoán delivery issue và retry lại các email đang FAILED."),
                        new Tag().name("Admin - Cases").description("Nhóm API workbench để admin nhận ownership case, gỡ ownership và xem operator activity nội bộ trên từng case vận hành."),
                        new Tag().name("Admin - Mentor Verification").description("Nhóm API cho admin review hồ sơ mentor verification, xem chi tiết request và xử lý quyết định theo cơ chế soft lock. FE admin dùng trong queue review và màn hình xử lý hồ sơ."),
                        new Tag().name("Admin - Mentoring Questionnaire").description("Nhóm API admin tạo version mới và activate bộ 5 câu hỏi nhu cầu mentoring."),
                        new Tag().name("Admin - Mentors").description("Nhóm API vận hành nội bộ để xem danh sách mentor và chi tiết mentor. FE admin dùng trong các màn hình quản trị mentor."),
                        new Tag().name("Admin - Users").description("Nhóm API vận hành nội bộ để xem danh sách user visible và thay đổi trạng thái tài khoản như ban hoặc unban. FE admin dùng trong các màn moderation user."),
                        new Tag().name("Admin - Bookings").description("Nhóm API vận hành nội bộ để theo dõi booking và session toàn hệ thống. FE admin dùng trong dashboard vận hành hoặc khi cần kiểm tra sự cố booking."),
                        new Tag().name("Admin - Forum").description("Nhóm API moderation forum dành cho admin để đọc queue report, ẩn hoặc khôi phục nội dung forum khi cần xử lý vi phạm."),
                        new Tag().name("System Admin - Roles").description("Nhóm API cấp hệ thống để cấp/thu hồi quyền ADMIN và xem danh sách tài khoản quản trị. Grant ADMIN sẽ gỡ MENTEE/MENTOR để tài khoản thành admin-only; revoke ADMIN sẽ trả user về MENTEE mặc định. Chỉ FE dành cho SYSTEM_ADMIN mới nên dùng nhóm API này."),
                        new Tag().name("System").description("Nhóm API kỹ thuật để kiểm tra sức khỏe dịch vụ và chẩn đoán cơ bản. FE hoặc đội vận hành dùng để smoke check theo đúng cấu hình security hiện tại.")
                ));
    }

    @Bean
    public OpenApiCustomizer deduplicateTags() {
        return openApi -> {
            if (openApi.getTags() == null || openApi.getTags().isEmpty()) {
                return;
            }

            Map<String, Tag> tagsByName = new LinkedHashMap<>();
            for (Tag tag : openApi.getTags()) {
                tagsByName.merge(tag.getName(), tag, (existing, incoming) -> {
                    boolean existingHasDescription = existing.getDescription() != null && !existing.getDescription().isBlank();
                    return existingHasDescription ? existing : incoming;
                });
            }

            List<String> preferredOrder = List.of(
                    "Authentication",
                    "Academic Catalog",
                    "Help Topic Catalog",
                    "Academic Profile",
                    "Mentee Matching Profile",
                    "Mentor Profile",
                    "Mentor Services",
                    "Mentor Verification",
                    "Mentor Discovery",
                    "Mentor Availability Slot",
                    "Mentor Booking",
                    "Review & Rating",
                    "Conversation",
                    "Notification",
                    "Wallet",
                    "Payment Orders",
                    "Mentor Payout Profiles",
                    "Payout Requests",
                    "Forum",
                    "Admin - Dashboard",
                    "Admin - Audit Logs",
                    "Admin - Notes",
                    "Admin - Email Outbox",
                    "Admin - Cases",
                    "Admin - Mentor Verification",
                    "Admin - Mentoring Questionnaire",
                    "Admin - Mentors",
                    "Admin - Users",
                    "Admin - Bookings",
                    "Admin - Forum",
                    "System Admin - Roles",
                    "System"
            );

            List<Tag> orderedTags = new ArrayList<>();
            for (String tagName : preferredOrder) {
                Tag tag = tagsByName.remove(tagName);
                if (tag != null) {
                    orderedTags.add(tag);
                }
            }
            orderedTags.addAll(tagsByName.values());
            openApi.setTags(orderedTags);
        };
    }
}
