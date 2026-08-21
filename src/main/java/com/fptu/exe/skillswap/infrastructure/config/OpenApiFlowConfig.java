package com.fptu.exe.skillswap.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
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
            tag("Course Vault", "Tài liệu khóa học", "Tải lên và xem tài liệu hoặc video của khóa học."),
            tag("File Storage", "Lưu trữ tệp", "Các API hỗ trợ tải tệp trong môi trường phát triển."),
            tag("SEO & Social Sharing", "Chia sẻ và tìm kiếm", "Hỗ trợ link chia sẻ và công cụ tìm kiếm."),
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
            tag("Admin - Email Outbox", "Quản trị - Email", "Kiểm tra và gửi lại email bị lỗi."),
            tag("Admin - Audit Logs", "Quản trị - Nhật ký", "Tra cứu lịch sử thao tác quản trị."),
            tag("System Admin - Roles", "Quản trị hệ thống - Phân quyền", "Cấp hoặc thu hồi quyền admin."),
            tag("Webhooks", "Kết nối bên ngoài", "Nhận thông báo từ dịch vụ bên ngoài."),
            tag("System", "Tình trạng hệ thống", "Kiểm tra dịch vụ có đang hoạt động hay không.")
    );

    private static final Map<String, TagRule> TAGS_BY_ORIGINAL_NAME = buildTagIndex();

    @Bean
    public GroupedOpenApi allApis() {
        return group("00-all", "Tất cả API", "/**");
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
    public GroupedOpenApi mentorApplicationFlowApis() {
        return group(
                "02-mentor-application",
                "2. Đăng ký mentor",
                "/api/me/mentor-profile/**",
                "/api/me/mentor-projects/**",
                "/api/me/mentor-achievements/**",
                "/api/me/mentor-verification/**"
        );
    }

    @Bean
    public GroupedOpenApi bookingFlowApis() {
        return group(
                "03-booking",
                "3. Tìm mentor và đặt lịch",
                "/api/mentors/**",
                "/api/mentor-discovery/**",
                "/api/mentor-services/*/pricing-preview",
                "/api/bookings/**",
                "/api/me/bookings/**",
                "/api/me/payment-orders/**",
                "/api/me/credit-wallet"
        );
    }

    @Bean
    public GroupedOpenApi mentorWorkspaceFlowApis() {
        return group(
                "04-mentor-workspace",
                "4. Khu vực mentor",
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
    public GroupedOpenApi communityFlowApis() {
        return group(
                "05-community",
                "5. Trò chuyện và cộng đồng",
                "/api/me/conversations/**",
                "/api/me/chat-attachments/**",
                "/api/me/notifications/**",
                "/api/forum/**",
                "/api/blog/**",
                "/api/me/blog/**",
                "/api/me/courses/**"
        );
    }

    @Bean
    public GroupedOpenApi adminFlowApis() {
        return group(
                "06-admin",
                "6. Khu vực quản trị",
                "/api/admin/**",
                "/api/system/**"
        );
    }

    @Bean
    public GroupedOpenApi integrationApis() {
        return group(
                "07-integrations",
                "7. Hệ thống và kết nối ngoài",
                "/health",
                "/api/files/**",
                "/api/webhooks/**",
                "/api/payments/webhook/**",
                "/api/private-download/**",
                "/share/**",
                "/sitemap.xml",
                "/robots.txt"
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
            operation.setDescription(null);
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
        value = replaceFirst(value, "Soft delete", "Xóa");
        value = replaceFirst(value, "Create", "Tạo");
        value = replaceFirst(value, "Get", "Lấy");
        value = replaceFirst(value, "List", "Lấy danh sách");
        value = replaceFirst(value, "Update", "Cập nhật");
        value = replaceFirst(value, "Delete", "Xóa");
        value = replaceFirst(value, "Restore", "Khôi phục");
        value = replaceFirst(value, "Archive", "Lưu trữ");
        value = replaceFirst(value, "Publish", "Xuất bản");
        value = replaceFirst(value, "Preview", "Xem trước");
        value = replaceFirst(value, "Confirm", "Xác nhận");
        value = replaceFirst(value, "Initialize", "Khởi tạo");
        value = replaceFirst(value, "Record", "Ghi nhận");
        value = replaceFirst(value, "Resolve", "Xử lý");
        value = replaceFirst(value, "Handle", "Xử lý");
        value = replaceFirst(value, "Pause", "Tạm dừng");
        value = replaceFirst(value, "Resume", "Tiếp tục");
        value = replaceFirst(value, "Skip", "Bỏ qua");
        value = replaceFirst(value, "Share", "Chia sẻ");
        value = replaceFirst(value, "Unfollow", "Bỏ theo dõi");
        value = replaceFirst(value, "Follow", "Theo dõi");
        value = replaceFirst(value, "Bookmark", "Lưu");
        value = replaceFirst(value, "Remove", "Bỏ");
        value = replaceFirst(value, "Mark", "Đánh dấu");
        value = replaceFirst(value, "Like", "Thích");

        value = replaceIgnoreCase(value, "availability templates", "mẫu lịch rảnh");
        value = replaceIgnoreCase(value, "availability template", "mẫu lịch rảnh");
        value = replaceIgnoreCase(value, "booking quote", "báo giá booking");
        value = replaceIgnoreCase(value, "blog posts", "bài viết blog");
        value = replaceIgnoreCase(value, "blog post", "bài viết blog");
        value = replaceIgnoreCase(value, "chat attachment", "tệp trong cuộc trò chuyện");
        value = replaceIgnoreCase(value, "course materials", "tài liệu khóa học");
        value = replaceIgnoreCase(value, "course video", "video khóa học");
        value = replaceIgnoreCase(value, "download URL", "đường dẫn tải tệp");
        value = replaceIgnoreCase(value, "playback URL", "đường dẫn xem video");
        value = replaceIgnoreCase(value, "upload intent", "lượt tải tệp");
        value = replaceIgnoreCase(value, "mentor discovery funnel event", "sự kiện tìm mentor");
        value = replaceIgnoreCase(value, "with cursor pagination", "có phân trang");
        value = replaceIgnoreCase(value, ". Idempotent.", "");
        value = value.trim();
        return adminOperation ? "Quản trị - " + value : value;
    }

    private static String replaceFirst(String source, String english, String vietnamese) {
        return source.replaceFirst("(?i)^" + Pattern.quote(english) + "\\b", Matcher.quoteReplacement(vietnamese));
    }

    private static String replaceIgnoreCase(String source, String english, String vietnamese) {
        return Pattern.compile(Pattern.quote(english), Pattern.CASE_INSENSITIVE)
                .matcher(source)
                .replaceAll(Matcher.quoteReplacement(vietnamese));
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
}
