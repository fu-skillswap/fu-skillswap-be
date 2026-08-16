package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.booking.service.BookingChatAccessPolicy;
import com.fptu.exe.skillswap.modules.chat.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.course.service.CourseChatAccessPolicy;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatAccessResolutionService {

    private final BookingChatAccessPolicy bookingChatAccessPolicy;
    private final ConversationSafetyPolicy conversationSafetyPolicy;
    private final ObjectProvider<CourseChatAccessPolicy> courseChatAccessPolicyProvider;

    public BookingChatAccessPolicy.Access resolveMessagingAccess(Conversation conversation, UUID userId) {
        BookingChatAccessPolicy.Access access;
        if (conversation.getSourceType() == ConversationSourceType.COURSE) {
            var coursePolicy = courseChatAccessPolicyProvider.getIfAvailable();
            access = coursePolicy != null
                    ? coursePolicy.resolve(conversation, userId)
                    : new BookingChatAccessPolicy.Access(
                            ChatMessagingAccess.OPEN,
                            true, true, true, null, null, false);
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

    public ErrorCode resolveMessagingAccessError(BookingChatAccessPolicy.Access access) {
        if (access.readOnlyReason() == ChatReadOnlyReason.ADMIN_LOCKED
                || access.readOnlyReason() == ChatReadOnlyReason.ACCOUNT_RESTRICTED) {
            return ErrorCode.CHAT_CONVERSATION_LOCKED;
        }
        return ErrorCode.CHAT_CONVERSATION_READ_ONLY;
    }
}
