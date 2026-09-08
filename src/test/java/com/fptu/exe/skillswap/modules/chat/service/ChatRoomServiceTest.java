package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationBookingLinkRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock ConversationParticipantRepository participantRepository;
    @Mock ConversationBookingLinkRepository bookingLinkRepository;
    @Mock CourseQueryPort courseQueryPort;
    @Mock UserQueryPort userQueryPort;

    @Test
    void mentorReplacementRevokesOldMentorAndReactivatesCurrentMentor() {
        UUID conversationId = UUID.randomUUID();
        User mentee = user(UUID.randomUUID());
        User oldMentor = user(UUID.randomUUID());
        User newMentor = user(UUID.randomUUID());
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .build();
        ConversationParticipant oldParticipant = participant(conversation, oldMentor, ConversationParticipantAccess.ACTIVE);
        ConversationParticipant revokedNewParticipant = participant(conversation, newMentor, ConversationParticipantAccess.REVOKED);
        ConversationParticipant menteeParticipant = participant(conversation, mentee, ConversationParticipantAccess.ACTIVE);

        when(participantRepository.findByConversationId(conversationId))
                .thenReturn(List.of(menteeParticipant, oldParticipant, revokedNewParticipant));
        when(participantRepository.findByConversationIdAndUserId(conversationId, newMentor.getId()))
                .thenReturn(Optional.of(revokedNewParticipant));
        when(participantRepository.findByConversationIdAndUserId(conversationId, mentee.getId()))
                .thenReturn(Optional.of(menteeParticipant));

        new ChatRoomService(conversationRepository, participantRepository, bookingLinkRepository,
                courseQueryPort, userQueryPort)
                .synchronizeCourseDirectParticipants(conversation, newMentor, mentee);

        assertEquals(ConversationParticipantAccess.REVOKED, oldParticipant.getAccessState());
        assertEquals(ConversationParticipantAccess.ACTIVE, revokedNewParticipant.getAccessState());
        assertEquals(ConversationParticipantAccess.ACTIVE, menteeParticipant.getAccessState());
        verify(participantRepository).save(oldParticipant);
        verify(participantRepository).save(revokedNewParticipant);
    }

    private User user(UUID id) {
        return User.builder().id(id).build();
    }

    private ConversationParticipant participant(Conversation conversation, User user,
                                                ConversationParticipantAccess access) {
        return ConversationParticipant.builder()
                .conversation(conversation).user(user).accessState(access).build();
    }
}
