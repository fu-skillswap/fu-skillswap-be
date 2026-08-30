package com.fptu.exe.skillswap.modules.chat.service;
import com.fptu.exe.skillswap.modules.chat.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatAccessResolutionService {

    private final BookingChatAccessPolicy bookingChatAccessPolicy;
    private final ConversationSafetyPolicy conversationSafetyPolicy;
    private final ConversationParticipantRepository participantRepository;
    private final CourseQueryPort courseQueryPort;

    public BookingChatAccessPolicy.Access resolveMessagingAccess(Conversation conversation, UUID userId) {
        BookingChatAccessPolicy.Access access;
        if (conversation.getSourceType() == ConversationSourceType.COURSE) {
            access = resolveCourseChatAccess(conversation, userId);
        } else if (bookingChatAccessPolicy == null) {
            access = new BookingChatAccessPolicy.Access(
                    ChatMessagingAccess.OPEN,
                    true, true, true, null, null, false);
        } else {
            access = bookingChatAccessPolicy.resolve(conversation.getId(), conversation.getStatus(), DateTimeUtil.now());
        }
        return conversationSafetyPolicy != null
                ? conversationSafetyPolicy.apply(conversation.getId(), access)
                : access;
    }

    private BookingChatAccessPolicy.Access resolveCourseChatAccess(Conversation conversation, UUID userId) {
        if (conversation == null || userId == null) {
            return BookingChatAccessPolicy.Access.readOnly(ChatReadOnlyReason.NO_EFFECTIVE_BOOKING);
        }
        if (conversation.getStatus() == ConversationStatus.LOCKED) {
            return BookingChatAccessPolicy.Access.readOnly(ChatReadOnlyReason.ADMIN_LOCKED);
        }

        // Course Mentor has permanent full access
        if (userId.equals(conversation.getMentorUserId())) {
            return BookingChatAccessPolicy.Access.open(null, true);
        }

        // Verify participant record state
        Optional<ConversationParticipant> participantOpt = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), userId);
        if (participantOpt.isEmpty() || participantOpt.get().getAccessState() == ConversationParticipantAccess.REVOKED) {
            return BookingChatAccessPolicy.Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
        }

        // Verify active or completed course enrollment via port
        UUID courseId = conversation.getSourceId();
        boolean isEnrolled = courseQueryPort != null && courseQueryPort.isStudentEnrolledInCourse(courseId, userId);
        if (isEnrolled) {
            return BookingChatAccessPolicy.Access.open(null, true);
        }

        return BookingChatAccessPolicy.Access.readOnly(ChatReadOnlyReason.GROUP_MEMBERSHIP_REVOKED);
    }

    public ErrorCode resolveMessagingAccessError(BookingChatAccessPolicy.Access access) {
        if (access.readOnlyReason() == ChatReadOnlyReason.ADMIN_LOCKED
                || access.readOnlyReason() == ChatReadOnlyReason.ACCOUNT_RESTRICTED) {
            return ErrorCode.CHAT_CONVERSATION_LOCKED;
        }
        return ErrorCode.CHAT_CONVERSATION_READ_ONLY;
    }
}
