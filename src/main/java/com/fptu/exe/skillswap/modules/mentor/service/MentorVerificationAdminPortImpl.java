package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.modules.mentor.domain.*;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.*;
import com.fptu.exe.skillswap.modules.mentor.event.MentorVerificationEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.mentor.port.MentorVerificationAdminPort;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class MentorVerificationAdminPortImpl implements MentorVerificationAdminPort {

    private static final int LOCK_TTL_MINUTES = 5;
    private static final int MAX_REVIEW_NOTE_LENGTH = 2000;
    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "submittedAt",
            "createdAt",
            "updatedAt",
            "status",
            "revisionCount"
    );

    private final UserQueryPort userQueryPort;

    @Override
    public List<String> verificationStatusNames() {
        return java.util.Arrays.stream(VerificationStatus.values()).map(Enum::name).toList();
    }

    @Value("${application.mentor-verification.terms-version:SKILLSWAP_MENTOR_TERMS_V1}")
    private String mentorTermsVersion = "SKILLSWAP_MENTOR_TERMS_V1";

    @Value("${application.mentor-verification.submit-requirements.student-profile-completed:true}")
    private boolean requireCompletedStudentProfile = true;

    @Value("${application.mentor-verification.submit-requirements.mentor-profile-completed:true}")
    private boolean requireCompletedMentorProfile = true;

    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final AcademicService academicService;
    private final MentorProfileService mentorProfileService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminMentorVerificationQueueItemResponse> getQueue(AdminMentorVerificationQueueFilterRequest filterRequest) {
        AdminMentorVerificationQueueFilterRequest resolvedFilter = filterRequest == null
                ? new AdminMentorVerificationQueueFilterRequest()
                : filterRequest;
        Pageable pageable = buildQueuePageable(resolvedFilter);
        Page<MentorVerificationRequest> page = mentorVerificationRequestRepository.findAdminQueue(
                resolvedFilter.getStatus(),
                resolvedFilter.getSubmittedFrom(),
                resolvedFilter.getSubmittedTo(),
                pageable
        );

        List<UUID> mentorUserIds = page.getContent().stream()
                .map(MentorVerificationRequest::getMentorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UserSummaryRecord> userMap = userQueryPort.findUserSummariesByIdIn(mentorUserIds);

        List<AdminMentorVerificationQueueItemResponse> items = page.getContent().stream()
                .map(req -> mapQueueItem(req, userMap.get(req.getMentorUserId())))
                .toList();

        return PageResponse.<AdminMentorVerificationQueueItemResponse>builder()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public AdminMentorVerificationRequestResponse getRequestDetail(UUID adminUserId, UUID requestId) {
        requireActiveUser(adminUserId);
        if (requestId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã hồ sơ xác thực không được để trống");
        }
        MentorVerificationRequest request = mentorVerificationRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor"));
        claimLockIfAvailable(request, adminUserId);
        return mapDetail(request, adminUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminMentorVerificationLockResponse getLockStatus(UUID adminUserId, UUID requestId) {
        requireActiveUser(adminUserId);
        MentorVerificationRequest request = getRequiredRequest(requestId);
        return mapLockStatus(request, adminUserId);
    }

    @Override
    @Transactional
    public AdminMentorVerificationLockResponse refreshLock(UUID adminUserId, UUID requestId) {
        requireActiveUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);

        LocalDateTime now = DateTimeUtil.now();
        request.setLockedByUserId(adminUserId);
        request.setLockedAt(now);
        request.setLockExpiresAt(now.plusMinutes(LOCK_TTL_MINUTES));
        mentorVerificationRequestRepository.save(request);

        return mapLockStatus(request, adminUserId);
    }

    @Override
    @Transactional
    public AdminMentorVerificationLockResponse releaseLock(UUID adminUserId, Set<RoleCode> roles, UUID requestId) {
        requireActiveUser(adminUserId);
        MentorVerificationRequest request = mentorVerificationRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor"));
        if (request.getLockedByUserId() == null && request.getLockedAt() == null && request.getLockExpiresAt() == null) {
            return mapLockStatus(request, adminUserId);
        }

        boolean isOwner = isLockedBy(request, adminUserId);
        boolean isSystemAdmin = roles != null && roles.contains(RoleCode.SYSTEM_ADMIN);
        if (!isOwner && !isSystemAdmin) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền giải phóng lock này");
        }

        clearLock(request);
        mentorVerificationRequestRepository.save(request);
        return mapLockStatus(request, adminUserId);
    }

    @Override
    @Transactional
    public AdminMentorVerificationRequestResponse requestRevision(UUID adminUserId, UUID requestId, String reviewNote) {
        UserSummaryRecord reviewer = requireActiveUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);
        String normalizedNote = normalizeRequiredReviewText(reviewNote, "Nội dung yêu cầu chỉnh sửa không được để trống");

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.NEEDS_REVISION);
        request.setReviewNote(normalizedNote);
        request.setRejectionReason(null);
        request.setReviewedByUserId(adminUserId);
        request.setReviewedAt(DateTimeUtil.now());
        request.setApprovedAt(null);
        clearLock(request);
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.REVISION_REQUESTED,
                adminUserId,
                previousStatus,
                VerificationStatus.NEEDS_REVISION,
                normalizedNote
        );

        updateMentorProfileStatus(request.getMentorUserId(), MentorStatus.DRAFT, null);
        publishMentorVerificationNotification(
                request.getMentorUserId(),
                NotificationType.MENTOR_VERIFICATION_NEEDS_REVISION,
                "Hồ sơ mentor cần được bổ sung",
                "Hồ sơ mentor của bạn cần được bổ sung thông tin trước khi xét duyệt.",
                request.getId()
        );
        publishMentorVerificationEmail(
                MentorVerificationEmailNotificationEvent.EventType.NEEDS_REVISION_EMAIL,
                request,
                reviewer,
                normalizedNote
        );
        return mapDetail(request, adminUserId);
    }

    @Override
    @Transactional
    public AdminMentorVerificationRequestResponse approve(UUID adminUserId, UUID requestId, String reviewNote) {
        UserSummaryRecord reviewer = requireActiveUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);
        ensureApprovalEligible(request);
        String normalizedReviewNote = normalizeOptionalReviewText(reviewNote);

        UserSummaryRecord mentor = userQueryPort.findUserSummaryById(request.getMentorUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy thông tin mentor"));
        if (mentor.hasRole(RoleCode.ADMIN) || mentor.hasRole(RoleCode.SYSTEM_ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Không thể duyệt quyền Mentor cho tài khoản quản trị viên");
        }

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.APPROVED);
        request.setReviewNote(normalizedReviewNote);
        request.setRejectionReason(null);
        request.setReviewedByUserId(adminUserId);
        request.setReviewedAt(DateTimeUtil.now());
        request.setApprovedAt(DateTimeUtil.now());
        clearLock(request);
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.APPROVED,
                adminUserId,
                previousStatus,
                VerificationStatus.APPROVED,
                request.getReviewNote()
        );

        updateMentorProfileStatus(request.getMentorUserId(), MentorStatus.ACTIVE, adminUserId);
        userQueryPort.grantMentorRole(request.getMentorUserId());

        publishMentorVerificationNotification(
                mentor.userId(),
                NotificationType.MENTOR_VERIFICATION_APPROVED,
                "Yêu cầu trở thành mentor đã được duyệt",
                "Hồ sơ mentor của bạn đã được duyệt. Bạn có thể bắt đầu nhận yêu cầu đặt lịch.",
                request.getId()
        );
        publishMentorVerificationEmail(
                MentorVerificationEmailNotificationEvent.EventType.APPROVED_EMAIL,
                request,
                reviewer,
                normalizedReviewNote
        );
        return mapDetail(request, adminUserId);
    }

    @Override
    @Transactional
    public AdminMentorVerificationRequestResponse reject(UUID adminUserId, UUID requestId, String rejectionReason) {
        UserSummaryRecord reviewer = requireActiveUser(adminUserId);
        MentorVerificationRequest request = getPendingRequest(requestId);
        assertReviewLockOwnership(request, adminUserId);
        String normalizedReason = normalizeRequiredReviewText(rejectionReason, "Lý do từ chối không được để trống");

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.REJECTED);
        request.setReviewNote(null);
        request.setRejectionReason(normalizedReason);
        request.setReviewedByUserId(adminUserId);
        request.setReviewedAt(DateTimeUtil.now());
        request.setApprovedAt(null);
        clearLock(request);
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.REJECTED,
                adminUserId,
                previousStatus,
                VerificationStatus.REJECTED,
                normalizedReason
        );

        updateMentorProfileStatus(request.getMentorUserId(), MentorStatus.DRAFT, null);
        publishMentorVerificationNotification(
                request.getMentorUserId(),
                NotificationType.MENTOR_VERIFICATION_REJECTED,
                "Yêu cầu trở thành mentor đã bị từ chối",
                "Hồ sơ mentor của bạn chưa được duyệt. Vui lòng xem lý do từ chối và cập nhật lại nếu cần.",
                request.getId()
        );
        return mapDetail(request, adminUserId);
    }

    @Override
    public long countPendingVerificationRequests() {
        return mentorVerificationRequestRepository.countByStatus(VerificationStatus.PENDING_REVIEW);
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

    private UserSummaryRecord requireActiveUser(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return userQueryPort.findUserSummaryById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private void updateMentorProfileStatus(UUID mentorUserId, MentorStatus targetStatus, UUID reviewerUserId) {
        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor liên kết"));
        mentorProfile.setStatus(targetStatus);
        if (targetStatus == MentorStatus.ACTIVE) {
            mentorProfile.setVerifiedAt(DateTimeUtil.now());
            if (reviewerUserId != null) {
                mentorProfile.setVerifiedByUserId(reviewerUserId);
            }
        }
        mentorProfileRepository.save(mentorProfile);
    }

    private AdminMentorVerificationQueueItemResponse mapQueueItem(MentorVerificationRequest req, UserSummaryRecord mentor) {
        return AdminMentorVerificationQueueItemResponse.builder()
                .requestId(req.getId())
                .mentorUserId(req.getMentorUserId())
                .mentorEmail(mentor != null ? mentor.email() : null)
                .mentorFullName(mentor != null ? mentor.fullName() : null)
                .mentorAvatarUrl(mentor != null ? mentor.avatarUrl() : null)
                .status(req.getStatus())
                .revisionCount(req.getRevisionCount())
                .submittedAt(req.getSubmittedAt())
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
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

        UserSummaryRecord mentor = userQueryPort.findUserSummaryById(request.getMentorUserId()).orElse(null);
        UserSummaryRecord reviewer = request.getReviewedByUserId() != null
                ? userQueryPort.findUserSummaryById(request.getReviewedByUserId()).orElse(null)
                : null;
        UserSummaryRecord lockedBy = request.getLockedByUserId() != null
                ? userQueryPort.findUserSummaryById(request.getLockedByUserId()).orElse(null)
                : null;

        MentorProfileResponse mentorProfile = mentorProfileService.getMyProfile(request.getMentorUserId());
        StudentProfileResponse studentProfile = null;
        try {
            studentProfile = academicService.getStudentProfile(request.getMentorUserId());
        } catch (ResourceNotFoundException ex) {
            studentProfile = null;
        }

        return AdminMentorVerificationRequestResponse.builder()
                .requestId(request.getId())
                .mentorUserId(request.getMentorUserId())
                .mentorEmail(mentor != null ? mentor.email() : null)
                .mentorFullName(mentor != null ? mentor.fullName() : null)
                .mentorAvatarUrl(mentor != null ? mentor.avatarUrl() : null)
                .status(request.getStatus())
                .submitNote(request.getSubmittedNote())
                .reviewNote(request.getReviewNote())
                .rejectionReason(request.getRejectionReason())
                .revisionCount(request.getRevisionCount())
                .reviewerEmail(reviewer == null ? null : reviewer.email())
                .lockedByAdminEmail(lockedBy == null ? null : lockedBy.email())
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
                .checklist(buildChecklist(request.getMentorUserId(), documents))
                .mentorProfile(mentorProfile)
                .studentProfile(studentProfile)
                .build();
    }

    private MentorVerificationTimelineEventResponse mapTimelineEventResponse(MentorVerificationRequestEvent event) {
        UserSummaryRecord actor = event.getActorUserId() != null
                ? userQueryPort.findUserSummaryById(event.getActorUserId()).orElse(null)
                : null;
        return MentorVerificationTimelineEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .actorUserId(event.getActorUserId())
                .actorEmail(actor == null ? null : actor.email())
                .actorFullName(actor == null ? null : actor.fullName())
                .note(event.getNote())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private void appendEvent(
            MentorVerificationRequest request,
            MentorVerificationEventType eventType,
            UUID actorUserId,
            VerificationStatus fromStatus,
            VerificationStatus toStatus,
            String note
    ) {
        mentorVerificationRequestEventRepository.save(MentorVerificationRequestEvent.builder()
                .request(request)
                .eventType(eventType)
                .actorUserId(actorUserId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .note(trimToNull(note))
                .build());
    }

    private void claimLockIfAvailable(MentorVerificationRequest request, UUID adminUserId) {
        if (request == null || adminUserId == null || request.getStatus() != VerificationStatus.PENDING_REVIEW) {
            return;
        }
        if (!hasActiveLock(request) || isLockedBy(request, adminUserId)) {
            request.setLockedByUserId(adminUserId);
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
        UUID lockedByUserId = activeLock ? request.getLockedByUserId() : null;
        UserSummaryRecord lockedBy = lockedByUserId != null ? userQueryPort.findUserSummaryById(lockedByUserId).orElse(null) : null;
        LocalDateTime lockExpiresAt = activeLock ? request.getLockExpiresAt() : null;
        long secondsRemaining = lockExpiresAt == null
                ? 0L
                : Math.max(0L, Duration.between(DateTimeUtil.now(), lockExpiresAt).toSeconds());

        return AdminMentorVerificationLockResponse.builder()
                .requestId(request.getId())
                .locked(activeLock)
                .canReview(activeLock && isLockedBy(request, adminUserId))
                .lockedByAdminId(lockedBy == null ? null : lockedBy.userId())
                .lockedByAdminEmail(lockedBy == null ? null : lockedBy.email())
                .lockedByAdminFullName(lockedBy == null ? null : lockedBy.fullName())
                .lockedAt(activeLock ? request.getLockedAt() : null)
                .lockExpiresAt(lockExpiresAt)
                .secondsRemaining(secondsRemaining)
                .build();
    }

    private boolean hasActiveLock(MentorVerificationRequest request) {
        return request.getLockedByUserId() != null
                && request.getLockExpiresAt() != null
                && request.getLockExpiresAt().isAfter(DateTimeUtil.now());
    }

    private boolean isLockedBy(MentorVerificationRequest request, UUID adminUserId) {
        return request.getLockedByUserId() != null && request.getLockedByUserId().equals(adminUserId);
    }

    private void clearLock(MentorVerificationRequest request) {
        request.setLockedByUserId(null);
        request.setLockedAt(null);
        request.setLockExpiresAt(null);
    }

    private MentorVerificationChecklistResponse buildChecklist(UUID userId, List<MentorVerificationDocumentResponse> documents) {
        boolean hasAcademicProfile = academicService.hasCompletedStudentProfile(userId);
        boolean hasMentorProfile = mentorProfileService.hasCompletedMentorProfile(userId);
        boolean studentProfileEligible = !requireCompletedStudentProfile || hasAcademicProfile;
        boolean mentorProfileEligible = !requireCompletedMentorProfile || hasMentorProfile;
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
                .canSubmit(studentProfileEligible && mentorProfileEligible && hasAffiliationProof && hasExpertiseProof)
                .build();
    }

    private void ensureApprovalEligible(MentorVerificationRequest request) {
        List<MentorVerificationDocumentResponse> documents = mentorVerificationDocumentRepository
                .findByRequestIdOrderByUploadedAtAsc(request.getId())
                .stream()
                .map(this::mapDocumentResponse)
                .toList();
        MentorVerificationChecklistResponse checklist = buildChecklist(request.getMentorUserId(), documents);
        if (requireCompletedStudentProfile && !checklist.academicProfileCompleted()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể duyệt hồ sơ khi người dùng chưa hoàn tất hồ sơ học thuật");
        }
        if (requireCompletedMentorProfile && !checklist.mentorProfileCompleted()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể duyệt hồ sơ khi người dùng chưa hoàn tất hồ sơ mentor");
        }
        if (!checklist.hasAffiliationProof()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể duyệt hồ sơ khi thiếu minh chứng liên kết FPTU");
        }
        if (!checklist.hasExpertiseProof()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể duyệt hồ sơ khi thiếu minh chứng chuyên môn");
        }
    }

    @Override
    public boolean existsById(UUID verificationRequestId) {
        return verificationRequestId != null && mentorVerificationRequestRepository.existsById(verificationRequestId);
    }

    private MentorVerificationDocumentResponse mapDocumentResponse(MentorVerificationDocument document) {
        return MentorVerificationDocumentResponse.builder()
                .id(document.getId())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .storageKind(document.getStorageKind())
                .originalFilename(document.getOriginalFilename())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .fileUrl(document.getFileUrl())
                .isActive(document.isActive())
                .version(document.getVersion())
                .reviewNote(document.getReviewNote())
                .rejectedReason(document.getRejectedReason())
                .uploadedAt(document.getUploadedAt())
                .build();
    }

    private String normalizeRequiredReviewText(String raw, String emptyMessage) {
        if (!StringUtils.hasText(raw)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, emptyMessage);
        }
        String normalized = raw.trim();
        if (normalized.length() > MAX_REVIEW_NOTE_LENGTH) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Ghi chú không được vượt quá " + MAX_REVIEW_NOTE_LENGTH + " ký tự");
        }
        return normalized;
    }

    private String normalizeOptionalReviewText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.length() > MAX_REVIEW_NOTE_LENGTH) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Ghi chú không được vượt quá " + MAX_REVIEW_NOTE_LENGTH + " ký tự");
        }
        return normalized;
    }

    private void publishMentorVerificationNotification(
            UUID recipientId,
            NotificationType type,
            String title,
            String message,
            UUID targetId
    ) {
        eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.notification.NotificationEvent(
                recipientId,
                type,
                title,
                message,
                "MENTOR_VERIFICATION",
                targetId
        ));
    }

    private void publishMentorVerificationEmail(
            MentorVerificationEmailNotificationEvent.EventType eventType,
            MentorVerificationRequest request,
            UserSummaryRecord reviewer,
            String note
    ) {
        UserSummaryRecord mentor = userQueryPort.findUserSummaryById(request.getMentorUserId()).orElse(null);
        if (mentor == null || mentor.email() == null || mentor.email().isBlank()) {
            return;
        }
        eventPublisher.publishEvent(MentorVerificationEmailNotificationEvent.builder()
                .eventType(eventType)
                .recipientEmail(mentor.email())
                .recipientName(mentor.fullName())
                .reviewerName(reviewer != null ? reviewer.fullName() : null)
                .reviewNote(note)
                .requestId(request.getId())
                .submittedAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .build());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
