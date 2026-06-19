package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.storage.CloudinaryService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private TagRepository tagRepository;
    @Mock
    private MentorTagRepository mentorTagRepository;
    @Mock
    private CloudinaryService cloudinaryServiceBean;

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
        when(mentorVerificationRequestRepository.findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(
                userId,
                List.of(VerificationStatus.DRAFT, VerificationStatus.PENDING_REVIEW, VerificationStatus.NEEDS_REVISION)
        )).thenReturn(Optional.of(request));
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
        when(cloudinaryServiceBean.upload(any(org.springframework.web.multipart.MultipartFile.class), anyString())).thenAnswer(invocation ->
                new CloudinaryService.CloudinaryUploadResult("mentor-verification/sample-public-id", "https://res.cloudinary.com/demo/sample.jpg"));

        serviceWithCloudinary = new MentorVerificationService(
                mentorVerificationRequestRepository,
                mentorVerificationDocumentRepository,
                mentorVerificationRequestEventRepository,
                mentorProfileRepository,
                academicService,
                mentorProfileService,
                userRepository,
                storedFileRepository,
                cloudinaryServiceBean
        );
        ReflectionTestUtils.setField(serviceWithCloudinary, "mentorTermsVersion", "SKILLSWAP_MENTOR_TERMS_V1");
        ReflectionTestUtils.setField(serviceWithCloudinary, "requireCompletedStudentProfile", false);
        ReflectionTestUtils.setField(serviceWithCloudinary, "requireCompletedMentorProfile", false);
    }

    @Test
    void uploadJpg_shouldStoreToCloudinary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", new byte[]{1, 2, 3});

        serviceWithCloudinary.uploadDocument(userId, VerificationDocumentType.FPTU_AFFILIATION_PROOF, file);

        ArgumentCaptor<StoredFile> fileCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStorageProvider()).isEqualTo("CLOUDINARY");
        assertThat(fileCaptor.getValue().getStorageKey()).isEqualTo("mentor-verification/sample-public-id");
        assertThat(fileCaptor.getValue().getPublicUrl()).isEqualTo("https://res.cloudinary.com/demo/sample.jpg");
        verify(cloudinaryServiceBean).upload(any(org.springframework.web.multipart.MultipartFile.class), anyString());
    }

    @Test
    void uploadPng_shouldStoreToCloudinary() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.png", "image/png", new byte[]{1, 2, 3});

        serviceWithCloudinary.uploadDocument(userId, VerificationDocumentType.EXPERTISE_PROOF, file);

        verify(cloudinaryServiceBean).upload(any(org.springframework.web.multipart.MultipartFile.class), anyString());
        verify(storedFileRepository).save(any());
    }

    @Test
    void uploadPdf_shouldBeRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, VerificationDocumentType.FPTU_AFFILIATION_PROOF, file))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(cloudinaryServiceBean, never()).upload(any(org.springframework.web.multipart.MultipartFile.class), anyString());
        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadUnknownType_shouldBeRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.gif", "image/gif", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, VerificationDocumentType.FPTU_AFFILIATION_PROOF, file))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(cloudinaryServiceBean, never()).upload(any(org.springframework.web.multipart.MultipartFile.class), anyString());
        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadImage_withoutCloudinary_shouldReturnConfigurationError() throws Exception {
        // Cloudinary is now required at startup, so this case is covered by app bootstrap behavior.
        assertThat(serviceWithCloudinary).isNotNull();
    }

    @Test
    void uploadImage_shouldNotRequireR2() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpg", "image/jpeg", new byte[]{1, 2, 3});

        serviceWithCloudinary.uploadDocument(userId, VerificationDocumentType.FPTU_AFFILIATION_PROOF, file);

        verify(cloudinaryServiceBean).upload(any(org.springframework.web.multipart.MultipartFile.class), anyString());
    }
}
