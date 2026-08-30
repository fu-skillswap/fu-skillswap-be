package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.domain.Message;
import com.fptu.exe.skillswap.modules.chat.domain.MessageState;
import com.fptu.exe.skillswap.modules.chat.domain.MessageType;
import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;
import com.fptu.exe.skillswap.modules.chat.dto.request.DeleteMessageRequest;
import com.fptu.exe.skillswap.modules.chat.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.chat.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.chat.event.ChatMessageRealtimeDelivery;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.chat.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ChatAttachmentService chatAttachmentService;
    private final ChatAccessResolutionService chatAccessResolutionService;
    private final ChatResponseMapper chatResponseMapper;
    private final DomainEventOutboxService domainEventOutboxService;
    private final RealtimeOutboxProperties realtimeOutboxProperties;
    private final ObjectProvider<GroupChatFanoutDispatcher> groupChatFanoutDispatcherProvider;
    private final ObjectProvider<UserQueryPort> userQueryPortProvider;
    private final ObjectProvider<UserRepository> userRepositoryProvider;

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        return sendMessage(conversationId, userId, request, null, (UserRepository) null);
    }

    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId,
            UUID userId,
            SendMessageRequest request,
            MessageRepository messageRepoOverride,
            UserRepository userRepoOverride
    ) {
        MessageRepository activeMessageRepo = messageRepoOverride != null ? messageRepoOverride : messageRepository;
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền gửi tin nhắn trong cuộc hội thoại này");
        }

        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .or(() -> conversationRepository.findById(conversationId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        var access = chatAccessResolutionService.resolveMessagingAccess(conversation, userId);
        if (!access.canSendMessages()) {
            throw new BaseException(chatAccessResolutionService.resolveMessagingAccessError(access));
        }

        User sender = resolveUser(userId, userRepoOverride);
        if (sender == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng");
        }

        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank() && (request.attachmentIntentIds() == null || request.attachmentIntentIds().isEmpty())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tin nhắn cần có nội dung hoặc tệp đính kèm");
        }

        String requestHash = requestHash(content, request.replyToMessageId(), request.attachmentIntentIds());
        var replay = activeMessageRepo.findByConversationIdAndSenderIdAndClientMessageId(conversationId, userId, request.clientMessageId());
        if (replay.isPresent()) {
            if (!Objects.equals(replay.get().getRequestHash(), requestHash)) {
                throw new BaseException(ErrorCode.CHAT_CLIENT_MESSAGE_CONFLICT);
            }
            return chatResponseMapper.toMessageResponse(replay.get(), userId);
        }
        validateReplyTarget(conversationId, request.replyToMessageId(), activeMessageRepo);

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .messageType(MessageType.TEXT)
                .content(content)
                .sequence(conversation.getNextSequence() + 1)
                .clientMessageId(request.clientMessageId())
                .requestHash(requestHash)
                .replyToMessageId(request.replyToMessageId())
                .build();
        message = activeMessageRepo.save(message);

        chatAttachmentService.consumeAttachmentIntents(conversation, sender.getId(), message, request.attachmentIntentIds());

        conversation.setNextSequence(message.getSequence());
        conversation.setLastMessageContent(content);
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);

        ChatMessageEvent event = ChatMessageEvent.builder()
                .conversationId(conversation.getId())
                .messageId(message.getId())
                .sequence(message.getSequence())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .conversationType(conversation.getType())
                .isSelf(false)
                .unreadCount(0L)
                .build();

        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        List<UUID> recipientIds = participants.stream()
                .filter(p -> p.getAccessState() != ConversationParticipantAccess.REVOKED)
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(userId))
                .toList();

        if (conversation.getType() == ConversationType.GROUP) {
            var dispatcher = groupChatFanoutDispatcherProvider.getIfAvailable();
            if (dispatcher != null) {
                dispatcher.dispatchGroupMessage(conversation.getId(), event, recipientIds);
            }
            if (realtimeOutboxProperties.isEnabled()) {
                domainEventOutboxService.enqueue(
                        "CONVERSATION",
                        conversation.getId(),
                        DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED,
                        new ConversationService.ChatMessageCreatedPayload(conversation.getId(), message.getId(), sender.getId())
                );
            }
        } else {
            enqueueRealtimeOutbox(conversation, message, sender, participants);
        }

        return MessageResponse.builder()
                .id(message.getId())
                .sequence(message.getSequence())
                .conversationId(conversation.getId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .state(message.getState())
                .version(message.getVersion())
                .createdAt(message.getCreatedAt())
                .isMine(true)
                .attachments(chatResponseMapper.mapAttachments(message.getId()))
                .build();
    }

    @Transactional
    public MessageResponse deleteMessage(UUID conversationId, UUID messageId, UUID userId, DeleteMessageRequest request) {
        var message = messageRepository.findById(messageId).filter(m -> m.getConversation().getId().equals(conversationId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tin nhắn"));
        assertEditable(message, userId, request.expectedVersion());
        message.setState(MessageState.DELETED);
        message.setDeletedAt(DateTimeUtil.now());
        message.setDeletedByUserId(userId);
        chatAttachmentRepository.findByMessageId(messageId).forEach(a -> {
            a.setState(com.fptu.exe.skillswap.modules.chat.domain.ChatAttachmentState.REVOKED);
            a.setRevokedAt(DateTimeUtil.now());
        });
        return chatResponseMapper.toMessageResponse(messageRepository.saveAndFlush(message), userId);
    }

    @Transactional
    public void createBookingConfirmedSystemMessage(UUID conversationId, UUID bookingId) {
        if (bookingId == null || messageRepository.findByBookingIdAndSystemEventType(bookingId, "BOOKING_CONFIRMED").isPresent()) {
            return;
        }
        Conversation lockedConversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        var message = Message.builder()
                .conversation(lockedConversation)
                .messageType(MessageType.SYSTEM)
                .content("Buổi mentoring đã được xác nhận.")
                .bookingId(bookingId)
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

    @Transactional(readOnly = true)
    public ChatMessageEvent buildGroupChatMessageEvent(UUID conversationId, UUID messageId, UUID senderId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tin nhắn"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        return ChatMessageEvent.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .sequence(message.getSequence())
                .senderId(senderId)
                .senderName(message.getSender() != null ? message.getSender().getFullName() : "Hệ thống")
                .messageType(message.getMessageType())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .conversationType(conversation.getType())
                .isSelf(false)
                .unreadCount(0L)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageRealtimeDelivery> buildChatMessageDeliveries(UUID conversationId, UUID messageId, UUID senderId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tin nhắn"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        return participants.stream()
                .filter(p -> senderId == null || !p.getUser().getId().equals(senderId))
                .map(p -> new ChatMessageRealtimeDelivery(
                        p.getUser().getId(),
                        ChatMessageEvent.builder()
                                .conversationId(conversationId)
                                .messageId(messageId)
                                .sequence(message.getSequence())
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

    public void enqueueRealtimeOutbox(
            Conversation conversation,
            Message message,
            User sender,
            List<ConversationParticipant> participants
    ) {
        if (!realtimeOutboxProperties.isEnabled()) {
            return;
        }
        domainEventOutboxService.enqueue(
                "CONVERSATION",
                conversation.getId(),
                DomainEventOutboxEventTypes.CHAT_MESSAGE_CREATED,
                new ConversationService.ChatMessageCreatedPayload(conversation.getId(), message.getId(), sender == null ? null : sender.getId())
        );
        domainEventOutboxService.enqueue(
                "CONVERSATION",
                conversation.getId(),
                DomainEventOutboxEventTypes.CHAT_CONVERSATION_UPDATED,
                new ConversationService.ChatConversationUpdatedPayload(conversation.getId(), sender == null ? null : sender.getId())
        );
        participants.stream()
                .map(ConversationParticipant::getUser)
                .filter(user -> user != null && user.getId() != null)
                .forEach(user -> domainEventOutboxService.enqueue(
                        "CONVERSATION",
                        conversation.getId(),
                        DomainEventOutboxEventTypes.CHAT_UNREAD_COUNT_UPDATED,
                        new ConversationService.ChatUnreadCountUpdatedPayload(conversation.getId(), user.getId())
                ));
    }

    private void assertEditable(Message message, UUID userId, Integer expectedVersion) {
        if (message.getMessageType() != MessageType.TEXT || message.getSender() == null || !message.getSender().getId().equals(userId) || message.getState() != MessageState.ACTIVE) {
            throw new BaseException(ErrorCode.CHAT_MESSAGE_NOT_EDITABLE);
        }
        if (!Objects.equals(message.getVersion(), expectedVersion)) {
            throw new BaseException(ErrorCode.CHAT_MESSAGE_VERSION_CONFLICT);
        }
        if (message.getCreatedAt().plusMinutes(15).isBefore(DateTimeUtil.now())) {
            throw new BaseException(ErrorCode.CHAT_MESSAGE_EDIT_WINDOW_EXPIRED);
        }
    }

    private void validateReplyTarget(UUID conversationId, UUID replyToMessageId, MessageRepository activeMessageRepo) {
        if (replyToMessageId == null) {
            return;
        }
        boolean belongsToConversation = activeMessageRepo.findById(replyToMessageId)
                .map(message -> message.getConversation().getId().equals(conversationId))
                .orElse(false);
        if (!belongsToConversation) {
            throw new BaseException(ErrorCode.CHAT_REPLY_TARGET_INVALID);
        }
    }

    public String requestHash(String content, UUID replyToMessageId, List<UUID> attachmentIntentIds) {
        String attachmentIds = (attachmentIntentIds == null ? List.<UUID>of() : attachmentIntentIds).stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        String canonicalPayload = content + "\n" + (replyToMessageId == null ? "" : replyToMessageId) + "\n" + attachmentIds;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "SHA-256 không khả dụng", exception);
        }
    }

    private long resolveUnreadCountForParticipant(UUID conversationId, ConversationParticipant participant) {
        return messageRepository.countUnreadMessages(conversationId, participant.getUser().getId(), participant.getLastReadSequence());
    }

    private User resolveUser(UUID userId, UserRepository userRepoOverride) {
        if (userRepoOverride != null) {
            return userRepoOverride.findById(userId).orElse(null);
        }
        var userPort = userQueryPortProvider.getIfAvailable();
        if (userPort != null) {
            return userPort.findUserById(userId).orElse(null);
        }
        var userRepo = userRepositoryProvider.getIfAvailable();
        return userRepo != null ? userRepo.findById(userId).orElse(null) : null;
    }
}
