package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.CourseConversationContextRepository;
import com.fptu.exe.skillswap.modules.chat.service.BookingChatAccessPolicy.Access;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseChatAccessPolicy {

    private final CourseQueryPort courseQueryPort;
    private final ConversationParticipantRepository participantRepository;
    private final CourseConversationContextRepository contextRepository;

    public Access resolve(Conversation conversation, UUID userId) {
        if (conversation == null || userId == null) {
            return Access.readOnly(ChatReadOnlyReason.NO_EFFECTIVE_BOOKING);
        }
        if (conversation.getStatus() == ConversationStatus.LOCKED) {
            return Access.readOnly(ChatReadOnlyReason.ADMIN_LOCKED);
        }

        UUID courseId = conversation.getSourceId();
        UUID mentorUserId = conversation.getMentorUserId();
        if (conversation.getType() == ConversationType.DIRECT) {
            var context = contextRepository.findByConversationId(conversation.getId()).orElse(null);
            if (context == null || !Objects.equals(conversation.getSourceId(), context.getId())) {
                return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
            }
            courseId = context.getCourseId();
            mentorUserId = courseQueryPort.findCourseChatContext(courseId)
                    .map(CourseQueryPort.CourseChatContext::mentorUserId)
                    .orElse(null);
        }

        // The current course mentor has permanent full access.
        if (mentorUserId != null && userId.equals(mentorUserId)) {
            return Access.open(null, true);
        }

        // Verify participant record state
        Optional<ConversationParticipant> participantOpt = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), userId);
        if (participantOpt.isEmpty() || participantOpt.get().getAccessState() == ConversationParticipantAccess.REVOKED) {
            return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
        }

        // Verify active or completed course enrollment via CourseQueryPort
        if (courseQueryPort.isUserEnrolledInCourse(courseId, userId)) {
            return Access.open(null, true);
        }

        return Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
    }
}
