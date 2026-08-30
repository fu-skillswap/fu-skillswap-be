package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationType;
import com.fptu.exe.skillswap.modules.chat.domain.Message;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.chat.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.system.port.TelemetryPort;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatQueryService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ChatRoomService chatRoomService;
    private final ChatAccessResolutionService chatAccessResolutionService;
    private final ChatResponseMapper chatResponseMapper;
    private final TelemetryPort internalTelemetryService;

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getMyConversations(UUID userId, Pageable pageable) {
        Page<Conversation> conversationsPage = conversationRepository.findByParticipantUserId(userId, pageable);
        if (conversationsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Conversation> conversations = conversationsPage.getContent();
        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();

        List<ConversationParticipant> allParticipants = participantRepository.findByConversationIdInWithUser(conversationIds);
        Map<UUID, List<ConversationParticipant>> participantsByConvId = allParticipants.stream()
                .collect(Collectors.groupingBy(cp -> cp.getConversation().getId()));

        List<Object[]> unreadCountsRaw = messageRepository.countUnreadMessagesBatch(conversationIds, userId);
        Map<UUID, Long> unreadCountsMap = unreadCountsRaw.stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1], (a, b) -> a));

        return conversationsPage.map(conv -> {
            var access = chatAccessResolutionService.resolveMessagingAccess(conv, userId);
            String courseTitle = conv.getType() == ConversationType.GROUP ? resolveCourseTitle(conv.getSourceId()) : null;
            return chatResponseMapper.mapConversationResponse(conv, userId, participantsByConvId, unreadCountsMap, access, courseTitle);
        });
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ConversationResponse> getMyConversations(UUID userId, String cursor, Integer limit) {
        int resolvedLimit = chatResponseMapper.defaultLimit(limit, 20);
        String filterHash = "conversations|userId=" + userId;
        ChatResponseMapper.DecodedCursor decodedCursor = chatResponseMapper.decodeCursor(cursor, filterHash, "conversation");
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

        List<UUID> conversationIds = visibleConversations.stream().map(Conversation::getId).toList();
        List<ConversationParticipant> allParticipants = conversationIds.isEmpty()
                ? List.of()
                : participantRepository.findByConversationIdInWithUser(conversationIds);
        Map<UUID, List<ConversationParticipant>> participantsByConvId = allParticipants.stream()
                .collect(Collectors.groupingBy(cp -> cp.getConversation().getId()));
        List<Object[]> unreadCountsRaw = conversationIds.isEmpty()
                ? List.of()
                : messageRepository.countUnreadMessagesBatch(conversationIds, userId);
        Map<UUID, Long> unreadCountsMap = unreadCountsRaw.stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1], (a, b) -> a));

        List<ConversationResponse> items = visibleConversations.stream()
                .map(conv -> {
                    var access = chatAccessResolutionService.resolveMessagingAccess(conv, userId);
                    String courseTitle = conv.getType() == ConversationType.GROUP ? resolveCourseTitle(conv.getSourceId()) : null;
                    return chatResponseMapper.mapConversationResponse(conv, userId, participantsByConvId, unreadCountsMap, access, courseTitle);
                })
                .toList();
        String nextCursor = hasNext && !visibleConversations.isEmpty()
                ? chatResponseMapper.encodeNextConversationCursor(visibleConversations.get(visibleConversations.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<ConversationResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable, MessageRepository messageRepoOverride) {
        MessageRepository activeRepo = messageRepoOverride != null ? messageRepoOverride : messageRepository;
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập vào cuộc hội thoại này");
        }
        if (internalTelemetryService != null) {
            internalTelemetryService.record("CHAT_OPENED", userId, "CONVERSATION", conversationId, Map.of());
        }

        long otherLastReadSequence = chatResponseMapper.resolveOtherLastReadSequence(conversationId, userId);
        return activeRepo.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(msg -> MessageResponse.builder()
                        .id(msg.getId())
                        .conversationId(msg.getConversation().getId())
                        .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                        .senderName(msg.getSender() != null ? msg.getSender().getFullName() : "Hệ thống")
                        .messageType(msg.getMessageType())
                        .content(msg.getContent())
                        .version(msg.getVersion())
                        .isReadByOther(msg.getSender() != null && msg.getSender().getId().equals(userId)
                                ? otherLastReadSequence >= msg.getSequence()
                                : null)
                        .createdAt(msg.getCreatedAt())
                        .isMine(msg.getSender() != null && msg.getSender().getId().equals(userId))
                        .build());
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(UUID conversationId, UUID userId, String cursor, Integer limit) {
        chatRoomService.ensureParticipant(conversationId, userId);
        if (internalTelemetryService != null) {
            internalTelemetryService.record("CHAT_OPENED", userId, "CONVERSATION", conversationId, Map.of());
        }
        int resolvedLimit = chatResponseMapper.defaultLimit(limit, 30);
        String filterHash = "messages|conversationId=" + conversationId + "|viewer=" + userId;
        ChatResponseMapper.DecodedCursor decodedCursor = chatResponseMapper.decodeCursor(cursor, filterHash, "message");
        List<Message> messageWindow = messageRepository.findMessageWindow(
                conversationId,
                decodedCursor.sortAt(),
                decodedCursor.entityId(),
                resolvedLimit + 1
        );
        boolean hasNext = messageWindow.size() > resolvedLimit;
        List<Message> visibleMessages = hasNext
                ? messageWindow.subList(0, resolvedLimit)
                : messageWindow;
        List<MessageResponse> items = visibleMessages.stream()
                .map(message -> chatResponseMapper.toMessageResponse(message, userId))
                .toList();
        String nextCursor = hasNext && !visibleMessages.isEmpty()
                ? chatResponseMapper.encodeNextMessageCursor(visibleMessages.get(visibleMessages.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<MessageResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesBySequence(UUID conversationId, UUID userId, Long beforeSequence, Long afterSequence, Integer limit) {
        chatRoomService.ensureParticipant(conversationId, userId);
        if (beforeSequence != null && afterSequence != null) throw new BaseException(ErrorCode.CHAT_MESSAGE_CURSOR_INVALID);
        int resolved = chatResponseMapper.defaultLimit(limit, 30);
        long otherLastReadSequence = chatResponseMapper.resolveOtherLastReadSequence(conversationId, userId);
        return messageRepository.findByConversationSequenceWindow(conversationId, beforeSequence, afterSequence, PageRequest.of(0, resolved)).stream()
                .map(message -> chatResponseMapper.toMessageResponse(message, userId, otherLastReadSequence))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationDetail(UUID conversationId, UUID userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        boolean isParticipant = participants.stream().anyMatch(p -> p.getUser().getId().equals(userId)
                && p.getAccessState() != ConversationParticipantAccess.REVOKED);
        if (!isParticipant) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập vào cuộc hội thoại này");
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
            unread = messageRepository.countUnreadMessages(conv.getId(), userId, me.getLastReadSequence());
        }

        String otherUserName;
        String otherUserAvatarUrl;
        UUID otherUserId;
        if (conv.getType() == ConversationType.GROUP) {
            String courseTitle = resolveCourseTitle(conv.getSourceId());
            otherUserName = courseTitle != null ? courseTitle : "Nhóm học tập";
            otherUserAvatarUrl = null;
            otherUserId = conv.getMentorUserId();
        } else {
            otherUserName = other != null ? other.getUser().getFullName() : null;
            otherUserAvatarUrl = other != null ? other.getUser().getAvatarUrl() : null;
            otherUserId = other != null ? other.getUser().getId() : null;
        }

        var access = chatAccessResolutionService.resolveMessagingAccess(conv, userId);

        return ConversationResponse.builder()
                .id(conv.getId())
                .type(conv.getType())
                .status(conv.getStatus())
                .otherUserId(otherUserId)
                .otherUserName(otherUserName)
                .otherUserAvatarUrl(otherUserAvatarUrl)
                .lastMessageContent(conv.getLastMessageContent())
                .lastMessageAt(conv.getLastMessageAt())
                .createdAt(conv.getCreatedAt())
                .unreadCount(unread)
                .myLastReadSequence(me != null ? me.getLastReadSequence() : 0L)
                .otherLastReadSequence(other != null ? other.getLastReadSequence() : 0L)
                .messagingAccess(access.messagingAccess())
                .canSendMessages(access.canSendMessages())
                .canUploadAttachments(access.canUploadAttachments())
                .canDownloadAttachments(access.canDownloadAttachments())
                .readOnlyReason(access.readOnlyReason())
                .messagingWindowEndsAt(access.messagingWindowEndsAt())
                .postSessionChatPermanent(access.postSessionChatPermanent())
                .participantCount(conv.getType() == ConversationType.GROUP ? participants.size() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadCount(UUID userId) {
        return messageRepository.countTotalUnreadMessages(userId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> findConversationIdsByBookingIds(List<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Conversation> convs = conversationRepository.findBySourceTypeAndSourceIdIn(ConversationSourceType.BOOKING, bookingIds);
        return convs.stream().collect(Collectors.toMap(
                Conversation::getSourceId,
                Conversation::getId,
                (a, b) -> a
        ));
    }



}
