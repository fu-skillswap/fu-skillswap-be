package com.fptu.exe.skillswap.modules.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Course-scoped metadata around the existing conversation response contract. */
@Schema(description = "Thông tin chat trong phạm vi một khóa học. Các trường context bên ngoài conversation giúp FE mở màn hình khóa học nhanh hơn; không cần tự suy luận từ backend.")
public record CourseConversationResponse(
        @Schema(description = "ID cuộc trò chuyện.")
        UUID conversationId,
        @Schema(description = "ID khóa học.")
        UUID courseId,
        @Schema(description = "Tên khóa học hiển thị.")
        String courseTitle,
        @Schema(description = "Loại ngữ cảnh, ví dụ COURSE_DIRECT hoặc COURSE_GROUP.")
        String contextType,
        @Schema(description = "ID mentor của khóa học do backend trả về.")
        UUID mentorUserId,
        @Schema(description = "Tên mentor hiển thị.")
        String mentorName,
        @Schema(description = "Avatar mentor nếu có.", nullable = true)
        String mentorAvatarUrl,
        @Schema(description = "Chi tiết conversation và quyền truy cập hiện tại.")
        ConversationResponse conversation
) {
}
