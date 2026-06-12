package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.storage.CloudinaryService;
import com.fptu.exe.skillswap.infrastructure.storage.R2DocumentStorageService;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.*;
import com.fptu.exe.skillswap.modules.mentor.dto.*;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
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
    private static final Set<String> SUPPORTED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final long MAX_FILES_PER_DOCUMENT_TYPE = 5;

    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final Optional<CloudinaryService> cloudinaryService;
    private final Optional<R2DocumentStorageService> r2DocumentStorageService;

    @Transactional
    public MentorVerificationRequestResponse requestToBecomeMentor(UUID userId) {
        requireUserId(userId);
        User user = getRequiredUser(userId);
        Optional<MentorVerificationRequest> activeRequest = findActiveRequest(userId);
        if (activeRequest.isPresent()) {
            return buildResponse(activeRequest.get());
        }
        MentorProfile mentorProfile = ensureMentorProfileExists(user);
        ensureMentorCanOpenVerificationRequest(mentorProfile);
        MentorVerificationRequest request = createDraftRequest(user);
        return buildResponse(request);
    }

    @Transactional(readOnly = true)
    public MentorVerificationRequestResponse getMyRequest(UUID userId) {
        requireUserId(userId);
        MentorVerificationRequest request = findActiveRequest(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor đang hoạt động"));
        return buildResponse(request);
    }

    @Transactional
    public MentorVerificationRequestResponse uploadDocument(
            UUID userId,
            VerificationDocumentType documentType,
            boolean isPrimary,
            MultipartFile file
    ) {
        requireUserId(userId);
        MentorVerificationRequest request = findEditableRequest(userId);
        validateUploadInput(documentType, file);
        enforceDocumentCountLimit(request.getId(), documentType);

        User user = getRequiredUser(userId);
        StoredFile storedFile = storeVerificationFile(user, file, documentType);

        if (isPrimary) {
            deactivateCurrentPrimary(request.getId(), documentType);
        }

        int nextVersion = mentorVerificationDocumentRepository
                .findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(request.getId(), documentType)
                .stream()
                .map(MentorVerificationDocument::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .request(request)
                .documentType(documentType)
                .status(VerificationDocumentStatus.UPLOADED)
                .storageKind(resolveStorageKind(file))
                .storedFile(storedFile)
                .isPrimary(isPrimary)
                .isActive(true)
                .version(nextVersion)
                .uploadedBy(user)
                .build();

        mentorVerificationDocumentRepository.save(document);
        return buildResponse(request);
    }

    @Transactional
    public MentorVerificationRequestResponse submit(UUID userId, MentorVerificationSubmitRequest submitRequest) {
        requireUserId(userId);
        if (submitRequest == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu nộp hồ sơ không được để trống");
        }
        MentorVerificationRequest request = findEditableRequest(userId);
        boolean wasNeedsRevision = request.getStatus() == VerificationStatus.NEEDS_REVISION;
        ensureSubmissionEligible(userId, request);

        request.setSubmittedNote(trimToNull(submitRequest.submitNote()));
        request.setStatus(VerificationStatus.PENDING_REVIEW);
        request.setSubmittedAt(LocalDateTime.now());
        if (wasNeedsRevision) {
            request.setRevisionCount(request.getRevisionCount() + 1);
        }
        mentorVerificationRequestRepository.save(request);

        MentorProfile mentorProfile = ensureMentorProfileExists(request.getMentor());
        mentorProfile.setStatus(MentorStatus.PENDING_VERIFICATION);
        mentorProfileRepository.save(mentorProfile);

        return buildResponse(request);
    }

    private MentorVerificationRequest createDraftRequest(User user) {
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .mentor(user)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.DRAFT)
                .build();
        return mentorVerificationRequestRepository.save(request);
    }

    private Optional<MentorVerificationRequest> findActiveRequest(UUID userId) {
        requireUserId(userId);
        return mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(
                userId,
                ACTIVE_REQUEST_STATUSES
        );
    }

    private MentorVerificationRequest findEditableRequest(UUID userId) {
        MentorVerificationRequest request = findActiveRequest(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Chưa có hồ sơ xác thực mentor để cập nhật"));
        if (request.getStatus() != VerificationStatus.DRAFT && request.getStatus() != VerificationStatus.NEEDS_REVISION) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Hồ sơ hiện tại không cho phép chỉnh sửa tài liệu");
        }
        return request;
    }

    private void ensureSubmissionEligible(UUID userId, MentorVerificationRequest request) {
        if (request == null || request.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Hồ sơ xác thực mentor không hợp lệ");
        }
        if (!studentProfileRepository.existsById(userId)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cần hoàn tất hồ sơ học thuật trước khi nộp xác thực mentor");
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

    private StoredFile storeVerificationFile(User user, MultipartFile file, VerificationDocumentType documentType) {
        if (user == null || user.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể xác định người dùng tải tài liệu");
        }
        if (documentType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại tài liệu xác thực là bắt buộc");
        }
        String contentType = normalizeContentType(file.getContentType());
        try {
            if (SUPPORTED_IMAGE_CONTENT_TYPES.contains(contentType)) {
                CloudinaryService service = cloudinaryService
                        .orElseThrow(() -> new BaseException(ErrorCode.CONFIGURATION_ERROR, "Cloudinary chưa được cấu hình cho upload ảnh"));
                CloudinaryService.CloudinaryUploadResult uploadResult = service.upload(file, "mentor-verification/" + user.getId());
                return storedFileRepository.save(StoredFile.builder()
                        .owner(user)
                        .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                        .originalName(file.getOriginalFilename())
                        .storageProvider("CLOUDINARY")
                        .storageKey(uploadResult.publicId())
                        .publicUrl(uploadResult.secureUrl())
                        .mimeType(contentType)
                        .sizeBytes(file.getSize())
                        .build());
            }

            R2DocumentStorageService service = r2DocumentStorageService
                    .orElseThrow(() -> new BaseException(ErrorCode.CONFIGURATION_ERROR, "R2 chưa được cấu hình cho upload PDF"));
            R2DocumentStorageService.R2UploadResult uploadResult = service.upload(file, "mentor-verification/" + user.getId());
            return storedFileRepository.save(StoredFile.builder()
                    .owner(user)
                    .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                    .originalName(file.getOriginalFilename())
                    .storageProvider("R2")
                    .storageKey(uploadResult.objectKey())
                    .publicUrl(uploadResult.fileUrl())
                    .mimeType(contentType)
                    .sizeBytes(file.getSize())
                    .build());
        } catch (IOException ex) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Tải tài liệu xác thực thất bại do hệ thống lưu trữ tạm thời không khả dụng", ex);
        }
    }

    private void validateUploadInput(VerificationDocumentType documentType, MultipartFile file) {
        if (documentType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại tài liệu xác thực là bắt buộc");
        }
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tệp tải lên không được để trống");
        }
        if (file.getSize() <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Kích thước tệp tải lên không hợp lệ");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!SUPPORTED_IMAGE_CONTENT_TYPES.contains(contentType) && !PDF_CONTENT_TYPE.equals(contentType)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ hỗ trợ file JPG, PNG hoặc PDF");
        }
    }

    private void enforceDocumentCountLimit(UUID requestId, VerificationDocumentType documentType) {
        if (requestId == null || documentType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể kiểm tra số lượng tài liệu do dữ liệu đầu vào không hợp lệ");
        }
        long count = mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(requestId, documentType);
        if (count >= MAX_FILES_PER_DOCUMENT_TYPE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mỗi loại tài liệu chỉ được tối đa " + MAX_FILES_PER_DOCUMENT_TYPE + " file");
        }
    }

    private void deactivateCurrentPrimary(UUID requestId, VerificationDocumentType documentType) {
        if (requestId == null || documentType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể cập nhật tài liệu chính do dữ liệu đầu vào không hợp lệ");
        }
        mentorVerificationDocumentRepository
                .findFirstByRequestIdAndDocumentTypeAndIsPrimaryTrueAndIsActiveTrue(requestId, documentType)
                .ifPresent(existing -> {
                    existing.setPrimary(false);
                    existing.setActive(false);
                    existing.setStatus(VerificationDocumentStatus.REMOVED);
                    mentorVerificationDocumentRepository.save(existing);
                });
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
                    mentorProfile.setHourlyRate(BigDecimal.ZERO);
                    mentorProfile.setSessionDuration(60);
                    return mentorProfileRepository.save(mentorProfile);
                });
    }

    private User getRequiredUser(UUID userId) {
        requireUserId(userId);
        return userRepository.findById(userId)
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

        return MentorVerificationRequestResponse.builder()
                .requestId(request.getId())
                .mentorUserId(request.getMentor().getId())
                .status(request.getStatus())
                .submitNote(request.getSubmittedNote())
                .reviewNote(request.getReviewNote())
                .rejectionReason(request.getRejectionReason())
                .revisionCount(request.getRevisionCount())
                .submittedAt(request.getSubmittedAt())
                .reviewedAt(request.getReviewedAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .documents(documents)
                .checklist(checklist)
                .allowedActions(allowedActions)
                .build();
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
                .isPrimary(document.isPrimary())
                .isActive(document.isActive())
                .version(document.getVersion())
                .reviewNote(document.getReviewNote())
                .rejectedReason(document.getRejectedReason())
                .uploadedAt(document.getUploadedAt())
                .build();
    }

    private VerificationStorageKind resolveStorageKind(MultipartFile file) {
        if (file == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tệp tải lên không được để trống");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (SUPPORTED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            return VerificationStorageKind.IMAGE;
        }
        return VerificationStorageKind.DOCUMENT;
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase();
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
}
