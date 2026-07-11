package com.fptu.exe.skillswap.modules.admin;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.admin.service.AdminMentorModerationService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMentorModerationServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @InjectMocks
    private AdminMentorModerationService adminMentorService;

    @Test
    void getMentors_shouldTrimKeywordAndQueryDirectly() {
        AdminMentorListItemResponse listItem = AdminMentorListItemResponse.builder()
                .mentorUserId(UUID.randomUUID())
                .displayName("Mentor One")
                .email("mentor@test.com")
                .avatarUrl("avatar")
                .primaryLabel("CNTT")
                .completedSessions(20)
                .ratingAverage(BigDecimal.valueOf(4.9))
                .mentorStatus(MentorStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        AdminMentorListRequest request = new AdminMentorListRequest();
        request.setKeyword(" backend ");
        request.setStatus(MentorStatus.ACTIVE);
        request.setIsAvailable(true);

        when(mentorProfileRepository.searchForAdmin(
                eq("%backend%"),
                eq("%backend%"),
                any(),
                any(),
                eq(MentorStatus.ACTIVE),
                eq(true),
                any(Pageable.class)
        ))
                .thenReturn(new PageImpl<>(List.of(listItem)));

        PageResponse<AdminMentorListItemResponse> response = adminMentorService.getMentors(request);

        assertEquals(1, response.getContent().size());
        assertEquals("Mentor One", response.getContent().getFirst().displayName());
        assertEquals(MentorStatus.ACTIVE, response.getContent().getFirst().mentorStatus());
        assertEquals("CNTT", response.getContent().getFirst().primaryLabel());
    }

    @Test
    void getMentors_nullRequest_shouldUseDefaultSortByUpdatedAt() {
        when(mentorProfileRepository.searchForAdmin(eq(null), eq(null), any(), any(), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        adminMentorService.getMentors(null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(mentorProfileRepository).searchForAdmin(eq(null), eq(null), any(), any(), eq(null), eq(null), captor.capture());
        assertEquals("updatedAt: DESC", captor.getValue().getSort().toString());
    }

    @Test
    void getMentorDetail_shouldReturnFullDetails() {
        UUID mentorId = UUID.randomUUID();
        User user = User.builder()
                .id(mentorId)
                .email("mentor@test.com")
                .fullName("Mentor Full Name")
                .avatarUrl("avatar")
                .status(UserStatus.ACTIVE)
                .build();

        MentorProfile profile = MentorProfile.builder()
                .userId(mentorId)
                .user(user)
                .status(MentorStatus.ACTIVE)
                .isAvailable(true)
                .headline("My Headline")
                .expertiseDescription("My Expertise")
                .supportingSubjects("Java, Spring")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .averageRating(BigDecimal.valueOf(4.5))
                .totalReviews(10)
                .totalCompletedSessions(15)
                .totalRejectedBookings(2)
                .lateCancellationPenaltyPoints(BigDecimal.valueOf(1.0))
                .portfolioUrl("portfolio")
                .linkedinUrl("linkedin")
                .githubUrl("github")
                .phoneNumber("123456789")
                .verifiedAt(LocalDateTime.now().minusDays(5))
                .build();

        AcademicProgram program = AcademicProgram.builder()
                .code("CNTT")
                .nameVi("Công nghệ thông tin")
                .build();

        StudentProfile studentProfile = StudentProfile.builder()
                .userId(mentorId)
                .program(program)
                .build();

        when(mentorProfileRepository.findWithUserByUserId(mentorId))
                .thenReturn(Optional.of(profile));
        when(studentProfileRepository.findWithDetailsByUserId(mentorId))
                .thenReturn(Optional.of(studentProfile));

        AdminMentorDetailResponse detail = adminMentorService.getMentorDetail(mentorId);

        assertEquals(mentorId, detail.mentorUserId());
        assertEquals("mentor@test.com", detail.email());
        assertEquals("Mentor Full Name", detail.displayName());
        assertEquals("My Headline", detail.headline());
        assertEquals("CNTT", detail.primaryLabel());
        assertEquals(15, detail.completedSessions());
        assertEquals(2, detail.rejectedBookings());
        assertEquals(BigDecimal.valueOf(1.0), detail.lateCancellationPenaltyPoints());
    }

    @Test
    void getMentorDetail_whenProfileNotFound_shouldThrowException() {
        UUID mentorId = UUID.randomUUID();
        when(mentorProfileRepository.findWithUserByUserId(mentorId))
                .thenReturn(Optional.empty());

        assertThrows(BaseException.class, () -> adminMentorService.getMentorDetail(mentorId));
    }
}
