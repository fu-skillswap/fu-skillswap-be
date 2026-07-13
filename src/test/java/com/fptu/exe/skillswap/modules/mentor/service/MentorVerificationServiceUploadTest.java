package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorVerificationServiceUploadTest {

    @Mock
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;
    @Mock
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    @Mock
    private MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private AcademicService academicService;
    @Mock
    private MentorProfileService mentorProfileService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private ObjectProvider<StorageGateway> r2StorageProvider;
    @Mock
    private StorageGateway storageGateway;

    private MentorVerificationService service;
    private UUID userId;
    private User user;
    private MentorVerificationRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("mentor@example.com")
                .fullName("Mentor Example")
                .status(UserStatus.ACTIVE)
                .build();

        request = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(user)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.DRAFT)
                .build();

        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(eq(userId), anyCollection()))
                .thenReturn(Optional.of(request));
        lenient().when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId()))
                .thenReturn(Optional.of(request));
        lenient().when(mentorVerificationDocumentRepository.findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(any(), any()))
                .thenReturn(0L);
        lenient().when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(storedFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(academicService.hasCompletedStudentProfile(userId)).thenReturn(true);
        lenient().when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(true);
        lenient().when(r2StorageProvider.getIfAvailable()).thenReturn(storageGateway);
        lenient().when(storageGateway.resolvePublicUrl(any())).thenAnswer(invocation -> "https://cdn.skillswap.com/" + invocation.getArgument(0));
        lenient().when(storageGateway.storageProviderName()).thenReturn("R2");
        lenient().when(storageGateway.headObject(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String ct = key != null && key.toLowerCase().endsWith(".jpg") ? "image/jpeg" : "image/jpeg";
            return new StorageGateway.ObjectMetadata(key, ct, 123L);
        });

        service = new MentorVerificationService(
                mentorVerificationRequestRepository,
                mentorVerificationDocumentRepository,
                mentorVerificationRequestEventRepository,
                mentorProfileRepository,
                academicService,
                mentorProfileService,
                userRepository,
                storedFileRepository,
                r2StorageProvider
        );
        ReflectionTestUtils.setField(service, "mentorTermsVersion", "SKILLSWAP_MENTOR_TERMS_V1");
        ReflectionTestUtils.setField(service, "requireCompletedStudentProfile", false);
        ReflectionTestUtils.setField(service, "requireCompletedMentorProfile", false);
    }

    @Test
    void uploadProof_shouldAcceptPresignedObjectKeyAfterStorageVerification() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                verificationObjectKey("proof.jpg"),
                "proof.jpg",
                "image/jpeg",
                123L
        );

        service.uploadDocument(userId, uploadRequest);

        ArgumentCaptor<StoredFile> fileCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStorageProvider()).isEqualTo("R2");
        assertThat(fileCaptor.getValue().getStorageKey()).isEqualTo(uploadRequest.objectKey());
        assertThat(fileCaptor.getValue().getPublicUrl()).isEqualTo("private://" + uploadRequest.objectKey());
    }

    @Test
    void uploadProof_shouldRejectInvalidObjectKey() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "../proof.jpg",
                "proof.jpg",
                "image/jpeg",
                123L
        );

        assertThatThrownBy(() -> service.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadProof_shouldPreserveTypeAndSizeValidation() {
        MentorVerificationDocumentUploadRequest oversized = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                verificationObjectKey("proof.jpg"),
                "proof.jpg",
                "image/jpeg",
                15L * 1024L * 1024L + 1L
        );

        assertThatThrownBy(() -> service.uploadDocument(userId, oversized))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);

        MentorVerificationDocumentUploadRequest badType = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                verificationObjectKey("proof.gif"),
                "proof.gif",
                "image/gif",
                123L
        );

        assertThatThrownBy(() -> service.uploadDocument(userId, badType))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void uploadAffiliationProofBeyondQuota_shouldBeRejected() {
        when(mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(
                request.getId(),
                VerificationDocumentType.FPTU_AFFILIATION_PROOF
        )).thenReturn(1L);

        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                verificationObjectKey("proof.jpg"),
                "proof.jpg",
                "image/jpeg",
                123L
        );

        assertThatThrownBy(() -> service.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private String verificationObjectKey(String filename) {
        return "skillswap/verification-documents/users/" + userId + "/" + filename;
    }
}
