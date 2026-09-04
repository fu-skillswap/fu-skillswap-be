package com.fptu.exe.skillswap.modules.course.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Thông báo đã công bố trong một khóa học. Mentor tạo thông báo; mentor và người học có enrollment ACTIVE/COMPLETED có thể đọc. Các timestamp dùng UTC/ISO-8601.")
public record CourseAnnouncementResponse(
        @Schema(description = "ID thông báo.", example = "019f7234-aaaa-bbbb-cccc-1234567890ab")
        UUID id,
        @Schema(description = "ID khóa học.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID courseId,
        @Schema(description = "ID tài khoản tác giả do backend xác định; FE không gửi field này khi tạo.", example = "019f1234-bbbb-cccc-dddd-1234567890ab")
        UUID authorUserId,
        @Schema(description = "Tiêu đề hiển thị.", example = "Cập nhật bài tập tuần này")
        String title,
        @Schema(description = "Nội dung thông báo.", example = "Mentor đã bổ sung một bài tập thực hành.")
        String content,
        @Schema(description = "Thời điểm tạo, theo UTC/ISO-8601.", example = "2026-09-04T03:20:00Z")
        Instant createdAt,
        @Schema(description = "Thời điểm cập nhật gần nhất, theo UTC/ISO-8601.", example = "2026-09-04T03:20:00Z")
        Instant updatedAt,
        @Schema(description = "Thời điểm công bố cho người học, theo UTC/ISO-8601.", example = "2026-09-04T03:20:00Z")
        Instant publishedAt
) {
}
