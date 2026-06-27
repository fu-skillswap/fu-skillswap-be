package com.fptu.exe.skillswap.modules.conversation.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationType;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    public Conversation createDirectForAcceptedBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking must not be null");
        }

        Optional<Conversation> existingConv = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.getId());
        Conversation conversation;
        if (existingConv.isPresent()) {
            conversation = existingConv.get();
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
        addParticipantIfAbsent(conversation, booking.getMentorProfile().getUser());
        addParticipantIfAbsent(conversation, booking.getMentee());

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
    public org.springframework.data.domain.Page<com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse> getMessages(UUID conversationId, UUID userId, org.springframework.data.domain.Pageable pageable, com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository messageRepository) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập vào cuộc hội thoại này");
        }

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

    @Transactional
    public com.fptu.exe.skillswap.modules.conversation.dto.response.MessageResponse sendMessage(UUID conversationId, UUID userId, com.fptu.exe.skillswap.modules.conversation.dto.request.SendMessageRequest request, com.fptu.exe.skillswap.modules.conversation.repository.MessageRepository messageRepository, com.fptu.exe.skillswap.modules.identity.repository.UserRepository userRepository) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Bạn không có quyền gửi tin nhắn trong cuộc hội thoại này");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));

        String content = request.content().trim();

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
                .build();

        java.util.List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        java.util.List<UUID> recipientUserIds = participants.stream()
                .map(p -> p.getUser().getId())
                .collect(java.util.stream.Collectors.toList());

        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.conversation.event.ChatMessageSavedEvent(event, recipientUserIds));

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
        java.util.List<ConversationParticipant> myParticipations = participantRepository.findByUserId(userId);
        long totalUnread = 0;
        for (ConversationParticipant cp : myParticipations) {
            java.time.LocalDateTime lastRead = cp.getLastReadAt() != null ? cp.getLastReadAt() : cp.getJoinedAt();
            totalUnread += messageRepository.countUnreadMessages(cp.getConversation().getId(), userId, lastRead);
        }
        return totalUnread;
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

    @Transactional
    public void markConversationAsRead(UUID conversationId, UUID userId) {
        ConversationParticipant me = participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(
                        com.fptu.exe.skillswap.shared.exception.ErrorCode.ACCESS_DENIED, "Bạn không tham gia cuộc hội thoại này"));
        me.setLastReadAt(DateTimeUtil.now());
        participantRepository.save(me);
    }
}
