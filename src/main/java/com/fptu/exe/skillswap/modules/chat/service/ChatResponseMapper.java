package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachment;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachmentState;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.domain.Message;
import com.fptu.exe.skillswap.modules.chat.domain.MessageState;
import com.fptu.exe.skillswap.modules.chat.dto.response.ChatAttachmentResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationContextMetadata;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.BusinessTime;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatResponseMapper {

    private final CursorCodec cursorCodec;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ConversationParticipantRepository participantRepository;

    public ConversationResponse mapConversationResponse(
            Conversation conv,
            UUID userId,
            Map<UUID, List<ConversationParticipant>> participantsByConvId,
            Map<UUID, Long> unreadCountsMap,
            BookingChatAccessPolicy.Access access,
            ConversationContextMetadata context
    ) {
        List<ConversationParticipant> participants = participantsByConvId.getOrDefault(conv.getId(), List.of());
        ConversationParticipant other = participants.stream()
                .filter(p -> !p.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        ConversationParticipant me = participants.stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        long unread = unreadCountsMap.getOrDefault(conv.getId(), 0L);

        String otherUserName;
        String otherUserAvatarUrl;
        UUID otherUserId;
        if (conv.getType() == ConversationType.GROUP) {
            otherUserName = context.courseTitle() != null ? context.courseTitle() : "Nhóm học tập";
            otherUserAvatarUrl = null;
            otherUserId = conv.getMentorUserId();
        } else {
            otherUserName = other != null ? other.getUser().getFullName() : null;
            otherUserAvatarUrl = other != null ? other.getUser().getAvatarUrl() : null;
            otherUserId = other != null ? other.getUser().getId() : null;
        }

        return ConversationResponse.builder()
                .id(conv.getId())
                .type(conv.getType())
                .status(conv.getStatus())
                .otherUserId(otherUserId)
                .otherUserName(otherUserName)
                .otherUserAvatarUrl(otherUserAvatarUrl)
                .lastMessageContent(conv.getLastMessageContent())
                .lastMessageAt(BusinessTime.toInstant(conv.getLastMessageAt()))
                .createdAt(BusinessTime.toInstant(conv.getCreatedAt()))
                .unreadCount(unread)
                .myLastReadSequence(me != null ? me.getLastReadSequence() : 0L)
                .otherLastReadSequence(other != null ? other.getLastReadSequence() : 0L)
                .messagingAccess(access.messagingAccess())
                .canSendMessages(access.canSendMessages())
                .canUploadAttachments(access.canUploadAttachments())
                .canDownloadAttachments(access.canDownloadAttachments())
                .readOnlyReason(access.readOnlyReason())
                .userActionMessage(ConversationResponse.userActionMessage(access.readOnlyReason()))
                .retryable(ConversationResponse.retryable(access.readOnlyReason()))
                .messagingWindowEndsAt(BusinessTime.toInstant(access.messagingWindowEndsAt()))
                .postSessionChatPermanent(access.postSessionChatPermanent())
                .participantCount(conv.getType() == ConversationType.GROUP ? participants.size() : null)
                .contextType(context.contextType())
                .bookingId(context.bookingId())
                .courseId(context.courseId())
                .courseTitle(context.courseTitle())
                .mentorUserId(context.mentorUserId())
                .mentorName(context.mentorName())
                .mentorAvatarUrl(context.mentorAvatarUrl())
                .build();
    }

    public MessageResponse toMessageResponse(Message msg, UUID userId) {
        return toMessageResponse(msg, userId,
                resolveOtherLastReadSequence(msg.getConversation().getId(), userId),
                chatAttachmentRepository.findByMessageId(msg.getId()));
    }

    public MessageResponse toMessageResponse(Message msg, UUID userId, long otherLastReadSequence) {
        return toMessageResponse(msg, userId, otherLastReadSequence,
                chatAttachmentRepository.findByMessageId(msg.getId()));
    }

    /**
     * Maps a page of messages using one attachment query and one participant query.
     * The single-message mapper remains for command/replay responses.
     */
    public List<MessageResponse> toMessageResponses(List<Message> messages, UUID userId) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<UUID> messageIds = messages.stream().map(Message::getId).toList();
        List<UUID> conversationIds = messages.stream()
                .map(message -> message.getConversation().getId())
                .distinct()
                .toList();

        Map<UUID, List<ChatAttachment>> attachmentsByMessageId = chatAttachmentRepository
                .findByMessageIdIn(messageIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(attachment -> attachment.getMessage().getId()));

        Map<UUID, Long> otherLastReadByConversationId = new HashMap<>();
        for (ConversationParticipant participant : participantRepository.findByConversationIdInWithUser(conversationIds)) {
            if (!participant.getUser().getId().equals(userId)) {
                UUID conversationId = participant.getConversation().getId();
                otherLastReadByConversationId.merge(
                        conversationId,
                        participant.getLastReadSequence(),
                        Math::max
                );
            }
        }

        return messages.stream()
                .map(message -> toMessageResponse(
                        message,
                        userId,
                        otherLastReadByConversationId.getOrDefault(
                                message.getConversation().getId(), 0L),
                        attachmentsByMessageId.getOrDefault(message.getId(), List.of())
                ))
                .toList();
    }

    private MessageResponse toMessageResponse(Message msg,
                                               UUID userId,
                                               long otherLastReadSequence,
                                               List<ChatAttachment> attachments) {
        boolean mine = msg.getSender() != null && msg.getSender().getId().equals(userId);
        return MessageResponse.builder()
                .id(msg.getId())
                .sequence(msg.getSequence())
                .conversationId(msg.getConversation().getId())
                .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                .senderName(msg.getSender() != null ? msg.getSender().getFullName() : "Hệ thống")
                .messageType(msg.getMessageType())
                .content(msg.getState() == MessageState.DELETED ? null : msg.getContent())
                .state(msg.getState())
                .version(msg.getVersion())
                .editedAt(BusinessTime.toInstant(msg.getEditedAt()))
                .deletedAt(BusinessTime.toInstant(msg.getDeletedAt()))
                .isReadByOther(mine ? otherLastReadSequence >= msg.getSequence() : null)
                .createdAt(BusinessTime.toInstant(msg.getCreatedAt()))
                .isMine(mine)
                .attachments(mapAttachmentResponses(attachments))
                .build();
    }

    public List<ChatAttachmentResponse> mapAttachments(UUID messageId) {
        if (chatAttachmentRepository == null) return List.of();
        return mapAttachmentResponses(chatAttachmentRepository.findByMessageId(messageId));
    }

    private List<ChatAttachmentResponse> mapAttachmentResponses(Collection<ChatAttachment> attachments) {
        return attachments.stream()
                .map(a -> new ChatAttachmentResponse(
                        a.getId(),
                        a.getOriginalFilename(),
                        a.getContentType(),
                        a.getSizeBytes(),
                        inlineCapable(a.getContentType()),
                        a.getState() == ChatAttachmentState.ACTIVE && a.getExpiresAt().isAfter(DateTimeUtil.now()),
                        BusinessTime.toInstant(a.getExpiresAt()),
                        a.getState()))
                .toList();
    }

    public boolean inlineCapable(String type) {
        return "image/png".equals(type) || "image/jpeg".equals(type);
    }

    public long resolveOtherLastReadSequence(UUID conversationId, UUID userId) {
        return participantRepository.findByConversationId(conversationId).stream()
                .filter(participant -> !participant.getUser().getId().equals(userId))
                .mapToLong(ConversationParticipant::getLastReadSequence)
                .max()
                .orElse(0L);
    }

    public int defaultLimit(Integer limit, int defaultValue) {
        int resolved = limit == null || limit <= 0 ? defaultValue : limit;
        return Math.min(resolved, 50);
    }

    public DecodedCursor decodeCursor(String cursor, String expectedFilterHash, String entityLabel) {
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

    public String encodeNextConversationCursor(Conversation conversation, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(resolveConversationActivityAt(conversation).toString())
                .secondaryKey(conversation.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    public String encodeNextMessageCursor(Message message, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(message.getCreatedAt().toString())
                .secondaryKey(message.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    public LocalDateTime resolveConversationActivityAt(Conversation conversation) {
        return conversation.getLastMessageAt() != null ? conversation.getLastMessageAt() : conversation.getCreatedAt();
    }

    public record DecodedCursor(LocalDateTime sortAt, UUID entityId) {
        public static DecodedCursor empty() {
            return new DecodedCursor(null, null);
        }
    }
}
