package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorServiceManagementService;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorServiceManagementServiceTest {

    @Mock
    private MentorServiceRepository mentorServiceRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MentorServiceManagementService mentorServiceManagementService;

    private UUID mentorUserId;
    private MentorProfile mentorProfile;
    private MentorService activeService;
    private MentorService inactiveService;

    @BeforeEach
    void setUp() {
        mentorUserId = UUID.randomUUID();
        mentorProfile = MentorProfile.builder()
                .userId(mentorUserId)
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now().minusDays(1))
                .headline("Backend Mentor")
                .expertiseDescription("Spring Boot va PostgreSQL")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build();

        activeService = MentorService.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .title("Active Service")
                .description("Mo ta")
                .durationMinutes(60)
                .isActive(true)
                .build();

        inactiveService = MentorService.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .title("Inactive Service")
                .description("Mo ta")
                .durationMinutes(30)
                .isActive(false)
                .build();

        when(userRepository.findById(mentorUserId)).thenReturn(Optional.of(User.builder().id(mentorUserId).build()));
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));
    }

    @Test
    void getMyServices_defaultAll_shouldReturnBothActiveAndInactive() {
        when(mentorServiceRepository.findByMentorProfileUserIdOrderByCreatedAtAsc(mentorUserId))
                .thenReturn(List.of(activeService, inactiveService));

        var response = mentorServiceManagementService.getMyServices(mentorUserId, "all");

        assertEquals(2, response.size());
        verify(mentorServiceRepository).findByMentorProfileUserIdOrderByCreatedAtAsc(mentorUserId);
        verify(mentorServiceRepository, never()).findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(eq(mentorUserId), anyBoolean());
    }

    @Test
    void getMyServices_activeTrue_shouldReturnOnlyActive() {
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, true))
                .thenReturn(List.of(activeService));

        var response = mentorServiceManagementService.getMyServices(mentorUserId, "true");

        assertEquals(1, response.size());
        assertEquals(true, response.getFirst().active());
        verify(mentorServiceRepository).findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, true);
    }

    @Test
    void getMyServices_activeFalse_shouldReturnOnlyInactive() {
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, false))
                .thenReturn(List.of(inactiveService));

        var response = mentorServiceManagementService.getMyServices(mentorUserId, "false");

        assertEquals(1, response.size());
        assertEquals(false, response.getFirst().active());
        verify(mentorServiceRepository).findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, false);
    }

    @Test
    void getMyServices_invalidActiveFilter_shouldThrowBadRequest() {
        BaseException exception = assertThrows(
                BaseException.class,
                () -> mentorServiceManagementService.getMyServices(mentorUserId, "abc")
        );

        assertEquals("Query param active chỉ chấp nhận true, false hoặc all", exception.getMessage());
        verify(mentorServiceRepository, never()).findByMentorProfileUserIdOrderByCreatedAtAsc(mentorUserId);
        verify(mentorServiceRepository, never()).findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(eq(mentorUserId), anyBoolean());
    }
}
