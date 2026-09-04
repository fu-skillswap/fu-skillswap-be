package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationContextMetadata;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationContextMetadataTest {

    private final ConversationParticipantRepository participantRepository = mock(ConversationParticipantRepository.class);
    private final ChatResponseMapper mapper = new ChatResponseMapper(
            mock(CursorCodec.class),
            mock(ChatAttachmentRepository.class),
            participantRepository
    );

    @Test
    void courseDirectResponseContainsCourseAndMentorContext() {
        UUID conversationId = UUID.randomUUID();
        UUID menteeId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();
        var mentee = com.fptu.exe.skillswap.modules.identity.domain.User.builder()
                .id(menteeId).fullName("Mentee").build();
        var mentor = com.fptu.exe.skillswap.modules.identity.domain.User.builder()
                .id(mentorId).fullName("Mentor").avatarUrl("mentor.png").build();
        ConversationParticipant me = ConversationParticipant.builder().conversation(conversation).user(mentee).build();
        ConversationParticipant other = ConversationParticipant.builder().conversation(conversation).user(mentor).build();

        ConversationResponse response = mapper.mapConversationResponse(
                conversation,
                menteeId,
                Map.of(conversationId, List.of(me, other)),
                Map.of(),
                BookingChatAccessPolicy.Access.open(null, true),
                new ConversationContextMetadata("COURSE_DIRECT", null, UUID.randomUUID(), "Java Course",
                        mentorId, "Mentor", "mentor.png")
        );

        assertEquals("COURSE_DIRECT", response.contextType());
        assertEquals("Java Course", response.courseTitle());
        assertEquals(mentorId, response.mentorUserId());
        assertEquals("Mentor", response.mentorName());
    }

    @Test
    void bookingResponseRetainsBookingContextWithoutCourseContext() {
        UUID conversationId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.BOOKING)
                .sourceId(bookingId)
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();
        var meUser = com.fptu.exe.skillswap.modules.identity.domain.User.builder().id(userId).build();
        var otherUser = com.fptu.exe.skillswap.modules.identity.domain.User.builder()
                .id(otherUserId).fullName("Booking Mentor").build();
        ConversationParticipant me = ConversationParticipant.builder().conversation(conversation).user(meUser).build();
        ConversationParticipant other = ConversationParticipant.builder().conversation(conversation).user(otherUser).build();

        ConversationResponse response = mapper.mapConversationResponse(
                conversation,
                userId,
                Map.of(conversationId, List.of(me, other)),
                Map.of(),
                BookingChatAccessPolicy.Access.open(null, false),
                new ConversationContextMetadata("BOOKING", bookingId, null, null,
                        otherUserId, "Booking Mentor", null)
        );

        assertEquals("BOOKING", response.contextType());
        assertEquals(bookingId, response.bookingId());
        assertEquals(otherUserId, response.otherUserId());
        assertEquals(null, response.courseId());
    }
}
