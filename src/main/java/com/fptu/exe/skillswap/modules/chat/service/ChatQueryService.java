package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
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
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
    private final InternalTelemetryService internalTelemetryService;
    private final CourseQueryPort courseQueryPort;

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

        String nextCursor = null;
        if (hasNext && !items.isEmpty()) {
            Conversation lastConv = visibleConversations.get(visibleConversations.size() - 1);
            nextCursor = chatResponseMapper.encodeNextConversationCursor(lastConv, filterHash);
        }

        return new CursorPageResponse<>(items, nextCursor, null, hasNext, false, resolvedLimit);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable, MessageRepository messageRepoOverride) {
        MessageRepository activeMessageRepo = messageRepoOverride != null ? messageRepoOverride : messageRepository;
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không thuộc cuộc hội thoại này");
        }

        Page<Message> messagePage = activeMessageRepo.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
        List<MessageResponse> dtos = messagePage.getContent().stream()
                .map(message -> chatResponseMapper.toMessageResponse(message, userId))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, messagePage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(UUID conversationId, UUID userId, String cursor, Integer limit) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không thuộc cuộc hội thoại này");
        }

        int resolvedLimit = chatResponseMapper.defaultLimit(limit, 30);
        String filterHash = "messages|convId=" + conversationId;
        ChatResponseMapper.DecodedCursor decodedCursor = chatResponseMapper.decodeCursor(cursor, filterHash, "message");

        List<Message> messageWindow;
        if (decodedCursor.sortAt() == null) {
            messageWindow = messageRepository.findLatestMessages(conversationId, PageRequest.of(0, resolvedLimit + 1));
        } else {
            messageWindow = messageRepository.findMessagesBefore(
                    conversationId,
                    decodedCursor.sortAt(),
                    decodedCursor.entityId(),
                    PageRequest.of(0, resolvedLimit + 1)
            );
        }

        boolean hasNext = messageWindow.size() > resolvedLimit;
        List<Message> visibleMessages = hasNext
                ? messageWindow.subList(0, resolvedLimit)
                : messageWindow;

        List<MessageResponse> items = visibleMessages.stream()
                .map(message -> chatResponseMapper.toMessageResponse(message, userId))
                .toList();

        String nextCursor = null;
        if (hasNext && !items.isEmpty()) {
            Message lastMessage = visibleMessages.get(visibleMessages.size() - 1);
            nextCursor = chatResponseMapper.encodeNextMessageCursor(lastMessage, filterHash);
        }

        return new CursorPageResponse<>(items, nextCursor, null, hasNext, false, resolvedLimit);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesBySequence(UUID conversationId, UUID userId, Long beforeSequence, Long afterSequence, Integer limit) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không thuộc cuộc hội thoại này");
        }

        int resolvedLimit = chatResponseMapper.defaultLimit(limit, 50);
        List<Message> messages;
        if (beforeSequence != null) {
            messages = messageRepository.findMessagesBeforeSequence(conversationId, beforeSequence, PageRequest.of(0, resolvedLimit));
        } else if (afterSequence != null) {
            messages = messageRepository.findMessagesAfterSequence(conversationId, afterSequence, PageRequest.of(0, resolvedLimit));
        } else {
            messages = messageRepository.findLatestMessages(conversationId, PageRequest.of(0, resolvedLimit));
        }

        return messages.stream().map(message -> chatResponseMapper.toMessageResponse(message, userId)).toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationDetail(UUID conversationId, UUID userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        List<ConversationParticipant> participants = participantRepository.findByConversationIdWithUser(conversationId);
        ConversationParticipant me = participants.stream()
                .filter(cp -> cp.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không thuộc cuộc hội thoại này"));

        ConversationParticipant other = participants.stream()
                .filter(cp -> !cp.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);

        long unread = messageRepository.countUnreadMessages(conversationId, userId, me.getLastReadSequence());

        String otherUserName;
        String otherUserAvatarUrl;
        UUID otherUserId;
        if (conv.getType() == ConversationType.GROUP) {
            otherUserName = resolveCourseTitle(conv.getSourceId());
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

    private String resolveCourseTitle(UUID courseId) {
        if (courseId == null) return null;
        return courseQueryPort.findCourseTitleById(courseId).orElse(null);
    }
}
