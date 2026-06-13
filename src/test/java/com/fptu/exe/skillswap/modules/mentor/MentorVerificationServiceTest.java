package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationSubmitRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorVerificationServiceTest {

    @Mock
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;

    @Mock
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private MultipartFile multipartFile;

    private MentorVerificationService mentorVerificationService;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        mentorVerificationService = new MentorVerificationService(
                mentorVerificationRequestRepository,
                mentorVerificationDocumentRepository,
                mentorProfileRepository,
                studentProfileRepository,
                userRepository,
                storedFileRepository,
                Optional.empty(),
                Optional.empty()
        );

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("mentor@fpt.edu.vn")
                .fullName("Mentor Candidate")
                .build();
    }

    @Test
    void requestToBecomeMentor_withoutExistingProfile_shouldCreateProfileSafely() {
        MentorVerificationRequest draftRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.DRAFT)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.empty());
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> {
            MentorProfile profile = invocation.getArgument(0);
            if (profile.getUserId() == null && profile.getUser() != null) {
                profile.setUserId(profile.getUser().getId());
            }
            return profile;
        });
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenReturn(draftRequest);
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(draftRequest.getId())).thenReturn(List.of());
        when(studentProfileRepository.existsById(userId)).thenReturn(false);

        MentorVerificationRequestResponse response = mentorVerificationService.requestToBecomeMentor(userId);

        ArgumentCaptor<MentorProfile> profileCaptor = ArgumentCaptor.forClass(MentorProfile.class);
        verify(mentorProfileRepository).save(profileCaptor.capture());
        MentorProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getUser()).isSameAs(user);
        assertThat(savedProfile.getStatus()).isEqualTo(MentorStatus.DRAFT);
        assertThat(response.requestId()).isEqualTo(draftRequest.getId());
        assertThat(response.checklist().academicProfileCompleted()).isFalse();
    }

    @Test
    void requestToBecomeMentor_existingActiveRequest_shouldReturnCurrentRequestWithoutCreatingProfile() {
        MentorVerificationRequest draftRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .status(VerificationStatus.PENDING_REVIEW)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(draftRequest));
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(draftRequest.getId())).thenReturn(List.of());
        when(studentProfileRepository.existsById(userId)).thenReturn(true);

        MentorVerificationRequestResponse response = mentorVerificationService.requestToBecomeMentor(userId);

        verify(mentorProfileRepository, never()).save(any(MentorProfile.class));
        assertThat(response.requestId()).isEqualTo(draftRequest.getId());
    }

    @Test
    void requestToBecomeMentor_activeMentor_shouldReturnConflictInsteadOf500() {
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(userId)
                .user(user)
                .status(MentorStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(mentorProfile));

        assertThatThrownBy(() -> mentorVerificationService.requestToBecomeMentor(userId))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT))
                .hasMessage("Bạn đã là mentor đã được xác thực");
    }

    @Test
    void submit_missingAcademicProfile_shouldReturnBadRequest() {
        MentorVerificationRequest draftRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .status(VerificationStatus.DRAFT)
                .build();

        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(draftRequest));
        when(studentProfileRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> mentorVerificationService.submit(userId, new MentorVerificationSubmitRequest("ready")))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessage("Cần hoàn tất hồ sơ học thuật trước khi nộp xác thực mentor");
    }

    @Test
    void uploadDocument_missingFile_shouldReturnBadRequest() {
        MentorVerificationRequest draftRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .status(VerificationStatus.DRAFT)
                .build();

        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(draftRequest));

        assertThatThrownBy(() -> mentorVerificationService.uploadDocument(userId, null, false, null))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessage("Loại tài liệu xác thực là bắt buộc");
    }

    @Test
    void getMyRequest_missingUserId_shouldReturnUnauthenticated() {
        assertThatThrownBy(() -> mentorVerificationService.getMyRequest(null))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void submit_existingPendingReviewRequest_shouldReturnConflict() {
        MentorVerificationRequest draftRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .status(VerificationStatus.DRAFT)
                .build();
        MentorVerificationRequest pendingRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .status(VerificationStatus.PENDING_REVIEW)
                .build();

        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(draftRequest))
                .thenReturn(Optional.of(pendingRequest));
        when(studentProfileRepository.existsById(userId)).thenReturn(true);
        when(mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(eq(draftRequest.getId()), any()))
                .thenReturn(1L);

        assertThatThrownBy(() -> mentorVerificationService.submit(userId, new MentorVerificationSubmitRequest(null)))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT))
                .hasMessage("Bạn đang có một hồ sơ chờ admin duyệt");
    }

    @Test
    void deleteDocument_editableRequest_shouldSoftDeleteDocument() {
        UUID documentId = UUID.randomUUID();
        MentorVerificationRequest draftRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .status(VerificationStatus.DRAFT)
                .build();
        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .id(documentId)
                .request(draftRequest)
                .documentType(VerificationDocumentType.FPTU_AFFILIATION_PROOF)
                .status(VerificationDocumentStatus.UPLOADED)
                .storedFile(StoredFile.builder()
                        .id(UUID.randomUUID())
                        .owner(user)
                        .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                        .originalName("fpt-card.jpg")
                        .storageProvider("CLOUDINARY")
                        .storageKey("cloudinary/key")
                        .publicUrl("https://example.com/fpt-card.jpg")
                        .mimeType("image/jpeg")
                        .sizeBytes(10L)
                        .build())
                .isPrimary(true)
                .isActive(true)
                .build();

        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(draftRequest));
        when(mentorVerificationDocumentRepository.findByIdAndRequestId(documentId, draftRequest.getId()))
                .thenReturn(Optional.of(document));
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(draftRequest.getId()))
                .thenReturn(List.of(document));
        when(studentProfileRepository.existsById(userId)).thenReturn(true);

        MentorVerificationRequestResponse response = mentorVerificationService.deleteDocument(userId, documentId);

        assertThat(document.isActive()).isFalse();
        assertThat(document.isPrimary()).isFalse();
        assertThat(document.getStatus()).isEqualTo(VerificationDocumentStatus.REMOVED);
        verify(mentorVerificationDocumentRepository, times(1)).save(document);
        assertThat(response.documents()).hasSize(1);
    }

    @Test
    void withdraw_pendingReviewRequest_shouldMarkWithdrawnAndResetProfileStatus() {
        MentorVerificationRequest pendingRequest = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .status(VerificationStatus.PENDING_REVIEW)
                .build();
        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(userId)
                .user(user)
                .status(MentorStatus.PENDING_VERIFICATION)
                .build();

        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(pendingRequest));
        when(mentorProfileRepository.findWithUserByUserId(userId)).thenReturn(Optional.of(mentorProfile));
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(pendingRequest.getId()))
                .thenReturn(List.of());
        when(studentProfileRepository.existsById(userId)).thenReturn(true);

        MentorVerificationRequestResponse response = mentorVerificationService.withdraw(userId);

        assertThat(pendingRequest.getStatus()).isEqualTo(VerificationStatus.WITHDRAWN);
        assertThat(pendingRequest.getWithdrawnAt()).isNotNull();
        assertThat(mentorProfile.getStatus()).isEqualTo(MentorStatus.DRAFT);
        verify(mentorVerificationRequestRepository).save(pendingRequest);
        verify(mentorProfileRepository).save(mentorProfile);
        assertThat(response.status()).isEqualTo(VerificationStatus.WITHDRAWN);
    }
}
