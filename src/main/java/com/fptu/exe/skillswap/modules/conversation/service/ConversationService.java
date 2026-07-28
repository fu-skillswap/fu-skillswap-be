package com.fptu.exe.skillswap.modules.conversation.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationBookingLink;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationBookingLinkRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ChatUploadIntentRepository;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final CursorCodec cursorCodec;
    private final DomainEventOutboxService domainEventOutboxService;
    private final RealtimeOutboxProperties realtimeOutboxProperties;
    private final com.fptu.exe.skillswap.modules.booking.service.BookingChatAccessPolicy bookingChatAccessPolicy;
    private final ConversationBookingLinkRepository conversationBookingLinkRepository;
    private final ChatUploadIntentRepository chatUploadIntentRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ObjectProvider<StorageGateway> storageGatewayProvider;
    private final com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService rateLimitService;
    private final com.fptu.exe.skillswap.modules.notification.service.NotificationService notificationService;

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

        Conversation conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.getId())
                .or(() -> conversationRepository.findByMentorUserIdAndMenteeUserId(mentorUser.getId(), menteeUser.getId()))
                .orElse(null);
        if (conversation == null) {
            try {
                conversation = conversationRepository.save(Conversation.builder()
                        // Keep legacy source columns populated while clients migrate to booking links.
                        .sourceType(ConversationSourceType.BOOKING)
                        .sourceId(booking.getId())
                        .mentorUserId(mentorUser.getId())
                        .menteeUserId(menteeUser.getId())
                        .type(ConversationType.DIRECT)
                        .status(ConversationStatus.ACTIVE)
                        .build());
            } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
                conversation = null;
            }
        }
        if (conversation == null) {
            // A concurrent insert can win the direct-pair uniqueness race.
            conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.getId())
                    .or(() -> conversationRepository.findByMentorUserIdAndMenteeUserId(mentorUser.getId(), menteeUser.getId()))
                    .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể tạo cuộc hội thoại trực tiếp"));
        }

        addParticipantIfAbsent(conversation, mentorUser);
        addParticipantIfAbsent(conversation, menteeUser);
        if (conversationBookingLinkRepository != null && !conversationBookingLinkRepository.existsByBookingId(booking.getId())) {
            conversationBookingLinkRepository.save(ConversationBookingLink.builder()
                    .conversation(conversation)
                    .booking(booking)
                    .build());
        }
        createBookingConfirmedSystemMessage(conversation.getId(), booking);
        return conversation;
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
            var access = resolveMessagingAccess(conv);

            return com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse.builder()
                    .id(conv.getId())
                    .type(conv.getType())
                    .status(conv.getStatus())
                    .otherUserId(other != null ? other.getUser().getId() : null)
                    .otherUserName(other != null ? other.getUser().getFullName() : null)
                    .otherUserAvatarUrl(other != null ? other.getUser().getAvatarUrl() : null)
                    .lastMessageContent(conv.getLastMessageContent())
                    .lastMessageAt(conv.getLastMessageAt())
                    .createdAt(conv.getCreatedAt())
                    .unreadCount(unread)
                    .messagingAccess(access.messagingAccess())
                    .canSendMessages(access.canSendMessages())
                    .canUploadAttachments(access.canUploadAttachments())
                    .canDownloadAttachments(access.canDownloadAttachments())
                    .readOnlyReason(access.readOnlyReason())
                    .messagingWindowEndsAt(access.messagingWindowEndsAt())
                    .postSessionChatPermanent(access.postSessionChatPermanent())
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

        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .or(() -> conversationRepository.findById(conversationId))
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        var access = resolveMessagingAccess(conversation);
        if (!access.canSendMessages()) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED,
                    "Cuộc hội thoại hiện chỉ cho phép xem lịch sử: " + access.readOnlyReason());
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));

        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank() && (request.attachmentIntentIds() == null || request.attachmentIntentIds().isEmpty())) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(
                    com.fptu.exe.skillswap.shared.exception.ErrorCode.BAD_REQUEST, "Tin nhắn cần có nội dung hoặc tệp đính kèm"
            );
        }
        String requestHash = Integer.toHexString(java.util.Objects.hash(content, request.replyToMessageId(), request.attachmentIntentIds()));
        var replay = messageRepository.findByConversationIdAndSenderIdAndClientMessageId(conversationId, userId, request.clientMessageId());
        if (replay.isPresent()) {
            if (!java.util.Objects.equals(replay.get().getRequestHash(), requestHash)) {
                throw new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.CHAT_CLIENT_MESSAGE_CONFLICT);
            }
            return toMessageResponse(replay.get(), userId);
        }

        com.fptu.exe.skillswap.modules.conversation.domain.Message message = com.fptu.exe.skillswap.modules.conversation.domain.Message.builder()
                .conversation(conversation)
                .sender(sender)
                .messageType(com.fptu.exe.skillswap.modules.conversation.domain.MessageType.TEXT)
                .content(content)
                .sequence(conversation.getNextSequence() + 1)
                .clientMessageId(request.clientMessageId())
                .requestHash(requestHash)
                .replyToMessageId(request.replyToMessageId())
                .build();
        message = messageRepository.save(message);

        consumeAttachmentIntents(conversation, sender.getId(), message, request.attachmentIntentIds());

        conversation.setNextSequence(message.getSequence());

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
        if (notificationService != null) {
            recipientIds.forEach(recipientId -> notificationService.upsertChatUnread(recipientId, conversation.getId()));
        }

        return com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse.builder()
                .id(message.getId())
                .sequence(message.getSequence())
                .conversationId(conversation.getId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .state(message.getState())
                .createdAt(message.getCreatedAt())
                .isMine(true)
                .attachments(mapAttachments(message.getId()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse> getMessagesBySequence(UUID conversationId, UUID userId, Long beforeSequence, Long afterSequence, Integer limit) {
        ensureParticipant(conversationId, userId);
        if (beforeSequence != null && afterSequence != null) throw new BaseException(ErrorCode.BAD_REQUEST, "CHAT_MESSAGE_CURSOR_INVALID");
        int resolved = defaultLimit(limit, 30);
        return messageRepository.findByConversationSequenceWindow(conversationId, beforeSequence, afterSequence, org.springframework.data.domain.PageRequest.of(0, resolved)).stream().map(m -> toMessageResponse(m, userId)).toList();
    }

    @Transactional
    public com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentUploadIntentResponse createAttachmentUploadIntent(
            UUID conversationId, UUID userId, com.fptu.exe.skillswap.modules.conversation.dto.request.ChatAttachmentUploadIntentRequest request) {
        ensureParticipant(conversationId, userId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        if (!resolveMessagingAccess(conversation).canUploadAttachments()) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "CHAT_CONVERSATION_READ_ONLY");
        }
        String contentType = normalizeAttachmentContentType(request.contentType());
        validateAttachmentFilename(request.filename(), contentType);
        if (request.sizeBytes() > 10L * 1024 * 1024) throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "CHAT_ATTACHMENT_QUOTA_EXCEEDED");
        if (chatAttachmentRepository.sumUploadedBytesByUserSince(userId, DateTimeUtil.now().toLocalDate().atStartOfDay()) + request.sizeBytes() > 50L * 1024 * 1024) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "CHAT_ATTACHMENT_QUOTA_EXCEEDED");
        }
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.TRANSFER, "chat:attachment-intent:" + userId, 20, java.time.Duration.ofMinutes(15), "Bạn tạo upload intent quá nhanh");
        UUID intentId = UUID.randomUUID();
        String key = "chat-attachments/" + conversationId + "/" + intentId + extensionFor(contentType);
        var intent = com.fptu.exe.skillswap.modules.conversation.domain.ChatUploadIntent.builder()
                .conversation(conversation).ownerUserId(userId).storageKey(key).originalFilename(request.filename().trim())
                .contentType(contentType).expectedSizeBytes(request.sizeBytes()).expiresAt(DateTimeUtil.now().plusMinutes(15)).build();
        chatUploadIntentRepository.save(intent);
        var upload = storageGateway().generatePrivateUploadUrl(key, contentType, java.time.Duration.ofMinutes(15));
        return new com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentUploadIntentResponse(intent.getId(), upload.uploadUrl(), upload.expiresAt(), contentType);
    }

    @Transactional(readOnly = true)
    public com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentDownloadResponse downloadAttachment(UUID attachmentId, UUID userId) {
        var attachment = chatAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp đính kèm"));
        Conversation conversation = attachment.getMessage().getConversation();
        ensureParticipant(conversation.getId(), userId);
        var access = resolveMessagingAccess(conversation);
        if (!access.canDownloadAttachments() || attachment.getState() != com.fptu.exe.skillswap.modules.conversation.domain.ChatAttachmentState.ACTIVE
                || !attachment.getExpiresAt().isAfter(DateTimeUtil.now())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp đính kèm");
        }
        String disposition = inlineCapable(attachment.getContentType()) ? "inline" : "attachment";
        var download = storageGateway().generatePrivateDownloadUrl(attachment.getStorageKey(), java.time.Duration.ofMinutes(10), disposition);
        return new com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentDownloadResponse(download.downloadUrl(), download.expiresAt());
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
                .type(conv.getType())
                .status(conv.getStatus())
                .otherUserId(other != null ? other.getUser().getId() : null)
                .otherUserName(other != null ? other.getUser().getFullName() : null)
                .otherUserAvatarUrl(other != null ? other.getUser().getAvatarUrl() : null)
                .lastMessageContent(conv.getLastMessageContent())
                .lastMessageAt(conv.getLastMessageAt())
                .createdAt(conv.getCreatedAt())
                .unreadCount(unread)
                .messagingAccess(resolveMessagingAccess(conv).messagingAccess())
                .canSendMessages(resolveMessagingAccess(conv).canSendMessages())
                .canUploadAttachments(resolveMessagingAccess(conv).canUploadAttachments())
                .canDownloadAttachments(resolveMessagingAccess(conv).canDownloadAttachments())
                .readOnlyReason(resolveMessagingAccess(conv).readOnlyReason())
                .messagingWindowEndsAt(resolveMessagingAccess(conv).messagingWindowEndsAt())
                .postSessionChatPermanent(resolveMessagingAccess(conv).postSessionChatPermanent())
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
                .filter(p -> senderId == null || !p.getUser().getId().equals(senderId))
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
        var access = resolveMessagingAccess(conv);
        return com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse.builder()
                .id(conv.getId())
                .type(conv.getType())
                .status(conv.getStatus())
                .otherUserId(other != null ? other.getUser().getId() : null)
                .otherUserName(other != null ? other.getUser().getFullName() : null)
                .otherUserAvatarUrl(other != null ? other.getUser().getAvatarUrl() : null)
                .lastMessageContent(conv.getLastMessageContent())
                .lastMessageAt(conv.getLastMessageAt())
                .createdAt(conv.getCreatedAt())
                .unreadCount(unread)
                .messagingAccess(access.messagingAccess())
                .canSendMessages(access.canSendMessages())
                .canUploadAttachments(access.canUploadAttachments())
                .canDownloadAttachments(access.canDownloadAttachments())
                .readOnlyReason(access.readOnlyReason())
                .messagingWindowEndsAt(access.messagingWindowEndsAt())
                .postSessionChatPermanent(access.postSessionChatPermanent())
                .build();
    }

    private com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse toMessageResponse(
            com.fptu.exe.skillswap.modules.conversation.domain.Message msg,
            UUID userId) {
        boolean mine = msg.getSender() != null && msg.getSender().getId().equals(userId);
        return com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse.builder()
                .id(msg.getId())
                .sequence(msg.getSequence())
                .conversationId(msg.getConversation().getId())
                .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                .senderName(msg.getSender() != null ? msg.getSender().getFullName() : "Hệ thống")
                .messageType(msg.getMessageType())
                .content(msg.getState() == com.fptu.exe.skillswap.modules.conversation.domain.MessageState.DELETED ? null : msg.getContent())
                .state(msg.getState())
                .editedAt(msg.getEditedAt())
                .deletedAt(msg.getDeletedAt())
                .isReadByOther(mine ? Boolean.FALSE : null)
                .createdAt(msg.getCreatedAt())
                .isMine(mine)
                .attachments(mapAttachments(msg.getId()))
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    @Transactional
    public com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse editMessage(UUID conversationId, UUID messageId, UUID userId, com.fptu.exe.skillswap.modules.conversation.dto.request.UpdateMessageRequest request) {
        var message = messageRepository.findById(messageId).filter(m -> m.getConversation().getId().equals(conversationId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tin nhắn"));
        assertEditable(message, userId, request.expectedVersion());
        message.setContent(request.content().trim()); message.setEditedAt(DateTimeUtil.now());
        return toMessageResponse(messageRepository.save(message), userId);
    }

    @Transactional
    public com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse deleteMessage(UUID conversationId, UUID messageId, UUID userId, com.fptu.exe.skillswap.modules.conversation.dto.request.DeleteMessageRequest request) {
        var message = messageRepository.findById(messageId).filter(m -> m.getConversation().getId().equals(conversationId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tin nhắn"));
        assertEditable(message, userId, request.expectedVersion());
        message.setState(com.fptu.exe.skillswap.modules.conversation.domain.MessageState.DELETED);
        message.setDeletedAt(DateTimeUtil.now()); message.setDeletedByUserId(userId);
        chatAttachmentRepository.findByMessageId(messageId).forEach(a -> { a.setState(com.fptu.exe.skillswap.modules.conversation.domain.ChatAttachmentState.REVOKED); a.setRevokedAt(DateTimeUtil.now()); });
        return toMessageResponse(messageRepository.save(message), userId);
    }

    @Transactional
    public com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationReadResponse markConversationAsRead(UUID conversationId, UUID userId, long requestedSequence) {
        ConversationParticipant participant = participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không tham gia cuộc hội thoại này"));
        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        long bounded = Math.min(requestedSequence, conversation.getNextSequence());
        if (bounded > participant.getLastReadSequence()) participant.setLastReadSequence(bounded);
        if (participant.getLastReadSequence() >= conversation.getNextSequence() && notificationService != null) notificationService.clearChatUnread(userId, conversationId);
        long other = participantRepository.findByConversationId(conversationId).stream().filter(p -> !p.getUser().getId().equals(userId)).mapToLong(ConversationParticipant::getLastReadSequence).max().orElse(0L);
        long unread = Math.max(0L, conversation.getNextSequence() - participant.getLastReadSequence());
        return new com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationReadResponse(conversationId, participant.getLastReadSequence(), other, unread);
    }

    private void assertEditable(com.fptu.exe.skillswap.modules.conversation.domain.Message message, UUID userId, Integer expectedVersion) {
        if (message.getMessageType() != com.fptu.exe.skillswap.modules.conversation.domain.MessageType.TEXT || message.getSender() == null || !message.getSender().getId().equals(userId) || message.getState() != com.fptu.exe.skillswap.modules.conversation.domain.MessageState.ACTIVE) throw new BaseException(ErrorCode.ACCESS_DENIED, "CHAT_MESSAGE_NOT_EDITABLE");
        if (!java.util.Objects.equals(message.getVersion(), expectedVersion)) throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "CHAT_MESSAGE_VERSION_CONFLICT");
        if (message.getCreatedAt().plusMinutes(15).isBefore(DateTimeUtil.now())) throw new BaseException(ErrorCode.ACCESS_DENIED, "CHAT_MESSAGE_EDIT_WINDOW_EXPIRED");
    }

    private void consumeAttachmentIntents(Conversation conversation, UUID userId, com.fptu.exe.skillswap.modules.conversation.domain.Message message, List<UUID> intentIds) {
        if (intentIds == null || intentIds.isEmpty()) return;
        if (intentIds.size() > 5) throw new BaseException(ErrorCode.BAD_REQUEST, "CHAT_ATTACHMENT_QUOTA_EXCEEDED");
        for (UUID intentId : new java.util.LinkedHashSet<>(intentIds)) {
            var intent = chatUploadIntentRepository.findByIdForUpdate(intentId).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "CHAT_UPLOAD_INTENT_INVALID"));
            if (!intent.getConversation().getId().equals(conversation.getId()) || !intent.getOwnerUserId().equals(userId) || intent.getStatus() != com.fptu.exe.skillswap.modules.conversation.domain.ChatUploadIntentStatus.PENDING_UPLOAD || intent.getExpiresAt().isBefore(DateTimeUtil.now())) throw new BaseException(ErrorCode.BAD_REQUEST, "CHAT_UPLOAD_INTENT_INVALID");
            var metadata = storageGateway().headObject(intent.getStorageKey());
            if (metadata.sizeBytes() != intent.getExpectedSizeBytes() || !intent.getContentType().equalsIgnoreCase(metadata.contentType())) throw new BaseException(ErrorCode.BAD_REQUEST, "CHAT_ATTACHMENT_INVALID");
            validateAttachmentSignature(intent.getStorageKey(), intent.getContentType());
            chatAttachmentRepository.save(com.fptu.exe.skillswap.modules.conversation.domain.ChatAttachment.builder().message(message).uploadIntent(intent).storageKey(intent.getStorageKey()).originalFilename(intent.getOriginalFilename()).contentType(intent.getContentType()).sizeBytes(metadata.sizeBytes()).expiresAt(message.getCreatedAt().plusDays(90)).build());
            intent.setStatus(com.fptu.exe.skillswap.modules.conversation.domain.ChatUploadIntentStatus.CONFIRMED);
        }
    }

    private List<com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentResponse> mapAttachments(UUID messageId) {
        if (chatAttachmentRepository == null) return List.of();
        return chatAttachmentRepository.findByMessageId(messageId).stream().map(a -> new com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentResponse(a.getId(), a.getOriginalFilename(), a.getContentType(), a.getSizeBytes(), inlineCapable(a.getContentType()), a.getState() == com.fptu.exe.skillswap.modules.conversation.domain.ChatAttachmentState.ACTIVE && a.getExpiresAt().isAfter(DateTimeUtil.now()), a.getExpiresAt(), a.getState())).toList();
    }

    private String normalizeAttachmentContentType(String value) { return switch(value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) { case "image/png" -> "image/png"; case "image/jpeg" -> "image/jpeg"; case "application/pdf" -> "application/pdf"; case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; default -> throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "CHAT_ATTACHMENT_INVALID"); }; }
    private void validateAttachmentFilename(String name, String type) { String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT); if (lower.isBlank() || !(type.equals("image/png") && lower.endsWith(".png") || type.equals("image/jpeg") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) || type.equals("application/pdf") && lower.endsWith(".pdf") || type.contains("wordprocessingml") && lower.endsWith(".docx"))) throw new BaseException(ErrorCode.BAD_REQUEST, "CHAT_ATTACHMENT_INVALID"); }
    private String extensionFor(String type) { return type.equals("image/png")?".png":type.equals("image/jpeg")?".jpg":type.equals("application/pdf")?".pdf":".docx"; }
    private boolean inlineCapable(String type) { return "image/png".equals(type) || "image/jpeg".equals(type); }
    private void validateAttachmentSignature(String key, String type) { try (var in = storageGateway().openObject(key)) { byte[] b=in.readNBytes(8); boolean ok=(type.equals("image/png")&&b.length>=4&&b[0]==(byte)0x89&&b[1]==0x50&&b[2]==(byte)0x4e&&b[3]==0x47)||(type.equals("image/jpeg")&&b.length>=3&&b[0]==(byte)0xff&&b[1]==(byte)0xd8&&b[2]==(byte)0xff)||(type.equals("application/pdf")&&new String(b,java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"))||(type.contains("wordprocessingml")&&b.length>=4&&b[0]==0x50&&b[1]==0x4b); if(!ok) throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,"CHAT_ATTACHMENT_INVALID"); } catch(java.io.IOException e){throw new BaseException(ErrorCode.STORAGE_ERROR,"Không thể kiểm tra tệp đính kèm");} }

    private StorageGateway storageGateway() {
        StorageGateway storageGateway = storageGatewayProvider.getIfAvailable();
        if (storageGateway == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình storage cho tệp đính kèm chat");
        }
        return storageGateway;
    }

    /** Booking callbacks may retry; the database uniqueness guard makes this message exactly-once per booking. */
    private void createBookingConfirmedSystemMessage(UUID conversationId, Booking booking) {
        if (messageRepository.findByBookingIdAndSystemEventType(booking.getId(), "BOOKING_CONFIRMED").isPresent()) {
            return;
        }
        Conversation lockedConversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        var message = com.fptu.exe.skillswap.modules.conversation.domain.Message.builder()
                .conversation(lockedConversation)
                .messageType(com.fptu.exe.skillswap.modules.conversation.domain.MessageType.SYSTEM)
                .content("Buổi mentoring đã được xác nhận.")
                .bookingId(booking.getId())
                .systemEventType("BOOKING_CONFIRMED")
                .sequence(lockedConversation.getNextSequence() + 1)
                .build();
        message = messageRepository.save(message);
        lockedConversation.setNextSequence(message.getSequence());
        lockedConversation.setLastMessageContent(message.getContent());
        lockedConversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(lockedConversation);
        enqueueRealtimeOutbox(lockedConversation, message, null, participantRepository.findByConversationId(conversationId));
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
                new ChatMessageCreatedPayload(conversation.getId(), message.getId(), sender == null ? null : sender.getId())
        );
        domainEventOutboxService.enqueue(
                "CONVERSATION",
                conversation.getId(),
                DomainEventOutboxEventTypes.CHAT_CONVERSATION_UPDATED,
                new ChatConversationUpdatedPayload(conversation.getId(), sender == null ? null : sender.getId())
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

    private com.fptu.exe.skillswap.modules.booking.service.BookingChatAccessPolicy.Access resolveMessagingAccess(Conversation conversation) {
        if (bookingChatAccessPolicy == null) {
            // Keeps legacy unit fixtures usable; Spring always injects the booking-owned policy.
            return new com.fptu.exe.skillswap.modules.booking.service.BookingChatAccessPolicy.Access(
                    com.fptu.exe.skillswap.modules.conversation.domain.ChatMessagingAccess.OPEN,
                    true, true, true, null, null, false);
        }
        return bookingChatAccessPolicy.resolve(conversation.getId(), conversation.getStatus(), DateTimeUtil.now());
    }
}
