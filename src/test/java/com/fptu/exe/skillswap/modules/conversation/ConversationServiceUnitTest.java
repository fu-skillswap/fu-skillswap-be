package com.fptu.exe.skillswap.modules.conversation;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import com.fptu.exe.skillswap.modules.conversation.domain.Message;
import com.fptu.exe.skillswap.modules.conversation.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.conversation.event.ChatMessageSavedEvent;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceUnitTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private InternalTelemetryService internalTelemetryService;
    @Mock
    private CursorCodec cursorCodec;
    @Mock
    private DomainEventOutboxService domainEventOutboxService;
    @Mock
    private RealtimeOutboxProperties realtimeOutboxProperties;

    @InjectMocks
    private ConversationService conversationService;

    private Booking booking;
    private User mentorUser;
    private User menteeUser;
    private MentorProfile mentorProfile;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        mentorUser = new User();
        mentorUser.setId(UUID.randomUUID());
        mentorUser.setFullName("Mentor FullName");

        menteeUser = new User();
        menteeUser.setId(UUID.randomUUID());
        menteeUser.setFullName("Mentee FullName");

        mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorUser.getId());
        mentorProfile.setUser(mentorUser);

        booking = Booking.builder()
                .id(bookingId)
                .mentorProfile(mentorProfile)
                .mentee(menteeUser)
                .build();
    }

    @Test
    void createDirectForAcceptedBooking_shouldReturnExisting_whenConversationAlreadyExists() {
        Conversation existing = Conversation.builder()
                .id(UUID.randomUUID())
                .sourceType(ConversationSourceType.BOOKING)
                .sourceId(bookingId)
                .build();

        when(conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId))
                .thenReturn(Optional.of(existing));

        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), mentorUser.getId())).thenReturn(true);
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), menteeUser.getId())).thenReturn(true);

        Conversation result = conversationService.createDirectForAcceptedBooking(booking);

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void createDirectForAcceptedBooking_shouldRecoverFromConcurrencyConflict() {
        Conversation existing = Conversation.builder()
                .id(UUID.randomUUID())
                .sourceType(ConversationSourceType.BOOKING)
                .sourceId(bookingId)
                .build();

        // First find returns empty
        when(conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing)); // Second find in catch block recovers it

        // Save fails due to DataIntegrityViolationException
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), mentorUser.getId())).thenReturn(true);
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), menteeUser.getId())).thenReturn(true);

        Conversation result = conversationService.createDirectForAcceptedBooking(booking);

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        verify(conversationRepository, times(1)).save(any(Conversation.class));
    }

    @Test
    void createDirectForAcceptedBooking_shouldReuseExistingDirectConversationForSameMentorAndMentee() {
        UUID secondBookingId = UUID.randomUUID();
        booking.setId(secondBookingId);
        Conversation existing = Conversation.builder()
                .id(UUID.randomUUID())
                .sourceType(ConversationSourceType.BOOKING)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();

        when(conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, secondBookingId))
                .thenReturn(Optional.empty());
        when(conversationRepository.findDirectActiveByParticipantPair(
                mentorUser.getId(),
                menteeUser.getId(),
                ConversationType.DIRECT,
                ConversationStatus.ACTIVE
        )).thenReturn(List.of(existing));
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), mentorUser.getId())).thenReturn(true);
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), menteeUser.getId())).thenReturn(true);

        Conversation result = conversationService.createDirectForAcceptedBooking(booking);

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void findConversationIdsForBookings_shouldFallbackToDirectConversationByParticipants() {
        Conversation existing = Conversation.builder()
                .id(UUID.randomUUID())
                .sourceType(ConversationSourceType.BOOKING)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();

        when(conversationRepository.findBySourceTypeAndSourceIdIn(ConversationSourceType.BOOKING, List.of(bookingId)))
                .thenReturn(List.of());
        when(conversationRepository.findDirectActiveByParticipantPair(
                mentorUser.getId(),
                menteeUser.getId(),
                ConversationType.DIRECT,
                ConversationStatus.ACTIVE
        )).thenReturn(List.of(existing));

        java.util.Map<UUID, UUID> result = conversationService.findConversationIdsForBookings(List.of(booking));

        assertEquals(existing.getId(), result.get(bookingId));
    }

    @Test
    void addParticipantIfAbsent_shouldIgnoreDataIntegrityViolationException() {
        Conversation conversation = Conversation.builder().id(UUID.randomUUID()).build();
        User user = new User();
        user.setId(UUID.randomUUID());

        when(participantRepository.existsByConversationIdAndUserId(conversation.getId(), user.getId())).thenReturn(false);
        when(participantRepository.save(any(ConversationParticipant.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate participant"));

        assertDoesNotThrow(() -> conversationService.addParticipantIfAbsent(conversation, user));
        verify(participantRepository, times(1)).save(any(ConversationParticipant.class));
    }

    @Test
    void getMyConversations_shouldUseBatchLoadingAndNotCallQueryPerConversation() {
        UUID userId = UUID.randomUUID();
        Conversation conv1 = Conversation.builder().id(UUID.randomUUID()).build();
        Conversation conv2 = Conversation.builder().id(UUID.randomUUID()).build();
        List<Conversation> conversations = List.of(conv1, conv2);
        Page<Conversation> page = new PageImpl<>(conversations, PageRequest.of(0, 10), 2);

        when(conversationRepository.findByParticipantUserId(eq(userId), any(Pageable.class))).thenReturn(page);

        User user1 = new User(); user1.setId(userId);
        User user2 = new User(); user2.setId(UUID.randomUUID()); user2.setFullName("User 2");

        ConversationParticipant cp1_conv1 = ConversationParticipant.builder().conversation(conv1).user(user1).build();
        ConversationParticipant cp2_conv1 = ConversationParticipant.builder().conversation(conv1).user(user2).build();

        ConversationParticipant cp1_conv2 = ConversationParticipant.builder().conversation(conv2).user(user1).build();
        ConversationParticipant cp2_conv2 = ConversationParticipant.builder().conversation(conv2).user(user2).build();

        when(participantRepository.findByConversationIdInWithUser(anyList()))
                .thenReturn(List.of(cp1_conv1, cp2_conv1, cp1_conv2, cp2_conv2));

        Page<ConversationResponse> response = conversationService.getMyConversations(userId, PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(2, response.getContent().size());
        verify(participantRepository, times(1)).findByConversationIdInWithUser(anyList());
        verify(participantRepository, never()).findByConversationId(any(UUID.class));
    }

    @Test
    void sendMessage_shouldPublishChatMessageSavedEvent() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("Hello world!");

        Conversation conversation = Conversation.builder().id(conversationId).type(ConversationType.DIRECT).build();
        User sender = new User(); sender.setId(senderId); sender.setFullName("Sender Name");

        MessageRepository messageRepository = mock(MessageRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        when(participantRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));

        Message savedMessage = Message.builder()
                .id(UUID.randomUUID())
                .content("Hello world!")
                .sender(sender)
                .conversation(conversation)
                .createdAt(LocalDateTime.now())
                .build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        ConversationParticipant otherParticipant = ConversationParticipant.builder()
                .conversation(conversation)
                .user(new User())
                .build();
        otherParticipant.getUser().setId(UUID.randomUUID());

        when(participantRepository.findByConversationId(conversationId))
                .thenReturn(List.of(
                        ConversationParticipant.builder().conversation(conversation).user(sender).build(),
                        otherParticipant
                ));
        when(messageRepository.countUnreadMessages(eq(conversationId), eq(otherParticipant.getUser().getId()), any()))
                .thenReturn(1L);

        MessageResponse response = conversationService.sendMessage(conversationId, senderId, request, messageRepository, userRepository);

        assertNotNull(response);
        assertEquals("Hello world!", response.content());
        assertTrue(response.isMine());

        ArgumentCaptor<ChatMessageSavedEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageSavedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        ChatMessageSavedEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("Hello world!", capturedEvent.getEvent().content());
        assertEquals(1, capturedEvent.getDeliveries().size());
        assertEquals(otherParticipant.getUser().getId(), capturedEvent.getDeliveries().getFirst().recipientUserId());
        assertEquals(1L, capturedEvent.getDeliveries().getFirst().event().unreadCount());
        assertFalse(Boolean.TRUE.equals(capturedEvent.getDeliveries().getFirst().event().isSelf()));
    }

    @Test
    void getConversationDetail_shouldReturnDetail_whenUserIsParticipant() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        User meUser = new User();
        meUser.setId(userId);

        User otherUser = new User();
        otherUser.setId(otherUserId);

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.BOOKING)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();

        ConversationParticipant me = ConversationParticipant.builder()
                .conversation(conversation)
                .user(meUser)
                .joinedAt(LocalDateTime.now().minusDays(1))
                .build();

        ConversationParticipant other = ConversationParticipant.builder()
                .conversation(conversation)
                .user(otherUser)
                .joinedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationId(conversationId)).thenReturn(List.of(me, other));
        when(messageRepository.countUnreadMessages(eq(conversationId), eq(userId), any())).thenReturn(5L);

        ConversationResponse response = conversationService.getConversationDetail(conversationId, userId);

        assertNotNull(response);
        assertEquals(conversationId, response.id());
        assertEquals(otherUserId, response.otherUserId());
        assertEquals(5L, response.unreadCount());
    }

    @Test
    void getConversationDetail_shouldThrowAccessDenied_whenUserIsNotParticipant() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = Conversation.builder().id(conversationId).build();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationId(conversationId)).thenReturn(Collections.emptyList());

        assertThrows(com.fptu.exe.skillswap.shared.exception.BaseException.class,
                () -> conversationService.getConversationDetail(conversationId, userId));
    }

    @Test
    void getTotalUnreadCount_shouldAccumulateUnreadCounts() {
        UUID userId = UUID.randomUUID();
        when(messageRepository.countTotalUnreadMessages(userId)).thenReturn(7L);

        long total = conversationService.getTotalUnreadCount(userId);
        assertEquals(7L, total);
    }

    @Test
    void markConversationAsRead_shouldUpdateLastReadAt() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ConversationParticipant me = ConversationParticipant.builder().build();
        when(participantRepository.findByConversationIdAndUserId(conversationId, userId)).thenReturn(Optional.of(me));

        conversationService.markConversationAsRead(conversationId, userId);

        assertNotNull(me.getLastReadAt());
        verify(participantRepository, times(1)).save(me);
    }
}
