package com.fptu.exe.skillswap.modules.mentor.port;

import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.Set;
import java.util.UUID;

public interface MentorVerificationAdminPort {
    PageResponse<AdminMentorVerificationQueueItemResponse> getQueue(AdminMentorVerificationQueueFilterRequest filterRequest);
    AdminMentorVerificationRequestResponse getRequestDetail(UUID adminUserId, UUID requestId);
    AdminMentorVerificationLockResponse getLockStatus(UUID adminUserId, UUID requestId);
    AdminMentorVerificationLockResponse refreshLock(UUID adminUserId, UUID requestId);
    AdminMentorVerificationLockResponse releaseLock(UUID adminUserId, Set<RoleCode> roles, UUID requestId);
    AdminMentorVerificationRequestResponse requestRevision(UUID adminUserId, UUID requestId, String reviewNote);
    AdminMentorVerificationRequestResponse approve(UUID adminUserId, UUID requestId, String reviewNote);
    AdminMentorVerificationRequestResponse reject(UUID adminUserId, UUID requestId, String rejectionReason);
    long countPendingVerificationRequests();
    boolean existsById(UUID verificationRequestId);
    java.util.List<String> verificationStatusNames();
}
