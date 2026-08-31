package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.dto.event.ChatMessageEvent;
import com.fptu.exe.skillswap.modules.chat.dto.request.ChatAttachmentUploadIntentRequest;
import com.fptu.exe.skillswap.modules.chat.dto.request.DeleteMessageRequest;
import com.fptu.exe.skillswap.modules.chat.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.chat.dto.response.ChatAttachmentDownloadResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.ChatAttachmentUploadIntentResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationReadResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.MessageResponse;
import com.fptu.exe.skillswap.modules.chat.event.ChatMessageRealtimeDelivery;
import com.fptu.exe.skillswap.modules.chat.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.course.port.CourseQueryPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Facade tương thích ngược cho ConversationService, ủy quyền toàn bộ cho ChatService.
 * Khuyến nghị sử dụng ChatService trực tiếp cho các tính năng mới.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ChatService chatService;

    @Transactional
    public Conversation createDirectForAcceptedBooking(UUID bookingId, UUID mentorUserId, UUID menteeUserId) {
        return chatService.createDirectForAcceptedBooking(bookingId, mentorUserId, menteeUserId);
    }

    @Transactional
    public void addParticipantIfAbsent(Conversation conversation, User user) {
        chatService.addParticipantIfAbsent(conversation, user);
    }

    @Transactional(readOnly = true)
    public Conversation findByBookingId(UUID bookingId) {
        return chatService.findByBookingId(bookingId);
    }

    @Transactional(readOnly = true)
    public Conversation findById(UUID conversationId) {
        return chatService.findById(conversationId);
    }

    @Transactional(readOnly = true)
    public Conversation findDirectByParticipants(UUID firstUserId, UUID secondUserId) {
        return chatService.findDirectByParticipants(firstUserId, secondUserId);
    }

    @Transactional
    public Conversation ensureCourseGroupConversation(CourseQueryPort.CourseChatContext course) {
        return chatService.ensureCourseGroupConversation(course);
    }

    @Transactional
    public void addCourseStudentParticipant(UUID courseId, UUID studentUserId) {
        chatService.addCourseStudentParticipant(courseId, studentUserId);
    }

    @Transactional
    public void revokeCourseStudentParticipant(UUID courseId, UUID studentUserId) {
        chatService.revokeCourseStudentParticipant(courseId, studentUserId);
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return chatService.isParticipant(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getActiveRecipientUserIds(UUID conversationId, UUID senderId) {
        return chatService.getActiveRecipientUserIds(conversationId, senderId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getConversationParticipantUserIds(UUID conversationId) {
        return chatService.getConversationParticipantUserIds(conversationId);
    }

    // Message Operations
    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        return chatService.sendMessage(conversationId, userId, request);
    }

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID userId, SendMessageRequest request, MessageRepository messageRepoOverride, UserQueryPort userQueryPortOverride) {
        return chatService.sendMessage(conversationId, userId, request);
    }

    @Transactional
    public MessageResponse deleteMessage(UUID conversationId, UUID messageId, UUID userId, DeleteMessageRequest request) {
        return chatService.deleteMessage(conversationId, messageId, userId, request);
    }

    @Transactional(readOnly = true)
    public ChatMessageEvent buildGroupChatMessageEvent(UUID conversationId, UUID messageId, UUID senderId) {
        return chatService.buildGroupChatMessageEvent(conversationId, messageId, senderId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageRealtimeDelivery> buildChatMessageDeliveries(UUID conversationId, UUID messageId, UUID senderId) {
        return chatService.buildChatMessageDeliveries(conversationId, messageId, senderId);
    }

    // Attachment Operations
    @Transactional
    public ChatAttachmentUploadIntentResponse createAttachmentUploadIntent(UUID conversationId, UUID userId, ChatAttachmentUploadIntentRequest request) {
        return chatService.createAttachmentUploadIntent(conversationId, userId, request);
    }

    @Transactional(readOnly = true)
    public ChatAttachmentDownloadResponse downloadAttachment(UUID attachmentId, UUID userId) {
        return chatService.downloadAttachment(attachmentId, userId);
    }

    // Read Receipt Operations
    @Transactional
    public ConversationReadResponse markConversationAsRead(UUID conversationId, UUID userId, long requestedSequence) {
        return chatService.markConversationAsRead(conversationId, userId, requestedSequence);
    }

    @Transactional
    public void markConversationAsRead(UUID conversationId, UUID userId) {
        chatService.markConversationAsRead(conversationId, userId);
    }

    // Query Operations
    @Transactional(readOnly = true)
    public Page<ConversationResponse> getMyConversations(UUID userId, Pageable pageable) {
        return chatService.getMyConversations(userId, pageable);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ConversationResponse> getMyConversations(UUID userId, String cursor, Integer limit) {
        return chatService.getMyConversations(userId, cursor, limit);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable, MessageRepository messageRepoOverride) {
        return chatService.getMessages(conversationId, userId, pageable, messageRepoOverride);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(UUID conversationId, UUID userId, String cursor, Integer limit) {
        return chatService.getMessages(conversationId, userId, cursor, limit);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesBySequence(UUID conversationId, UUID userId, Long beforeSequence, Long afterSequence, Integer limit) {
        return chatService.getMessagesBySequence(conversationId, userId, beforeSequence, afterSequence, limit);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationDetail(UUID conversationId, UUID userId) {
        return chatService.getConversationDetail(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadCount(UUID userId) {
        return chatService.getTotalUnreadCount(userId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> findConversationIdsByBookingIds(List<UUID> bookingIds) {
        return chatService.findConversationIdsByBookingIds(bookingIds);
    }

    @Transactional(readOnly = true)

    // Static / Public payload records for backwards compatibility with existing consumers
    public record ChatMessageCreatedPayload(UUID conversationId, UUID messageId, UUID senderId) {
    }

    public record ChatConversationUpdatedPayload(UUID conversationId, UUID actorUserId) {
    }

    public record ChatUnreadCountUpdatedPayload(UUID conversationId, UUID recipientUserId) {
    }
}
