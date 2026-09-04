package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.config.RealtimeOutboxProperties;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationParticipant;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationReadResponse;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxEventTypes;
import com.fptu.exe.skillswap.shared.outbox.DomainEventOutboxService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatReadReceiptService {

    private final ConversationParticipantRepository participantRepository;
    private final ConversationRepository conversationRepository;
    private final DomainEventOutboxService domainEventOutboxService;
    private final RealtimeOutboxProperties realtimeOutboxProperties;
    private final UserQueryPort userQueryPort;

    @Transactional
    public ConversationReadResponse markConversationAsRead(UUID conversationId, UUID userId, long requestedSequence) {
        requireActiveUser(userId);
        ConversationParticipant participant = participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không tham gia cuộc hội thoại này"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        long bounded = Math.min(requestedSequence, conversation.getNextSequence());
        if (bounded > participant.getLastReadSequence()) {
            participant.setLastReadSequence(bounded);
        }
        long other = participantRepository.findByConversationId(conversationId).stream()
                .filter(p -> !p.getUser().getId().equals(userId))
                .mapToLong(ConversationParticipant::getLastReadSequence)
                .max()
                .orElse(0L);
        long unread = Math.max(0L, conversation.getNextSequence() - participant.getLastReadSequence());
        return new ConversationReadResponse(conversationId, participant.getLastReadSequence(), other, unread);
    }

    @Transactional
    public void markConversationAsRead(UUID conversationId, UUID userId) {
        requireActiveUser(userId);
        ConversationParticipant me = participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không tham gia cuộc hội thoại này"));
        LocalDateTime now = DateTimeUtil.now();
        if (me.getLastReadAt() == null || me.getLastReadAt().isBefore(now)) {
            me.setLastReadAt(now);
            participantRepository.save(me);
            if (realtimeOutboxProperties.isEnabled()) {
                domainEventOutboxService.enqueue(
                        "CONVERSATION",
                        conversationId,
                        DomainEventOutboxEventTypes.CHAT_UNREAD_COUNT_UPDATED,
                        new ConversationService.ChatUnreadCountUpdatedPayload(conversationId, userId)
                );
                domainEventOutboxService.enqueue(
                        "CONVERSATION",
                        conversationId,
                        DomainEventOutboxEventTypes.CHAT_CONVERSATION_UPDATED,
                        new ConversationService.ChatConversationUpdatedPayload(conversationId, userId)
                );
            }
        }
    }

    private void requireActiveUser(UUID userId) {
        if (!userQueryPort.isUserActive(userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Tài khoản không hoạt động không được sử dụng chat");
        }
    }
}
