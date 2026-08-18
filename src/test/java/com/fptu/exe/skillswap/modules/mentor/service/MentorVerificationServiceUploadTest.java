package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingEligibilityPolicy;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationUploadIntent;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationUploadIntentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadIntentRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationUploadIntentRepository;
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
    private MentorVerificationUploadIntentRepository uploadIntentRepository;
    @Mock
    private ObjectProvider<StorageGateway> r2StorageProvider;
    @Mock
    private StorageGateway storageGateway;
    @Mock
    private MentorServiceRepository mentorServiceRepository;
    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    @Mock
    private BookingEligibilityPolicy bookingEligibilityPolicy;

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
        lenient().when(uploadIntentRepository.save(any())).thenAnswer(invocation -> {
            MentorVerificationUploadIntent intent = invocation.getArgument(0);
            if (intent.getId() == null) intent.setId(UUID.randomUUID());
            return intent;
        });
        lenient().when(academicService.hasCompletedStudentProfile(userId)).thenReturn(true);
        lenient().when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(true);
        lenient().when(r2StorageProvider.getIfAvailable()).thenReturn(storageGateway);
        lenient().when(storageGateway.resolvePublicUrl(any())).thenAnswer(invocation -> "https://cdn.skillswap.com/" + invocation.getArgument(0));
        lenient().when(storageGateway.storageProviderName()).thenReturn("R2");
        lenient().when(storageGateway.generatePrivateUploadUrl(any(), any(), any())).thenAnswer(invocation ->
                new StorageGateway.PrivatePresignedUpload("https://private-upload.example/test", invocation.getArgument(0),
                        java.time.Instant.now().plusSeconds(900)));
        lenient().when(storageGateway.headObject(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String ct = key != null && key.toLowerCase().endsWith(".jpg") ? "image/jpeg" : "image/jpeg";
            return new StorageGateway.ObjectMetadata(key, ct, 123L, java.util.Collections.emptyMap());
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
                r2StorageProvider,
                uploadIntentRepository,
                mentorServiceRepository,
                mentorAvailabilitySlotRepository,
                bookingEligibilityPolicy
        );
        ReflectionTestUtils.setField(service, "mentorTermsVersion", "SKILLSWAP_MENTOR_TERMS_V1");
        ReflectionTestUtils.setField(service, "requireCompletedStudentProfile", false);
        ReflectionTestUtils.setField(service, "requireCompletedMentorProfile", false);
    }

    @Test
    void uploadProof_shouldConfirmOwnedPrivateUploadIntentAfterStorageVerification() {
        MentorVerificationUploadIntent intent = validIntent();
        when(uploadIntentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                intent.getId()
        );

        service.uploadDocument(userId, uploadRequest);

        ArgumentCaptor<StoredFile> fileCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileRepository).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStorageProvider()).isEqualTo("R2");
        assertThat(fileCaptor.getValue().getStorageKey()).isEqualTo(intent.getStorageKey());
        assertThat(fileCaptor.getValue().getPublicUrl()).isEqualTo("private://" + intent.getStorageKey());
        assertThat(intent.getStatus()).isEqualTo(MentorVerificationUploadIntentStatus.CONFIRMED);
    }

    @Test
    void uploadProof_shouldRejectUploadIntentOwnedByAnotherUser() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        MentorVerificationUploadIntent intent = validIntent();
        intent.setOwner(otherUser);
        when(uploadIntentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                intent.getId()
        );

        assertThatThrownBy(() -> service.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(storedFileRepository, never()).save(any());
    }

    @Test
    void uploadProof_shouldRejectExpiredUploadIntent() {
        MentorVerificationUploadIntent intent = validIntent();
        intent.setStatus(MentorVerificationUploadIntentStatus.EXPIRED);
        when(uploadIntentRepository.findByIdForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                intent.getId()
        );

        assertThatThrownBy(() -> service.uploadDocument(userId, uploadRequest))
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

        MentorVerificationUploadIntent intent = validIntent();
        MentorVerificationDocumentUploadRequest uploadRequest = new MentorVerificationDocumentUploadRequest(
                VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                intent.getId()
        );

        assertThatThrownBy(() -> service.uploadDocument(userId, uploadRequest))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void createUploadIntent_shouldKeepStorageKeyServerOwned() {
        var response = service.createDocumentUploadIntent(userId,
                new MentorVerificationDocumentUploadIntentRequest("proof.jpg", "image/jpeg", 123L));

        assertThat(response.uploadIntentId()).isNotNull();
        assertThat(response.uploadUrl()).isEqualTo("https://private-upload.example/test");
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
        verify(storageGateway).generatePrivateUploadUrl(any(), eq("image/jpeg"), any());
    }

    private MentorVerificationUploadIntent validIntent() {
        return MentorVerificationUploadIntent.builder()
                .id(UUID.randomUUID())
                .owner(user)
                .storageKey("skillswap/verification-documents/users/" + userId + "/proof.jpg")
                .originalFilename("proof.jpg")
                .expectedContentType("image/jpeg")
                .expectedSizeBytes(123L)
                .expiresAt(com.fptu.exe.skillswap.shared.util.DateTimeUtil.now().plusMinutes(10))
                .status(MentorVerificationUploadIntentStatus.PENDING_UPLOAD)
                .build();
    }
}
