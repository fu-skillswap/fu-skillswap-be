package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.infrastructure.storage.R2DocumentStorageService;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.*;
import com.fptu.exe.skillswap.modules.mentor.dto.request.*;
import com.fptu.exe.skillswap.modules.mentor.dto.response.*;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_./-]+$");

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
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final com.fptu.exe.skillswap.infrastructure.config.StorageSecurityProperties storageSecurityProperties;
    private final ObjectProvider<R2DocumentStorageService> r2StorageProvider;

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
    public MentorVerificationRequestResponse uploadDocument(
            UUID userId,
            VerificationDocumentType documentType,
            MultipartFile file
    ) {
        requireUserId(userId);
        MentorVerificationRequest verificationRequest = findEditableRequestForUpdate(userId);
        validateMultipartUploadInput(documentType, file);
        enforceDocumentCountLimit(verificationRequest.getId(), documentType);

        User user = getRequiredUser(userId);
        StoredFile storedFile = storeVerificationFile(user, documentType, file);

        int nextVersion = mentorVerificationDocumentRepository
                .findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(verificationRequest.getId(), documentType)
                .stream()
                .map(MentorVerificationDocument::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .request(verificationRequest)
                .documentType(documentType)
                .status(VerificationDocumentStatus.UPLOADED)
                .storageKind(resolveStorageKind(file.getContentType()))
                .storedFile(storedFile)
                .isActive(true)
                .version(nextVersion)
                .uploadedBy(user)
                .build();

        mentorVerificationDocumentRepository.save(document);
        return buildResponse(verificationRequest);
    }

    @Transactional
    public MentorVerificationRequestResponse uploadDocument(
            UUID userId,
            MentorVerificationDocumentUploadRequest uploadRequest
    ) {
        requireUserId(userId);
        MentorVerificationRequest verificationRequest = findEditableRequestForUpdate(userId);
        validateUploadInput(uploadRequest);
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
                .storageKind(resolveStorageKind(uploadRequest))
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

    private MentorVerificationRequest createDraftRequest(User user, MentorVerificationRequest previousRequest) {
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .mentor(user)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.DRAFT)
                .previousRequest(previousRequest)
                .build();
        MentorVerificationRequest savedRequest = mentorVerificationRequestRepository.save(request);
        appendEvent(savedRequest, MentorVerificationEventType.REQUEST_CREATED, user, null, VerificationStatus.DRAFT, null);
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
        String fileUrl = trimToNull(request.fileUrl());
        String publicId = trimToNull(request.publicId());
        // Strip path separators from the client-supplied filename to prevent path separator
        // injection into the stored record (the file is never written to disk here, but we
        // keep the persisted name clean for admin display and future use).
        String originalFilename = sanitizeFilename(trimToNull(request.originalFilename()));
        String contentType = canonicalizeContentType(request.contentType());
        return storedFileRepository.save(StoredFile.builder()
                .owner(user)
                .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                .originalName(originalFilename)
                .storageProvider("CLOUDINARY")
                .storageKey(publicId)
                .publicUrl(fileUrl)
                .mimeType(contentType)
                .sizeBytes(request.sizeBytes())
                .build());
    }

    private void validateUploadInput(MentorVerificationDocumentUploadRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu tài liệu không được để trống");
        }
        if (request.documentType() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại tài liệu xác thực là bắt buộc");
        }
        if (!StringUtils.hasText(request.fileUrl())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu không được để trống");
        }
        validateDocumentUrl(request.fileUrl());
        if (!StringUtils.hasText(request.publicId()) || !isValidPublicId(request.publicId())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã publicId của tài liệu không hợp lệ");
        }
        if (!StringUtils.hasText(request.originalFilename())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tên file gốc không được để trống");
        }
        if (request.sizeBytes() == null || request.sizeBytes() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Kích thước file không hợp lệ");
        }
        if (request.sizeBytes() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Kích thước file không được vượt quá 15MB");
        }
        String contentType = canonicalizeContentType(request.contentType());
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ file JPG, PNG hoặc PDF");
        }
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
        return MentorVerificationAllowedActionsResponse.builder()
                .canUploadDocuments(editable)
                .canSubmit(editable && canSubmit)
                .canWithdraw(withdrawable)
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

    private VerificationStorageKind resolveStorageKind(MentorVerificationDocumentUploadRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu tài liệu không được để trống");
        }
        return resolveStorageKind(request.contentType());
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

    private void validateDocumentUrl(String fileUrl) {
        if (storageSecurityProperties.getAllowedUrlHosts() == null || storageSecurityProperties.getAllowedUrlHosts().isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Hệ thống chưa cấu hình danh sách domain lưu trữ hợp lệ");
        }
        try {
            URI uri = new URI(fileUrl.trim());
            if (!uri.isAbsolute()) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu sai định dạng");
            }
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (!"https".equalsIgnoreCase(scheme)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu phải sử dụng giao thức https an toàn");
            }

            if (!StringUtils.hasText(host)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu không hợp lệ (thiếu host)");
            }

            String lowerHost = host.toLowerCase();
            if (lowerHost.equals("localhost") || lowerHost.startsWith("127.") || lowerHost.startsWith("10.") || lowerHost.startsWith("192.168.") || lowerHost.equals("::1") || lowerHost.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+")) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu không hợp lệ (không hỗ trợ IP nội bộ)");
            }

            boolean isAllowed = storageSecurityProperties.getAllowedUrlHosts().stream()
                    .anyMatch(allowedHost -> allowedHost.equalsIgnoreCase(host));

            if (!isAllowed) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu không thuộc danh sách các nguồn lưu trữ được phép");
            }
        } catch (URISyntaxException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Đường dẫn tài liệu sai định dạng");
        }
    }

    private boolean isValidPublicId(String publicId) {
        if (publicId == null) {
            return false;
        }
        String trimmed = publicId.trim();
        if (!PUBLIC_ID_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        // Even though the regex allows '/' and '.', explicitly reject any path segment
        // equal to ".." to prevent path-traversal sequences such as "../../admin".
        for (String segment : trimmed.split("/", -1)) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
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

    private StoredFile storeVerificationFile(User user, VerificationDocumentType documentType, MultipartFile file) {
        R2DocumentStorageService storageService = r2StorageProvider.getIfAvailable();
        if (storageService == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình R2 để upload minh chứng");
        }
        try {
            R2DocumentStorageService.R2UploadResult uploadResult = storageService.upload(
                    file,
                    "mentor-verification/" + user.getId() + "/" + documentType.name().toLowerCase()
            );
            return storedFileRepository.save(StoredFile.builder()
                    .owner(user)
                    .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                    .originalName(sanitizeFilename(trimToNull(file.getOriginalFilename())))
                    .storageProvider("R2")
                    .storageKey(uploadResult.objectKey())
                    .publicUrl(uploadResult.fileUrl())
                    .mimeType(canonicalizeContentType(file.getContentType()))
                    .sizeBytes(file.getSize())
                    .build());
        } catch (IOException ex) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Upload minh chứng mentor thất bại");
        }
    }

    private void validateMultipartUploadInput(VerificationDocumentType documentType, MultipartFile file) {
        if (documentType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại tài liệu xác thực là bắt buộc");
        }
        maxFilesFor(documentType);
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "File minh chứng không được để trống");
        }
        if (file.getSize() <= 0 || file.getSize() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Kích thước file không được vượt quá 15MB");
        }
        String contentType = canonicalizeContentType(file.getContentType());
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ file JPG, PNG hoặc PDF");
        }
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
}




