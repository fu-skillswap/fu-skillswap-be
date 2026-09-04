package com.fptu.exe.skillswap.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String API_RESPONSE_OBJECT_SCHEMA = "ApiResponseObject";
    private static final String VALIDATION_ERROR_SCHEMA = "ValidationErrorResponse";

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${application.openapi.version:0.1.0-beta}") String apiVersion
    ) {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .addServersItem(new Server()
                        .url("/")
                        .description("Môi trường đang chạy hiện tại"))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("🖥️ Máy local cho phát triển"))
                .addServersItem(new Server()
                        .url("https://api.skillswap.asia")
                        .description("🌐 API production"))
                .info(new Info()
                        .title("SkillSwap API")
                        .description("""
                            ## Hướng dẫn nhanh cho Frontend

                            Chọn một **nhóm luồng** ở đầu trang, sau đó đọc API từ trên xuống dưới.

                            ### Các luồng chính
                            1. **Đăng nhập và hồ sơ:** đăng nhập Google → lấy user hiện tại → hoàn thành hồ sơ sinh viên.
                            2. **Đăng ký mentor:** tạo hồ sơ mentor → tải minh chứng → nộp hồ sơ → chờ admin duyệt.
                            3. **Đặt lịch:** tìm mentor → chọn lịch → xem giá → tạo booking → thanh toán.
                            4. **Khu vực mentor:** tạo dịch vụ → mở lịch rảnh → xử lý booking → nhận và rút tiền.
                            5. **Quản trị:** xem hàng chờ → mở chi tiết → xử lý hồ sơ hoặc sự cố.

                            ### Quy ước cần nhớ
                            - API nghiệp vụ trả về `ApiResponse<T>`; dữ liệu chính nằm trong `data`.
                            - API cần đăng nhập phải gửi access token bằng nút **Authorize**.
                            - Với phân trang bằng `cursor`, FE truyền lại nguyên `nextCursor` từ lần gọi trước.
                            - Khi tải file, FE tạo URL tải lên trước rồi mới xác nhận file với backend.
                            - Thời gian trả về theo ISO-8601.
                            - `400/422`: sửa input; `401/403`: đăng nhập hoặc kiểm tra quyền; `409`: tải lại dữ liệu; `429`: chờ theo `retryAfterSeconds`.
                            - API có nhãn **Internal/System - không dùng cho FE** chỉ dành cho vận hành hoặc provider callback.

                            ### Dùng token trên Swagger
                            Bấm **Authorize** và dán access token, không cần thêm chữ `Bearer`.

                            ### Phân biệt nhóm API
                            - **API nghiệp vụ:** dùng cho màn hình người dùng hoặc admin đã tích hợp.
                            - **Internal/System - không dùng cho FE:** health check, provider webhook, storage simulator và công cụ vận hành.
                            - **Kết nối bên ngoài:** provider gọi vào hoặc trình duyệt dùng cho link chia sẻ; FE không dùng để thay thế API nghiệp vụ.
                            """)
                        .version(apiVersion)
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
                                        .description("Dán JWT Access Token vào đây, không cần thêm tiền tố `Bearer`. Ví dụ: `eyJhbGci...`"))
                        .addSchemas(API_RESPONSE_OBJECT_SCHEMA, apiResponseObjectSchema())
                        .addSchemas(VALIDATION_ERROR_SCHEMA, validationErrorSchema())
                        .addResponses("BadRequest", errorResponse("Request body, parameter hoặc field không hợp lệ.", true))
                        .addResponses("Unauthorized", errorResponse("Chưa xác thực hoặc access token không hợp lệ.", false))
                        .addResponses("Forbidden", errorResponse("Không có quyền thực hiện thao tác này.", false))
                        .addResponses("NotFound", errorResponse("Không tìm thấy resource hoặc resource không được phép enumerate.", false))
                        .addResponses("Conflict", errorResponse("Resource hoặc state đã thay đổi; refetch canonical data trước khi retry.", false))
                        .addResponses("PayloadTooLarge", errorResponse("Payload hoặc file vượt giới hạn cho phép.", false))
                        .addResponses("UnsupportedMediaType", errorResponse("Content-Type hoặc cấu trúc file không được hỗ trợ.", false))
                        .addResponses("UnprocessableEntity", errorResponse("Input đúng định dạng nhưng không thể xử lý theo business rule tĩnh.", false))
                        .addResponses("TooManyRequests", errorResponse("Rate limit đã được áp dụng; retry sau thời gian backoff.", false))
                        .addResponses("InternalServerError", errorResponse("Lỗi hệ thống không mong muốn.", false)))
                .tags(List.of(
                        new Tag().name("Authentication").description("Nhóm API dùng cho đăng nhập Google, làm mới token, đăng xuất và lấy thông tin user hiện tại. FE dùng nhóm này ở đầu luồng onboarding và khi cần khôi phục phiên đăng nhập."),
                        new Tag().name("Google Calendar").description("Kết nối hoặc ngắt kết nối Google Calendar. FE dùng để cấp quyền trước khi hệ thống đồng bộ lịch booking và tạo Google Meet."),
                        new Tag().name("Academic Catalog").description("Danh sách campus, program và specialization để điền form hồ sơ. FE chỉ cần đọc và hiển thị lựa chọn phù hợp."),
                        new Tag().name("Onboarding").description("Cho biết người dùng đã hoàn thành bước nào và nên làm gì tiếp theo trong onboarding."),
                        new Tag().name("Academic Profile").description("Xem và lưu hồ sơ học tập của người dùng hiện tại."),
                        new Tag().name("Mentee Matching Profile").description("Nhóm API lấy 5 câu hỏi nhu cầu mentoring và lưu câu trả lời flat của mentee để phục vụ Smart Matching."),
                        new Tag().name("Mentor Profile").description("Tạo và cập nhật hồ sơ mentor, dự án, thành tích và thông tin hiển thị trên trang tìm mentor. Trường bắt buộc được ghi trong schema."),
                        new Tag().name("Mentor Services").description("Mentor tạo và quản lý các dịch vụ mà mentee có thể chọn khi đặt lịch."),
                        new Tag().name("Group Sessions").description("Nhóm API mentor quản lý supply/capacity và learner khám phá, giữ seat cho group session. Checkout, payment và refund vẫn tái sử dụng booking ID hiện có."),
                        new Tag().name("Mentor Verification").description("Mở hồ sơ đăng ký mentor, tải minh chứng, nộp hồ sơ và theo dõi kết quả duyệt."),
                        new Tag().name("Mentor Discovery").description("Tìm mentor, lọc kết quả, xem hồ sơ công khai và xem lịch trống trước khi đặt lịch."),
                        new Tag().name("Mentor Availability Slot").description("Nhóm API để mentor quản lý trực tiếp các slot rảnh (CRUD) và gắn các service có thể nhận mentoring trên từng slot."),
                        new Tag().name("Availability Templates").description("Tạo lịch rảnh lặp theo tuần và xử lý ngày ngoại lệ."),
                        new Tag().name("Mentor Booking Policy").description("Xem và cập nhật thời gian báo trước, khoảng ngày được đặt và timezone của mentor."),
                        new Tag().name("Mentor Booking").description("Nhóm API cho toàn bộ vòng đời booking: mentee tạo request, hai bên xem chi tiết, mentor accept/reject, hai bên cancel/complete và mentor cập nhật meeting info. FE dùng nhóm này sau khi mentee đã chọn mentor, service và slot."),
                        new Tag().name("Conversation").description("Đọc, gửi và đồng bộ tin nhắn theo sequence. REST là nơi lấy lại lịch sử chính; realtime chỉ giúp cập nhật nhanh hơn."),
                        new Tag().name("Notification").description("Đọc thông báo, xem số chưa đọc và đánh dấu đã đọc cho người dùng hiện tại."),
                        new Tag().name("Wallet").description("Nhóm API xem ví SCoin của mentee và ví settlement của mentor. FE dùng cho màn số dư, giao dịch gần nhất và trạng thái earnings."),
                        new Tag().name("Payment Orders").description("Xem trước chi phí, tạo checkout và kiểm tra trạng thái thanh toán. Webhook chỉ dành cho PayOS, không gọi từ FE."),
                        new Tag().name("Payout Requests").description("Nhóm API mentor tạo payout request và admin duyệt/từ chối/mark-paid. FE mentor và FE admin dùng ở các màn tài chính beta."),
                        new Tag().name("Mentor Payout Profiles").description("Nhóm API mentor quản lý tài khoản nhận tiền payout. FE dùng để tạo, cập nhật và chọn payout profile trước khi tạo payout request."),
                        new Tag().name("Forum").description("Nhóm API forum nội bộ cho người dùng đăng bài, bình luận, thả reaction và report nội dung theo 4 chủ đề Hỏi đáp, Chia sẻ, Tìm kiếm và Review."),
                        new Tag().name("Blog").description("Nhóm API public blog cho bài viết SEO, kiến thức dev/non-tech, featured articles, view tracking nhẹ và author CTA tracking."),
                        new Tag().name("File Storage").description("Internal/System - không dùng cho FE. FE nên dùng API upload của đúng module, không tự chọn object key."),
                        new Tag().name("SEO & Social Sharing").description("Internal/System - không dùng cho màn hình nghiệp vụ. Dùng cho sitemap, robots và link chia sẻ công khai."),
                        new Tag().name("Review & Rating").description("Nhóm API để mentee gửi feedback sau buổi mentoring và để hệ thống hiển thị dữ liệu review của mentor. FE dùng sau khi booking đã hoàn thành."),
                        new Tag().name("Admin - Dashboard").description("Nhóm API snapshot, queue cards, queue drill-down và timeseries dành cho admin dashboard/workbench. FE admin dùng để hiển thị tổng quan vận hành, backlog cần xử lý và mở từng queue case cụ thể."),
                        new Tag().name("Admin - Audit Logs").description("Internal/System - không dùng cho FE người dùng. Tra cứu lịch sử thao tác khi vận hành."),
                        new Tag().name("Admin - Notes").description("Nhóm API nội bộ để admin ghi chú vận hành lên user, booking, report, payout và các target moderation khác."),
                        new Tag().name("Admin - Email Outbox").description("Internal/System - không dùng cho FE người dùng. Chỉ vận hành dùng để kiểm tra và xử lý email lỗi."),
                        new Tag().name("Admin - Cases").description("Nhóm API workbench để admin nhận ownership case, gỡ ownership và xem operator activity nội bộ trên từng case vận hành."),
                        new Tag().name("Admin - Mentor Verification").description("Nhóm API cho admin review hồ sơ mentor verification, xem chi tiết request và xử lý quyết định theo cơ chế soft lock. FE admin dùng trong queue review và màn hình xử lý hồ sơ."),
                        new Tag().name("Admin - Mentoring Questionnaire").description("Nhóm API admin tạo version mới và activate bộ 5 câu hỏi nhu cầu mentoring."),
                        new Tag().name("Admin - Mentors").description("Nhóm API vận hành nội bộ để xem danh sách mentor và chi tiết mentor. FE admin dùng trong các màn hình quản trị mentor."),
                        new Tag().name("Admin - Users").description("Nhóm API vận hành nội bộ để xem danh sách user visible và thay đổi trạng thái tài khoản như ban hoặc unban. FE admin dùng trong các màn moderation user."),
                        new Tag().name("Admin - Bookings").description("Nhóm API vận hành nội bộ để theo dõi booking và session toàn hệ thống. FE admin dùng trong dashboard vận hành hoặc khi cần kiểm tra sự cố booking."),
                        new Tag().name("Admin - Forum").description("Nhóm API moderation forum dành cho admin để đọc queue report, ẩn hoặc khôi phục nội dung forum khi cần xử lý vi phạm."),
                        new Tag().name("Admin - Blog").description("Nhóm API admin quản trị blog: draft, update, publish, archive, feature, category và tag."),
                        new Tag().name("Admin Chat Moderation").description("Nhóm API admin xử lý report và moderation lock cho direct booking chat."),
                        new Tag().name("System Admin - Roles").description("Nhóm API cấp hệ thống để cấp/thu hồi quyền ADMIN và xem danh sách tài khoản quản trị. Grant ADMIN sẽ gỡ MENTEE/MENTOR để tài khoản thành admin-only; revoke ADMIN sẽ trả user về MENTEE mặc định. Chỉ FE dành cho SYSTEM_ADMIN mới nên dùng nhóm API này."),
                        new Tag().name("System").description("Nhóm API kỹ thuật để kiểm tra sức khỏe dịch vụ và chẩn đoán cơ bản. FE hoặc đội vận hành dùng để smoke check theo đúng cấu hình security hiện tại.")
                ));
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public OpenApiCustomizer deduplicateTags() {
        return openApi -> {
            registerReusableErrorComponents(openApi);
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
                    "Google Calendar",
                    "Academic Catalog",
                    "Onboarding",
                    "Academic Profile",
                    "Mentee Matching Profile",
                    "Mentor Profile",
                    "Mentor Services",
                    "Group Sessions",
                    "Mentor Verification",
                    "Mentor Discovery",
                    "Mentor Availability Slot",
                    "Availability Templates",
                    "Mentor Booking Policy",
                    "Mentor Booking",
                    "Review & Rating",
                    "Conversation",
                    "Notification",
                    "Wallet",
                    "Payment Orders",
                    "Mentor Payout Profiles",
                    "Payout Requests",
                    "Forum",
                    "Blog",
                    "File Storage",
                    "SEO & Social Sharing",
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
                    "Admin - Blog",
                    "Admin Chat Moderation",
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

    private void registerReusableErrorComponents(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }
        components.addSchemas(API_RESPONSE_OBJECT_SCHEMA, apiResponseObjectSchema());
        components.addSchemas(VALIDATION_ERROR_SCHEMA, validationErrorSchema());
        components.addResponses("BadRequest", errorResponse("Request body, parameter hoặc field không hợp lệ.", true));
        components.addResponses("Unauthorized", errorResponse("Chưa xác thực hoặc access token không hợp lệ.", false));
        components.addResponses("Forbidden", errorResponse("Không có quyền thực hiện thao tác này.", false));
        components.addResponses("NotFound", errorResponse("Không tìm thấy resource hoặc resource không được phép enumerate.", false));
        components.addResponses("Conflict", errorResponse("Resource hoặc state đã thay đổi; refetch canonical data trước khi retry.", false));
        components.addResponses("PayloadTooLarge", errorResponse("Payload hoặc file vượt giới hạn cho phép.", false));
        components.addResponses("UnsupportedMediaType", errorResponse("Content-Type hoặc cấu trúc file không được hỗ trợ.", false));
        components.addResponses("UnprocessableEntity", errorResponse("Input đúng định dạng nhưng không thể xử lý theo business rule tĩnh.", false));
        components.addResponses("TooManyRequests", errorResponse("Rate limit đã được áp dụng; retry sau thời gian backoff.", false));
        components.addResponses("InternalServerError", errorResponse("Lỗi hệ thống không mong muốn.", false));
    }

    private Schema<?> apiResponseObjectSchema() {
        return new ObjectSchema()
                .description("ApiResponse<Object> envelope returned by runtime exception handling.")
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty("status", new IntegerSchema().description("HTTP status code"))
                .addProperty("code", new StringSchema().description("Machine-readable business code"))
                .addProperty("message", new StringSchema().description("Safe user-facing message"))
                .addProperty("data", new ObjectSchema().nullable(true));
    }

    private Schema<?> validationErrorSchema() {
        return new ObjectSchema()
                .description("Validation detail returned in ApiResponse.data for invalid input.")
                .addProperty("field", new StringSchema().example("startAt"))
                .addProperty("message", new StringSchema().example("Thời điểm bắt đầu không hợp lệ"))
                .addProperty("rejectedValue", new ObjectSchema().nullable(true));
    }

    private ApiResponse errorResponse(String description, boolean validationDetails) {
        Schema<?> responseSchema = new Schema<>().$ref("#/components/schemas/" + API_RESPONSE_OBJECT_SCHEMA);
        if (validationDetails) {
            responseSchema = new ObjectSchema()
                    .allOf(List.of(new Schema<>().$ref("#/components/schemas/" + API_RESPONSE_OBJECT_SCHEMA)))
                    .addProperty("data", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/" + VALIDATION_ERROR_SCHEMA)));
        }
        return new ApiResponse()
                .description(description)
                .content(new io.swagger.v3.oas.models.media.Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType().schema(responseSchema)
                ));
    }
}
