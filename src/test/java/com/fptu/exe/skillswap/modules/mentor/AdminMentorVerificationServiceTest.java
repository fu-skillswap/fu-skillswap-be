package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.AdminMentorVerificationQueueProjection;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorVerificationService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
class AdminMentorVerificationServiceTest {

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
    private NotificationService notificationService;

    @InjectMocks
    private AdminMentorVerificationService adminMentorVerificationService;

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
            // Expected to fail at assertReviewLockOwnership or missing profile, 
            // but findByIdForUpdate should have been called first.
        }

        org.mockito.Mockito.verify(mentorVerificationRequestRepository).findByIdForUpdate(requestId);
    }

    @Test
    void approveVerification_shouldNotifyApplicant() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = pendingLockedRequest(admin, mentor);
        MentorProfile mentorProfile = MentorProfile.builder().userId(mentor.getId()).user(mentor).build();

        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorVerificationRequestEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mentorProfileRepository.findWithUserByUserId(mentor.getId())).thenReturn(Optional.of(mentorProfile));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(mentor)).thenReturn(mentor);
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())).thenReturn(List.of());
        when(mentorProfileService.getMyProfile(mentor.getId())).thenReturn(null);
        when(academicService.hasCompletedStudentProfile(mentor.getId())).thenReturn(false);
        when(mentorProfileService.hasCompletedMentorProfile(mentor.getId())).thenReturn(false);
        when(academicService.getStudentProfile(mentor.getId())).thenThrow(new ResourceNotFoundException("Not found"));

        adminMentorVerificationService.approve(admin.getId(), request.getId(), "OK");

        verify(notificationService).createNotification(
                eq(mentor.getId()),
                eq(NotificationType.MENTOR_VERIFICATION_APPROVED),
                eq("Yêu cầu trở thành mentor đã được duyệt"),
                eq("Hồ sơ mentor của bạn đã được duyệt. Bạn có thể bắt đầu nhận yêu cầu đặt lịch."),
                eq("MENTOR_VERIFICATION"),
                eq(request.getId())
        );
    }

    @Test
    void rejectVerification_shouldNotifyApplicant() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = pendingLockedRequest(admin, mentor);
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

        verify(notificationService).createNotification(
                eq(mentor.getId()),
                eq(NotificationType.MENTOR_VERIFICATION_REJECTED),
                eq("Yêu cầu trở thành mentor đã bị từ chối"),
                eq("Hồ sơ mentor của bạn chưa được duyệt. Vui lòng xem lý do từ chối và cập nhật lại nếu cần."),
                eq("MENTOR_VERIFICATION"),
                eq(request.getId())
        );
    }

    @Test
    void requestRevision_shouldNotifyApplicant() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com").fullName("Admin").build();
        User mentor = User.builder().id(UUID.randomUUID()).email("mentor@test.com").fullName("Mentor").build();
        MentorVerificationRequest request = pendingLockedRequest(admin, mentor);
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

        verify(notificationService).createNotification(
                eq(mentor.getId()),
                eq(NotificationType.MENTOR_VERIFICATION_NEEDS_REVISION),
                eq("Hồ sơ mentor cần được bổ sung"),
                eq("Hồ sơ mentor của bạn cần được bổ sung thông tin trước khi xét duyệt."),
                eq("MENTOR_VERIFICATION"),
                eq(request.getId())
        );
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

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    private MentorVerificationRequest pendingLockedRequest(User admin, User mentor) {
        return MentorVerificationRequest.builder()
                .id(UUID.randomUUID())
                .mentor(mentor)
                .status(VerificationStatus.PENDING_REVIEW)
                .method(VerificationMethod.MANUAL)
                .lockedBy(admin)
                .lockExpiresAt(DateTimeUtil.now().plusMinutes(5))
                .build();
    }
}
