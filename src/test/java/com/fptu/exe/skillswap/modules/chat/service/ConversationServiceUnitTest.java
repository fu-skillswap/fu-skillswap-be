package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.domain.CourseConversationContext;
import com.fptu.exe.skillswap.modules.chat.domain.Message;
import com.fptu.exe.skillswap.modules.chat.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.MessageResponse;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationBookingLinkRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ChatUploadIntentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.chat.repository.CourseConversationContextRepository;
import com.fptu.exe.skillswap.modules.chat.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.chat.service.ConversationSafetyPolicy;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
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
    @Mock
    private ConversationSafetyPolicy conversationSafetyPolicy;

    @Mock
    private ConversationBookingLinkRepository conversationBookingLinkRepository;
    @Mock
    private ChatAttachmentRepository chatAttachmentRepository;
    @Mock
    private ChatUploadIntentRepository chatUploadIntentRepository;
    @Mock
    private UserQueryPort userQueryPort;
    @Mock
    private CourseQueryPort courseQueryPort;
    @Mock
    private CourseConversationContextRepository courseConversationContextRepository;
    @Mock
    private ChatAttachmentService chatAttachmentService;
    @Mock
    private ChatAccessResolutionService chatAccessResolutionService;
    @Mock
    private ObjectProvider<GroupChatFanoutDispatcher> groupChatFanoutDispatcherProvider;
    @Mock
    private ObjectProvider<UserQueryPort> userQueryPortProvider;

    private ConversationService conversationService;

    private Booking booking;
    private User mentorUser;
    private User menteeUser;
    private MentorProfile mentorProfile;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        lenient().when(conversationSafetyPolicy.apply(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(userQueryPort.isUserActive(any())).thenReturn(true);
        lenient().when(userQueryPort.findUsersByIdIn(any())).thenAnswer(invocation ->
                ((java.util.Collection<UUID>) invocation.getArgument(0)).stream().map(id -> {
                    User user = new User();
                    user.setId(id);
                    user.setStatus(UserStatus.ACTIVE);
                    return user;
                }).toList());
        lenient().when(userQueryPortProvider.getIfAvailable()).thenReturn(userQueryPort);
        bookingId = UUID.randomUUID();
        mentorUser = new User();
        mentorUser.setId(UUID.randomUUID());
        mentorUser.setFullName("Mentor FullName");

        menteeUser = new User();
        menteeUser.setId(UUID.randomUUID());
        menteeUser.setFullName("Mentee FullName");

        mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorUser.getId());

        booking = Booking.builder()
                .id(bookingId)
                .mentorUserId(mentorProfile.getUserId())
                .menteeUserId(menteeUser.getId())
                .build();

        ChatRoomService chatRoomService = new ChatRoomService(
                conversationRepository,
                participantRepository,
                conversationBookingLinkRepository,
                courseQueryPort,
                userQueryPort
        );
        ChatResponseMapper chatResponseMapper = new ChatResponseMapper(
                cursorCodec,
                chatAttachmentRepository,
                participantRepository
        );
        ChatMessageService chatMessageService = new ChatMessageService(
                messageRepository,
                conversationRepository,
                participantRepository,
                chatAttachmentRepository,
                chatAttachmentService,
                chatAccessResolutionService,
                chatResponseMapper,
                domainEventOutboxService,
                realtimeOutboxProperties,
                groupChatFanoutDispatcherProvider,
                userQueryPortProvider
        );
        ChatReadReceiptService chatReadReceiptService = new ChatReadReceiptService(
                participantRepository,
                conversationRepository,
                domainEventOutboxService,
                realtimeOutboxProperties,
                userQueryPort
        );
        ChatQueryService chatQueryService = new ChatQueryService(
                conversationRepository,
                participantRepository,
                messageRepository,
                chatRoomService,
                chatAccessResolutionService,
                chatResponseMapper,
                internalTelemetryService,
                courseQueryPort,
                userQueryPort,
                courseConversationContextRepository
        );
        ChatService chatService = new ChatService(
                chatRoomService,
                chatMessageService,
                chatAttachmentService,
                chatReadReceiptService,
                chatQueryService
        );
        conversationService = new ConversationService(chatService);

        lenient().when(messageRepository.findByBookingIdAndSystemEventType(any(), eq("BOOKING_CONFIRMED")))
                .thenReturn(Optional.of(new Message()));
        lenient().when(conversationRepository.findByIdForUpdate(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.of(Conversation.builder()
                    .id(id)
                    .sourceType(ConversationSourceType.BOOKING)
                    .sourceId(bookingId)
                    .nextSequence(0L)
                    .build());
        });
        lenient().when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message msg = inv.getArgument(0);
            if (msg.getId() == null) {
                msg.setId(UUID.randomUUID());
            }
            if (msg.getSequence() == null) {
                msg.setSequence(1L);
            }
            return msg;
        });
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
        when(userQueryPort.findUserById(mentorUser.getId())).thenReturn(Optional.of(mentorUser));
        when(userQueryPort.findUserById(menteeUser.getId())).thenReturn(Optional.of(menteeUser));

        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), mentorUser.getId())).thenReturn(true);
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), menteeUser.getId())).thenReturn(true);

        Conversation result = conversationService.createDirectForAcceptedBooking(
                booking.getId(), mentorUser.getId(), menteeUser.getId());

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
        when(userQueryPort.findUserById(mentorUser.getId())).thenReturn(Optional.of(mentorUser));
        when(userQueryPort.findUserById(menteeUser.getId())).thenReturn(Optional.of(menteeUser));

        // Save fails due to DataIntegrityViolationException
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), mentorUser.getId())).thenReturn(true);
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), menteeUser.getId())).thenReturn(true);

        Conversation result = conversationService.createDirectForAcceptedBooking(
                booking.getId(), mentorUser.getId(), menteeUser.getId());

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
                .sourceId(bookingId)
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();

        when(conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, secondBookingId))
                .thenReturn(Optional.empty());
        when(userQueryPort.findUserById(mentorUser.getId())).thenReturn(Optional.of(mentorUser));
        when(userQueryPort.findUserById(menteeUser.getId())).thenReturn(Optional.of(menteeUser));
        when(conversationRepository.findDirectActiveByParticipantPair(
                mentorUser.getId(),
                menteeUser.getId(),
                ConversationType.DIRECT,
                ConversationStatus.ACTIVE
        )).thenReturn(List.of(existing));
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), mentorUser.getId())).thenReturn(true);
        when(participantRepository.existsByConversationIdAndUserId(existing.getId(), menteeUser.getId())).thenReturn(true);

        Conversation result = conversationService.createDirectForAcceptedBooking(
                booking.getId(), mentorUser.getId(), menteeUser.getId());

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void findConversationIdsForBookings_shouldLoadBookingLinkedConversations() {
        Conversation existing = Conversation.builder()
                .id(UUID.randomUUID())
                .sourceType(ConversationSourceType.BOOKING)
                .sourceId(bookingId)
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();

        when(conversationRepository.findBySourceTypeAndSourceIdIn(ConversationSourceType.BOOKING, List.of(bookingId)))
                .thenReturn(List.of(existing));

        java.util.Map<UUID, UUID> result = conversationService.findConversationIdsByBookingIds(List.of(bookingId));

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
        when(messageRepository.countUnreadMessagesBatch(anyList(), eq(userId))).thenReturn(List.of());
        when(chatAccessResolutionService.resolveMessagingAccess(any(), eq(userId)))
                .thenReturn(BookingChatAccessPolicy.Access.open(null, false));

        Page<ConversationResponse> response = conversationService.getMyConversations(userId, PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(2, response.getContent().size());
        verify(participantRepository, times(1)).findByConversationIdInWithUser(anyList());
        verify(participantRepository, never()).findByConversationId(any(UUID.class));
    }

    @Test
    void sendMessage_shouldEnqueueDomainEventOutbox() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("Hello world!");

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .build();
        User sender = new User(); sender.setId(senderId); sender.setFullName("Sender Name");

        when(participantRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
        when(userQueryPortProvider.getIfAvailable()).thenReturn(userQueryPort);
        when(userQueryPort.findUserById(senderId)).thenReturn(Optional.of(sender));
        when(chatAccessResolutionService.resolveMessagingAccess(any(), any()))
                .thenReturn(BookingChatAccessPolicy.Access.open(null, false));
        when(realtimeOutboxProperties.isEnabled()).thenReturn(true);

        Message savedMessage = Message.builder()
                .id(UUID.randomUUID())
                .content("Hello world!")
                .sender(sender)
                .conversation(conversation)
                .sequence(1L)
                .createdAt(LocalDateTime.now())
                .build();
        when(this.messageRepository.save(any(Message.class))).thenReturn(savedMessage);

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


        MessageResponse response = conversationService.sendMessage(conversationId, senderId, request);

        assertNotNull(response);
        assertEquals("Hello world!", response.content());
        assertTrue(response.isMine());

        verify(domainEventOutboxService, times(4)).enqueue(any(), any(), any(), any());
    }

    @Test
    void attachmentValidation_shouldCompleteBeforeConversationLock() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID attachmentIntentId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.DIRECT)
                .nextSequence(0L)
                .build();
        User sender = new User();
        sender.setId(senderId);
        sender.setFullName("Sender");

        when(participantRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
        when(userQueryPortProvider.getIfAvailable()).thenReturn(userQueryPort);
        when(userQueryPort.findUserById(senderId)).thenReturn(Optional.of(sender));
        when(chatAccessResolutionService.resolveMessagingAccess(any(), eq(senderId)))
                .thenReturn(BookingChatAccessPolicy.Access.open(null, false));
        when(realtimeOutboxProperties.isEnabled()).thenReturn(true);
        doReturn(java.util.Map.of()).when(chatAttachmentService)
                .validateAttachmentIntents(conversationId, senderId, List.of(attachmentIntentId));
        doNothing().when(chatAttachmentService).consumeAttachmentIntents(
                any(), any(), any(), any(), any());

        InOrder order = inOrder(chatAttachmentService, conversationRepository);
        conversationService.sendMessage(
                conversationId,
                senderId,
                new SendMessageRequest(UUID.randomUUID(), "with attachment", null, List.of(attachmentIntentId))
        );

        order.verify(chatAttachmentService).validateAttachmentIntents(
                conversationId, senderId, List.of(attachmentIntentId));
        order.verify(conversationRepository).findByIdForUpdate(conversationId);
    }

    @Test
    void sendMessage_shouldRejectInactiveUserBeforePersistence() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User inactiveUser = new User();
        inactiveUser.setId(userId);
        inactiveUser.setStatus(UserStatus.INACTIVE);

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.DIRECT)
                .nextSequence(0L)
                .build();

        when(participantRepository.existsByConversationIdAndUserId(conversationId, userId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(userQueryPortProvider.getIfAvailable()).thenReturn(userQueryPort);
        when(userQueryPort.findUserById(userId)).thenReturn(Optional.of(inactiveUser));
        when(chatAccessResolutionService.resolveMessagingAccess(any(), eq(userId)))
                .thenReturn(BookingChatAccessPolicy.Access.open(null, false));

        var exception = assertThrows(com.fptu.exe.skillswap.shared.exception.BaseException.class,
                () -> conversationService.sendMessage(conversationId, userId, new SendMessageRequest("blocked")));

        assertEquals(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void getMessages_shouldRejectDeletedUserBeforeReadingConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID deletedUserId = UUID.randomUUID();
        when(userQueryPort.isUserActive(deletedUserId)).thenReturn(false);

        var exception = assertThrows(com.fptu.exe.skillswap.shared.exception.BaseException.class,
                () -> conversationService.getMessages(conversationId, deletedUserId, PageRequest.of(0, 10), null));

        assertEquals(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(participantRepository, never()).existsByConversationIdAndUserId(any(), any());
    }

    @Test
    void sendGroupMessage_shouldUseOutboxOnlyWhenRealtimeIsEnabled() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        User sender = new User();
        sender.setId(senderId);
        sender.setFullName("Sender");
        User recipient = new User();
        recipient.setId(UUID.randomUUID());

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.GROUP)
                .nextSequence(0L)
                .build();
        when(participantRepository.existsByConversationIdAndUserId(conversationId, senderId)).thenReturn(true);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
        when(userQueryPortProvider.getIfAvailable()).thenReturn(userQueryPort);
        when(userQueryPort.findUserById(senderId)).thenReturn(Optional.of(sender));
        when(chatAccessResolutionService.resolveMessagingAccess(any(), eq(senderId)))
                .thenReturn(BookingChatAccessPolicy.Access.open(null, false));
        when(realtimeOutboxProperties.isEnabled()).thenReturn(true);
        when(participantRepository.findByConversationId(conversationId)).thenReturn(List.of(
                ConversationParticipant.builder().conversation(conversation).user(sender).build(),
                ConversationParticipant.builder().conversation(conversation).user(recipient).build()
        ));

        conversationService.sendMessage(conversationId, senderId, new SendMessageRequest("group message"));

        verify(domainEventOutboxService, times(1)).enqueue(
                eq("CONVERSATION"), eq(conversationId), eq(DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED), any());
        verify(groupChatFanoutDispatcherProvider, never()).getIfAvailable();
    }

    @Test
    void buildChatMessageDeliveries_shouldExposePersistedSequence() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Conversation conversation = Conversation.builder().id(conversationId).type(ConversationType.DIRECT).build();
        User sender = new User();
        sender.setId(senderId);
        sender.setFullName("Sender");
        Message message = Message.builder()
                .id(messageId)
                .conversation(conversation)
                .sender(sender)
                .sequence(42L)
                .content("Ordered message")
                .createdAt(LocalDateTime.now())
                .build();
        User recipient = new User();
        recipient.setId(UUID.randomUUID());

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationId(conversationId)).thenReturn(List.of(
                ConversationParticipant.builder().conversation(conversation).user(sender).build(),
                ConversationParticipant.builder().conversation(conversation).user(recipient).build()
        ));

        var deliveries = conversationService.buildChatMessageDeliveries(conversationId, messageId, senderId);

        assertEquals(1, deliveries.size());
        assertEquals(42L, deliveries.getFirst().event().sequence());
    }

    @Test
    void buildChatMessageDeliveries_shouldSkipInactiveRecipients() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID inactiveRecipientId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .build();
        User sender = new User();
        sender.setId(senderId);
        sender.setFullName("Sender");
        Message message = Message.builder()
                .id(messageId)
                .conversation(conversation)
                .sender(sender)
                .sequence(42L)
                .content("Ordered message")
                .createdAt(LocalDateTime.now())
                .build();
        User inactiveRecipient = new User();
        inactiveRecipient.setId(inactiveRecipientId);

        doReturn(List.of(sender)).when(userQueryPort).findUsersByIdIn(any());
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationId(conversationId)).thenReturn(List.of(
                ConversationParticipant.builder().conversation(conversation).user(sender).build(),
                ConversationParticipant.builder().conversation(conversation).user(inactiveRecipient).build()
        ));

        var deliveries = conversationService.buildChatMessageDeliveries(conversationId, messageId, senderId);

        assertEquals(0, deliveries.size());
        verify(userQueryPort, times(1)).findUsersByIdIn(any());
    }

    @Test
    void messagePageMapping_shouldBatchAttachmentsAndReadReceipts() {
        UUID userId = UUID.randomUUID();
        Conversation conversation = Conversation.builder()
                .id(UUID.randomUUID())
                .type(ConversationType.DIRECT)
                .build();
        List<Message> messages = java.util.stream.IntStream.range(0, 50)
                .mapToObj(index -> Message.builder()
                        .id(UUID.randomUUID())
                        .conversation(conversation)
                        .sequence((long) index)
                        .content("message-" + index)
                        .build())
                .toList();
        when(chatAttachmentRepository.findByMessageIdIn(any())).thenReturn(List.of());
        when(participantRepository.findByConversationIdInWithUser(any())).thenReturn(List.of());

        List<MessageResponse> responses = new ChatResponseMapper(
                cursorCodec,
                chatAttachmentRepository,
                participantRepository
        ).toMessageResponses(messages, userId);

        assertEquals(50, responses.size());
        verify(chatAttachmentRepository, times(1)).findByMessageIdIn(any());
        verify(participantRepository, times(1)).findByConversationIdInWithUser(any());
        verify(chatAttachmentRepository, never()).findByMessageId(any());
        verify(participantRepository, never()).findByConversationId(any());
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
                .lastReadSequence(42L)
                .build();

        ConversationParticipant other = ConversationParticipant.builder()
                .conversation(conversation)
                .user(otherUser)
                .joinedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationIdWithUser(conversationId)).thenReturn(List.of(me, other));
        when(messageRepository.countUnreadMessages(eq(conversationId), eq(userId), eq(42L))).thenReturn(5L);
        when(chatAccessResolutionService.resolveMessagingAccess(any(), eq(userId)))
                .thenReturn(BookingChatAccessPolicy.Access.open(null, false));

        ConversationResponse response = conversationService.getConversationDetail(conversationId, userId);

        assertNotNull(response);
        assertEquals(conversationId, response.id());
        assertEquals(otherUserId, response.otherUserId());
        assertEquals(5L, response.unreadCount());
        assertEquals(42L, response.myLastReadSequence());
    }

    @Test
    void getConversationDetail_shouldExposeCourseContextMetadata() {
        UUID conversationId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();

        User meUser = new User();
        meUser.setId(userId);
        User mentorUser = new User();
        mentorUser.setId(mentorId);
        mentorUser.setFullName("Course Mentor");

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .sourceType(ConversationSourceType.COURSE)
                .sourceId(contextId)
                .type(ConversationType.DIRECT)
                .status(ConversationStatus.ACTIVE)
                .build();
        ConversationParticipant me = ConversationParticipant.builder()
                .conversation(conversation).user(meUser).lastReadSequence(0L).build();
        ConversationParticipant mentor = ConversationParticipant.builder()
                .conversation(conversation).user(mentorUser).build();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationIdWithUser(conversationId)).thenReturn(List.of(me, mentor));
        when(messageRepository.countUnreadMessages(conversationId, userId, 0L)).thenReturn(0L);
        when(chatAccessResolutionService.resolveMessagingAccess(any(), eq(userId)))
                .thenReturn(BookingChatAccessPolicy.Access.open(null, true));
        when(courseConversationContextRepository.findByConversationIdIn(any()))
                .thenReturn(List.of(CourseConversationContext.builder()
                        .id(contextId).courseId(courseId).menteeUserId(userId)
                        .conversationId(conversationId).build()));
        when(courseQueryPort.findCourseTitleById(courseId)).thenReturn(Optional.of("Course X"));
        when(courseQueryPort.findCourseChatContext(courseId))
                .thenReturn(Optional.of(new CourseQueryPort.CourseChatContext(courseId, mentorId)));
        when(userQueryPort.findUserSummaryById(mentorId)).thenReturn(Optional.of(
                new com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord(
                        mentorId, "mentor@example.com", "Course Mentor", "mentor.png",
                        java.util.Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR),
                        "ACTIVE", true)));

        ConversationResponse response = conversationService.getConversationDetail(conversationId, userId);

        assertEquals("COURSE_DIRECT", response.contextType());
        assertEquals(courseId, response.courseId());
        assertEquals("Course X", response.courseTitle());
        assertEquals(mentorId, response.mentorUserId());
        assertEquals("Course Mentor", response.mentorName());
        assertEquals("mentor.png", response.mentorAvatarUrl());
    }

    @Test
    void getConversationDetail_shouldThrowAccessDenied_whenUserIsNotParticipant() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = Conversation.builder().id(conversationId).build();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationIdWithUser(conversationId)).thenReturn(Collections.emptyList());

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
