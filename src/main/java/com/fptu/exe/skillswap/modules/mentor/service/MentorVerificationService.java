package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityQueryPort;
import com.fptu.exe.skillswap.modules.filestorage.port.VerificationDocumentStoragePort;
import com.fptu.exe.skillswap.modules.identity.port.AcademicEligibilityQuery;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequestEvent;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationUploadIntent;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationUploadIntentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadIntentRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationRequestActionResult;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationSubmitRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationAllowedActionsResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationChecklistResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationDocumentUploadIntentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationProgressResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationUploadIntentStatusResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationUploadIntentRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorVerificationService {

    private static final List<VerificationStatus> ACTIVE_REQUEST_STATUSES = List.of(
            VerificationStatus.DRAFT,
            VerificationStatus.PENDING_REVIEW,
            VerificationStatus.NEEDS_REVISION
    );

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "application/pdf"
    );

    private static final long MAX_DOCUMENT_SIZE_BYTES = 15L * 1024 * 1024;

    @Value("${application.mentor-verification.terms-version:SKILLSWAP_MENTOR_TERMS_V1}")
    private String mentorTermsVersion = "SKILLSWAP_MENTOR_TERMS_V1";

    @Value("${application.mentor-verification.require-completed-student-profile:true}")
    private boolean requireCompletedStudentProfile = true;

    @Value("${application.mentor-verification.require-completed-mentor-profile:true}")
    private boolean requireCompletedMentorProfile = true;

    @Value("${application.storage.documents-prefix:skillswap/verification-documents}")
    private String documentsPrefix = "skillswap/verification-documents";

    @Value("${application.mentor-verification.review-target-hours:48}")
    private int reviewTargetHours = 48;

    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final AcademicEligibilityQuery academicEligibilityQuery;
    private final MentorProfileService mentorProfileService;
    private final UserQueryPort userQueryPort;
    private final VerificationDocumentStoragePort verificationDocumentStoragePort;
    private final MentorVerificationUploadIntentRepository uploadIntentRepository;
    private final ObjectProvider<StorageGateway> r2StorageProvider;
    private final BookingAvailabilityQueryPort bookingAvailabilityQueryPort;

    @Transactional
    public MentorVerificationRequestActionResult<MentorVerificationRequestResponse> requestToBecomeMentor(UUID userId) {
        requireUserId(userId);
        if (!userQueryPort.existsById(userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng");
        }

        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    MentorProfile profile = new MentorProfile();
                    profile.setUserId(userId);
                    profile.setAvailable(false);
                    profile.setStatus(MentorStatus.DRAFT);
                    return mentorProfileRepository.save(profile);
                });

        ensureMentorCanOpenVerificationRequest(mentorProfile);

        Optional<MentorVerificationRequest> activeRequest = findActiveRequest(userId);
        if (activeRequest.isPresent()) {
            return new MentorVerificationRequestActionResult<>(buildResponse(activeRequest.get()), false);
        }

        MentorVerificationRequest previousRequest = findLatestRequest(userId).orElse(null);
        MentorVerificationRequest request = createDraftRequest(userId, previousRequest);
        return new MentorVerificationRequestActionResult<>(buildResponse(request), true);
    }

    @Transactional(readOnly = true)
    public MentorVerificationRequestResponse getMyRequest(UUID userId) {
        requireUserId(userId);
        MentorVerificationRequest request = findActiveRequest(userId)
                .or(() -> findLatestRequest(userId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor nào"));
        return buildResponse(request);
    }

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
        boolean hasFutureSlot = bookingAvailabilityQueryPort.findMentorUserIdsWithActiveSlotsInFuture(List.of(userId), DateTimeUtil.instantNow())
                .contains(userId);
        boolean bookingSuspended = profile != null && profile.getBookingSuspendedUntil() != null
                && profile.getBookingSuspendedUntil().isAfter(now);
        boolean bookingOfferReady = approved && profile != null && Boolean.TRUE.equals(profile.isAvailable())
                && !bookingSuspended && hasActiveService && hasFutureSlot;

        List<MentorVerificationProgressResponse.Step> steps = List.of(
                step("ACADEMIC_PROFILE", checklist.academicProfileCompleted(), true, false, "/profile", "Hoàn tất thông tin học thuật"),
                step("MENTOR_PROFILE", checklist.mentorProfileCompleted(), true, false, "/me/mentor-profile", "Điền thông tin giới thiệu mentor"),
                step("AFFILIATION_PROOF", checklist.hasAffiliationProof(), true, false, "/me/mentor-verification", "Tải lên minh chứng sinh viên FPTU"),
                step("EXPERTISE_PROOF", checklist.hasExpertiseProof(), true, false, "/me/mentor-verification", "Tải lên minh chứng năng lực mentoring"),
                step("SUBMITTED", request.map(r -> r.getStatus() != VerificationStatus.DRAFT).orElse(false), true, false, "/me/mentor-verification", "Nộp hồ sơ để admin duyệt"),
                step("APPROVED", approved, false, true, null, "Admin phê duyệt hồ sơ"),
                step("ACTIVE_SERVICE", hasActiveService, false, true, "/me/mentor-services", "Tạo ít nhất một dịch vụ 1:1 đang hoạt động"),
                step("FUTURE_AVAILABILITY", hasFutureSlot, false, true, "/me/availability-slots", "Mở ít nhất một khung giờ rảnh trong tương lai")
        );

        LocalDateTime submittedAt = request.map(MentorVerificationRequest::getSubmittedAt).orElse(null);
        LocalDateTime estimatedReviewBy = estimatedReviewBy(request.orElse(null));
        boolean reviewOverdue = isReviewOverdue(request.orElse(null), now);
        MentorVerificationProgressResponse.NextAction nextAction = resolveNextAction(
                request.orElse(null), checklist, approved, hasActiveService, hasFutureSlot, bookingOfferReady);

        List<MentorVerificationProgressResponse.Step> submissionSteps = steps.stream()
                .filter(MentorVerificationProgressResponse.Step::requiredForSubmission)
                .toList();
        List<MentorVerificationProgressResponse.Step> activationSteps = steps.stream()
                .filter(MentorVerificationProgressResponse.Step::requiredForBookingOffer)
                .toList();

        return new MentorVerificationProgressResponse(
                request.map(MentorVerificationRequest::getId).orElse(null),
                request.map(r -> r.getStatus().name()).orElse("NOT_STARTED"),
                submittedAt,
                estimatedReviewBy,
                reviewTargetHours,
                reviewOverdue,
                submissionSteps,
                activationSteps,
                nextAction
        );
    }

    @Transactional(readOnly = true)
    public List<MentorVerificationTimelineEventResponse> getTimeline(UUID userId) {
        requireUserId(userId);
        MentorVerificationRequest request = findActiveRequest(userId)
                .or(() -> findLatestRequest(userId))
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor"));
        return mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())
                .stream()
                .map(this::mapTimelineEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorVerificationDocumentResponse getDocument(UUID userId, UUID documentId) {
        requireUserId(userId);
        if (documentId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã tài liệu không hợp lệ");
        }
        MentorVerificationDocument document = mentorVerificationDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu xác thực"));
        if (!document.getRequest().getMentorUserId().equals(userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu xác thực");
        }
        return mapDocumentResponse(document);
    }

    @Transactional
    public MentorVerificationDocumentUploadIntentResponse createDocumentUploadIntent(
            UUID userId,
            MentorVerificationDocumentUploadIntentRequest request
    ) {
        requireUserId(userId);
        if (!userQueryPort.existsById(userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng");
        }
        String contentType = canonicalizeContentType(request.contentType());
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ file JPG, PNG hoặc PDF");
        }
        if (request.sizeBytes() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Kích thước file không được vượt quá 15MB");
        }
        String filename = sanitizeFilename(request.filename());
        if (!StringUtils.hasText(filename)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tên file không được để trống");
        }

        findEditableRequestForUpdate(userId);

        StorageGateway storageGateway = getRequiredStorageGateway();
        UUID intentId = UUID.randomUUID();
        String extension = extensionOf(filename);
        String storageKey = documentsPrefix + "/" + userId + "/" + intentId + extension;
        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);

        MentorVerificationUploadIntent intent = MentorVerificationUploadIntent.builder()
                .id(intentId)
                .ownerUserId(userId)
                .storageKey(storageKey)
                .originalFilename(filename)
                .expectedContentType(contentType)
                .expectedSizeBytes(request.sizeBytes())
                .status(MentorVerificationUploadIntentStatus.PENDING_UPLOAD)
                .expiresAt(expiresAt)
                .build();
        uploadIntentRepository.save(intent);

        StorageGateway.PrivatePresignedUpload uploadAuth = storageGateway.generatePrivateUploadUrl(storageKey, contentType, Duration.ofMinutes(15));
        return new MentorVerificationDocumentUploadIntentResponse(
                intent.getId(),
                uploadAuth.uploadUrl(),
                uploadAuth.expiresAt(),
                Map.of("Content-Type", contentType),
                intent.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public MentorVerificationUploadIntentStatusResponse getDocumentUploadIntentStatus(UUID userId, UUID uploadIntentId) {
        requireUserId(userId);
        MentorVerificationUploadIntent intent = getOwnedUploadIntentForUpdate(userId, uploadIntentId);
        return mapUploadIntentStatus(intent);
    }

    @Transactional
    public MentorVerificationDocumentUploadIntentResponse retryDocumentUploadIntent(UUID userId, UUID uploadIntentId) {
        requireUserId(userId);
        MentorVerificationUploadIntent existingIntent = getOwnedUploadIntentForUpdate(userId, uploadIntentId);
        findEditableRequestForUpdate(userId);

        if (existingIntent.getStatus() == MentorVerificationUploadIntentStatus.CONFIRMED
                && existingIntent.getConfirmedStoredFileId() != null) {
            UUID documentId = mentorVerificationDocumentRepository
                    .findByStoredFileId(existingIntent.getConfirmedStoredFileId())
                    .map(MentorVerificationDocument::getId)
                    .orElse(null);
            if (documentId != null) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Upload intent đã được xác nhận thành công");
            }
        }

        StorageGateway storageGateway = getRequiredStorageGateway();
        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);
        existingIntent.setExpiresAt(expiresAt);
        existingIntent.setStatus(MentorVerificationUploadIntentStatus.PENDING_UPLOAD);
        uploadIntentRepository.save(existingIntent);

        StorageGateway.PrivatePresignedUpload uploadAuth = storageGateway.generatePrivateUploadUrl(
                existingIntent.getStorageKey(), existingIntent.getExpectedContentType(), Duration.ofMinutes(15));
        return new MentorVerificationDocumentUploadIntentResponse(
                existingIntent.getId(),
                uploadAuth.uploadUrl(),
                uploadAuth.expiresAt(),
                Map.of("Content-Type", existingIntent.getExpectedContentType()),
                existingIntent.getStatus()
        );
    }

    @Transactional
    public MentorVerificationRequestResponse uploadDocument(UUID userId, MentorVerificationDocumentUploadRequest uploadRequest) {
        requireUserId(userId);
        if (!userQueryPort.existsById(userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng");
        }
        VerificationDocumentStoragePort.VerificationDocumentMetadata storedFile = storeVerificationFile(userId, uploadRequest);
        MentorVerificationRequest verificationRequest = findEditableRequestForUpdate(userId);

        Optional<MentorVerificationDocument> activeDoc = mentorVerificationDocumentRepository
                .findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(verificationRequest.getId(), uploadRequest.documentType())
                .stream().findFirst();

        int nextVersion = 1;
        if (activeDoc.isPresent()) {
            MentorVerificationDocument existing = activeDoc.get();
            existing.setActive(false);
            mentorVerificationDocumentRepository.save(existing);
            nextVersion = existing.getVersion() + 1;
        }

        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .request(verificationRequest)
                .documentType(uploadRequest.documentType())
                .status(VerificationDocumentStatus.UPLOADED)
                .storageKind(resolveStorageKind(storedFile.contentType()))
                .storedFileId(storedFile.fileId())
                .originalFilename(storedFile.originalFilename())
                .contentType(storedFile.contentType())
                .sizeBytes(storedFile.sizeBytes())
                .fileUrl(storedFile.privateUrl())
                .isActive(true)
                .version(nextVersion)
                .uploadedByUserId(userId)
                .build();

        mentorVerificationDocumentRepository.save(document);
        return buildResponse(verificationRequest);
    }

    @Transactional
    public MentorVerificationRequestResponse deleteDocument(UUID userId, UUID documentId) {
        requireUserId(userId);
        MentorVerificationRequest verificationRequest = findEditableRequestForUpdate(userId);
        MentorVerificationDocument document = mentorVerificationDocumentRepository
                .findByIdAndRequestId(documentId, verificationRequest.getId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu minh chứng để xóa"));

        if (!document.isActive()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tài liệu đã ở trạng thái không hoạt động");
        }

        document.setActive(false);
        mentorVerificationDocumentRepository.save(document);
        return buildResponse(verificationRequest);
    }

    @Transactional
    public MentorVerificationRequestResponse submit(UUID userId, MentorVerificationSubmitRequest submitRequest) {
        requireUserId(userId);
        MentorVerificationRequest request = findEditableRequestForUpdate(userId);
        boolean wasNeedsRevision = request.getStatus() == VerificationStatus.NEEDS_REVISION;
        VerificationStatus previousStatus = request.getStatus();

        ensureSubmissionEligible(userId, request);
        ensureTermsAccepted(submitRequest, request);

        request.setStatus(VerificationStatus.PENDING_REVIEW);
        request.setSubmittedAt(DateTimeUtil.now());
        request.setSubmittedNote(trimToNull(submitRequest.submitNote()));
        request.setTermsAcceptedAt(DateTimeUtil.now());
        request.setTermsVersion(mentorTermsVersion);
        if (wasNeedsRevision) {
            request.setRevisionCount(request.getRevisionCount() + 1);
        }
        clearAdminLock(request);
        MentorVerificationRequest savedRequest = mentorVerificationRequestRepository.save(request);

        appendEvent(
                savedRequest,
                MentorVerificationEventType.SUBMITTED,
                userId,
                previousStatus,
                VerificationStatus.PENDING_REVIEW,
                submitRequest.submitNote()
        );

        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(userId)
                .orElseGet(() -> {
                    MentorProfile profile = new MentorProfile();
                    profile.setUserId(userId);
                    profile.setAvailable(false);
                    return profile;
                });
        mentorProfile.setStatus(MentorStatus.PENDING_VERIFICATION);
        mentorProfileRepository.save(mentorProfile);

        return buildResponse(savedRequest);
    }

    @Transactional
    public MentorVerificationRequestResponse withdraw(UUID userId) {
        requireUserId(userId);
        if (!userQueryPort.existsById(userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng");
        }
        UUID latestRequestId = findLatestRequest(userId)
                .map(MentorVerificationRequest::getId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor đang hoạt động"));
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
                userId,
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
        if (!userQueryPort.existsById(userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng");
        }
        UUID latestRequestId = findLatestRequest(userId)
                .map(MentorVerificationRequest::getId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ xác thực mentor đang hoạt động"));
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
                userId,
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

    private MentorVerificationRequest createDraftRequest(UUID userId, MentorVerificationRequest previousRequest) {
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .mentorUserId(userId)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.DRAFT)
                .previousRequest(previousRequest)
                .build();
        MentorVerificationRequest savedRequest = mentorVerificationRequestRepository.save(request);
        appendEvent(savedRequest, MentorVerificationEventType.REQUEST_CREATED, userId, null, VerificationStatus.DRAFT, null);

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
                            .storedFileId(prevDoc.getStoredFileId())
                            .originalFilename(prevDoc.getOriginalFilename())
                            .contentType(prevDoc.getContentType())
                            .sizeBytes(prevDoc.getSizeBytes())
                            .fileUrl(prevDoc.getFileUrl())
                            .isActive(true)
                            .version(prevDoc.getVersion())
                            .uploadedByUserId(prevDoc.getUploadedByUserId())
                            .build();
                    mentorVerificationDocumentRepository.save(clonedDoc);
                }
            }
        }

        return savedRequest;
    }

    private Optional<MentorVerificationRequest> findActiveRequest(UUID userId) {
        requireUserId(userId);
        return mentorVerificationRequestRepository.findFirstByMentorUserIdAndStatusInOrderByCreatedAtDesc(
                userId,
                ACTIVE_REQUEST_STATUSES
        );
    }

    private Optional<MentorVerificationRequest> findLatestRequest(UUID userId) {
        requireUserId(userId);
        return mentorVerificationRequestRepository.findFirstByMentorUserIdOrderByCreatedAtDesc(userId);
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
        if (requireCompletedStudentProfile && !academicEligibilityQuery.hasCompletedStudentProfile(userId)) {
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
        mentorVerificationRequestRepository.findFirstByMentorUserIdAndStatusInOrderByCreatedAtDesc(
                        userId,
                        List.of(VerificationStatus.PENDING_REVIEW)
                )
                .filter(existing -> !existing.getId().equals(request.getId()))
                .ifPresent(existing -> {
                    throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đang có một hồ sơ chờ admin duyệt");
                });
    }

    private VerificationDocumentStoragePort.VerificationDocumentMetadata storeVerificationFile(
            UUID userId,
            MentorVerificationDocumentUploadRequest request
    ) {
        if (userId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể xác định người dùng tải tài liệu");
        }
        MentorVerificationUploadIntent intent = uploadIntentRepository.findByIdForUpdate(request.uploadIntentId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent"));
        if (!intent.getOwnerUserId().equals(userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent");
        }
        if (intent.getStatus() == MentorVerificationUploadIntentStatus.CONFIRMED || intent.getConfirmedStoredFileId() != null) {
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
        if (objectMetadata.sizeBytes() > 0 && objectMetadata.sizeBytes() != intent.getExpectedSizeBytes()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "sizeBytes xác nhận không khớp file đã upload");
        }

        VerificationDocumentStoragePort.VerificationDocumentMetadata storedFile = verificationDocumentStoragePort
                .registerVerificationDocument(new VerificationDocumentStoragePort.VerificationDocumentRegistration(
                        userId,
                        originalFilename,
                        storageGateway.storageProviderName(),
                        objectKey,
                        contentType,
                        objectMetadata.sizeBytes() > 0 ? objectMetadata.sizeBytes() : intent.getExpectedSizeBytes()
                ));

        intent.setConfirmedStoredFileId(storedFile.fileId());
        intent.setConfirmedAt(DateTimeUtil.now());
        intent.setStatus(MentorVerificationUploadIntentStatus.CONFIRMED);
        uploadIntentRepository.save(intent);
        return storedFile;
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

    private MentorVerificationRequestResponse buildResponse(MentorVerificationRequest request) {
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

        MentorVerificationChecklistResponse checklist = buildChecklist(request.getMentorUserId(), documents);
        MentorVerificationAllowedActionsResponse allowedActions = buildAllowedActions(request.getStatus(), checklist.canSubmit());

        return MentorVerificationRequestResponse.builder()
                .requestId(request.getId())
                .mentorUserId(request.getMentorUserId())
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
        UserSummaryRecord actor = event.getActorUserId() == null ? null : userQueryPort.findUserSummaryById(event.getActorUserId()).orElse(null);
        return MentorVerificationTimelineEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .actorUserId(actor == null ? null : actor.userId())
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

    private MentorVerificationChecklistResponse buildChecklist(UUID userId, List<MentorVerificationDocumentResponse> documents) {
        boolean hasAcademicProfile = academicEligibilityQuery.hasCompletedStudentProfile(userId);
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

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
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
        return request.getLockedByUserId() != null
                && request.getLockExpiresAt() != null
                && request.getLockExpiresAt().isAfter(DateTimeUtil.now());
    }

    private void clearAdminLock(MentorVerificationRequest request) {
        request.setLockedByUserId(null);
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
        if (!userId.equals(intent.getOwnerUserId())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent");
        }
        return intent;
    }

    private MentorVerificationUploadIntentStatusResponse mapUploadIntentStatus(MentorVerificationUploadIntent intent) {
        UUID confirmedDocumentId = intent.getConfirmedStoredFileId() == null ? null : mentorVerificationDocumentRepository
                .findByStoredFileId(intent.getConfirmedStoredFileId())
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
