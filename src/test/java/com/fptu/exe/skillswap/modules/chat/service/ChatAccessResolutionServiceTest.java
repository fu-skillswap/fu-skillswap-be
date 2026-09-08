package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.ChatMessagingAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ChatReadOnlyReason;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAccessResolutionServiceTest {

    @Mock BookingChatAccessPolicy bookingChatAccessPolicy;
    @Mock ConversationSafetyPolicy conversationSafetyPolicy;
    @Mock ObjectProvider<CourseChatAccessPolicy> coursePolicyProvider;
    @Mock CourseChatAccessPolicy courseChatAccessPolicy;

    @Test
    void readOnlyCourseParticipantRemainsEligibleToReceiveRealtime() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .build();
        var readOnlyAccess = new BookingChatAccessPolicy.Access(
                ChatMessagingAccess.READ_ONLY,
                false,
                false,
                true,
                ChatReadOnlyReason.CHAT_WINDOW_EXPIRED,
                null,
                false
        );
        when(coursePolicyProvider.getIfAvailable()).thenReturn(courseChatAccessPolicy);
        when(courseChatAccessPolicy.isCurrentCourseDirectParticipant(conversation, userId)).thenReturn(true);
        when(courseChatAccessPolicy.resolve(conversation, userId)).thenReturn(readOnlyAccess);
        when(conversationSafetyPolicy.apply(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        ChatAccessResolutionService service = new ChatAccessResolutionService(
                bookingChatAccessPolicy, conversationSafetyPolicy, coursePolicyProvider);

        assertTrue(service.isEligibleCourseDirectRealtimeRecipient(conversation, userId));
    }
}
