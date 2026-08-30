package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityPort;
import com.fptu.exe.skillswap.modules.mentor.domain.*;
import com.fptu.exe.skillswap.modules.mentor.dto.request.*;
import com.fptu.exe.skillswap.modules.mentor.dto.response.*;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationUploadIntentRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorVerificationService {

    private static final Set<VerificationStatus> ACTIVE_REQUEST_STATUSES = EnumSet.of(
            VerificationStatus.DRAFT,
            VerificationStatus.PENDING_REVIEW,
            VerificationStatus.NEEDS_REVISION
    );
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final long MAX_AFFILIATION_PROOF_FILES = 1;
    private static final long MAX_EXPERTISE_PROOF_FILES = 3;
    private static final long MAX_DOCUMENT_SIZE_BYTES = 15L * 1024L * 1024L;

    @Value("${application.mentor-verification.terms-version:SKILLSWAP_MENTOR_TERMS_V1}")
    private String mentorTermsVersion = "SKILLSWAP_MENTOR_TERMS_V1";

    @Value("${application.mentor-verification.submit-requirements.student-profile-completed:true}")
    private boolean requireCompletedStudentProfile = true;

    @Value("${application.mentor-verification.submit-requirements.mentor-profile-completed:true}")
    private boolean requireCompletedMentorProfile = true;

    @Value("${application.storage.documents-prefix:skillswap/verification-documents}")
    private String verificationDocumentsPrefix = "skillswap/verification-documents";

    @Value("${application.mentor-verification.review-target-hours:48}")
    private int reviewTargetHours = 48;

    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final AcademicService academicService;
    private final MentorProfileService mentorProfileService;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final ObjectProvider<StorageGateway> r2StorageProvider;
    private final MentorVerificationUploadIntentRepository uploadIntentRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final BookingAvailabilityPort bookingAvailabilityPort;
    
    @Transactional
    public MentorVerificationRequestActionResult<MentorVerificationRequestResponse> requestToBecomeMentor(UUID userId) {
        requireUserId(userId);
        User user = getRequiredUserForUpdate(userId);
        Optional<MentorVerificationRequest> activeRequest = findActiveRequest(userId);
        if (activeRequest.isPresent()) {
            return new MentorVerificationRequestActionResult<>(buildResponse(activeRequest.get()), false);
        }
        MentorProfile mentorProfile = ensureMentorProfileExists(user);
        ensureMentorCanOpenVerificationRequest(mentorProfile);
        MentorVerificationRequest previousRequest = findLatestRequest(userId)
                .filter(existing -> existing.getStatus() == VerificationStatus.REJECTED
                        || existing.getStatus() == VerificationStatus.WITHDRAWN)
                .orElse(null);
        try {
            MentorVerificationRequest request = createDraftRequest(user, previousRequest);
            return new MentorVerificationRequestActionResult<>(buildResponse(request), true);
        } catch (DataIntegrityViolationException ex) {
            MentorVerificationRequest existingRequest = findActiveRequest(userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đang có một hồ sơ xác thực mentor đang hoạt động"));
            return new MentorVerificationRequestActionResult<>(buildResponse(existingRequest), false);
        }
    }

    @Transactional(readOnly = true)
    public MentorVerificationRequestResponse getMyRequest(UUID userId) {
        requireUserId(userId);
        MentorVerificationRequest request = findLatestRequest(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor nào"));
        return buildResponse(request);
    }

    /**
     * Một read model duy nhất cho wizard. Approval và khả năng nhận booking là hai trạng thái
     * khác nhau để user không phải đoán vì sao profile chưa xuất hiện trên Discovery.
     */
    @Transactional(readOnly = true)
    public MentorVerificationProgressResponse getMyProgress(UUID userId) {
        requireUserId(userId);
        Optional<MentorVerificationRequest> request = findLatestRequest(userId);
        List<MentorVerificationDocumentResponse> documents = request
                .map(value -> mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(value.getId())
                        .stream().map(this::mapDocumentResponse).toList())
                .orElseGet(List::of);
        MentorVerificationChecklistResponse checklist = buildChecklist(userId, documents);
        MentorProfile profile = mentorProfileRepository.findWithUserByUserId(userId).orElse(null);
        boolean approved = request.map(value -> value.getStatus() == VerificationStatus.APPROVED).orElse(false);
        boolean hasActiveService = mentorServiceRepository.existsByMentorProfileUserIdAndIsActiveTrueAndDeliveryMode(
                userId, MentorServiceDeliveryMode.ONE_TO_ONE);
        LocalDateTime now = DateTimeUtil.now();
        boolean hasFutureSlot = mentorAvailabilitySlotRepository.findMentorUserIdsWithActiveSlotsInFuture(List.of(userId), DateTimeUtil.instantNow())
                .contains(userId);
        boolean offerReady = approved && hasFutureSlot && bookingEligibilityPolicy
                .isPublicBookingOfferAvailable(profile, hasActiveService, now);

        List<MentorVerificationProgressResponse.Step> submissionSteps = List.of(
                step("ACADEMIC_PROFILE", checklist.academicProfileCompleted(), true, false,
                        "/me/academic-profile", "Hoàn tất thông tin học thuật để admin xác minh danh tính sinh viên."),
                step("MENTOR_PROFILE", checklist.mentorProfileCompleted(), true, false,
                        "/me/mentor-profile", "Hoàn tất hồ sơ mentor và các môn học/kết quả tiêu biểu."),
                step("AFFILIATION_PROOF", checklist.hasAffiliationProof(), true, false,
                        "/me/mentor-verification", "Tải ít nhất một minh chứng liên kết với trường."),
                step("EXPERTISE_PROOF", checklist.hasExpertiseProof(), true, false,
                        "/me/mentor-verification", "Tải ít nhất một minh chứng năng lực mentoring."),
                step("MENTOR_TERMS", request.map(this::hasAcceptedCurrentTerms).orElse(false), true, false,
                        "/me/mentor-verification", "Đọc và đồng ý điều khoản mentor trước khi nộp hồ sơ.")
        );
        List<MentorVerificationProgressResponse.Step> activationSteps = List.of(
                step("VERIFICATION_APPROVED", approved, false, true,
                        "/me/mentor-verification", "Hồ sơ cần được admin phê duyệt trước khi nhận booking."),
                step("ACTIVE_SERVICE", hasActiveService, false, true,
                        "/me/mentor-services", "Tạo và bật ít nhất một dịch vụ mentoring sau khi được duyệt."),
                step("PUBLIC_AVAILABILITY", hasFutureSlot, false, true,
                        "/me/availability-slots", "Thêm ít nhất một khung giờ rảnh trong tương lai để mentee có thể đặt lịch.")
        );

        MentorVerificationRequest latest = request.orElse(null);
        return new MentorVerificationProgressResponse(
                latest == null ? null : latest.getId(),
                latest == null ? "NOT_STARTED" : latest.getStatus().name(),
                latest == null ? null : latest.getSubmittedAt(),
                estimatedReviewBy(latest),
                reviewTargetHours,
                isReviewOverdue(latest, now),
                submissionSteps,
                activationSteps,
                resolveNextAction(latest, checklist, approved, hasActiveService, hasFutureSlot, offerReady)
        );
    }

    @Transactional(readOnly = true)
    public List<MentorVerificationTimelineEventResponse> getTimeline(UUID userId) {
        requireUserId(userId);
        MentorVerificationRequest request = findLatestRequest(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor nào"));
        return mentorVerificationRequestEventRepository
                .findByRequestIdOrderByCreatedAtAsc(request.getId())
                .stream()
                .map(this::mapTimelineEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorVerificationDocumentResponse getDocument(UUID userId, UUID documentId) {
        requireUserId(userId);
        if (documentId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã tài liệu không được để trống");
        }

        MentorVerificationRequest request = findLatestRequest(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor nào"));
        MentorVerificationDocument document = mentorVerificationDocumentRepository.findByIdAndRequestId(documentId, request.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu xác thực"));
        return mapDocumentResponse(document);
    }

    @Transactional
    public MentorVerificationDocumentUploadIntentResponse createDocumentUploadIntent(
            UUID userId,
            MentorVerificationDocumentUploadIntentRequest request
    ) {
        requireUserId(userId);
        findEditableRequestForUpdate(userId);
        validateUploadIntentRequest(request);

        User user = getRequiredUser(userId);
        String contentType = canonicalizeContentType(request.contentType());
        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);
        MentorVerificationUploadIntent intent = uploadIntentRepository.save(MentorVerificationUploadIntent.builder()
                .owner(user)
                .storageKey(verificationObjectPrefix(userId) + UUID.randomUUID() + extensionOf(request.filename()))
                .originalFilename(sanitizeFilename(request.filename()))
                .expectedContentType(contentType)
                .expectedSizeBytes(request.sizeBytes())
                .expiresAt(expiresAt)
                .build());
        StorageGateway.PrivatePresignedUpload upload = getRequiredStorageGateway()
                .generatePrivateUploadUrl(intent.getStorageKey(), contentType, java.time.Duration.ofMinutes(15));
        return new MentorVerificationDocumentUploadIntentResponse(
                intent.getId(), upload.uploadUrl(), upload.expiresAt(), java.util.Map.of("Content-Type", contentType), intent.getStatus());
    }

    @Transactional
    public MentorVerificationUploadIntentStatusResponse getDocumentUploadIntentStatus(UUID userId, UUID uploadIntentId) {
        requireUserId(userId);
        MentorVerificationUploadIntent intent = getOwnedUploadIntentForUpdate(userId, uploadIntentId);
        if (intent.getStatus() == MentorVerificationUploadIntentStatus.PENDING_UPLOAD
                && !intent.getExpiresAt().isAfter(DateTimeUtil.now())) {
            intent.setStatus(MentorVerificationUploadIntentStatus.EXPIRED);
        }
        return mapUploadIntentStatus(intent);
    }

    @Transactional
    public MentorVerificationDocumentUploadIntentResponse retryDocumentUploadIntent(UUID userId, UUID uploadIntentId) {
        requireUserId(userId);
        MentorVerificationUploadIntent expiredIntent = getOwnedUploadIntentForUpdate(userId, uploadIntentId);
        if (expiredIntent.getStatus() == MentorVerificationUploadIntentStatus.PENDING_UPLOAD
                && !expiredIntent.getExpiresAt().isAfter(DateTimeUtil.now())) {
            expiredIntent.setStatus(MentorVerificationUploadIntentStatus.EXPIRED);
        }
        if (expiredIntent.getStatus() != MentorVerificationUploadIntentStatus.EXPIRED
                && expiredIntent.getStatus() != MentorVerificationUploadIntentStatus.REJECTED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Upload intent hiện tại chưa cần tạo lại");
        }
        return createDocumentUploadIntent(userId, new MentorVerificationDocumentUploadIntentRequest(
                expiredIntent.getOriginalFilename(), expiredIntent.getExpectedContentType(), expiredIntent.getExpectedSizeBytes()));
    }

    @Transactional
    public MentorVerificationRequestResponse uploadDocument(
            UUID userId,
            MentorVerificationDocumentUploadRequest uploadRequest
    ) {
        requireUserId(userId);
        validateUploadInput(uploadRequest);
        MentorVerificationUploadIntent existingIntent = getOwnedUploadIntentForUpdate(userId, uploadRequest.uploadIntentId());
        if (existingIntent.getStatus() == MentorVerificationUploadIntentStatus.CONFIRMED
                && existingIntent.getConfirmedStoredFile() != null) {
            MentorVerificationDocument existingDocument = mentorVerificationDocumentRepository
                    .findByStoredFileId(existingIntent.getConfirmedStoredFile().getId())
                    .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT,
                            "Upload intent đã xác nhận nhưng chưa có tài liệu tương ứng"));
            if (existingDocument.getDocumentType() != uploadRequest.documentType()) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Upload intent đã được dùng cho loại minh chứng khác");
            }
            return buildResponse(existingDocument.getRequest());
        }

        MentorVerificationRequest verificationRequest = findEditableRequestForUpdate(userId);
        enforceDocumentCountLimit(verificationRequest.getId(), uploadRequest.documentType());

        User user = getRequiredUser(userId);
        StoredFile storedFile = storeVerificationFile(user, uploadRequest);

        int nextVersion = mentorVerificationDocumentRepository
                .findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(verificationRequest.getId(), uploadRequest.documentType())
                .stream()
                .map(MentorVerificationDocument::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .request(verificationRequest)
                .documentType(uploadRequest.documentType())
                .status(VerificationDocumentStatus.UPLOADED)
                .storageKind(resolveStorageKind(storedFile.getMimeType()))
                .storedFile(storedFile)
                .isActive(true)
                .version(nextVersion)
                .uploadedBy(user)
                .build();

        mentorVerificationDocumentRepository.save(document);
        return buildResponse(verificationRequest);
    }

    @Transactional
    public MentorVerificationRequestResponse submit(UUID userId, MentorVerificationSubmitRequest submitRequest) {
        requireUserId(userId);
        if (submitRequest == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu nộp hồ sơ không được để trống");
        }
        MentorVerificationRequest request = findEditableRequestForUpdate(userId);
        boolean wasNeedsRevision = request.getStatus() == VerificationStatus.NEEDS_REVISION;
        VerificationStatus previousStatus = request.getStatus();
        ensureSubmissionEligible(userId, request);
        ensureTermsAccepted(submitRequest, request);

        request.setSubmittedNote(trimToNull(submitRequest.submitNote()));
        request.setStatus(VerificationStatus.PENDING_REVIEW);
        request.setSubmittedAt(DateTimeUtil.now());
        if (!hasAcceptedCurrentTerms(request)) {
            request.setTermsAcceptedAt(DateTimeUtil.now());
            request.setTermsVersion(mentorTermsVersion);
        }
        if (wasNeedsRevision) {
            request.setRevisionCount(request.getRevisionCount() + 1);
        }
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                wasNeedsRevision ? MentorVerificationEventType.RESUBMITTED : MentorVerificationEventType.SUBMITTED,
                request.getMentor(),
                previousStatus,
                VerificationStatus.PENDING_REVIEW,
                request.getSubmittedNote()
        );

        MentorProfile mentorProfile = ensureMentorProfileExists(request.getMentor());
        mentorProfile.setStatus(MentorStatus.PENDING_VERIFICATION);
        mentorProfileRepository.save(mentorProfile);

        return buildResponse(request);
    }

    @Transactional
    public MentorVerificationRequestResponse deleteDocument(UUID userId, UUID documentId) {
        requireUserId(userId);
        if (documentId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã tài liệu không được để trống");
        }

        MentorVerificationRequest request = findEditableRequestForUpdate(userId);
        MentorVerificationDocument document = mentorVerificationDocumentRepository.findByIdAndRequestId(documentId, request.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu xác thực"));

        if (!document.isActive() || document.getStatus() == VerificationDocumentStatus.REMOVED) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tài liệu này đã bị xóa hoặc không còn khả dụng");
        }

        document.setActive(false);
        document.setStatus(VerificationDocumentStatus.REMOVED);
        mentorVerificationDocumentRepository.save(document);

        return buildResponse(request);
    }

    @Transactional
    public MentorVerificationRequestResponse withdraw(UUID userId) {
        requireUserId(userId);
        getRequiredUserForUpdate(userId);
        UUID latestRequestId = findLatestRequest(userId)
                .map(MentorVerificationRequest::getId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor nào"));
        MentorVerificationRequest request = mentorVerificationRequestRepository.findByIdForUpdate(latestRequestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor để rút"));

        if (request.getStatus() != VerificationStatus.DRAFT
                && request.getStatus() != VerificationStatus.NEEDS_REVISION
                && request.getStatus() != VerificationStatus.PENDING_REVIEW) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Hồ sơ hiện tại không cho phép rút");
        }
        if (request.getStatus() == VerificationStatus.PENDING_REVIEW && hasActiveAdminLock(request)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Hồ sơ đang được admin xử lý, hiện chưa thể rút");
        }

        VerificationStatus previousStatus = request.getStatus();
        clearAdminLock(request);
        request.setStatus(VerificationStatus.WITHDRAWN);
        request.setWithdrawnAt(DateTimeUtil.now());
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.WITHDRAWN,
                request.getMentor(),
                previousStatus,
                VerificationStatus.WITHDRAWN,
                null
        );

        mentorProfileRepository.findWithUserByUserId(userId)
                .ifPresent(profile -> {
                    if (profile.getStatus() == MentorStatus.PENDING_VERIFICATION) {
                        profile.setStatus(MentorStatus.DRAFT);
                        mentorProfileRepository.save(profile);
                    }
                });

        return buildResponse(request);
    }

    @Transactional
    public MentorVerificationRequestResponse unsubmit(UUID userId) {
        requireUserId(userId);
        getRequiredUserForUpdate(userId);
        UUID latestRequestId = findLatestRequest(userId)
                .map(MentorVerificationRequest::getId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor nào"));
        MentorVerificationRequest request = mentorVerificationRequestRepository.findByIdForUpdate(latestRequestId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor để thu hồi"));

        if (request.getStatus() != VerificationStatus.PENDING_REVIEW) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ có thể thu hồi hồ sơ khi đang chờ duyệt");
        }
        if (hasActiveAdminLock(request)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Hồ sơ đang được admin xử lý, hiện chưa thể thu hồi");
        }

        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.DRAFT);
        mentorVerificationRequestRepository.save(request);
        appendEvent(
                request,
                MentorVerificationEventType.UNSUBMITTED,
                request.getMentor(),
                previousStatus,
                VerificationStatus.DRAFT,
                "Người dùng chủ động thu hồi đơn để chỉnh sửa"
        );

        mentorProfileRepository.findWithUserByUserId(userId)
                .ifPresent(profile -> {
                    if (profile.getStatus() == MentorStatus.PENDING_VERIFICATION) {
                        profile.setStatus(MentorStatus.DRAFT);
                        mentorProfileRepository.save(profile);
                    }
                });

        return buildResponse(request);
    }

    private MentorVerificationRequest createDraftRequest(User user, MentorVerificationRequest previousRequest) {
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .mentor(user)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.DRAFT)
                .previousRequest(previousRequest)
                .build();
        MentorVerificationRequest savedRequest = mentorVerificationRequestRepository.save(request);
        appendEvent(savedRequest, MentorVerificationEventType.REQUEST_CREATED, user, null, VerificationStatus.DRAFT, null);

        if (previousRequest != null) {
            List<MentorVerificationDocument> previousDocs = mentorVerificationDocumentRepository
                    .findByRequestIdOrderByUploadedAtAsc(previousRequest.getId());
            for (MentorVerificationDocument prevDoc : previousDocs) {
                if (prevDoc.isActive() && prevDoc.getStatus() == VerificationDocumentStatus.UPLOADED) {
                    MentorVerificationDocument clonedDoc = MentorVerificationDocument.builder()
                            .request(savedRequest)
                            .documentType(prevDoc.getDocumentType())
                            .status(VerificationDocumentStatus.UPLOADED)
                            .storageKind(prevDoc.getStorageKind())
                            .storedFile(prevDoc.getStoredFile())
                            .isActive(true)
                            .version(prevDoc.getVersion())
                            .uploadedBy(prevDoc.getUploadedBy())
                            .build();
                    mentorVerificationDocumentRepository.save(clonedDoc);
                }
            }
        }

        return savedRequest;
    }

    private Optional<MentorVerificationRequest> findActiveRequest(UUID userId) {
        requireUserId(userId);
        return mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(
                userId,
                ACTIVE_REQUEST_STATUSES
        );
    }

    private Optional<MentorVerificationRequest> findLatestRequest(UUID userId) {
        requireUserId(userId);
        return mentorVerificationRequestRepository.findFirstByMentorIdOrderByCreatedAtDesc(userId);
    }

    private MentorVerificationRequest findEditableRequestForUpdate(UUID userId) {
        MentorVerificationRequest request = findActiveRequest(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor để cập nhật"));
        request = mentorVerificationRequestRepository.findByIdForUpdate(request.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor để cập nhật"));
        if (request.getStatus() != VerificationStatus.DRAFT && request.getStatus() != VerificationStatus.NEEDS_REVISION) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Hồ sơ hiện tại không cho phép chỉnh sửa tài liệu");
        }
        return request;
    }

    private void ensureSubmissionEligible(UUID userId, MentorVerificationRequest request) {
        if (request == null || request.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Hồ sơ xác thực mentor không hợp lệ");
        }
        if (requireCompletedStudentProfile && !academicService.hasCompletedStudentProfile(userId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cần hoàn tất hồ sơ học thuật trước khi nộp xác thực mentor");
        }
        if (requireCompletedMentorProfile && !mentorProfileService.hasCompletedMentorProfile(userId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cần hoàn tất hồ sơ mentor trước khi nộp xác thực mentor");
        }
        long affiliationProofCount = mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(
                request.getId(),
                VerificationDocumentType.FPTU_AFFILIATION_PROOF
        );
        long expertiseProofCount = mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(
                request.getId(),
                VerificationDocumentType.EXPERTISE_PROOF
        );
        if (affiliationProofCount == 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cần tải lên ít nhất một minh chứng FPTU");
        }
        if (expertiseProofCount == 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cần tải lên ít nhất một minh chứng năng lực mentoring");
        }
        mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(
                        userId,
                        List.of(VerificationStatus.PENDING_REVIEW)
                )
                .filter(existing -> !existing.getId().equals(request.getId()))
                .ifPresent(existing -> {
                    throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đang có một hồ sơ chờ admin duyệt");
                });
    }

    private StoredFile storeVerificationFile(User user, MentorVerificationDocumentUploadRequest request) {
        if (user == null || user.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể xác định người dùng tải tài liệu");
        }
        MentorVerificationUploadIntent intent = uploadIntentRepository.findByIdForUpdate(request.uploadIntentId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent"));
        if (!intent.getOwner().getId().equals(user.getId())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent");
        }
        if (intent.getStatus() == MentorVerificationUploadIntentStatus.CONFIRMED || intent.getConfirmedStoredFile() != null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Upload intent đã được xác nhận");
        }
        if (intent.getStatus() != MentorVerificationUploadIntentStatus.PENDING_UPLOAD || intent.getExpiresAt().isBefore(DateTimeUtil.now())) {
            intent.setStatus(MentorVerificationUploadIntentStatus.EXPIRED);
            throw new BaseException(ErrorCode.BAD_REQUEST, "Upload intent không còn hợp lệ");
        }
        String objectKey = intent.getStorageKey();
        String originalFilename = intent.getOriginalFilename();
        String contentType = intent.getExpectedContentType();
        StorageGateway storageGateway = getRequiredStorageGateway();
        StorageGateway.ObjectMetadata objectMetadata = storageGateway.headObject(objectKey);
        String uploadedContentType = canonicalizeContentType(objectMetadata.contentType());
        if (!contentType.equals(uploadedContentType)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "contentType xác nhận không khớp file đã upload");
        }
        if (objectMetadata.sizeBytes() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Kích thước file đã upload không được vượt quá 15MB");
        }
        // sizeBytes == 0 is the local-profile sentinel for "unknown size" (file not on disk).
        // Skip the size check in that case; production storage always returns the actual size.
        if (objectMetadata.sizeBytes() > 0 && objectMetadata.sizeBytes() != intent.getExpectedSizeBytes()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "sizeBytes xác nhận không khớp file đã upload");
        }
        StoredFile storedFile = storedFileRepository.save(StoredFile.builder()
                .owner(user)
                .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                .originalName(originalFilename)
                .storageProvider(storageGateway.storageProviderName())
                .storageKey(objectKey)
                .publicUrl("private://" + objectKey)
                .mimeType(contentType)
                .sizeBytes(objectMetadata.sizeBytes() > 0 ? objectMetadata.sizeBytes() : intent.getExpectedSizeBytes())
                .build());
        intent.setConfirmedStoredFile(storedFile);
        intent.setConfirmedAt(DateTimeUtil.now());
        intent.setStatus(MentorVerificationUploadIntentStatus.CONFIRMED);
        uploadIntentRepository.save(intent);
        return storedFile;
    }

    private void validateUploadInput(MentorVerificationDocumentUploadRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu tài liệu không được để trống");
        }
        if (request.documentType() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại tài liệu xác thực là bắt buộc");
        }
        if (request.uploadIntentId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "uploadIntentId không được để trống");
        }
    }

    private void validateUploadIntentRequest(MentorVerificationDocumentUploadIntentRequest request) {
        if (request == null || !StringUtils.hasText(request.filename()) || !StringUtils.hasText(request.contentType())
                || request.sizeBytes() == null || request.sizeBytes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu upload intent không hợp lệ");
        }
        if (request.filename().contains("/") || request.filename().contains("\\") || sanitizeFilename(request.filename()).isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tên file không hợp lệ");
        }
        if (request.sizeBytes() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Kích thước file không được vượt quá 15MB");
        }
        if (!SUPPORTED_CONTENT_TYPES.contains(canonicalizeContentType(request.contentType()))) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ file JPG, PNG hoặc PDF");
        }
    }

    private String verificationObjectPrefix(UUID userId) {
        return verificationDocumentsPrefix.replaceAll("^/+|/+$", "") + "/users/" + userId + "/";
    }

    private void enforceDocumentCountLimit(UUID requestId, VerificationDocumentType documentType) {
        if (requestId == null || documentType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể kiểm tra số lượng tài liệu do dữ liệu đầu vào không hợp lệ");
        }
        long count = mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(requestId, documentType);
        long limit = maxFilesFor(documentType);
        if (count >= limit) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại tài liệu " + documentType + " chỉ được tối đa " + limit + " file đang hoạt động");
        }
    }

    private long maxFilesFor(VerificationDocumentType documentType) {
        if (documentType == VerificationDocumentType.FPTU_AFFILIATION_PROOF) {
            return MAX_AFFILIATION_PROOF_FILES;
        }
        if (documentType == VerificationDocumentType.EXPERTISE_PROOF) {
            return MAX_EXPERTISE_PROOF_FILES;
        }
        throw new BaseException(ErrorCode.BAD_REQUEST, "Loại tài liệu xác thực không được hỗ trợ");
    }

    private MentorProfile ensureMentorProfileExists(User user) {
        if (user == null || user.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể khởi tạo hồ sơ mentor do người dùng không hợp lệ");
        }
        return mentorProfileRepository.findWithUserByUserId(user.getId())
                .orElseGet(() -> {
                    MentorProfile mentorProfile = new MentorProfile();
                    mentorProfile.setUser(user);
                    mentorProfile.setStatus(MentorStatus.DRAFT);
                    mentorProfile.setSessionDuration(60);
                    return mentorProfileRepository.save(mentorProfile);
                });
    }

    private User getRequiredUser(UUID userId) {
        requireUserId(userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private User getRequiredUserForUpdate(UUID userId) {
        requireUserId(userId);
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private MentorVerificationRequestResponse buildResponse(MentorVerificationRequest request) {
        List<MentorVerificationDocumentResponse> documents = mentorVerificationDocumentRepository
                .findByRequestIdOrderByUploadedAtAsc(request.getId())
                .stream()
                .map(this::mapDocumentResponse)
                .toList();

        MentorVerificationChecklistResponse checklist = buildChecklist(request.getMentor().getId(), documents);
        MentorVerificationAllowedActionsResponse allowedActions = buildAllowedActions(request.getStatus(), checklist.canSubmit());
        List<MentorVerificationTimelineEventResponse> timeline = mentorVerificationRequestEventRepository
                .findByRequestIdOrderByCreatedAtAsc(request.getId())
                .stream()
                .map(this::mapTimelineEventResponse)
                .toList();

        return MentorVerificationRequestResponse.builder()
                .requestId(request.getId())
                .mentorUserId(request.getMentor().getId())
                .status(request.getStatus())
                .submitNote(request.getSubmittedNote())
                .reviewNote(request.getReviewNote())
                .rejectionReason(request.getRejectionReason())
                .revisionCount(request.getRevisionCount())
                .submittedAt(request.getSubmittedAt())
                .estimatedReviewBy(estimatedReviewBy(request))
                .reviewTargetHours(reviewTargetHours)
                .reviewOverdue(isReviewOverdue(request, DateTimeUtil.now()))
                .termsAcceptedAt(request.getTermsAcceptedAt())
                .termsVersion(request.getTermsVersion())
                .reviewedAt(request.getReviewedAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .documents(documents)
                .timeline(timeline)
                .checklist(checklist)
                .allowedActions(allowedActions)
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

    private void ensureTermsAccepted(MentorVerificationSubmitRequest submitRequest, MentorVerificationRequest request) {
        if (hasAcceptedCurrentTerms(request)) {
            return;
        }
        if (!Boolean.TRUE.equals(submitRequest.termsAccepted())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cần xác nhận đã đọc và đồng ý điều khoản mentor của SkillSwap trước khi nộp hồ sơ");
        }
    }

    private boolean hasAcceptedCurrentTerms(MentorVerificationRequest request) {
        return request != null
                && request.getTermsAcceptedAt() != null
                && StringUtils.hasText(request.getTermsVersion())
                && request.getTermsVersion().equals(mentorTermsVersion);
    }

    private MentorVerificationAllowedActionsResponse buildAllowedActions(VerificationStatus status, boolean canSubmit) {
        boolean editable = status == VerificationStatus.DRAFT || status == VerificationStatus.NEEDS_REVISION;
        boolean withdrawable = editable || status == VerificationStatus.PENDING_REVIEW;
        boolean unsubmittable = status == VerificationStatus.PENDING_REVIEW;
        return MentorVerificationAllowedActionsResponse.builder()
                .canUploadDocuments(editable)
                .canSubmit(editable && canSubmit)
                .canWithdraw(withdrawable)
                .canUnsubmit(unsubmittable)
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

    private VerificationStorageKind resolveStorageKind(String rawContentType) {
        String contentType = canonicalizeContentType(rawContentType);
        if ("application/pdf".equals(contentType)) {
            return VerificationStorageKind.DOCUMENT;
        }
        if (SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            return VerificationStorageKind.IMAGE;
        }
        throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ file JPG, PNG hoặc PDF");
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase();
    }

    private String canonicalizeContentType(String contentType) {
        String normalized = normalizeContentType(contentType);
        if ("image/jpg".equals(normalized) || "image/pjpeg".equals(normalized) || "image/jpe".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }

    private String extensionOf(String filename) {
        String sanitized = sanitizeFilename(filename);
        int extensionIndex = sanitized.lastIndexOf('.');
        return extensionIndex >= 0 ? sanitized.substring(extensionIndex).toLowerCase(java.util.Locale.ROOT) : "";
    }

    /**
     * Strips Unix and Windows path separators from a client-supplied filename so that the
     * value stored in the database is a plain filename with no directory components.
     * The file is never written to local disk in this flow, but keeping the stored name clean
     * makes admin review safer and avoids unexpected values if the field is later used for
     * Content-Disposition headers.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        // Replace both forward slash and backslash, then trim the result
        return filename.replace("/", "").replace("\\", "").trim();
    }

    private void ensureMentorCanOpenVerificationRequest(MentorProfile mentorProfile) {
        if (mentorProfile == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể khởi tạo hồ sơ mentor");
        }
        if (mentorProfile.getStatus() == MentorStatus.ACTIVE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đã là mentor đã được xác thực");
        }
        if (mentorProfile.getStatus() == MentorStatus.PENDING_VERIFICATION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Hồ sơ mentor của bạn đang chờ xác thực");
        }
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasActiveAdminLock(MentorVerificationRequest request) {
        return request.getLockedBy() != null
                && request.getLockExpiresAt() != null
                && request.getLockExpiresAt().isAfter(DateTimeUtil.now());
    }

    private void clearAdminLock(MentorVerificationRequest request) {
        request.setLockedBy(null);
        request.setLockedAt(null);
        request.setLockExpiresAt(null);
    }

    @Transactional(readOnly = true)
    public String getLatestVerificationStatus(UUID userId) {
        return findLatestRequest(userId)
                .map(req -> req.getStatus().name())
                .orElse("NOT_STARTED");
    }

    private MentorVerificationProgressResponse.Step step(
            String code,
            boolean completed,
            boolean requiredForSubmission,
            boolean requiredForBookingOffer,
            String actionPath,
            String message
    ) {
        return new MentorVerificationProgressResponse.Step(
                code, completed, requiredForSubmission, requiredForBookingOffer, actionPath, message);
    }

    private LocalDateTime estimatedReviewBy(MentorVerificationRequest request) {
        if (request == null || request.getStatus() != VerificationStatus.PENDING_REVIEW || request.getSubmittedAt() == null) {
            return null;
        }
        return request.getSubmittedAt().plusHours(Math.max(1, reviewTargetHours));
    }

    private boolean isReviewOverdue(MentorVerificationRequest request, LocalDateTime now) {
        LocalDateTime estimatedReviewBy = estimatedReviewBy(request);
        return estimatedReviewBy != null && now != null && now.isAfter(estimatedReviewBy);
    }

    private MentorVerificationProgressResponse.NextAction resolveNextAction(
            MentorVerificationRequest request,
            MentorVerificationChecklistResponse checklist,
            boolean approved,
            boolean hasActiveService,
            boolean hasFutureSlot,
            boolean offerReady
    ) {
        if (request == null || request.getStatus() == VerificationStatus.REJECTED || request.getStatus() == VerificationStatus.WITHDRAWN) {
            return new MentorVerificationProgressResponse.NextAction(
                    "OPEN_APPLICATION", "/api/me/mentor-verification/request", "Mở hồ sơ đăng ký mentor để bắt đầu hoặc nộp lại.");
        }
        if (request.getStatus() == VerificationStatus.PENDING_REVIEW) {
            return new MentorVerificationProgressResponse.NextAction(
                    "WAIT_FOR_REVIEW", null, "Hồ sơ đang được admin xem xét. Thời gian trả về là thời gian dự kiến, không phải cam kết.");
        }
        if (request.getStatus() == VerificationStatus.NEEDS_REVISION || !checklist.canSubmit()) {
            return new MentorVerificationProgressResponse.NextAction(
                    "COMPLETE_SUBMISSION", "/me/mentor-verification", "Hoàn tất các bước bắt buộc rồi nộp hồ sơ để admin duyệt.");
        }
        if (!approved) {
            return new MentorVerificationProgressResponse.NextAction(
                    "SUBMIT_APPLICATION", "/me/mentor-verification", "Hồ sơ đã đủ điều kiện, hãy xác nhận điều khoản và nộp để admin duyệt.");
        }
        if (!hasActiveService) {
            return new MentorVerificationProgressResponse.NextAction(
                    "CREATE_SERVICE", "/me/mentor-services", "Bạn đã được xác thực. Tạo dịch vụ đầu tiên để nhận booking.");
        }
        if (!hasFutureSlot) {
            return new MentorVerificationProgressResponse.NextAction(
                    "CREATE_AVAILABILITY", "/me/availability-slots", "Thêm lịch rảnh trong tương lai để mentee có thể đặt buổi mentoring.");
        }
        if (offerReady) {
            return new MentorVerificationProgressResponse.NextAction(
                    "BOOKING_OFFER_READY", "/me/mentor-profile", "Mentor đã sẵn sàng xuất hiện với khả năng nhận booking.");
        }
        return new MentorVerificationProgressResponse.NextAction(
                "CHECK_BOOKING_OFFER", "/me/mentor-profile", "Kiểm tra lại trạng thái nhận booking và lịch rảnh của bạn.");
    }

    private MentorVerificationUploadIntent getOwnedUploadIntentForUpdate(UUID userId, UUID uploadIntentId) {
        if (uploadIntentId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "uploadIntentId không được để trống");
        }
        MentorVerificationUploadIntent intent = uploadIntentRepository.findByIdForUpdate(uploadIntentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent"));
        if (intent.getOwner() == null || !userId.equals(intent.getOwner().getId())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent");
        }
        return intent;
    }

    private MentorVerificationUploadIntentStatusResponse mapUploadIntentStatus(MentorVerificationUploadIntent intent) {
        UUID confirmedDocumentId = intent.getConfirmedStoredFile() == null ? null : mentorVerificationDocumentRepository
                .findByStoredFileId(intent.getConfirmedStoredFile().getId())
                .map(MentorVerificationDocument::getId)
                .orElse(null);
        boolean canRetry = intent.getStatus() == MentorVerificationUploadIntentStatus.EXPIRED
                || intent.getStatus() == MentorVerificationUploadIntentStatus.REJECTED;
        return new MentorVerificationUploadIntentStatusResponse(
                intent.getId(), intent.getStatus(), intent.getExpiresAt(), canRetry, confirmedDocumentId);
    }

    private StorageGateway getRequiredStorageGateway() {
        StorageGateway storageGateway = r2StorageProvider.getIfAvailable();
        if (storageGateway == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình storage để upload minh chứng");
        }
        return storageGateway;
    }
}




