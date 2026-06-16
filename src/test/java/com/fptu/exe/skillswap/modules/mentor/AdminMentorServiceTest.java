package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMentorServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @InjectMocks
    private AdminMentorService adminMentorService;

    @Test
    void getMentors_shouldTrimKeywordAndMapResponse() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("mentor@test.com");
        user.setFullName("Mentor One");
        user.setAvatarUrl("avatar");
        user.setStatus(UserStatus.ACTIVE);

        MentorProfile profile = MentorProfile.builder()
                .userId(user.getId())
                .user(user)
                .status(MentorStatus.ACTIVE)
                .isAvailable(true)
                .headline("Backend mentor")
                .teachingMode(TeachingMode.HYBRID)
                .sessionDuration(60)
                .averageRating(BigDecimal.valueOf(4.9))
                .totalReviews(8)
                .totalCompletedSessions(20)
                .totalRejectedBookings(1)
                .lateCancellationPenaltyPoints(BigDecimal.ZERO)
                .verifiedAt(LocalDateTime.now().minusDays(10))
                .build();

        AdminMentorListRequest request = new AdminMentorListRequest();
        request.setKeyword(" backend ");
        request.setStatus(MentorStatus.ACTIVE);
        request.setIsAvailable(true);

        when(mentorProfileRepository.searchForAdmin(eq("backend"), eq(MentorStatus.ACTIVE), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile)));

        PageResponse<AdminMentorListItemResponse> response = adminMentorService.getMentors(request);

        assertEquals(1, response.getContent().size());
        assertEquals("Mentor One", response.getContent().getFirst().displayName());
        assertEquals(MentorStatus.ACTIVE, response.getContent().getFirst().mentorStatus());
    }

    @Test
    void getMentors_nullRequest_shouldUseDefaultSortByUpdatedAt() {
        when(mentorProfileRepository.searchForAdmin(eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        adminMentorService.getMentors(null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(mentorProfileRepository).searchForAdmin(eq(null), eq(null), eq(null), captor.capture());
        assertEquals("updatedAt: DESC", captor.getValue().getSort().toString());
    }
}
