package com.fptu.exe.skillswap.modules.conversation.service;

import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatReport;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatReportStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationStatus;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationUserBlock;
import com.fptu.exe.skillswap.modules.conversation.dto.request.ChatReportCreateRequest;
import com.fptu.exe.skillswap.modules.conversation.dto.request.ChatReportResolveRequest;
import com.fptu.exe.skillswap.modules.conversation.dto.request.ConversationLockRequest;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ChatReportResponse;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationBlockResponse;
import com.fptu.exe.skillswap.modules.conversation.repository.ChatReportRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationUserBlockRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationSafetyService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationUserBlockRepository conversationUserBlockRepository;
    private final ChatReportRepository chatReportRepository;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional
    public ConversationBlockResponse block(UUID conversationId, UUID blockerUserId) {
        Conversation conversation = requireParticipantConversation(conversationId, blockerUserId, true);
        UUID blockedUserId = otherParticipantId(conversationId, blockerUserId);
        ConversationUserBlock block = conversationUserBlockRepository
                .findByConversationIdAndBlockerUserId(conversationId, blockerUserId)
                .orElseGet(() -> conversationUserBlockRepository.save(ConversationUserBlock.builder()
                        .conversation(conversation)
                        .blockerUserId(blockerUserId)
                        .blockedUserId(blockedUserId)
                        .build()));
        return new ConversationBlockResponse(conversationId, blockerUserId, block.getBlockedUserId(), true, block.getCreatedAt());
    }

    @Transactional
    public ConversationBlockResponse unblock(UUID conversationId, UUID blockerUserId) {
        requireParticipantConversation(conversationId, blockerUserId, false);
        ConversationUserBlock block = conversationUserBlockRepository
                .findByConversationIdAndBlockerUserId(conversationId, blockerUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Bạn chưa chặn participant trong cuộc hội thoại này"));
        conversationUserBlockRepository.delete(block);
        return new ConversationBlockResponse(conversationId, blockerUserId, block.getBlockedUserId(), false, null);
    }

    @Transactional
    public ChatReportResponse createReport(UUID conversationId, UUID reporterUserId, ChatReportCreateRequest request) {
        Conversation conversation = requireParticipantConversation(conversationId, reporterUserId, false);
        if (chatReportRepository.existsByConversationIdAndReporterUserIdAndStatus(
                conversationId, reporterUserId, ChatReportStatus.OPEN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đang có một chat report chưa được xử lý");
        }
        try {
            ChatReport report = chatReportRepository.saveAndFlush(ChatReport.builder()
                    .conversation(conversation)
                    .reporterUserId(reporterUserId)
                    .reportedUserId(otherParticipantId(conversationId, reporterUserId))
                    .reasonType(request.reasonType())
                    .description(normalizeDescription(request.description()))
                    .build());
            return toResponse(report);
        } catch (DataIntegrityViolationException ex) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đang có một chat report chưa được xử lý", ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<ChatReportResponse> getReports(ChatReportStatus status, Pageable pageable) {
        Page<ChatReport> reports = status == null ? chatReportRepository.findAll(pageable) : chatReportRepository.findByStatus(status, pageable);
        return reports.map(ConversationSafetyService::toResponse);
    }

    @Transactional
    public ChatReportResponse resolveReport(UUID reportId, UUID adminUserId, ChatReportResolveRequest request) {
        if (request.status() == ChatReportStatus.OPEN) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Report chỉ có thể được resolve bằng một trạng thái kết thúc");
        }
        ChatReport report = chatReportRepository.findById(reportId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy chat report"));
        if (report.getStatus() != ChatReportStatus.OPEN) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chat report đã được xử lý");
        }
        Conversation conversation = conversationRepository.findByIdForUpdate(report.getConversation().getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        if (request.status() == ChatReportStatus.RESOLVED_LOCKED) {
            conversation.setStatus(ConversationStatus.LOCKED);
            conversation.setLockAt(DateTimeUtil.now());
        }
        report.setStatus(request.status());
        report.setReviewedByUserId(adminUserId);
        report.setReviewNote(normalizeDescription(request.reviewNote()));
        report.setResolvedAt(DateTimeUtil.now());
        ChatReport saved = chatReportRepository.save(report);
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "CHAT_REPORT",
                saved.getId(),
                request.status().name(),
                Map.of("previousStatus", ChatReportStatus.OPEN.name(), "conversationId", conversation.getId()),
                Map.of("status", saved.getStatus().name(), "conversationLocked", conversation.getStatus() == ConversationStatus.LOCKED)
        );
        return toResponse(saved);
    }

    @Transactional
    public void setAdminLock(UUID conversationId, UUID adminUserId, ConversationLockRequest request) {
        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        ConversationStatus previousStatus = conversation.getStatus();
        conversation.setStatus(Boolean.TRUE.equals(request.locked()) ? ConversationStatus.LOCKED : ConversationStatus.ACTIVE);
        conversation.setLockAt(Boolean.TRUE.equals(request.locked()) ? DateTimeUtil.now() : null);
        Map<String, Object> newValue = new java.util.LinkedHashMap<>();
        newValue.put("status", conversation.getStatus().name());
        String note = normalizeDescription(request.note());
        if (note != null) {
            newValue.put("note", note);
        }
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "CONVERSATION",
                conversationId,
                Boolean.TRUE.equals(request.locked()) ? "CHAT_CONVERSATION_LOCKED" : "CHAT_CONVERSATION_UNLOCKED",
                Map.of("status", previousStatus.name()),
                newValue
        );
    }

    private Conversation requireParticipantConversation(UUID conversationId, UUID userId, boolean lock) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không tham gia cuộc hội thoại này");
        }
        return (lock ? conversationRepository.findByIdForUpdate(conversationId) : conversationRepository.findById(conversationId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
    }

    private UUID otherParticipantId(UUID conversationId, UUID currentUserId) {
        List<UUID> others = participantRepository.findByConversationId(conversationId).stream()
                .map(participant -> participant.getUser().getId())
                .filter(userId -> !userId.equals(currentUserId))
                .toList();
        if (others.size() != 1) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Cuộc hội thoại trực tiếp không hợp lệ");
        }
        return others.getFirst();
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static ChatReportResponse toResponse(ChatReport report) {
        return new ChatReportResponse(
                report.getId(), report.getConversation().getId(), report.getReporterUserId(), report.getReportedUserId(),
                report.getReasonType(), report.getDescription(), report.getStatus(), report.getReviewedByUserId(),
                report.getReviewNote(), report.getResolvedAt(), report.getCreatedAt());
    }
}
