package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AdminCaseActivityEventType;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorVerificationAdminPort;
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
        return mentorVerificationAdminPort.getQueue(filterRequest);
    }

    @Transactional
    public AdminMentorVerificationRequestResponse getRequestDetail(UUID adminUserId, UUID requestId) {
        return mentorVerificationAdminPort.getRequestDetail(adminUserId, requestId);
    }

    @Transactional(readOnly = true)
    public AdminMentorVerificationLockResponse getLockStatus(UUID adminUserId, UUID requestId) {
        return mentorVerificationAdminPort.getLockStatus(adminUserId, requestId);
    }

    @Transactional
    public AdminMentorVerificationLockResponse refreshLock(UUID adminUserId, UUID requestId) {
        return mentorVerificationAdminPort.refreshLock(adminUserId, requestId);
    }

    @Transactional
    public AdminMentorVerificationLockResponse releaseLock(UUID adminUserId, Set<RoleCode> roles, UUID requestId) {
        AdminMentorVerificationLockResponse previous = mentorVerificationAdminPort.getLockStatus(adminUserId, requestId);
        AdminMentorVerificationLockResponse response = mentorVerificationAdminPort.releaseLock(adminUserId, roles, requestId);

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
        return mentorVerificationAdminPort.requestRevision(adminUserId, requestId, reviewNote);
    }

    @Transactional
    public AdminMentorVerificationRequestResponse approve(UUID adminUserId, UUID requestId, String reviewNote) {
        return mentorVerificationAdminPort.approve(adminUserId, requestId, reviewNote);
    }

    @Transactional
    public AdminMentorVerificationRequestResponse reject(UUID adminUserId, UUID requestId, String rejectionReason) {
        return mentorVerificationAdminPort.reject(adminUserId, requestId, rejectionReason);
    }
}
