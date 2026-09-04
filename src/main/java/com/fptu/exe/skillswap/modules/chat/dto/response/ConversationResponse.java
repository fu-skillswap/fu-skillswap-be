package com.fptu.exe.skillswap.modules.chat.dto.response;

import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;
import com.fptu.exe.skillswap.modules.chat.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;

@Builder
@Schema(description = "Tóm tắt cuộc trò chuyện trong inbox của tài khoản hiện tại. Conversation thường do backend tự tạo sau khi booking hoặc quyền chat hợp lệ được xác lập.")
public record ConversationResponse(
        @Schema(description = "ID cuộc trò chuyện.", example = "019f5234-aaaa-bbbb-cccc-1234567890ab")
        UUID id,
        @Schema(description = "Loại cuộc trò chuyện: DIRECT là hai người; GROUP là nhiều participant.", example = "DIRECT")
        ConversationType type,
        @Schema(description = "Trạng thái cuộc trò chuyện. LOCKED nghĩa là tạm thời không thể gửi tin.", example = "ACTIVE")
        ConversationStatus status,
        @Schema(description = "ID participant còn lại nhìn từ tài khoản hiện tại; backend tự xác định từ tài khoản đăng nhập.", example = "019f6234-aaaa-bbbb-cccc-1234567890ab")
        UUID otherUserId,
        @Schema(description = "Tên hiển thị participant còn lại do backend trả về.", example = "Nguyen Van B")
        String otherUserName,
        @Schema(description = "Avatar URL của participant còn lại nếu có.", example = "https://lh3.googleusercontent.com/example")
        String otherUserAvatarUrl,
        @Schema(description = "Nội dung rút gọn của tin nhắn gần nhất để hiển thị inbox.", example = "Anh đã cập nhật meeting link cho buổi mentoring.")
        String lastMessageContent,
        @Schema(description = "Thời điểm tin nhắn mới nhất theo UTC.", example = "2026-06-24T04:45:00Z")
        Instant lastMessageAt,
        @Schema(description = "Thời điểm tạo cuộc trò chuyện theo UTC.", example = "2026-06-24T03:30:00Z")
        Instant createdAt,
        @Schema(description = "Số tin nhắn chưa đọc của tài khoản hiện tại.", example = "3")
        long unreadCount,
        @Schema(description = "Sequence lớn nhất tài khoản hiện tại đã đọc; dùng để đồng bộ read receipt.", example = "128")
        long myLastReadSequence,
        @Schema(description = "Sequence lớn nhất participant còn lại đã đọc; dùng cho read receipt khi cần.", example = "130")
        long otherLastReadSequence,
        @Schema(description = "Quyền nhắn tin hiện tại: OPEN hoặc READ_ONLY.")
        ChatMessagingAccess messagingAccess,
        @Schema(description = "FE có thể gửi tin nhắn hay không. Dùng để bật/tắt nút gửi.", example = "true")
        boolean canSendMessages,
        @Schema(description = "FE có thể upload file trong cuộc trò chuyện hay không.", example = "true")
        boolean canUploadAttachments,
        @Schema(description = "FE có thể tải file đính kèm hay không.", example = "true")
        boolean canDownloadAttachments,
        @Schema(description = "Nếu READ_ONLY, lý do giúp FE hiển thị thông báo phù hợp.", example = "CHAT_WINDOW_EXPIRED", nullable = true)
        ChatReadOnlyReason readOnlyReason,
        @Schema(description = "Thời điểm hết hạn cửa sổ nhắn tin theo UTC; null nếu không áp dụng.", example = "2026-06-25T04:45:00Z", nullable = true)
        Instant messagingWindowEndsAt,
        boolean postSessionChatPermanent,
        @Schema(description = "Số participant đang hiển thị đối với GROUP; có thể null với DIRECT.", nullable = true)
        Integer participantCount,
        @Schema(description = "Ngữ cảnh nghiệp vụ của cuộc trò chuyện, ví dụ BOOKING, COURSE_DIRECT hoặc COURSE_GROUP.", nullable = true)
        String contextType,
        @Schema(description = "ID booking nếu conversation gắn với booking.", nullable = true)
        UUID bookingId,
        @Schema(description = "ID khóa học nếu là course chat trực tiếp hoặc nhóm khóa học cũ.", nullable = true)
        UUID courseId,
        @Schema(description = "Tên khóa học cho conversation thuộc khóa học.", nullable = true)
        String courseTitle,
        @Schema(description = "ID mentor hiện tại của khóa học, do backend xác định.", nullable = true)
        UUID mentorUserId,
        @Schema(description = "Tên hiển thị mentor hiện tại của khóa học.", nullable = true)
        String mentorName,
        @Schema(description = "Avatar mentor hiện tại của khóa học nếu có.", nullable = true)
        String mentorAvatarUrl,
        @Schema(description = "Thông báo an toàn để FE hiển thị khi chat bị giới hạn; không chứa chi tiết nội bộ.", nullable = true)
        String userActionMessage,
        @Schema(description = "Cho biết FE có thể thử lại thao tác gửi tin hay không. Với quyền bị từ chối, thường là false.", example = "false")
        boolean retryable
) {
    public static String userActionMessage(ChatReadOnlyReason reason) {
        if (reason == null) return null;
        return switch (reason) {
            case ADMIN_LOCKED -> "Tính năng chat đang bị khóa. Vui lòng liên hệ hỗ trợ.";
            case ACCOUNT_RESTRICTED -> "Tài khoản hiện không thể sử dụng chat.";
            case UNDER_REVIEW -> "Cuộc trò chuyện đang được kiểm tra. Vui lòng thử lại sau.";
            case PARTICIPANT_BLOCKED -> "Bạn không thể nhắn tin với participant này.";
            case GROUP_MEMBERSHIP_REVOKED -> "Bạn không còn là thành viên của nhóm học tập này.";
            case NO_EFFECTIVE_BOOKING -> "Bạn không còn quyền chat trong booking này.";
            case CHAT_WINDOW_EXPIRED -> "Thời gian nhắn tin của cuộc trò chuyện đã kết thúc.";
        };
    }

    public static boolean retryable(ChatReadOnlyReason reason) {
        return reason == ChatReadOnlyReason.UNDER_REVIEW;
    }
}
