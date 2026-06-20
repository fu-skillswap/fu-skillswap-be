package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
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
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private AdminMentorVerificationService adminMentorVerificationService;

    @Test
    void getQueue_whenNoRequestExists_shouldReturnEmptyPage() {
        when(mentorVerificationRequestRepository.findAdminQueueWithoutKeyword(
                eq(VerificationStatus.PENDING_REVIEW),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"))));

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
        )).thenReturn(Page.empty(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"))));

        PageResponse<AdminMentorVerificationQueueItemResponse> response =
                adminMentorVerificationService.getQueue(request);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
    }

    @Test
    void approve_whenRequestLockedByAnotherAdmin_shouldThrowConflict() {
        UUID actingAdminId = UUID.randomUUID();
        UUID ownerAdminId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        User actingAdmin = User.builder().id(actingAdminId).email("acting-admin@test.com").build();
        User ownerAdmin = User.builder().id(ownerAdminId).email("owner-admin@test.com").build();
        User mentor = User.builder().id(mentorId).email("mentor@test.com").build();

        MentorVerificationRequest request = MentorVerificationRequest.builder()
                .id(requestId)
                .mentor(mentor)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.PENDING_REVIEW)
                .lockedBy(ownerAdmin)
                .lockExpiresAt(LocalDateTime.now().plusMinutes(3))
                .build();

        when(userRepository.findById(actingAdminId)).thenReturn(Optional.of(actingAdmin));
        when(mentorVerificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> adminMentorVerificationService.approve(actingAdminId, requestId, "ok"))
                .isInstanceOfSatisfying(BaseException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
                    assertThat(ex.getMessage()).contains("admin khác xử lý");
                });
    }

    @Test
    void getRequestDetail_whenRequestIdNotFound_shouldThrowNotFound() {
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User admin = User.builder().id(adminId).email("admin@test.com").build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(mentorVerificationRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminMentorVerificationService.getRequestDetail(adminId, requestId))
                .isInstanceOfSatisfying(BaseException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(ex.getMessage()).contains("Không tìm thấy hồ sơ xác thực mentor");
                });
    }
}
