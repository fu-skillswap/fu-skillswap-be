package com.fptu.exe.skillswap.modules.chat.dto.response;

import java.util.UUID;

/** Additive context metadata for inbox and conversation-detail responses. */
public record ConversationContextMetadata(
        String contextType,
        UUID bookingId,
        UUID courseId,
        String courseTitle,
        UUID mentorUserId,
        String mentorName,
        String mentorAvatarUrl
) {
}
