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
 * Facade chính điều phối toàn bộ các nghiệp vụ chat và hội thoại của hệ thống.
 * Tuân thủ Single Responsibility Principle (SRP) bằng cách ủy quyền cho các sub-services chuyên biệt:
 * - ChatRoomService: Quản lý phòng chat, booking link và danh sách người tham gia.
 * - ChatMessageService: Gửi nhận, xóa tin nhắn và dispatch realtime outbox.
 * - ChatAttachmentService: Upload/download tệp đính kèm và kiểm tra an toàn tệp.
 * - ChatReadReceiptService: Quản lý con trỏ đã đọc và số lượng tin chưa đọc.
 * - ChatQueryService: Truy vấn danh sách, phân trang cursor/pageable và chi tiết hội thoại.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final ChatAttachmentService chatAttachmentService;
    private final ChatReadReceiptService chatReadReceiptService;
    private final ChatQueryService chatQueryService;

    // Room & Participant Lifecycle
    @Transactional
    public Conversation createDirectForAcceptedBooking(UUID bookingId, UUID mentorUserId, UUID menteeUserId) {
        Conversation conversation = chatRoomService.createDirectForAcceptedBooking(bookingId, mentorUserId, menteeUserId);
        chatMessageService.createBookingConfirmedSystemMessage(conversation.getId(), bookingId);
        return conversation;
    }

    @Transactional(readOnly = true)
    public Conversation findByBookingId(UUID bookingId) {
        return chatRoomService.findByBookingId(bookingId);
    }

    @Transactional(readOnly = true)
    public Conversation findById(UUID conversationId) {
        return chatRoomService.findById(conversationId);
    }

    @Transactional(readOnly = true)
    public Conversation findDirectByParticipants(UUID firstUserId, UUID secondUserId) {
        return chatRoomService.findDirectByParticipants(firstUserId, secondUserId);
    }

    @Transactional
    public Conversation ensureCourseGroupConversation(UUID courseId, UUID mentorUserId) {
        return chatRoomService.ensureCourseGroupConversation(courseId, mentorUserId);
    }

    @Transactional
    public void addCourseStudentParticipant(UUID courseId, UUID studentUserId) {
        chatRoomService.addCourseStudentParticipant(courseId, studentUserId);
    }

    @Transactional
    public void addCourseStudentParticipant(UUID courseId, UUID mentorUserId, UUID studentUserId) {
        chatRoomService.addCourseStudentParticipant(courseId, mentorUserId, studentUserId);
    }

    @Transactional
    public void revokeCourseStudentParticipant(UUID courseId, UUID studentUserId) {
        chatRoomService.revokeCourseStudentParticipant(courseId, studentUserId);
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return chatRoomService.isParticipant(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getActiveRecipientUserIds(UUID conversationId, UUID senderId) {
        return chatRoomService.getActiveRecipientUserIds(conversationId, senderId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getConversationParticipantUserIds(UUID conversationId) {
        return chatRoomService.getConversationParticipantUserIds(conversationId);
    }

    // Message Operations
    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        return chatMessageService.sendMessage(conversationId, userId, request);
    }

    @Transactional
    public MessageResponse deleteMessage(UUID conversationId, UUID messageId, UUID userId, DeleteMessageRequest request) {
        return chatMessageService.deleteMessage(conversationId, messageId, userId, request);
    }

    @Transactional(readOnly = true)
    public ChatMessageEvent buildGroupChatMessageEvent(UUID conversationId, UUID messageId, UUID senderId) {
        return chatMessageService.buildGroupChatMessageEvent(conversationId, messageId, senderId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageRealtimeDelivery> buildChatMessageDeliveries(UUID conversationId, UUID messageId, UUID senderId) {
        return chatMessageService.buildChatMessageDeliveries(conversationId, messageId, senderId);
    }

    // Attachment Operations
    @Transactional
    public ChatAttachmentUploadIntentResponse createAttachmentUploadIntent(UUID conversationId, UUID userId, ChatAttachmentUploadIntentRequest request) {
        return chatAttachmentService.createAttachmentUploadIntent(conversationId, userId, request);
    }

    @Transactional(readOnly = true)
    public ChatAttachmentDownloadResponse downloadAttachment(UUID attachmentId, UUID userId) {
        return chatAttachmentService.downloadAttachment(attachmentId, userId);
    }

    // Read Receipt Operations
    @Transactional
    public ConversationReadResponse markConversationAsRead(UUID conversationId, UUID userId, long requestedSequence) {
        return chatReadReceiptService.markConversationAsRead(conversationId, userId, requestedSequence);
    }

    @Transactional
    public void markConversationAsRead(UUID conversationId, UUID userId) {
        chatReadReceiptService.markConversationAsRead(conversationId, userId);
    }

    // Query Operations
    @Transactional(readOnly = true)
    public Page<ConversationResponse> getMyConversations(UUID userId, Pageable pageable) {
        return chatQueryService.getMyConversations(userId, pageable);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ConversationResponse> getMyConversations(UUID userId, String cursor, Integer limit) {
        return chatQueryService.getMyConversations(userId, cursor, limit);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable) {
        return chatQueryService.getMessages(conversationId, userId, pageable, null);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<MessageResponse> getMessages(UUID conversationId, UUID userId, String cursor, Integer limit) {
        return chatQueryService.getMessages(conversationId, userId, cursor, limit);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesBySequence(UUID conversationId, UUID userId, Long beforeSequence, Long afterSequence, Integer limit) {
        return chatQueryService.getMessagesBySequence(conversationId, userId, beforeSequence, afterSequence, limit);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationDetail(UUID conversationId, UUID userId) {
        return chatQueryService.getConversationDetail(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadCount(UUID userId) {
        return chatQueryService.getTotalUnreadCount(userId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> findConversationIdsByBookingIds(List<UUID> bookingIds) {
        return chatQueryService.findConversationIdsByBookingIds(bookingIds);
    }
}
