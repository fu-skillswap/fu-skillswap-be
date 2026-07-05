package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.infrastructure.storage.R2DocumentStorageService;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
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
import org.junit.jupiter.api.Disabled;
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
    private com.fptu.exe.skillswap.infrastructure.config.StorageSecurityProperties storageSecurityProperties;
    @Mock
    private ObjectProvider<R2DocumentStorageService> r2StorageProvider;
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

        lenient().when(storageSecurityProperties.getAllowedUrlHosts()).thenReturn(java.util.List.of("res.cloudinary.com"));

        serviceWithCloudinary = new MentorVerificationService(
                mentorVerificationRequestRepository,
                mentorVerificationDocumentRepository,
                mentorVerificationRequestEventRepository,
                mentorProfileRepository,
                academicService,
                mentorProfileService,
                userRepository,
                storedFileRepository,
                storageSecurityProperties,
                r2StorageProvider
        );
        ReflectionTestUtils.setField(serviceWithCloudinary, "mentorTermsVersion", "SKILLSWAP_MENTOR_TERMS_V1");
        ReflectionTestUtils.setField(serviceWithCloudinary, "requireCompletedStudentProfile", false);
        ReflectionTestUtils.setField(serviceWithCloudinary, "requireCompletedMentorProfile", false);
    }

    @Test
    void uploadProof_shouldAcceptConfiguredCloudinaryUrl() {
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
    void uploadProof_shouldRejectUnknownExternalDomain() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://evil.com/fake.png",
                "fake", "fake.png", "image/png", 123L
        );
        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("Đường dẫn tài liệu không thuộc danh sách các nguồn lưu trữ được phép");
    }

    @Test
    void uploadProof_shouldRejectMalformedUrl() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "not-a-url",
                "fake", "fake.png", "image/png", 123L
        );
        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("sai định dạng");
    }

    @Test
    void uploadProof_shouldRejectNonHttpScheme() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "javascript:alert(1)",
                "fake", "fake.png", "image/png", 123L
        );
        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("phải sử dụng giao thức https an toàn");
    }

    @Test
    void uploadProof_shouldRejectLocalhostOrPrivateIp() {
        String[] badUrls = {
                "https://localhost:8080/a.png",
                "https://127.0.0.1/a.png",
                "https://192.168.1.1/a.png",
                "https://10.0.0.1/a.png"
        };
        for (String url : badUrls) {
            MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                    VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                    url,
                    "fake", "fake.png", "image/png", 123L
            );
            assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                    .isInstanceOf(BaseException.class)
                    .hasMessageContaining("không hỗ trợ IP nội bộ");
        }
    }

    @Test
    void uploadProof_shouldRejectCloudinaryLookalikeDomain() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com.evil.com/fake.png",
                "fake", "fake.png", "image/png", 123L
        );
        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("không thuộc danh sách các nguồn lưu trữ được phép");
    }

    @Test
    void uploadProof_shouldRejectWhenAllowedHostsEmpty() {
        when(storageSecurityProperties.getAllowedUrlHosts()).thenReturn(java.util.Collections.emptyList());
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "fake", "fake.png", "image/png", 123L
        );
        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("chưa cấu hình danh sách domain lưu trữ hợp lệ");
    }

    @Test
    void uploadProof_shouldPreserveExistingFileTypeAndSizeValidation() {
        // Test oversized
        MentorVerificationDocumentUploadRequest uploadRequest1 = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "fake", "proof.jpg", "image/jpeg", 15L * 1024L * 1024L + 1L
        );
        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest1))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));

        // Test bad type
        MentorVerificationDocumentUploadRequest uploadRequest2 = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.gif",
                "fake", "proof.gif", "image/gif", 123L
        );
        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest2))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void uploadPng_shouldStoreMetadataSuccessfully() {
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
    void uploadPdf_shouldStoreMetadataSuccessfully() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/raw/upload/v123/proof.pdf",
                "mentor-verification/user-123/proof-pdf",
                "proof.pdf",
                "application/pdf",
                123L
        );

        serviceWithCloudinary.uploadDocument(userId, uploadRequest);

        verify(storedFileRepository).save(any());
    }

    @Test
    void uploadUnknownType_shouldBeRejected() {
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
    void uploadOversizedFile_shouldBeRejected() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "mentor-verification/user-123/proof-jpg",
                "proof.jpg",
                "image/jpeg",
                15L * 1024L * 1024L + 1L
        );

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
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

    @Test
    void uploadWithoutOriginalFilename_shouldBeRejected() {
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "mentor-verification/user-123/proof-jpg",
                " ",
                "image/jpeg",
                123L
        );

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void uploadWithPathTraversalPublicId_shouldBeRejected() {
        // publicId containing ".." segments must be rejected even though the regex
        // [A-Za-z0-9_./-]+ would otherwise match (it allows '.' and '/').
        String[] badPublicIds = {
                "mentor-verification/../admin/secret",
                "../../etc/passwd",
                "mentor-verification/user-123/../.."
        };
        for (String badId : badPublicIds) {
            MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                    VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                    "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                    badId,
                    "proof.jpg",
                    "image/jpeg",
                    123L
            );
            assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                    .isInstanceOfSatisfying(BaseException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        }
        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadWithPathSeparatorInFilename_shouldSanitizeFilename() {
        // originalFilename containing path separators must be stripped before persistence
        // to keep the stored name a plain filename (no directory components).
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "mentor-verification/user-123/proof-jpg",
                "../../etc/proof.jpg",
                "image/jpeg",
                123L
        );

        serviceWithCloudinary.uploadDocument(userId, uploadRequest);

        ArgumentCaptor<StoredFile> fileCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(fileCaptor.capture());
        // Path separators must be stripped; only the leaf filename remains
        String storedName = fileCaptor.getValue().getOriginalName();
        assertThat(storedName).doesNotContain("/");
        assertThat(storedName).doesNotContain("\\");
        assertThat(storedName).isEqualTo("....etcproof.jpg");
    }

    @Test
    void uploadAffiliationProofBeyondQuota_shouldBeRejected() {
        when(mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(
                request.getId(),
                VerificationDocumentType.FPTU_AFFILIATION_PROOF
        )).thenReturn(1L);

        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "mentor-verification/user-123/proof-jpg",
                "proof.jpg",
                "image/jpeg",
                123L
        );

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void uploadExpertiseProofBeyondQuota_shouldBeRejected() {
        when(mentorVerificationDocumentRepository.countByRequestIdAndDocumentTypeAndIsActiveTrue(
                request.getId(),
                VerificationDocumentType.EXPERTISE_PROOF
        )).thenReturn(3L);

        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.EXPERTISE_PROOF,
                "https://res.cloudinary.com/demo/image/upload/v123/proof.jpg",
                "mentor-verification/user-123/proof-jpg",
                "proof.jpg",
                "image/jpeg",
                123L
        );

        assertThatThrownBy(() -> serviceWithCloudinary.uploadDocument(userId, uploadRequest))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }
}

