package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseActivityEventType;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorVerificationAdminPort;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationLockDto;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationQueueFilterQuery;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationQueueItemDto;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationRequestDto;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMentorVerificationModerationService {

    private final MentorVerificationAdminPort mentorVerificationAdminPort;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorVerificationQueueItemResponse> getQueue(AdminMentorVerificationQueueFilterRequest filterRequest) {
        MentorVerificationQueueFilterQuery query = new MentorVerificationQueueFilterQuery();
        if (filterRequest != null) {
            query.setStatus(filterRequest.getStatus());
            query.setKeyword(filterRequest.getKeyword());
            query.setSubmittedFrom(filterRequest.getSubmittedFrom());
            query.setSubmittedTo(filterRequest.getSubmittedTo());
            query.setPage(filterRequest.getPage());
            query.setSize(filterRequest.getSize());
            query.setSortBy(filterRequest.getSortBy());
            query.setDirection(filterRequest.getDirection());
        }
        PageResponse<MentorVerificationQueueItemDto> result = mentorVerificationAdminPort.getQueue(query);
        return PageResponse.<AdminMentorVerificationQueueItemResponse>builder()
                .content(result.getContent().stream().map(this::toQueueItemResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional
    public AdminMentorVerificationRequestResponse getRequestDetail(UUID adminUserId, UUID requestId) {
        return toDetailResponse(mentorVerificationAdminPort.getRequestDetail(adminUserId, requestId));
    }

    @Transactional(readOnly = true)
    public AdminMentorVerificationLockResponse getLockStatus(UUID adminUserId, UUID requestId) {
        return toLockResponse(mentorVerificationAdminPort.getLockStatus(adminUserId, requestId));
    }

    @Transactional
    public AdminMentorVerificationLockResponse refreshLock(UUID adminUserId, UUID requestId) {
        return toLockResponse(mentorVerificationAdminPort.refreshLock(adminUserId, requestId));
    }

    @Transactional
    public AdminMentorVerificationLockResponse releaseLock(UUID adminUserId, Set<RoleCode> roles, UUID requestId) {
        AdminMentorVerificationLockResponse previous = toLockResponse(mentorVerificationAdminPort.getLockStatus(adminUserId, requestId));
        AdminMentorVerificationLockResponse response = toLockResponse(mentorVerificationAdminPort.releaseLock(adminUserId, roles, requestId));

        if (previous.locked()) {
            Map<String, Object> oldValue = new LinkedHashMap<>();
            oldValue.put("lockedByAdminId", previous.lockedByAdminId());
            oldValue.put("lockedAt", previous.lockedAt());
            oldValue.put("lockExpiresAt", previous.lockExpiresAt());

            adminAuditWriterService.writeOperatorEvent(
                    adminUserId,
                    "MENTOR_VERIFICATION_REQUEST",
                    requestId,
                    AdminCaseActivityEventType.VERIFICATION_LOCK_RELEASED.name(),
                    oldValue,
                    Collections.emptyMap()
            );
        }

        return response;
    }

    @Transactional
    public AdminMentorVerificationRequestResponse requestRevision(UUID adminUserId, UUID requestId, String reviewNote) {
        return toDetailResponse(mentorVerificationAdminPort.requestRevision(adminUserId, requestId, reviewNote));
    }

    @Transactional
    public AdminMentorVerificationRequestResponse approve(UUID adminUserId, UUID requestId, String reviewNote) {
        return toDetailResponse(mentorVerificationAdminPort.approve(adminUserId, requestId, reviewNote));
    }

    @Transactional
    public AdminMentorVerificationRequestResponse reject(UUID adminUserId, UUID requestId, String rejectionReason) {
        return toDetailResponse(mentorVerificationAdminPort.reject(adminUserId, requestId, rejectionReason));
    }

    private AdminMentorVerificationQueueItemResponse toQueueItemResponse(MentorVerificationQueueItemDto dto) {
        return new AdminMentorVerificationQueueItemResponse(
                dto.requestId(),
                dto.mentorUserId(),
                dto.mentorFullName(),
                dto.mentorEmail(),
                dto.mentorAvatarUrl(),
                dto.status(),
                dto.revisionCount(),
                dto.submittedAt(),
                dto.lockedAt(),
                dto.lockExpiresAt()
        );
    }

    private AdminMentorVerificationLockResponse toLockResponse(MentorVerificationLockDto dto) {
        return new AdminMentorVerificationLockResponse(
                dto.requestId(),
                dto.locked(),
                dto.canReview(),
                dto.lockedByAdminId(),
                dto.lockedByAdminFullName(),
                dto.lockedByAdminEmail(),
                dto.lockedAt(),
                dto.lockExpiresAt(),
                dto.secondsRemaining()
        );
    }

    private AdminMentorVerificationRequestResponse toDetailResponse(MentorVerificationRequestDto dto) {
        if (dto == null) {
            return null;
        }
        return AdminMentorVerificationRequestResponse.builder()
                .requestId(dto.requestId())
                .mentorUserId(dto.mentorUserId())
                .mentorFullName(dto.mentorFullName())
                .mentorEmail(dto.mentorEmail())
                .mentorAvatarUrl(dto.mentorAvatarUrl())
                .status(dto.status())
                .submitNote(dto.submitNote())
                .reviewNote(dto.reviewNote())
                .rejectionReason(dto.rejectionReason())
                .revisionCount(dto.revisionCount())
                .reviewerEmail(dto.reviewerEmail())
                .lockedByAdminEmail(dto.lockedByAdminEmail())
                .lockedAt(dto.lockedAt())
                .lockExpiresAt(dto.lockExpiresAt())
                .canReview(dto.canReview())
                .submittedAt(dto.submittedAt())
                .termsAcceptedAt(dto.termsAcceptedAt())
                .termsVersion(dto.termsVersion())
                .reviewedAt(dto.reviewedAt())
                .approvedAt(dto.approvedAt())
                .withdrawnAt(dto.withdrawnAt())
                .createdAt(dto.createdAt())
                .updatedAt(dto.updatedAt())
                .documents(dto.documents())
                .timeline(dto.timeline())
                .checklist(dto.checklist())
                .mentorProfile(dto.mentorProfile())
                .studentProfile(dto.studentProfile())
                .build();
    }
}
