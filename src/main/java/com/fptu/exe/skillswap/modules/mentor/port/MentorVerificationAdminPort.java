package com.fptu.exe.skillswap.modules.mentor.port;

import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationLockDto;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationQueueFilterQuery;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationQueueItemDto;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorVerificationRequestDto;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.Set;
import java.util.UUID;

public interface MentorVerificationAdminPort {
    PageResponse<MentorVerificationQueueItemDto> getQueue(MentorVerificationQueueFilterQuery filterRequest);
    MentorVerificationRequestDto getRequestDetail(UUID adminUserId, UUID requestId);
    MentorVerificationLockDto getLockStatus(UUID adminUserId, UUID requestId);
    MentorVerificationLockDto refreshLock(UUID adminUserId, UUID requestId);
    MentorVerificationLockDto releaseLock(UUID adminUserId, Set<RoleCode> roles, UUID requestId);
    MentorVerificationRequestDto requestRevision(UUID adminUserId, UUID requestId, String reviewNote);
    MentorVerificationRequestDto approve(UUID adminUserId, UUID requestId, String reviewNote);
    MentorVerificationRequestDto reject(UUID adminUserId, UUID requestId, String rejectionReason);
    long countPendingVerificationRequests();
}
