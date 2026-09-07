package com.fptu.exe.skillswap.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tổ chức Swagger theo các màn hình và luồng mà Frontend thực sự sử dụng.
 * Cấu hình này chỉ thay đổi tài liệu, không thay đổi endpoint hoặc quyền truy cập.
 */
@Configuration
public class OpenApiFlowConfig {

    private static final int MAX_DESCRIPTION_LENGTH = 260;

    private static final Pattern ENGLISH_SUMMARY = Pattern.compile(
            "^(admin\\s+)?(archive|bookmark|confirm|create|delete|get|handle|initialize|like|list|local|mark|pause|preview|publish|record|remove|resolve|restore|resume|share|skip|soft delete|unfollow|follow|update)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ENGLISH_DESCRIPTION = Pattern.compile(
            "^(this|returns?|creates?|requires?|updates?|verifies?|endpoint|read-only|public|local|only|records?|re-authorizes?|the client)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VIETNAMESE_CHARACTER = Pattern.compile("[À-ỹĐđ]");

    private static final List<TagRule> TAG_RULES = List.of(
            tag("Authentication", "Đăng nhập", "Đăng nhập Google, làm mới phiên và lấy thông tin người dùng hiện tại."),
            tag("Academic Catalog", "Dữ liệu học tập", "Danh sách campus, ngành và chuyên ngành dùng trong biểu mẫu."),
            tag("Catalog", "Dữ liệu biểu mẫu", "Các lựa chọn có sẵn để FE hiển thị trong biểu mẫu."),
            tag("Onboarding", "Trạng thái bắt đầu", "Cho FE biết người dùng cần hoàn thành bước nào tiếp theo."),
            tag("Academic Profile", "Hồ sơ sinh viên", "Xem và cập nhật thông tin học tập của người dùng."),
            tag("Mentor Profile", "Hồ sơ mentor", "Tạo hồ sơ, dự án và thành tích trước khi đăng ký mentor."),
            tag("Mentor Verification", "Đăng ký mentor", "Tải minh chứng, nộp hồ sơ và theo dõi kết quả duyệt."),
            tag("Mentor Services", "Dịch vụ mentor", "Tạo và quản lý các dịch vụ mà mentee có thể đặt."),
            tag("Mentor Availability Slot", "Lịch rảnh của mentor", "Tạo và quản lý các khung giờ có thể nhận booking."),
            tag("Availability Templates", "Mẫu lịch hằng tuần", "Tạo lịch lặp lại và xử lý các ngày ngoại lệ."),
            tag("Mentor Booking Policy", "Quy tắc đặt lịch", "Cấu hình thời gian báo trước và phạm vi được đặt lịch."),
            tag("Google Calendar", "Google Calendar", "Kết nối lịch để đồng bộ booking và tạo Google Meet."),
            tag("Mentor Discovery", "Tìm mentor", "Tìm kiếm, xem hồ sơ và lịch trống của mentor."),
            tag("Mentor Discovery Telemetry", "Thống kê tìm mentor", "Ghi nhận thao tác để đo hiệu quả màn tìm mentor."),
            tag("Mentor Booking", "Đặt lịch mentoring", "Xem giá, tạo booking và xử lý toàn bộ vòng đời buổi học."),
            tag("Payment Orders", "Thanh toán", "Xem trước chi phí, tạo thanh toán và kiểm tra kết quả."),
            tag("Review & Rating", "Đánh giá buổi học", "Mentee gửi đánh giá sau khi buổi mentoring hoàn tất."),
            tag("Conversation", "Trò chuyện", "Đọc, gửi tin nhắn và quản lý tệp trong cuộc trò chuyện."),
            tag("Notification", "Thông báo", "Xem thông báo và cập nhật trạng thái đã đọc."),
            tag("Wallet", "Ví", "Xem số dư và các giao dịch gần đây."),
            tag("Mentor Payout Profiles", "Tài khoản nhận tiền", "Mentor quản lý tài khoản dùng để nhận tiền."),
            tag("Payout Requests", "Yêu cầu rút tiền", "Mentor tạo và theo dõi yêu cầu rút tiền."),
            tag("Forum", "Diễn đàn", "Đăng bài, bình luận, tương tác và báo cáo nội dung."),
            tag("Blog", "Blog", "Đọc, viết và quản lý bài viết."),
            tag("Course Vault", "Khóa học", "Xem và quản lý chương, tài liệu và video của khóa học."),
            tag("Course curriculum", "Khóa học", "Xem và quản lý chương, tài liệu và thứ tự nội dung khóa học."),
            tag("Course announcements", "Thông báo khóa học", "Đọc hoặc tạo thông báo dành cho người học trong khóa học."),
            tag("Course chat", "Trò chuyện khóa học", "Mở cuộc trò chuyện trong phạm vi một khóa học."),
            tag("File Storage", "Internal / System", "Internal/System - không dùng cho FE. Chỉ dùng cho vận hành hoặc local development."),
            tag("SEO & Social Sharing", "Internal / System", "Internal/System - không dùng cho màn hình nghiệp vụ."),
            tag("Admin - Dashboard", "Quản trị - Tổng quan", "Số liệu chính và các hàng chờ cần xử lý."),
            tag("Admin - Mentor Verification", "Quản trị - Duyệt mentor", "Xem hồ sơ, yêu cầu bổ sung, duyệt hoặc từ chối mentor."),
            tag("Admin - Users", "Quản trị - Người dùng", "Xem danh sách và khóa hoặc mở khóa tài khoản."),
            tag("Admin - Mentors", "Quản trị - Mentor", "Xem danh sách và chi tiết mentor trong hệ thống."),
            tag("Admin - Bookings", "Quản trị - Booking", "Theo dõi booking và xử lý sự cố buổi học."),
            tag("Admin - Cases", "Quản trị - Vụ việc", "Nhận xử lý và theo dõi hoạt động của từng vụ việc."),
            tag("Admin - Notes", "Quản trị - Ghi chú", "Lưu ghi chú nội bộ cho quá trình xử lý."),
            tag("Admin - Forum", "Quản trị - Diễn đàn", "Kiểm duyệt báo cáo, bài viết và bình luận."),
            tag("Admin - Blog", "Quản trị - Blog", "Quản lý và kiểm duyệt bài viết blog."),
            tag("Admin Chat Moderation", "Quản trị - Trò chuyện", "Xử lý báo cáo và khóa cuộc trò chuyện khi cần."),
            tag("Admin - Campaigns", "Quản trị - Chiến dịch", "Quản lý chương trình khuyến mãi."),
            tag("Admin - Coupons", "Quản trị - Mã giảm giá", "Quản lý mã giảm giá và lịch sử sử dụng."),
            tag("Admin - Email Outbox", "Internal / System", "Internal/System - không dùng cho FE người dùng. Chỉ vận hành dùng."),
            tag("Admin - Audit Logs", "Internal / System", "Internal/System - không dùng cho FE người dùng. Chỉ vận hành dùng."),
            tag("System Admin - Roles", "Quản trị hệ thống - Phân quyền", "Cấp hoặc thu hồi quyền admin."),
            tag("Webhooks", "Internal / System", "Internal/System - không dùng cho FE. Nhận callback từ dịch vụ bên ngoài."),
            tag("File Upload", "Internal / System", "Internal/System - không dùng cho FE. Tải tệp lên hệ thống."),
            tag("Mentor Violation", "Hồ sơ mentor", "Xem lịch sử vi phạm nội bộ của mentor."),
            tag("Internal/System - Video streaming", "Internal / System", "Internal/System - không dùng cho FE. Phục vụ phát video."),
            tag("Internal/System", "Internal / System", "Internal/System - không dùng cho FE. Webhook và dịch vụ nội bộ."),
            tag("System", "Internal / System", "Internal/System - không dùng cho FE. Kiểm tra tình trạng dịch vụ.")
    );

    private record ReplacementRule(Pattern pattern, String replacement) {}

    private static ReplacementRule prefixRule(String english, String vietnamese) {
        return new ReplacementRule(
                Pattern.compile("(?i)^" + Pattern.quote(english) + "\\b"),
                Matcher.quoteReplacement(vietnamese)
        );
    }

    private static ReplacementRule phraseRule(String english, String vietnamese) {
        return new ReplacementRule(
                Pattern.compile(Pattern.quote(english), Pattern.CASE_INSENSITIVE),
                Matcher.quoteReplacement(vietnamese)
        );
    }

    private static final List<ReplacementRule> PREFIX_RULES = List.of(
            prefixRule("Soft delete", "Xóa"),
            prefixRule("Create", "Tạo"),
            prefixRule("Get", "Lấy"),
            prefixRule("List", "Lấy danh sách"),
            prefixRule("Update", "Cập nhật"),
            prefixRule("Delete", "Xóa"),
            prefixRule("Restore", "Khôi phục"),
            prefixRule("Archive", "Lưu trữ"),
            prefixRule("Publish", "Xuất bản"),
            prefixRule("Preview", "Xem trước"),
            prefixRule("Confirm", "Xác nhận"),
            prefixRule("Initialize", "Khởi tạo"),
            prefixRule("Record", "Ghi nhận"),
            prefixRule("Resolve", "Xử lý"),
            prefixRule("Handle", "Xử lý"),
            prefixRule("Pause", "Tạm dừng"),
            prefixRule("Resume", "Tiếp tục"),
            prefixRule("Skip", "Bỏ qua"),
            prefixRule("Share", "Chia sẻ"),
            prefixRule("Unfollow", "Bỏ theo dõi"),
            prefixRule("Follow", "Theo dõi"),
            prefixRule("Bookmark", "Lưu"),
            prefixRule("Remove", "Bỏ"),
            prefixRule("Mark", "Đánh dấu"),
            prefixRule("Like", "Thích")
    );

    private static final List<ReplacementRule> PHRASE_RULES = List.of(
            phraseRule("availability templates", "mẫu lịch rảnh"),
            phraseRule("availability template", "mẫu lịch rảnh"),
            phraseRule("course curriculum", "chương trình khóa học"),
            phraseRule("course chapters", "chương khóa học"),
            phraseRule("course chapter", "chương khóa học"),
            phraseRule("mentor blog posts", "bài viết blog của mentor"),
            phraseRule("mentor blog post", "bài viết blog của mentor"),
            phraseRule("payment orders", "đơn thanh toán"),
            phraseRule("payout requests", "yêu cầu rút tiền"),
            phraseRule("payout profile", "tài khoản nhận tiền"),
            phraseRule("booking quote", "báo giá booking"),
            phraseRule("blog posts", "bài viết blog"),
            phraseRule("blog post", "bài viết blog"),
            phraseRule("chat attachment", "tệp trong cuộc trò chuyện"),
            phraseRule("course materials", "tài liệu khóa học"),
            phraseRule("course video", "video khóa học"),
            phraseRule("download URL", "đường dẫn tải tệp"),
            phraseRule("playback URL", "đường dẫn xem video"),
            phraseRule("upload intent", "lượt tải tệp"),
            phraseRule("mentor discovery funnel event", "sự kiện tìm mentor"),
            phraseRule("with cursor pagination", "có phân trang"),
            phraseRule(". Idempotent.", "")
    );

    private static final Map<String, TagRule> TAGS_BY_ORIGINAL_NAME = buildTagIndex();

    @Bean
    @ConditionalOnProperty(
            name = "application.openapi.include-all-group",
            havingValue = "true",
            matchIfMissing = true
    )
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("00-all")
                .displayName("Tất cả API")
                .pathsToMatch("/api/**")
                .pathsToExclude(
                        "/error",
                        "/actuator/**",
                        "/api/internal/**",
                        "/api/webhooks/**",
                        "/api/payments/webhook/**",
                        "/api/courses/webhook/**",
                        "/api/files/**",
                        "/api/system/**",
                        "/api/admin/email-outbox/**",
                        "/api/admin/audit-logs/**"
                )
                .addOpenApiCustomizer(beginnerFriendlyOpenApi())
                .build();
    }

    @Bean
    public GroupedOpenApi identityFlowApis() {
        return group(
                "01-identity",
                "1. Đăng nhập và hồ sơ",
                "/api/auth/**",
                "/api/campuses",
                "/api/academic-programs/**",
                "/api/specializations",
                "/api/catalog/**",
                "/api/me/onboarding-status",
                "/api/me/student-profile/**"
        );
    }

    @Bean
    public GroupedOpenApi mentorFlowApis() {
        return group(
                "02-mentor",
                "2. Khu vực mentor & Đăng ký",
                "/api/me/mentor-profile/**",
                "/api/me/mentor-projects/**",
                "/api/me/mentor-achievements/**",
                "/api/me/mentor-verification/**",
                "/api/me/mentor-violations",
                "/api/me/mentor-services/**",
                "/api/me/availability-slots/**",
                "/api/me/availability-templates/**",
                "/api/me/mentor-scheduling-constraints/**",
                "/api/me/mentor-booking-policy/**",
                "/api/me/google-calendar/**",
                "/api/mentor/bookings/**",
                "/api/mentor/payout-profiles/**",
                "/api/mentor/payout-requests/**",
                "/api/me/mentor-wallet",
                "/api/me/mentor/courses/**"
        );
    }

    @Bean
    public GroupedOpenApi bookingFlowApis() {
        return group(
                "03-booking",
                "3. Tìm mentor và đặt lịch",
                "/api/mentors/**",
                "/api/mentor-discovery/**",
                "/api/mentor-services/**",
                "/api/bookings/**",
                "/api/me/bookings/**",
                "/api/me/payment-orders/**",
                "/api/me/credit-wallet"
        );
    }

    @Bean
    public GroupedOpenApi communityFlowApis() {
        return group(
                "04-community",
                "4. Trò chuyện và cộng đồng",
                "/api/me/conversations/**",
                "/api/me/chat-attachments/**",
                "/api/me/notifications/**",
                "/api/forum/**",
                "/api/blog/**",
                "/api/me/blog/**",
                "/api/courses/**",
                "/api/me/courses/**"
        );
    }

    @Bean
    public GroupedOpenApi adminFlowApis() {
        return group(
                "05-admin",
                "5. Khu vực quản trị",
                "/api/admin/**",
                "/api/system/**"
        );
    }

    @Bean
    public GroupedOpenApi integrationApis() {
        return group(
                "06-integration",
                "6. Kết nối bên ngoài",
                "/api/webhooks/**",
                "/api/payments/webhook/**",
                "/share/**",
                "/sitemap.xml",
                "/robots.txt"
        );
    }

    @Bean
    public GroupedOpenApi internalSystemApis() {
        return group(
                "07-internal",
                "7. Internal/System - không dùng cho FE",
                "/health",
                "/api/files/**",
                "/api/internal/**"
        );
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public OpenApiCustomizer beginnerFriendlyOpenApi() {
        return openApi -> {
            Set<String> usedTags = new LinkedHashSet<>();

            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                    makeOperationEasyToRead(operation);
                    if (operation.getTags() == null) {
                        return;
                    }
                    List<String> mappedTags = operation.getTags().stream()
                            .map(OpenApiFlowConfig::displayTagName)
                            .distinct()
                            .toList();
                    operation.setTags(mappedTags);
                    usedTags.addAll(mappedTags);
                }));
            }

            openApi.setTags(buildVisibleTags(openApi, usedTags));
        };
    }

    private GroupedOpenApi group(String id, String displayName, String... paths) {
        return GroupedOpenApi.builder()
                .group(id)
                .displayName(displayName)
                .pathsToMatch(paths)
                .pathsToExclude("/error", "/actuator/**")
                .addOpenApiCustomizer(beginnerFriendlyOpenApi())
                .build();
    }

    private static void makeOperationEasyToRead(Operation operation) {
        if (operation.getSummary() != null && ENGLISH_SUMMARY.matcher(operation.getSummary()).find()) {
            operation.setSummary(translateCommonSummary(operation.getSummary()));
        }

        String description = operation.getDescription();
        if (description == null || description.isBlank()) {
            return;
        }
        if (!VIETNAMESE_CHARACTER.matcher(description).find()
                && ENGLISH_DESCRIPTION.matcher(description).find()) {
            // Keep a useful English technical note when a controller has not yet
            // been translated. Removing it makes the generated contract less
            // useful; the controller documentation pass can translate it later.
            operation.setDescription(shorten(description));
            return;
        }
        operation.setDescription(shorten(description));
    }

    private static String translateCommonSummary(String source) {
        if (source.equalsIgnoreCase("Local-only raw upload endpoint")) {
            return "Tải tệp trực tiếp (chỉ dùng local)";
        }
        if (source.equalsIgnoreCase("Local-only upload endpoint")) {
            return "Tải tệp (chỉ dùng local)";
        }
        if (source.equalsIgnoreCase("Local private resource download")) {
            return "Tải tài nguyên riêng tư (chỉ dùng local)";
        }

        boolean adminOperation = source.regionMatches(true, 0, "Admin ", 0, "Admin ".length());
        if (adminOperation) {
            source = source.substring("Admin ".length());
        }
        String value = source;
        for (ReplacementRule rule : PREFIX_RULES) {
            Matcher matcher = rule.pattern().matcher(value);
            if (matcher.find()) {
                value = matcher.replaceFirst(rule.replacement());
                break;
            }
        }
        for (ReplacementRule rule : PHRASE_RULES) {
            value = rule.pattern().matcher(value).replaceAll(rule.replacement());
        }
        value = value.trim();
        return adminOperation ? "Quản trị - " + value : value;
    }

    private static String shorten(String description) {
        String compact = description.replaceAll("\\s+", " ").trim();
        if (compact.length() <= MAX_DESCRIPTION_LENGTH) {
            return compact;
        }
        int sentenceEnd = compact.indexOf(". ");
        if (sentenceEnd >= 60 && sentenceEnd < MAX_DESCRIPTION_LENGTH) {
            return compact.substring(0, sentenceEnd + 1);
        }
        return compact.substring(0, MAX_DESCRIPTION_LENGTH - 3).trim() + "...";
    }

    private static List<Tag> buildVisibleTags(OpenAPI openApi, Set<String> usedTags) {
        Map<String, String> oldDescriptions = new LinkedHashMap<>();
        if (openApi.getTags() != null) {
            openApi.getTags().forEach(tag -> oldDescriptions.putIfAbsent(tag.getName(), tag.getDescription()));
        }

        List<Tag> tags = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();
        for (TagRule rule : TAG_RULES) {
            if (usedTags.contains(rule.displayName()) && added.add(rule.displayName())) {
                tags.add(new Tag().name(rule.displayName()).description(rule.description()));
            }
        }
        for (String usedTag : usedTags) {
            if (added.add(usedTag)) {
                tags.add(new Tag().name(usedTag).description(shortenUnknownDescription(oldDescriptions.get(usedTag))));
            }
        }
        return tags;
    }

    private static String shortenUnknownDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        if (!VIETNAMESE_CHARACTER.matcher(description).find()
                && ENGLISH_DESCRIPTION.matcher(description).find()) {
            return null;
        }
        return shorten(description);
    }

    private static String displayTagName(String originalName) {
        TagRule rule = TAGS_BY_ORIGINAL_NAME.get(originalName.toLowerCase(Locale.ROOT));
        return rule == null ? originalName : rule.displayName();
    }

    private static Map<String, TagRule> buildTagIndex() {
        Map<String, TagRule> index = new LinkedHashMap<>();
        TAG_RULES.forEach(rule -> index.put(rule.originalName().toLowerCase(Locale.ROOT), rule));
        return Map.copyOf(index);
    }

    private static TagRule tag(String originalName, String displayName, String description) {
        return new TagRule(originalName, displayName, description);
    }

    private record TagRule(String originalName, String displayName, String description) {
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice(assignableTypes = {
            org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource.class,
            org.springdoc.webmvc.api.OpenApiWebMvcResource.class
    })
    public static class OpenApiExceptionHandler {

        @org.springframework.web.bind.annotation.ExceptionHandler(org.springdoc.api.OpenApiResourceNotFoundException.class)
        public org.springframework.http.ResponseEntity<com.fptu.exe.skillswap.shared.dto.response.ApiResponse<Object>> handleOpenApiNotFound(
                org.springdoc.api.OpenApiResourceNotFoundException ex
        ) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(com.fptu.exe.skillswap.shared.dto.response.ApiResponse.builder()
                            .timestamp(com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow())
                            .status(org.springframework.http.HttpStatus.NOT_FOUND.value())
                            .code(com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND.getCode())
                            .message(ex.getMessage())
                            .build());
        }
    }
}
