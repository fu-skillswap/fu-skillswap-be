package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
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
import com.fptu.exe.skillswap.modules.academic.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationChecklistResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.AdminMentorVerificationQueueProjection;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMentorVerificationService {

    private static final int LOCK_TTL_MINUTES = 5;
    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "submittedAt",
            "createdAt",
            "updatedAt",
            "mentorFullName",
            "mentorEmail",
            "status",
            "revisionCount"
    );
    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";

    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserRepository userRepository;
    private final AcademicService academicService;
    private final MentorProfileService mentorProfileService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorVerificationQueueItemResponse> getQueue(AdminMentorVerificationQueueFilterRequest filterRequest) {
        AdminMentorVerificationQueueFilterRequest resolvedFilter = filterRequest == null
                ? new AdminMentorVerificationQueueFilterRequest()
                : filterRequest;
        String keyword = trimToNull(resolvedFilter.getKeyword());
        String normalizedKeyword = normalizeSearchText(keyword);
        String keywordPattern = toLikePattern(keyword);
        String normalizedKeywordPattern = toLikePattern(normalizedKeyword);
        Pageable pageable = buildQueuePageable(resolvedFilter);
        Page<AdminMentorVerificationQueueProjection> page = keywordPattern == null
                ? mentorVerificationRequestRepository.findAdminQueueWithoutKeyword(
                        resolvedFilter.getStatus(),
                        resolvedFilter.getSubmittedFrom(),
                        resolvedFilter.getSubmittedTo(),
                        pageable
                )
                : mentorVerificationRequestRepository.searchAdminQueue(
                        resolvedFilter.getStatus(),
                        keyword,
                        normalizedKeyword,
                        keywordPattern,
                        normalizedKeywordPattern,
                        resolvedFilter.getSubmittedFrom(),
                        resolvedFilter.getSubmittedTo(),
                        pageable
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

    private Pageable buildQueuePageable(AdminMentorVerificationQueueFilterRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = resolveQueueSortBy(request.getSortBy());
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String resolveQueueSortBy(String sortBy) {
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            return "submittedAt";
        }
        return sortBy;
    }

    @Transactional
    public AdminMentorVerificationRequestResponse getRequestDetail(UUID adminUserId, UUID requestId) {
        User admin = getRequiredUser(adminUserId);
        if (requestId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã hồ sơ xác thực không được để trống");
        }
        MentorVerificationRequest request = mentorVerificationRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor"));
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

        LocalDateTime now = DateTimeUtil.now();
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
        request.setReviewedAt(DateTimeUtil.now());
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
        notificationService.createNotification(
                request.getMentor().getId(),
                NotificationType.MENTOR_VERIFICATION_NEEDS_REVISION,
                "Hồ sơ mentor cần được bổ sung",
                "Hồ sơ mentor của bạn cần được bổ sung thông tin trước khi xét duyệt.",
                "MENTOR_VERIFICATION",
                request.getId()
        );
        return mapDetail(request, adminUserId);
    }

    @Transactional
    public AdminMentorVerificationRequestResponse approve(UUID adminUserId, UUID requestId, String reviewNote) {
        User reviewer = getRequiredUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);

        User mentor = request.getMentor();
        if (mentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.ADMIN) ||
            mentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.SYSTEM_ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể duyệt quyền Mentor cho tài khoản quản trị viên");
        }

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.APPROVED);
        request.setReviewNote(trimToNull(reviewNote));
        request.setRejectionReason(null);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(DateTimeUtil.now());
        request.setApprovedAt(DateTimeUtil.now());
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

        updateMentorProfileStatus(request.getMentor().getId(), MentorStatus.ACTIVE, reviewer);

        // Grant MENTOR role to user (preserving MENTEE and avoiding duplicates)
        if (!mentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)) {
            mentor.getRoles().add(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR);
            userRepository.save(mentor);
        }

        notificationService.createNotification(
                mentor.getId(),
                NotificationType.MENTOR_VERIFICATION_APPROVED,
                "Yêu cầu trở thành mentor đã được duyệt",
                "Hồ sơ mentor của bạn đã được duyệt. Bạn có thể bắt đầu nhận yêu cầu đặt lịch.",
                "MENTOR_VERIFICATION",
                request.getId()
        );
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
        request.setReviewedAt(DateTimeUtil.now());
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
        notificationService.createNotification(
                request.getMentor().getId(),
                NotificationType.MENTOR_VERIFICATION_REJECTED,
                "Yêu cầu trở thành mentor đã bị từ chối",
                "Hồ sơ mentor của bạn chưa được duyệt. Vui lòng xem lý do từ chối và cập nhật lại nếu cần.",
                "MENTOR_VERIFICATION",
                request.getId()
        );
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
        if (requestId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã hồ sơ xác thực không được để trống");
        }
        MentorVerificationRequest request = mentorVerificationRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor"));
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
        updateMentorProfileStatus(mentorUserId, targetStatus, null);
    }

    private void updateMentorProfileStatus(UUID mentorUserId, MentorStatus targetStatus, User reviewer) {
        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor liên kết"));
        mentorProfile.setStatus(targetStatus);
        if (targetStatus == MentorStatus.ACTIVE) {
            mentorProfile.setVerifiedAt(DateTimeUtil.now());
            if (reviewer != null) {
                mentorProfile.setVerifiedBy(reviewer);
            }
        }
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

        MentorProfileResponse mentorProfile = mentorProfileService.getMyProfile(mentor.getId());
        StudentProfileResponse studentProfile = null;
        try {
            studentProfile = academicService.getStudentProfile(mentor.getId());
        } catch (ResourceNotFoundException ex) {
            studentProfile = null;
        }

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
                .termsAcceptedAt(request.getTermsAcceptedAt())
                .termsVersion(request.getTermsVersion())
                .reviewedAt(request.getReviewedAt())
                .approvedAt(request.getApprovedAt())
                .withdrawnAt(request.getWithdrawnAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .documents(documents)
                .timeline(timeline)
                .checklist(buildChecklist(mentor.getId(), documents))
                .mentorProfile(mentorProfile)
                .studentProfile(studentProfile)
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
            request.setLockedAt(DateTimeUtil.now());
            request.setLockExpiresAt(DateTimeUtil.now().plusMinutes(LOCK_TTL_MINUTES));
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
                : Math.max(0L, Duration.between(DateTimeUtil.now(), lockExpiresAt).toSeconds());

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
                && request.getLockExpiresAt().isAfter(DateTimeUtil.now());
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
        boolean hasAcademicProfile = academicService.hasCompletedStudentProfile(userId);
        boolean hasMentorProfile = mentorProfileService.hasCompletedMentorProfile(userId);
        boolean hasAffiliationProof = documents.stream()
                .anyMatch(document -> document.isActive()
                        && document.documentType() == VerificationDocumentType.FPTU_AFFILIATION_PROOF);
        boolean hasExpertiseProof = documents.stream()
                .anyMatch(document -> document.isActive()
                        && document.documentType() == VerificationDocumentType.EXPERTISE_PROOF);
        return MentorVerificationChecklistResponse.builder()
                .academicProfileCompleted(hasAcademicProfile)
                .mentorProfileCompleted(hasMentorProfile)
                .hasAffiliationProof(hasAffiliationProof)
                .hasExpertiseProof(hasExpertiseProof)
                .canSubmit(hasAcademicProfile && hasMentorProfile && hasAffiliationProof && hasExpertiseProof)
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

    private String normalizeSearchText(String keyword) {
        String normalized = trimToNull(keyword);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String toLikePattern(String keyword) {
        String normalized = trimToNull(keyword);
        if (normalized == null) {
            return null;
        }
        return "%" + normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + "%";
    }
}




