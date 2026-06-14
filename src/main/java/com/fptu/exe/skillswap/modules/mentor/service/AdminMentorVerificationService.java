package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequestEvent;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationChecklistResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.AdminMentorVerificationQueueProjection;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMentorVerificationService {

    private static final int LOCK_TTL_MINUTES = 5;

    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorVerificationQueueItemResponse> getQueue(AdminMentorVerificationQueueFilterRequest filterRequest) {
        AdminMentorVerificationQueueFilterRequest resolvedFilter = filterRequest == null
                ? new AdminMentorVerificationQueueFilterRequest()
                : filterRequest;
        Page<AdminMentorVerificationQueueProjection> page = mentorVerificationRequestRepository.searchAdminQueue(
                resolvedFilter.getStatus(),
                normalizeKeyword(resolvedFilter.getKeyword()),
                resolvedFilter.getSubmittedFrom(),
                resolvedFilter.getSubmittedTo(),
                resolvedFilter.getPageable()
        );

        return PageResponse.<AdminMentorVerificationQueueItemResponse>builder()
                .content(page.getContent().stream().map(this::mapQueueItem).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional
    public AdminMentorVerificationRequestResponse getRequestDetail(UUID adminUserId, UUID requestId) {
        User admin = getRequiredUser(adminUserId);
        MentorVerificationRequest request = getRequiredRequest(requestId);
        claimLockIfAvailable(request, admin);
        return mapDetail(request, adminUserId);
    }

    @Transactional(readOnly = true)
    public AdminMentorVerificationLockResponse getLockStatus(UUID adminUserId, UUID requestId) {
        getRequiredUser(adminUserId);
        MentorVerificationRequest request = getRequiredRequest(requestId);
        return mapLockStatus(request, adminUserId);
    }

    @Transactional
    public AdminMentorVerificationLockResponse refreshLock(UUID adminUserId, UUID requestId) {
        User admin = getRequiredUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);

        LocalDateTime now = LocalDateTime.now();
        request.setLockedBy(admin);
        request.setLockedAt(now);
        request.setLockExpiresAt(now.plusMinutes(LOCK_TTL_MINUTES));
        mentorVerificationRequestRepository.save(request);

        return mapLockStatus(request, adminUserId);
    }

    @Transactional
    public AdminMentorVerificationRequestResponse requestRevision(UUID adminUserId, UUID requestId, String reviewNote) {
        User reviewer = getRequiredUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);
        String normalizedNote = trimToNull(reviewNote);
        if (normalizedNote == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Nội dung yêu cầu chỉnh sửa không được để trống");
        }

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.NEEDS_REVISION);
        request.setReviewNote(normalizedNote);
        request.setRejectionReason(null);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setApprovedAt(null);
        clearLock(request);
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.REVISION_REQUESTED,
                reviewer,
                previousStatus,
                VerificationStatus.NEEDS_REVISION,
                normalizedNote
        );

        updateMentorProfileStatus(request.getMentor().getId(), MentorStatus.DRAFT);
        return mapDetail(request, adminUserId);
    }

    @Transactional
    public AdminMentorVerificationRequestResponse approve(UUID adminUserId, UUID requestId, String reviewNote) {
        User reviewer = getRequiredUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.APPROVED);
        request.setReviewNote(trimToNull(reviewNote));
        request.setRejectionReason(null);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setApprovedAt(LocalDateTime.now());
        clearLock(request);
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.APPROVED,
                reviewer,
                previousStatus,
                VerificationStatus.APPROVED,
                request.getReviewNote()
        );

        updateMentorProfileStatus(request.getMentor().getId(), MentorStatus.ACTIVE);
        return mapDetail(request, adminUserId);
    }

    @Transactional
    public AdminMentorVerificationRequestResponse reject(UUID adminUserId, UUID requestId, String rejectionReason) {
        User reviewer = getRequiredUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);
        String normalizedReason = trimToNull(rejectionReason);
        if (normalizedReason == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Lý do từ chối không được để trống");
        }

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.REJECTED);
        request.setReviewNote(null);
        request.setRejectionReason(normalizedReason);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setApprovedAt(null);
        clearLock(request);
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.REJECTED,
                reviewer,
                previousStatus,
                VerificationStatus.REJECTED,
                normalizedReason
        );

        updateMentorProfileStatus(request.getMentor().getId(), MentorStatus.DRAFT);
        return mapDetail(request, adminUserId);
    }

    private MentorVerificationRequest getRequiredRequest(UUID requestId) {
        if (requestId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã hồ sơ xác thực không được để trống");
        }
        return mentorVerificationRequestRepository.findById(requestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor"));
    }

    private MentorVerificationRequest getPendingRequest(UUID requestId) {
        MentorVerificationRequest request = getRequiredRequest(requestId);
        if (request.getStatus() != VerificationStatus.PENDING_REVIEW) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ có thể review hồ sơ đang chờ duyệt");
        }
        return request;
    }

    private User getRequiredUser(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng thực hiện duyệt"));
    }

    private void updateMentorProfileStatus(UUID mentorUserId, MentorStatus targetStatus) {
        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor liên kết"));
        mentorProfile.setStatus(targetStatus);
        mentorProfileRepository.save(mentorProfile);
    }

    private AdminMentorVerificationQueueItemResponse mapQueueItem(AdminMentorVerificationQueueProjection projection) {
        return AdminMentorVerificationQueueItemResponse.builder()
                .requestId(projection.getRequestId())
                .mentorUserId(projection.getMentorUserId())
                .mentorEmail(projection.getMentorEmail())
                .mentorFullName(projection.getMentorFullName())
                .mentorAvatarUrl(projection.getMentorAvatarUrl())
                .status(projection.getStatus())
                .revisionCount(projection.getRevisionCount())
                .submittedAt(projection.getSubmittedAt())
                .createdAt(projection.getCreatedAt())
                .updatedAt(projection.getUpdatedAt())
                .build();
    }

    private AdminMentorVerificationRequestResponse mapDetail(MentorVerificationRequest request, UUID adminUserId) {
        List<MentorVerificationDocumentResponse> documents = mentorVerificationDocumentRepository
                .findByRequestIdOrderByUploadedAtAsc(request.getId())
                .stream()
                .map(this::mapDocumentResponse)
                .toList();
        List<MentorVerificationTimelineEventResponse> timeline = mentorVerificationRequestEventRepository
                .findByRequestIdOrderByCreatedAtAsc(request.getId())
                .stream()
                .map(this::mapTimelineEventResponse)
                .toList();

        User mentor = request.getMentor();
        User reviewer = request.getReviewedBy();
        User lockedBy = request.getLockedBy();

        return AdminMentorVerificationRequestResponse.builder()
                .requestId(request.getId())
                .mentorUserId(mentor.getId())
                .mentorEmail(mentor.getEmail())
                .mentorFullName(mentor.getFullName())
                .mentorAvatarUrl(mentor.getAvatarUrl())
                .status(request.getStatus())
                .submitNote(request.getSubmittedNote())
                .reviewNote(request.getReviewNote())
                .rejectionReason(request.getRejectionReason())
                .revisionCount(request.getRevisionCount())
                .reviewerEmail(reviewer == null ? null : reviewer.getEmail())
                .lockedByAdminEmail(lockedBy == null ? null : lockedBy.getEmail())
                .lockedAt(request.getLockedAt())
                .lockExpiresAt(request.getLockExpiresAt())
                .canReview(canReview(request, adminUserId))
                .submittedAt(request.getSubmittedAt())
                .reviewedAt(request.getReviewedAt())
                .approvedAt(request.getApprovedAt())
                .withdrawnAt(request.getWithdrawnAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .documents(documents)
                .timeline(timeline)
                .checklist(buildChecklist(mentor.getId(), documents))
                .build();
    }

    private MentorVerificationTimelineEventResponse mapTimelineEventResponse(MentorVerificationRequestEvent event) {
        User actor = event.getActorUser();
        return MentorVerificationTimelineEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .actorUserId(actor == null ? null : actor.getId())
                .actorEmail(actor == null ? null : actor.getEmail())
                .actorFullName(actor == null ? null : actor.getFullName())
                .note(event.getNote())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private void appendEvent(
            MentorVerificationRequest request,
            MentorVerificationEventType eventType,
            User actorUser,
            VerificationStatus fromStatus,
            VerificationStatus toStatus,
            String note
    ) {
        mentorVerificationRequestEventRepository.save(MentorVerificationRequestEvent.builder()
                .request(request)
                .eventType(eventType)
                .actorUser(actorUser)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .note(trimToNull(note))
                .build());
    }

    private void claimLockIfAvailable(MentorVerificationRequest request, User admin) {
        if (request == null || admin == null || request.getStatus() != VerificationStatus.PENDING_REVIEW) {
            return;
        }
        if (!hasActiveLock(request) || isLockedBy(request, admin.getId())) {
            request.setLockedBy(admin);
            request.setLockedAt(LocalDateTime.now());
            request.setLockExpiresAt(LocalDateTime.now().plusMinutes(LOCK_TTL_MINUTES));
            mentorVerificationRequestRepository.save(request);
        }
    }

    private void assertReviewLockOwnership(MentorVerificationRequest request, UUID adminUserId) {
        if (!hasActiveLock(request)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Hồ sơ này chưa được admin nào mở để xử lý, vui lòng tải lại chi tiết trước khi duyệt");
        }
        if (!isLockedBy(request, adminUserId)) {
            throw new BaseException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "Hồ sơ này đang được admin khác xử lý tới " + request.getLockExpiresAt()
            );
        }
    }

    private boolean canReview(MentorVerificationRequest request, UUID adminUserId) {
        if (request.getStatus() != VerificationStatus.PENDING_REVIEW) {
            return false;
        }
        return hasActiveLock(request) && isLockedBy(request, adminUserId);
    }

    private AdminMentorVerificationLockResponse mapLockStatus(MentorVerificationRequest request, UUID adminUserId) {
        boolean activeLock = request.getStatus() == VerificationStatus.PENDING_REVIEW && hasActiveLock(request);
        User lockedBy = activeLock ? request.getLockedBy() : null;
        LocalDateTime lockExpiresAt = activeLock ? request.getLockExpiresAt() : null;
        long secondsRemaining = lockExpiresAt == null
                ? 0L
                : Math.max(0L, Duration.between(LocalDateTime.now(), lockExpiresAt).toSeconds());

        return AdminMentorVerificationLockResponse.builder()
                .requestId(request.getId())
                .locked(activeLock)
                .canReview(activeLock && isLockedBy(request, adminUserId))
                .lockedByAdminId(lockedBy == null ? null : lockedBy.getId())
                .lockedByAdminEmail(lockedBy == null ? null : lockedBy.getEmail())
                .lockedByAdminFullName(lockedBy == null ? null : lockedBy.getFullName())
                .lockedAt(activeLock ? request.getLockedAt() : null)
                .lockExpiresAt(lockExpiresAt)
                .secondsRemaining(secondsRemaining)
                .build();
    }

    private boolean hasActiveLock(MentorVerificationRequest request) {
        return request.getLockedBy() != null
                && request.getLockExpiresAt() != null
                && request.getLockExpiresAt().isAfter(LocalDateTime.now());
    }

    private boolean isLockedBy(MentorVerificationRequest request, UUID adminUserId) {
        return request.getLockedBy() != null
                && request.getLockedBy().getId() != null
                && request.getLockedBy().getId().equals(adminUserId);
    }

    private void clearLock(MentorVerificationRequest request) {
        request.setLockedBy(null);
        request.setLockedAt(null);
        request.setLockExpiresAt(null);
    }

    private MentorVerificationChecklistResponse buildChecklist(UUID userId, List<MentorVerificationDocumentResponse> documents) {
        boolean hasAcademicProfile = studentProfileRepository.existsById(userId);
        boolean hasAffiliationProof = documents.stream()
                .anyMatch(document -> document.isActive()
                        && document.documentType() == VerificationDocumentType.FPTU_AFFILIATION_PROOF);
        boolean hasExpertiseProof = documents.stream()
                .anyMatch(document -> document.isActive()
                        && document.documentType() == VerificationDocumentType.EXPERTISE_PROOF);
        return MentorVerificationChecklistResponse.builder()
                .academicProfileCompleted(hasAcademicProfile)
                .hasAffiliationProof(hasAffiliationProof)
                .hasExpertiseProof(hasExpertiseProof)
                .canSubmit(hasAcademicProfile && hasAffiliationProof && hasExpertiseProof)
                .build();
    }

    private MentorVerificationDocumentResponse mapDocumentResponse(MentorVerificationDocument document) {
        StoredFile storedFile = document.getStoredFile();
        return MentorVerificationDocumentResponse.builder()
                .id(document.getId())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .storageKind(document.getStorageKind())
                .originalFilename(storedFile.getOriginalName())
                .contentType(storedFile.getMimeType())
                .sizeBytes(storedFile.getSizeBytes())
                .fileUrl(storedFile.getPublicUrl())
                .isPrimary(document.isPrimary())
                .isActive(document.isActive())
                .version(document.getVersion())
                .reviewNote(document.getReviewNote())
                .rejectedReason(document.getRejectedReason())
                .uploadedAt(document.getUploadedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeKeyword(String keyword) {
        String normalized = trimToNull(keyword);
        return normalized == null ? null : normalized.toLowerCase();
    }
}
