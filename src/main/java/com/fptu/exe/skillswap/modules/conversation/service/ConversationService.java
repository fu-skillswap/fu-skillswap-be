package com.fptu.exe.skillswap.modules.conversation.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.conversation.event.ChatMessageRealtimeDelivery;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final ConcurrentHashMap<String, ReentrantLock> DIRECT_CONVERSATION_LOCKS = new ConcurrentHashMap<>();

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final CursorCodec cursorCodec;
    private final DomainEventOutboxService domainEventOutboxService;
    private final RealtimeOutboxProperties realtimeOutboxProperties;

    @Transactional
    public Conversation createDirectForAcceptedBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking must not be null");
        }

        User mentorUser = booking.getMentorProfile() == null ? null : booking.getMentorProfile().getUser();
        User menteeUser = booking.getMentee();
        if (mentorUser == null || mentorUser.getId() == null || menteeUser == null || menteeUser.getId() == null) {
            throw new IllegalArgumentException("Booking must have mentor and mentee users");
        }

        String lockKey = directConversationLockKey(mentorUser.getId(), menteeUser.getId());
        ReentrantLock lock = DIRECT_CONVERSATION_LOCKS.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            Optional<Conversation> existingConv = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.getId());
            Conversation conversation;
            if (existingConv.isPresent()) {
                conversation = existingConv.get();
            } else if ((conversation = findDirectByParticipants(mentorUser.getId(), menteeUser.getId())) != null) {
                // Reuse the same direct chat across multiple bookings between the same mentee and mentor.
            } else {
                conversation = Conversation.builder()
                        .sourceType(ConversationSourceType.BOOKING)
                        .sourceId(booking.getId())
                        .type(ConversationType.DIRECT)
                        .status(ConversationStatus.ACTIVE)
                        .build();
                try {
                    conversation = conversationRepository.save(conversation);
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.getId())
                            .orElseThrow(() -> ex);
                }
            }

            // Add participants idempotently
            addParticipantIfAbsent(conversation, mentorUser);
            addParticipantIfAbsent(conversation, menteeUser);

            return conversation;
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                DIRECT_CONVERSATION_LOCKS.remove(lockKey, lock);
            }
        }
    }

    @Transactional
    public void addParticipantIfAbsent(Conversation conversation, User user) {
        if (!participantRepository.existsByConversationIdAndUserId(conversation.getId(), user.getId())) {
            ConversationParticipant participant = ConversationParticipant.builder()
                    .conversation(conversation)
                    .user(user)
                    .joinedAt(DateTimeUtil.now())
                    .build();
            try {
                participantRepository.save(participant);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // Ignore if participant already created concurrently
            }
        }
    }

    @Transactional(readOnly = true)
    public Conversation findByBookingId(UUID bookingId) {
        return conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, bookingId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Conversation findDirectByParticipants(UUID firstUserId, UUID secondUserId) {
        if (firstUserId == null || secondUserId == null) {
            return null;
        }
        java.util.List<Conversation> conversations = conversationRepository.findDirectActiveByParticipantPair(
                firstUserId,
                secondUserId,
                ConversationType.DIRECT,
                ConversationStatus.ACTIVE
        );
        return conversations == null || conversations.isEmpty() ? null : conversations.getFirst();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse> getMyConversations(UUID userId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Conversation> conversationsPage = conversationRepository.findByParticipantUserId(userId, pageable);
        if (conversationsPage.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        java.util.List<Conversation> conversations = conversationsPage.getContent();
        java.util.List<UUID> conversationIds = conversations.stream()
                .map(Conversation::getId)
                .toList();

        java.util.List<ConversationParticipant> allParticipants = participantRepository.findByConversationIdInWithUser(conversationIds);
        java.util.Map<UUID, java.util.List<ConversationParticipant>> participantsByConvId = allParticipants.stream()
                .collect(java.util.stream.Collectors.groupingBy(cp -> cp.getConversation().getId()));

        java.util.List<Object[]> unreadCountsRaw = messageRepository.countUnreadMessagesBatch(conversationIds, userId);
        java.util.Map<UUID, Long> unreadCountsMap = unreadCountsRaw.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a
                ));

        return conversationsPage.map(conv -> {
            java.util.List<ConversationParticipant> participants = participantsByConvId.getOrDefault(conv.getId(), java.util.Collections.emptyList());
            ConversationParticipant other = participants.stream()
                    .filter(p -> !p.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);

            long unread = unreadCountsMap.getOrDefault(conv.getId(), 0L);

            return com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse.builder()
                    .id(conv.getId())
                    .sourceType(conv.getSourceType())
                    .sourceId(conv.getSourceId())
                    .type(conv.getType())
                    .status(conv.getStatus())
                    .otherUserId(other != null ? other.getUser().getId() : null)
                    .otherUserName(other != null ? other.getUser().getFullName() : null)
                    .otherUserAvatarUrl(other != null ? other.getUser().getAvatarUrl() : null)
                    .lastMessageContent(conv.getLastMessageContent())
                    .lastMessageAt(conv.getLastMessageAt())
                    .createdAt(conv.getCreatedAt())
                    .unreadCount(unread)
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse> getMyConversations(UUID userId,
                                                                                                                               String cursor,
                                                                                                                               Integer limit) {
        int resolvedLimit = defaultLimit(limit, 20);
        String filterHash = "conversations|userId=" + userId;
        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash, "conversation");
        List<Conversation> conversationWindow = conversationRepository.findConversationWindowByParticipant(
                userId,
                decodedCursor.sortAt(),
                decodedCursor.entityId(),
                resolvedLimit + 1
        );
        boolean hasNext = conversationWindow.size() > resolvedLimit;
        List<Conversation> visibleConversations = hasNext
                ? conversationWindow.subList(0, resolvedLimit)
                : conversationWindow;

        List<UUID> conversationIds = visibleConversations.stream()
                .map(Conversation::getId)
                .toList();
        List<ConversationParticipant> allParticipants = conversationIds.isEmpty()
                ? List.of()
                : participantRepository.findByConversationIdInWithUser(conversationIds);
        Map<UUID, List<ConversationParticipant>> participantsByConvId = allParticipants.stream()
                .collect(java.util.stream.Collectors.groupingBy(cp -> cp.getConversation().getId()));
        List<Object[]> unreadCountsRaw = conversationIds.isEmpty()
                ? List.of()
                : messageRepository.countUnreadMessagesBatch(conversationIds, userId);
        Map<UUID, Long> unreadCountsMap = unreadCountsRaw.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a
                ));
        List<com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse> items = visibleConversations.stream()
                .map(conv -> mapConversationResponse(conv, userId, participantsByConvId, unreadCountsMap))
                .toList();
        String nextCursor = hasNext && !visibleConversations.isEmpty()
                ? encodeNextConversationCursor(visibleConversations.get(visibleConversations.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse> getMessages(UUID conversationId, UUID userId, org.springframework.data.domain.Pageable pageable, com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository messageRepository) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập vào cuộc hội thoại này");
        }
        internalTelemetryService.record("CHAT_OPENED", userId, "CONVERSATION", conversationId, java.util.Map.of());

        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(msg -> com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse.builder()
                        .id(msg.getId())
                        .conversationId(msg.getConversation().getId())
                        .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                        .senderName(msg.getSender() != null ? msg.getSender().getFullName() : "Hệ thống")
                        .messageType(msg.getMessageType())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .isMine(msg.getSender() != null && msg.getSender().getId().equals(userId))
                        .build());
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse> getMessages(UUID conversationId,
                                                                                                                     UUID userId,
                                                                                                                     String cursor,
                                                                                                                     Integer limit) {
        ensureParticipant(conversationId, userId);
        internalTelemetryService.record("CHAT_OPENED", userId, "CONVERSATION", conversationId, java.util.Map.of());
        int resolvedLimit = defaultLimit(limit, 30);
        String filterHash = "messages|conversationId=" + conversationId + "|viewer=" + userId;
        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash, "message");
        List<com.fptu.exe.skillswap.modules.conversation.domain.Message> messageWindow = messageRepository.findMessageWindow(
                conversationId,
                decodedCursor.sortAt(),
                decodedCursor.entityId(),
                resolvedLimit + 1
        );
        boolean hasNext = messageWindow.size() > resolvedLimit;
        List<com.fptu.exe.skillswap.modules.conversation.domain.Message> visibleMessages = hasNext
                ? messageWindow.subList(0, resolvedLimit)
                : messageWindow;
        List<com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse> items = visibleMessages.stream()
                .map(message -> toMessageResponse(message, userId))
                .toList();
        String nextCursor = hasNext && !visibleMessages.isEmpty()
                ? encodeNextMessageCursor(visibleMessages.get(visibleMessages.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional
    public com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse sendMessage(UUID conversationId, UUID userId, com.fptu.exe.skillswap.modules.conversation.dto.request.SendMessageRequest request, com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository messageRepository, com.fptu.exe.skillswap.modules.identity.repository.UserRepository userRepository) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Bạn không có quyền gửi tin nhắn trong cuộc hội thoại này");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        if (com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus.LOCKED.equals(conversation.getStatus())) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Cuộc hội thoại này đã bị khóa.");
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));

        String content = request.content().trim();
        if (messageRepository.existsRecentDuplicateMessage(conversationId, userId, content, DateTimeUtil.now().minusSeconds(10))) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.TOO_MANY_REQUESTS,
                    "Bạn vừa gửi nội dung này rồi, vui lòng chờ một chút trước khi gửi lại"
            );
        }

        com.fptu.exe.skillswap.modules.conversation.domain.Message message = com.fptu.exe.skillswap.modules.conversation.domain.Message.builder()
                .conversation(conversation)
                .sender(sender)
                .messageType(com.fptu.exe.skillswap.modules.conversation.domain.MessageType.TEXT)
                .content(content)
                .build();
        message = messageRepository.save(message);

        conversation.setLastMessageContent(content);
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);

        com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent event = com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent.builder()
                .conversationId(conversation.getId())
                .messageId(message.getId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .conversationType(conversation.getType())
                .isSelf(false)
                .unreadCount(0L)
                .build();

        java.util.List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());

        java.util.List<UUID> recipientIds = participants.stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(userId))
                .toList();

        java.util.Map<UUID, Long> unreadCountsMap = java.util.Collections.emptyMap();
        if (!recipientIds.isEmpty()) {
            java.util.List<Object[]> unreadCountsRaw = messageRepository.countUnreadMessagesForParticipants(conversation.getId(), recipientIds);
            unreadCountsMap = unreadCountsRaw.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> (Long) row[1]
                    ));
        }

        final java.util.Map<UUID, Long> finalUnreadCountsMap = unreadCountsMap;

        java.util.List<ChatMessageRealtimeDelivery> deliveries = participants.stream()
                .filter(p -> !p.getUser().getId().equals(userId))
                .map(p -> {
                    long unreadCount = finalUnreadCountsMap.getOrDefault(p.getUser().getId(), 0L);
                    com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent recipientEvent =
                            com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent.builder()
                                    .conversationId(event.conversationId())
                                    .messageId(event.messageId())
                                    .senderId(event.senderId())
                                    .senderName(event.senderName())
                                    .messageType(event.messageType())
                                    .content(event.content())
                                    .createdAt(event.createdAt())
                                    .conversationType(event.conversationType())
                                    .isSelf(false)
                                    .unreadCount(unreadCount)
                                    .build();
                    return new ChatMessageRealtimeDelivery(p.getUser().getId(), recipientEvent);
                })
                .collect(java.util.stream.Collectors.toList());

        enqueueRealtimeOutbox(conversation, message, sender, participants);

        return com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse.builder()
                .id(message.getId())
                .conversationId(conversation.getId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isMine(true)
                .build();
    }

    @Transactional(readOnly = true)
    public com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse getConversationDetail(UUID conversationId, UUID userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(
                        com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        java.util.List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        boolean isParticipant = participants.stream().anyMatch(p -> p.getUser().getId().equals(userId));
        if (!isParticipant) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập vào cuộc hội thoại này");
        }

        ConversationParticipant other = participants.stream()
                .filter(p -> !p.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);

        ConversationParticipant me = participants.stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);

        long unread = 0;
        if (me != null) {
            java.time.LocalDateTime lastRead = me.getLastReadAt() != null ? me.getLastReadAt() : me.getJoinedAt();
            unread = messageRepository.countUnreadMessages(conv.getId(), userId, lastRead);
        }

        return com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse.builder()
                .id(conv.getId())
                .sourceType(conv.getSourceType())
                .sourceId(conv.getSourceId())
                .type(conv.getType())
                .status(conv.getStatus())
                .otherUserId(other != null ? other.getUser().getId() : null)
                .otherUserName(other != null ? other.getUser().getFullName() : null)
                .otherUserAvatarUrl(other != null ? other.getUser().getAvatarUrl() : null)
                .lastMessageContent(conv.getLastMessageContent())
                .lastMessageAt(conv.getLastMessageAt())
                .createdAt(conv.getCreatedAt())
                .unreadCount(unread)
                .build();
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadCount(UUID userId) {
        return messageRepository.countTotalUnreadMessages(userId);
    }

    @Transactional(readOnly = true)
    public java.util.Map<UUID, UUID> findConversationIdsByBookingIds(java.util.List<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.List<Conversation> convs = conversationRepository.findBySourceTypeAndSourceIdIn(ConversationSourceType.BOOKING, bookingIds);
        return convs.stream().collect(java.util.stream.Collectors.toMap(
                Conversation::getSourceId,
                Conversation::getId,
                (a, b) -> a
        ));
    }

    @Transactional(readOnly = true)
    public java.util.Map<UUID, UUID> findConversationIdsForBookings(java.util.List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.List<UUID> bookingIds = bookings.stream()
                .filter(booking -> booking != null && booking.getId() != null)
                .map(Booking::getId)
                .toList();
        java.util.Map<UUID, UUID> result = new java.util.HashMap<>(findConversationIdsByBookingIds(bookingIds));
        for (Booking booking : bookings) {
            if (booking == null || booking.getId() == null || result.containsKey(booking.getId())) {
                continue;
            }
            User mentorUser = booking.getMentorProfile() == null ? null : booking.getMentorProfile().getUser();
            User menteeUser = booking.getMentee();
            if (mentorUser == null || mentorUser.getId() == null || menteeUser == null || menteeUser.getId() == null) {
                continue;
            }
            Conversation directConversation = findDirectByParticipants(mentorUser.getId(), menteeUser.getId());
            if (directConversation != null) {
                result.put(booking.getId(), directConversation.getId());
            }
        }
        return result;
    }

    private String directConversationLockKey(UUID firstUserId, UUID secondUserId) {
        if (firstUserId == null || secondUserId == null) {
            return "direct:null";
        }
        String left = firstUserId.toString();
        String right = secondUserId.toString();
        return left.compareTo(right) <= 0 ? left + ":" + right : right + ":" + left;
    }

    @Transactional
    public void markConversationAsRead(UUID conversationId, UUID userId) {
        ConversationParticipant me = participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(
                        com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Bạn không tham gia cuộc hội thoại này"));
        java.time.LocalDateTime now = DateTimeUtil.now();
        if (me.getLastReadAt() == null || me.getLastReadAt().isBefore(now)) {
            me.setLastReadAt(now);
            participantRepository.save(me);
            if (realtimeOutboxProperties.isEnabled()) {
                domainEventOutboxService.enqueue(
                        "CONVERSATION",
                        conversationId,
                        DomainEventOutboxEventTypes.CHAT_UNREAD_COUNT_UPDATED,
                        new ChatUnreadCountUpdatedPayload(conversationId, userId)
                );
                domainEventOutboxService.enqueue(
                        "CONVERSATION",
                        conversationId,
                        DomainEventOutboxEventTypes.CHAT_CONVERSATION_UPDATED,
                        new ChatConversationUpdatedPayload(conversationId, userId)
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ChatMessageRealtimeDelivery> buildChatMessageDeliveries(UUID conversationId, UUID messageId, UUID senderId) {
        com.fptu.exe.skillswap.modules.conversation.domain.Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tin nhắn"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        return participants.stream()
                .filter(p -> !p.getUser().getId().equals(senderId))
                .map(p -> new ChatMessageRealtimeDelivery(
                        p.getUser().getId(),
                        com.fptu.exe.skillswap.modules.conversation.dto.event.ChatMessageEvent.builder()
                                .conversationId(conversationId)
                                .messageId(messageId)
                                .senderId(senderId)
                                .senderName(message.getSender() != null ? message.getSender().getFullName() : "Hệ thống")
                                .messageType(message.getMessageType())
                                .content(message.getContent())
                                .createdAt(message.getCreatedAt())
                                .conversationType(conversation.getType())
                                .isSelf(false)
                                .unreadCount(resolveUnreadCountForParticipant(conversationId, p))
                                .build()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getConversationParticipantUserIds(UUID conversationId) {
        return participantRepository.findByConversationId(conversationId).stream()
                .map(p -> p.getUser().getId())
                .toList();
    }

    private com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse mapConversationResponse(
            Conversation conv,
            UUID userId,
            Map<UUID, List<ConversationParticipant>> participantsByConvId,
            Map<UUID, Long> unreadCountsMap) {
        List<ConversationParticipant> participants = participantsByConvId.getOrDefault(conv.getId(), java.util.Collections.emptyList());
        ConversationParticipant other = participants.stream()
                .filter(p -> !p.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        long unread = unreadCountsMap.getOrDefault(conv.getId(), 0L);
        return com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse.builder()
                .id(conv.getId())
                .sourceType(conv.getSourceType())
                .sourceId(conv.getSourceId())
                .type(conv.getType())
                .status(conv.getStatus())
                .otherUserId(other != null ? other.getUser().getId() : null)
                .otherUserName(other != null ? other.getUser().getFullName() : null)
                .otherUserAvatarUrl(other != null ? other.getUser().getAvatarUrl() : null)
                .lastMessageContent(conv.getLastMessageContent())
                .lastMessageAt(conv.getLastMessageAt())
                .createdAt(conv.getCreatedAt())
                .unreadCount(unread)
                .build();
    }

    private com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse toMessageResponse(
            com.fptu.exe.skillswap.modules.conversation.domain.Message msg,
            UUID userId) {
        return com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse.builder()
                .id(msg.getId())
                .conversationId(msg.getConversation().getId())
                .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                .senderName(msg.getSender() != null ? msg.getSender().getFullName() : "Hệ thống")
                .messageType(msg.getMessageType())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .isMine(msg.getSender() != null && msg.getSender().getId().equals(userId))
                .build();
    }

    private void ensureParticipant(UUID conversationId, UUID userId) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập vào cuộc hội thoại này");
        }
    }

    private int defaultLimit(Integer limit, int defaultValue) {
        int resolved = limit == null || limit <= 0 ? defaultValue : limit;
        return Math.min(resolved, 50);
    }

    private DecodedCursor decodeCursor(String cursor, String expectedFilterHash, String entityLabel) {
        if (cursor == null || cursor.isBlank()) {
            return DecodedCursor.empty();
        }
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!expectedFilterHash.equals(payload.filterHash())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không khớp với bộ lọc hiện tại");
        }
        if (payload.sortKey() == null || payload.secondaryKey() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không hợp lệ");
        }
        try {
            return new DecodedCursor(
                    LocalDateTime.parse(payload.sortKey()),
                    UUID.fromString(payload.secondaryKey())
            );
        } catch (RuntimeException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor chứa " + entityLabel + " window không hợp lệ", ex);
        }
    }

    private String encodeNextConversationCursor(Conversation conversation, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(resolveConversationActivityAt(conversation).toString())
                .secondaryKey(conversation.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    private String encodeNextMessageCursor(com.fptu.exe.skillswap.modules.conversation.domain.Message message, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(message.getCreatedAt().toString())
                .secondaryKey(message.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    private LocalDateTime resolveConversationActivityAt(Conversation conversation) {
        return conversation.getLastMessageAt() != null ? conversation.getLastMessageAt() : conversation.getCreatedAt();
    }

    private record DecodedCursor(LocalDateTime sortAt, UUID entityId) {
        private static DecodedCursor empty() {
            return new DecodedCursor(null, null);
        }
    }

    private void enqueueRealtimeOutbox(Conversation conversation,
                                       com.fptu.exe.skillswap.modules.conversation.domain.Message message,
                                       User sender,
                                       List<ConversationParticipant> participants) {
        if (!realtimeOutboxProperties.isEnabled()) {
            return;
        }
        domainEventOutboxService.enqueue(
                "CONVERSATION",
                conversation.getId(),
                DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED,
                new ChatMessageCreatedPayload(conversation.getId(), message.getId(), sender.getId())
        );
        domainEventOutboxService.enqueue(
                "CONVERSATION",
                conversation.getId(),
                DomainEventOutboxEventTypes.CHAT_CONVERSATION_UPDATED,
                new ChatConversationUpdatedPayload(conversation.getId(), sender.getId())
        );
        participants.stream()
                .map(ConversationParticipant::getUser)
                .filter(user -> user != null && user.getId() != null)
                .forEach(user -> domainEventOutboxService.enqueue(
                        "CONVERSATION",
                        conversation.getId(),
                        DomainEventOutboxEventTypes.CHAT_UNREAD_COUNT_UPDATED,
                        new ChatUnreadCountUpdatedPayload(conversation.getId(), user.getId())
                ));
    }

    private long resolveUnreadCountForParticipant(UUID conversationId, ConversationParticipant participant) {
        LocalDateTime lastRead = participant.getLastReadAt() != null ? participant.getLastReadAt() : participant.getJoinedAt();
        return messageRepository.countUnreadMessages(conversationId, participant.getUser().getId(), lastRead);
    }

    public record ChatMessageCreatedPayload(UUID conversationId, UUID messageId, UUID senderId) {
    }

    public record ChatConversationUpdatedPayload(UUID conversationId, UUID actorUserId) {
    }

    public record ChatUnreadCountUpdatedPayload(UUID conversationId, UUID recipientUserId) {
    }
}
