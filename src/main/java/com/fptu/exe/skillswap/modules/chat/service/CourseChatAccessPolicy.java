package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.service.BookingChatAccessPolicy.Access;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseChatAccessPolicy {

    private final CourseQueryPort courseQueryPort;
    private final ConversationParticipantRepository participantRepository;

    public Access resolve(Conversation conversation, UUID userId) {
        if (conversation == null || userId == null) {
            return Access.readOnly(ChatReadOnlyReason.NO_EFFECTIVE_BOOKING);
        }
        if (conversation.getStatus() == ConversationStatus.LOCKED) {
            return Access.readOnly(ChatReadOnlyReason.ADMIN_LOCKED);
        }

        // Course Mentor has permanent full access
        if (userId.equals(conversation.getMentorUserId())) {
            return Access.open(null, true);
        }

        // Verify participant record state
        Optional<ConversationParticipant> participantOpt = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), userId);
        if (participantOpt.isEmpty() || participantOpt.get().getAccessState() == ConversationParticipantAccess.REVOKED) {
            return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
        }

        // Verify active or completed course enrollment via CourseQueryPort
        UUID courseId = conversation.getSourceId();
        if (courseQueryPort.isUserEnrolledInCourse(courseId, userId)) {
            return Access.open(null, true);
        }

        return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
    }
}
