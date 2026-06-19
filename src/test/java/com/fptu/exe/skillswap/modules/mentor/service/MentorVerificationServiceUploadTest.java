package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private TagRepository tagRepository;
    @Mock
    private MentorTagRepository mentorTagRepository;

    private MentorVerificationService serviceWithCloudinary;
    private UUID userId;
    private User user;
    private MentorVerificationRequest request;

    @BeforeEach
    void setUp() throws Exception {
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().doReturn(Optional.of(request)).when(mentorVerificationRequestRepository)
                .findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(any(UUID.class), anyCollection());
        when(mentorVerificationDocumentRepository.findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(any(), any()))
                .thenReturn(0L);
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(any()))
                .thenReturn(Collections.emptyList());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(any()))
                .thenReturn(Collections.emptyList());
        when(academicService.hasCompletedStudentProfile(userId)).thenReturn(true);
        when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(true);

        serviceWithCloudinary = new MentorVerificationService(
                mentorVerificationRequestRepository,
                mentorVerificationDocumentRepository,
                mentorVerificationRequestEventRepository,
                mentorProfileRepository,
                academicService,
                mentorProfileService,
                userRepository,
                storedFileRepository
        );
        ReflectionTestUtils.setField(serviceWithCloudinary, "mentorTermsVersion", "SKILLSWAP_MENTOR_TERMS_V1");
        ReflectionTestUtils.setField(serviceWithCloudinary, "requireCompletedStudentProfile", false);
        ReflectionTestUtils.setField(serviceWithCloudinary, "requireCompletedMentorProfile", false);
    }

    @Test
    void uploadJpg_shouldStoreToCloudinary() throws Exception {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "mentor-verification/user-123/proof-jpg",
                "proof.jpg",
                "image/jpeg",
                123L
        );

        serviceWithCloudinary.uploadDocument(userId, uploadRequest);

        ArgumentCaptor<StoredFile> fileCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStorageProvider()).isEqualTo("CLOUDINARY");
        assertThat(fileCaptor.getValue().getStorageKey()).isEqualTo(uploadRequest.publicId());
        assertThat(fileCaptor.getValue().getPublicUrl()).isEqualTo(uploadRequest.fileUrl());
    }

    @Test
    void uploadPng_shouldStoreToCloudinary() throws Exception {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.EXPERTISE_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.png",
                "mentor-verification/user-123/proof-png",
                "proof.png",
                "image/png",
                123L
        );

        serviceWithCloudinary.uploadDocument(userId, uploadRequest);

        verify(storedFileRepository).save(any());
    }

    @Test
    void uploadPdf_shouldBeRejected() throws Exception {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.pdf",
                "mentor-verification/user-123/proof-pdf",
                "proof.pdf",
                "application/pdf",
                123L
        );

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadUnknownType_shouldBeRejected() throws Exception {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.gif",
                "mentor-verification/user-123/proof-gif",
                "proof.gif",
                "image/gif",
                123L
        );

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadImage_shouldNotRequireR2() throws Exception {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "mentor-verification/user-123/proof-jpg",
                "proof.jpg",
                "image/jpeg",
                123L
        );

        serviceWithCloudinary.uploadDocument(userId, uploadRequest);
    }

    @Test
    void uploadWithoutPublicId_shouldBeRejected() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                " ",
                "proof.jpg",
                "image/jpeg",
                123L
        );

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(storedFileRepository, never()).save(any());
    }
}
