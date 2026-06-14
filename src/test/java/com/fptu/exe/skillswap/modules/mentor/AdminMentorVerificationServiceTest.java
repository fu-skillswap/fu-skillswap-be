package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.AdminMentorVerificationQueueProjection;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorVerificationService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMentorVerificationServiceTest {

    private MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private MentorProfileRepository mentorProfileRepository;
    private UserRepository userRepository;
    private StudentProfileRepository studentProfileRepository;
    private AdminMentorVerificationService adminMentorVerificationService;

    @BeforeEach
    void setUp() {
        mentorVerificationRequestRepository = mock(MentorVerificationRequestRepository.class);
        mentorVerificationDocumentRepository = mock(MentorVerificationDocumentRepository.class);
        mentorVerificationRequestEventRepository = mock(MentorVerificationRequestEventRepository.class);
        mentorProfileRepository = mock(MentorProfileRepository.class);
        userRepository = mock(UserRepository.class);
        studentProfileRepository = mock(StudentProfileRepository.class);

        adminMentorVerificationService = new AdminMentorVerificationService(
                mentorVerificationRequestRepository,
                mentorVerificationDocumentRepository,
                mentorVerificationRequestEventRepository,
                mentorProfileRepository,
                userRepository,
                studentProfileRepository
        );
    }

    @Test
    void getQueue_shouldUseProjectionBasedSearch() {
        AdminMentorVerificationQueueFilterRequest filterRequest = new AdminMentorVerificationQueueFilterRequest();
        filterRequest.setKeyword("mentor");
        filterRequest.setStatus(VerificationStatus.PENDING_REVIEW);

        AdminMentorVerificationQueueProjection projection = new AdminMentorVerificationQueueProjection() {
            @Override
            public UUID getRequestId() { return UUID.randomUUID(); }
            @Override
            public UUID getMentorUserId() { return UUID.randomUUID(); }
            @Override
            public String getMentorEmail() { return "mentor@fpt.edu.vn"; }
            @Override
            public String getMentorFullName() { return "Mentor User"; }
            @Override
            public String getMentorAvatarUrl() { return "https://example.com/avatar.jpg"; }
            @Override
            public VerificationStatus getStatus() { return VerificationStatus.PENDING_REVIEW; }
            @Override
            public Integer getRevisionCount() { return 2; }
            @Override
            public LocalDateTime getSubmittedAt() { return LocalDateTime.now().minusHours(2); }
            @Override
            public LocalDateTime getCreatedAt() { return LocalDateTime.now().minusDays(1); }
            @Override
            public LocalDateTime getUpdatedAt() { return LocalDateTime.now().minusHours(1); }
        };

        when(mentorVerificationRequestRepository.searchAdminQueue(
                eq(VerificationStatus.PENDING_REVIEW),
                eq("mentor"),
                eq(null),
                eq(null),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(projection), PageRequest.of(0, 20), 1));

        PageResponse<?> response = adminMentorVerificationService.getQueue(filterRequest);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void requestRevision_pendingReview_shouldMoveToNeedsRevision() {
        UUID adminId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@fpt.edu.vn").fullName("Admin").build();
        User mentor = User.builder().id(mentorId).email("mentor@fpt.edu.vn").fullName("Mentor").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(requestId)
                .mentor(mentor)
                .status(VerificationStatus.PENDING_REVIEW)
                .submittedAt(LocalDateTime.now().minusHours(1))
                .lockedBy(admin)
                .lockedAt(LocalDateTime.now().minusMinutes(1))
                .lockExpiresAt(LocalDateTime.now().plusMinutes(4))
                .build();
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setUser(mentor);
        mentorProfile.setStatus(MentorStatus.PENDING_VERIFICATION);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenReturn(request);
        when(mentorProfileRepository.findWithUserByUserId(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenReturn(mentorProfile);
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(requestId)).thenReturn(List.of());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());
        when(studentProfileRepository.existsById(mentorId)).thenReturn(true);

        AdminMentorVerificationRequestResponse response = adminMentorVerificationService.requestRevision(
                adminId,
                requestId,
                "Bổ sung minh chứng rõ nét hơn"
        );

        assertThat(response.status()).isEqualTo(VerificationStatus.NEEDS_REVISION);
        assertThat(response.reviewNote()).isEqualTo("Bổ sung minh chứng rõ nét hơn");
        assertThat(mentorProfile.getStatus()).isEqualTo(MentorStatus.DRAFT);
        assertThat(request.getReviewedBy()).isEqualTo(admin);
        assertThat(request.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_pendingReview_shouldActivateMentorProfile() {
        UUID adminId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@fpt.edu.vn").fullName("Admin").build();
        User mentor = User.builder().id(mentorId).email("mentor@fpt.edu.vn").fullName("Mentor").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(requestId)
                .mentor(mentor)
                .status(VerificationStatus.PENDING_REVIEW)
                .lockedBy(admin)
                .lockedAt(LocalDateTime.now().minusMinutes(1))
                .lockExpiresAt(LocalDateTime.now().plusMinutes(4))
                .build();
        MentorProfile mentorProfile = new MentorProfile();
        mentorProfile.setUser(mentor);
        mentorProfile.setStatus(MentorStatus.PENDING_VERIFICATION);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenReturn(request);
        when(mentorProfileRepository.findWithUserByUserId(mentorId)).thenReturn(Optional.of(mentorProfile));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenReturn(mentorProfile);
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(requestId)).thenReturn(List.of());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());
        when(studentProfileRepository.existsById(mentorId)).thenReturn(true);

        AdminMentorVerificationRequestResponse response = adminMentorVerificationService.approve(adminId, requestId, "Hồ sơ hợp lệ");

        assertThat(response.status()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(response.reviewNote()).isEqualTo("Hồ sơ hợp lệ");
        assertThat(response.reviewerEmail()).isEqualTo("admin@fpt.edu.vn");
        assertThat(request.getApprovedAt()).isNotNull();
        assertThat(mentorProfile.getStatus()).isEqualTo(MentorStatus.ACTIVE);
    }

    @Test
    void reject_nonPendingReview_shouldThrowBadRequest() {
        UUID adminId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@fpt.edu.vn").fullName("Admin").build();
        User mentor = User.builder().id(mentorId).email("mentor@fpt.edu.vn").fullName("Mentor").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(requestId)
                .mentor(mentor)
                .status(VerificationStatus.NEEDS_REVISION)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminMentorVerificationService.reject(adminId, requestId, "Không đạt"))
                .isInstanceOf(BaseException.class)
                .hasMessage("Chỉ có thể review hồ sơ đang chờ duyệt");
    }

    @Test
    void getRequestDetail_unlockedPendingReview_shouldClaimLockForCurrentAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@fpt.edu.vn").fullName("Admin").build();
        User mentor = User.builder().id(mentorId).email("mentor@fpt.edu.vn").fullName("Mentor").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(requestId)
                .mentor(mentor)
                .status(VerificationStatus.PENDING_REVIEW)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(mentorVerificationRequestRepository.save(any(MentorVerificationRequest.class))).thenReturn(request);
        when(mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(requestId)).thenReturn(List.of());
        when(mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());
        when(studentProfileRepository.existsById(mentorId)).thenReturn(true);

        AdminMentorVerificationRequestResponse response = adminMentorVerificationService.getRequestDetail(adminId, requestId);

        assertThat(request.getLockedBy()).isEqualTo(admin);
        assertThat(request.getLockExpiresAt()).isNotNull();
        assertThat(response.lockedByAdminEmail()).isEqualTo("admin@fpt.edu.vn");
        assertThat(response.canReview()).isTrue();
    }

    @Test
    void approve_lockedByOtherAdmin_shouldThrowConflict() {
        UUID adminId = UUID.randomUUID();
        UUID otherAdminId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@fpt.edu.vn").fullName("Admin").build();
        User otherAdmin = User.builder().id(otherAdminId).email("other-admin@fpt.edu.vn").fullName("Other Admin").build();
        User mentor = User.builder().id(mentorId).email("mentor@fpt.edu.vn").fullName("Mentor").build();
        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(requestId)
                .mentor(mentor)
                .status(VerificationStatus.PENDING_REVIEW)
                .lockedBy(otherAdmin)
                .lockedAt(LocalDateTime.now().minusMinutes(1))
                .lockExpiresAt(LocalDateTime.now().plusMinutes(4))
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminMentorVerificationService.approve(adminId, requestId, "ok"))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("đang được admin khác xử lý");
    }
}
