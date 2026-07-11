package com.fptu.exe.skillswap.modules.admin;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.event.MentorVerificationEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.AdminMentorVerificationQueueProjection;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.admin.service.AdminMentorVerificationModerationService;
import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMentorVerificationModerationServiceTest {

    @Mock
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;
    @Mock
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    @Mock
    private MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AcademicService academicService;
    @Mock
    private MentorProfileService mentorProfileService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AdminAuditWriterService adminAuditWriterService;

    @InjectMocks
    private AdminMentorVerificationModerationService adminMentorVerificationService;

    @Test
    void getQueue_whenNoRequestExists_shouldReturnEmptyPage() {
        when(mentorVerificationRequestRepository.findAdminQueueWithoutKeyword(
                eq(VerificationStatus.PENDING_REVIEW),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "submittedAt"))));

        PageResponse<AdminMentorVerificationQueueItemResponse> response =
                adminMentorVerificationService.getQueue(new AdminMentorVerificationQueueFilterRequest());

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    void getQueue_whenFilterHasNoResult_shouldReturnEmptyPage() {
        AdminMentorVerificationQueueFilterRequest request = new AdminMentorVerificationQueueFilterRequest();
        request.setKeyword("no-match");

        when(mentorVerificationRequestRepository.searchAdminQueue(
                eq(VerificationStatus.PENDING_REVIEW),
                eq("no-match"),
                eq("no match"),
                eq("%no-match%"),
                eq("%no match%"),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "submittedAt"))));

        PageResponse<AdminMentorVerificationQueueItemResponse> response =
                adminMentorVerificationService.getQueue(request);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    void getRequestDetail_whenRequestIdNotFound_shouldThrowNotFound() {
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@test.com").build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminMentorVerificationService.getRequestDetail(adminId, requestId))
                .isInstanceOfSatisfying(BaseException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Không tìm thấy hồ sơ xác thực mentor");
                });
    }

    @Test
    void approveRejectRequest_shouldUsePessimisticLock() {
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@test.com").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(requestId)
                .status(VerificationStatus.PENDING_REVIEW)
                .mentor(User.builder().id(UUID.randomUUID()).build())
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        try {
            adminMentorVerificationService.approve(adminId, requestId, "OK");
        } catch (BaseException ignored) {
            // Expected to fail at assertReviewLockOwnership or missing profile
        }

        verify(mentorVerificationRequestRepository).findByIdForUpdate(requestId);
    }

    @Test
    void approveVerification_shouldNotifyApplicant() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = pendingLockedRequest(admin, mentor);
        request.setSubmittedAt(DateTimeUtil.now().minusMinutes(1));
        request.setTermsAcceptedAt(DateTimeUtil.now().minusMinutes(1));
        request.setTermsVersion("SKILLSWAP_MENTOR_TERMS_V1");
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentor.getId()).user(mentor).build();

        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorVerificationRequestEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorProfileRepository.findWithUserByUserId(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(mentor)).thenReturn(mentor);
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(request.getId())).thenReturn(List.of(
                verificationDocument(request, mentor, VerificationDocumentType.FPTU_AFFILIATION_PROOF),
                verificationDocument(request, mentor, VerificationDocumentType.EXPERTISE_PROOF)
        ));
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorProfileService.getMyProfile(mentor.getId())).thenReturn(null);
        when(academicService.hasCompletedStudentProfile(mentor.getId())).thenReturn(true);
        when(mentorProfileService.hasCompletedMentorProfile(mentor.getId())).thenReturn(true);
        when(academicService.getStudentProfile(mentor.getId())).thenThrow(new ResourceNotFoundException("Not found"));

        adminMentorVerificationService.approve(admin.getId(), request.getId(), "OK");
        ArgumentCaptor<MentorVerificationRequest> requestCaptor = ArgumentCaptor.forClass(MentorVerificationRequest.class);

        verify(mentorVerificationRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(requestCaptor.getValue().getLockedBy()).isNull();
        assertThat(requestCaptor.getValue().getLockExpiresAt()).isNull();
        

        
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).hasSize(2);
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance)
                .findFirst()
                .orElseThrow();
        MentorVerificationEmailNotificationEvent emailEvent = (MentorVerificationEmailNotificationEvent) eventCaptor.getAllValues().stream()
                .filter(MentorVerificationEmailNotificationEvent.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(notificationEvent.recipientUserId()).isEqualTo(mentor.getId());
        assertThat(notificationEvent.type()).isEqualTo(NotificationType.MENTOR_VERIFICATION_APPROVED);
        assertThat(notificationEvent.relatedEntityId()).isEqualTo(request.getId());
        assertThat(emailEvent.getEventType()).isEqualTo(MentorVerificationEmailNotificationEvent.EventType.APPROVED_EMAIL);
        assertThat(emailEvent.getRecipientEmail()).isEqualTo("mentor@test.com");
    }

    @Test
    void rejectVerification_shouldNotifyApplicant() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = pendingLockedRequest(admin, mentor);
        request.setSubmittedAt(DateTimeUtil.now().minusMinutes(1));
        request.setTermsAcceptedAt(DateTimeUtil.now().minusMinutes(1));
        request.setTermsVersion("SKILLSWAP_MENTOR_TERMS_V1");
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentor.getId()).user(mentor).build();

        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorVerificationRequestEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorProfileRepository.findWithUserByUserId(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorProfileService.getMyProfile(mentor.getId())).thenReturn(null);
        when(academicService.hasCompletedStudentProfile(mentor.getId())).thenReturn(false);
        when(mentorProfileService.hasCompletedMentorProfile(mentor.getId())).thenReturn(false);
        when(academicService.getStudentProfile(mentor.getId())).thenThrow(new ResourceNotFoundException("Not found"));

        adminMentorVerificationService.reject(admin.getId(), request.getId(), "Missing proof");
        ArgumentCaptor<MentorVerificationRequest> requestCaptor = ArgumentCaptor.forClass(MentorVerificationRequest.class);

        verify(mentorVerificationRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(requestCaptor.getValue().getLockedBy()).isNull();
        assertThat(requestCaptor.getValue().getLockExpiresAt()).isNull();



        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.recipientUserId()).isEqualTo(mentor.getId());
        assertThat(notificationEvent.type()).isEqualTo(NotificationType.MENTOR_VERIFICATION_REJECTED);
        assertThat(notificationEvent.relatedEntityId()).isEqualTo(request.getId());
    }

    @Test
    void requestRevision_shouldNotifyApplicant() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = pendingLockedRequest(admin, mentor);
        request.setSubmittedAt(DateTimeUtil.now().minusMinutes(1));
        request.setTermsAcceptedAt(DateTimeUtil.now().minusMinutes(1));
        request.setTermsVersion("SKILLSWAP_MENTOR_TERMS_V1");
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentor.getId()).user(mentor).build();

        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorVerificationRequestEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorProfileRepository.findWithUserByUserId(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorProfileService.getMyProfile(mentor.getId())).thenReturn(null);
        when(academicService.hasCompletedStudentProfile(mentor.getId())).thenReturn(false);
        when(mentorProfileService.hasCompletedMentorProfile(mentor.getId())).thenReturn(false);
        when(academicService.getStudentProfile(mentor.getId())).thenThrow(new ResourceNotFoundException("Not found"));

        adminMentorVerificationService.requestRevision(admin.getId(), request.getId(), "Need more info");
        ArgumentCaptor<MentorVerificationRequest> requestCaptor = ArgumentCaptor.forClass(MentorVerificationRequest.class);

        verify(mentorVerificationRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(VerificationStatus.NEEDS_REVISION);
        assertThat(requestCaptor.getValue().getLockedBy()).isNull();
        assertThat(requestCaptor.getValue().getLockExpiresAt()).isNull();



        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).hasSize(2);
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance)
                .findFirst()
                .orElseThrow();
        MentorVerificationEmailNotificationEvent emailEvent = (MentorVerificationEmailNotificationEvent) eventCaptor.getAllValues().stream()
                .filter(MentorVerificationEmailNotificationEvent.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(notificationEvent.recipientUserId()).isEqualTo(mentor.getId());
        assertThat(notificationEvent.type()).isEqualTo(NotificationType.MENTOR_VERIFICATION_NEEDS_REVISION);
        assertThat(emailEvent.getEventType()).isEqualTo(MentorVerificationEmailNotificationEvent.EventType.NEEDS_REVISION_EMAIL);
        assertThat(emailEvent.getRecipientEmail()).isEqualTo("mentor@test.com");
        assertThat(emailEvent.getReviewNote()).isEqualTo("Need more info");
    }

    @Test
    void failedVerificationAction_shouldNotCreateNotification() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(mentor)
                .status(VerificationStatus.APPROVED)
                .build();

        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminMentorVerificationService.approve(admin.getId(), request.getId(), "OK"))
                .isInstanceOf(BaseException.class);

        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void refreshLock_nonOwner_shouldThrowConflict() {
        User ownerAdmin = User.builder().id(UUID.randomUUID()).email("owner@test.com").fullName("Owner").build();
        User anotherAdmin = User.builder().id(UUID.randomUUID()).email("another@test.com").fullName("Another").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = pendingLockedRequest(ownerAdmin, mentor);

        when(userRepository.findById(anotherAdmin.getId())).thenReturn(Optional.of(anotherAdmin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminMentorVerificationService.refreshLock(anotherAdmin.getId(), request.getId()))
                .isInstanceOfSatisfying(BaseException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT));
    }

    @Test
    void getRequestDetail_expiredLock_shouldClaimForCurrentAdmin() {
        User previousAdmin = User.builder().id(UUID.randomUUID()).email("old-admin@test.com").fullName("Old Admin").build();
        User currentAdmin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(mentor)
                .status(VerificationStatus.PENDING_REVIEW)
                .method(VerificationMethod.MANUAL)
                .lockedBy(previousAdmin)
                .lockedAt(DateTimeUtil.now().minusMinutes(10))
                .lockExpiresAt(DateTimeUtil.now().minusMinutes(1))
                .build();

        when(userRepository.findById(currentAdmin.getId())).thenReturn(Optional.of(currentAdmin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorProfileService.getMyProfile(mentor.getId())).thenReturn(null);
        when(academicService.hasCompletedStudentProfile(mentor.getId())).thenReturn(false);
        when(mentorProfileService.hasCompletedMentorProfile(mentor.getId())).thenReturn(false);
        when(academicService.getStudentProfile(mentor.getId())).thenThrow(new ResourceNotFoundException("Not found"));

        adminMentorVerificationService.getRequestDetail(currentAdmin.getId(), request.getId());

        ArgumentCaptor<MentorVerificationRequest> requestCaptor = ArgumentCaptor.forClass(MentorVerificationRequest.class);
        verify(mentorVerificationRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getLockedBy()).isEqualTo(currentAdmin);
        assertThat(requestCaptor.getValue().getLockExpiresAt()).isAfter(DateTimeUtil.now());
    }

    private MentorVerificationRequest pendingLockedRequest(User admin, User mentor) {
        return MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(mentor)
                .status(VerificationStatus.PENDING_REVIEW)
                .method(VerificationMethod.MANUAL)
                .lockedBy(admin)
                .lockExpiresAt(DateTimeUtil.now().plusDays(1))
                .build();
    }

    private MentorVerificationDocument verificationDocument(
            MentorVerificationRequest request,
            User mentor,
            VerificationDocumentType documentType
    ) {
        StoredFile storedFile = StoredFile.builder()
                .originalName(documentType.name().toLowerCase() + ".png")
                .publicUrl("https://localhost:8080/uploads/storage/" + documentType.name().toLowerCase() + ".png")
                .mimeType("image/png")
                .sizeBytes(1024L)
                .build();
        return MentorVerificationDocument.builder()
                .id(UUID.randomUUID())
                .request(request)
                .documentType(documentType)
                .status(VerificationDocumentStatus.UPLOADED)
                .storageKind(VerificationStorageKind.IMAGE)
                .storedFile(storedFile)
                .isActive(true)
                .version(1)
                .uploadedBy(mentor)
                .build();
    }
}
