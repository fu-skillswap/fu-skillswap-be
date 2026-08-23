package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.port.GoogleCalendarConnectionPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.CreateMentorServiceRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorServiceManagementServiceTest {

    @Mock
    private MentorServiceRepository mentorServiceRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MentorProfileService mentorProfileService;

    @Mock
    private GoogleCalendarConnectionPort googleCalendarConnectionPort;

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
                .expectedOutcome("Ket qua")
                .durationMinutes(60)
                .isFree(false)
                .priceScoin(72_000)
                .isActive(true)
                .build();

        inactiveService = MentorService.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .title("Inactive Service")
                .description("Mo ta")
                .expectedOutcome("Ket qua")
                .durationMinutes(30)
                .isFree(true)
                .priceScoin(0)
                .isActive(false)
                .build();

        org.mockito.Mockito.lenient().when(userRepository.findById(mentorUserId)).thenReturn(Optional.of(User.builder().id(mentorUserId).build()));
        org.mockito.Mockito.lenient().when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));
        org.mockito.Mockito.lenient().when(mentorProfileService.hasCompletedMentorProfile(mentorUserId)).thenReturn(true);
    }

    @Test
    void getMyServices_nullActive_shouldReturnBothActiveAndInactive() {
        when(mentorServiceRepository.findByMentorProfileUserIdOrderByCreatedAtAsc(mentorUserId))
                .thenReturn(List.of(activeService, inactiveService));

        var response = mentorServiceManagementService.getMyServices(mentorUserId, (Boolean) null);

        assertEquals(2, response.size());
        verify(mentorServiceRepository).findByMentorProfileUserIdOrderByCreatedAtAsc(mentorUserId);
        verify(mentorServiceRepository, never()).findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(eq(mentorUserId), anyBoolean());
    }

    @Test
    void getMyServices_activeTrue_shouldReturnOnlyActive() {
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, true))
                .thenReturn(List.of(activeService));

        var response = mentorServiceManagementService.getMyServices(mentorUserId, true);

        assertEquals(1, response.size());
        assertEquals(true, response.getFirst().active());
        verify(mentorServiceRepository).findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, true);
    }

    @Test
    void getMyServices_activeFalse_shouldReturnOnlyInactive() {
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, false))
                .thenReturn(List.of(inactiveService));

        var response = mentorServiceManagementService.getMyServices(mentorUserId, false);

        assertEquals(1, response.size());
        assertEquals(false, response.getFirst().active());
        verify(mentorServiceRepository).findByMentorProfileUserIdAndIsActiveOrderByCreatedAtAsc(mentorUserId, false);
    }

    @Test
    void createService_invalidDuration_15mins_shouldThrowBadRequest() {
        CreateMentorServiceRequest request = new CreateMentorServiceRequest(
                "Review project",
                "Mo ta",
                "Ket qua",
                15,
                false,
                72_000,
                false,
                MentorServiceDeliveryMode.ONE_TO_ONE
        );

        BaseException exception = assertThrows(
                BaseException.class,
                () -> mentorServiceManagementService.createService(mentorUserId, request)
        );

        assertEquals("Thời lượng dịch vụ chỉ được chọn 30, 60, 90 hoặc 120 phút", exception.getMessage());
        verify(mentorServiceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createService_paidServiceBelowMinimum_shouldThrowBadRequest() {
        CreateMentorServiceRequest request = new CreateMentorServiceRequest(
                "Review project",
                "Mo ta",
                "Ket qua",
                60,
                false,
                29_999,
                false,
                MentorServiceDeliveryMode.ONE_TO_ONE
        );

        BaseException exception = assertThrows(
                BaseException.class,
                () -> mentorServiceManagementService.createService(mentorUserId, request)
        );

        assertEquals("Dịch vụ có phí phải có giá tối thiểu 30000 SCoin cho 60 phút", exception.getMessage());
        verify(googleCalendarConnectionPort, never()).requireActiveConnectionForServiceCreation(mentorUserId);
        verify(mentorServiceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createService_paidServiceAboveMaximum_shouldThrowBadRequest() {
        CreateMentorServiceRequest request = new CreateMentorServiceRequest(
                "Review project",
                "Mo ta",
                "Ket qua",
                60,
                false,
                30_000_001,
                false,
                MentorServiceDeliveryMode.ONE_TO_ONE
        );

        BaseException exception = assertThrows(
                BaseException.class,
                () -> mentorServiceManagementService.createService(mentorUserId, request)
        );

        assertEquals("Dịch vụ có phí chỉ được đặt tối đa 30000000 SCoin cho 60 phút", exception.getMessage());
        verify(googleCalendarConnectionPort, never()).requireActiveConnectionForServiceCreation(mentorUserId);
        verify(mentorServiceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createService_validDuration120mins_shouldSucceed() {
        when(mentorServiceRepository.save(org.mockito.ArgumentMatchers.any(MentorService.class)))
                .thenAnswer(inv -> {
                    MentorService service = inv.getArgument(0);
                    service.setId(UUID.randomUUID());
                    return service;
                });

        CreateMentorServiceRequest request = new CreateMentorServiceRequest(
                "Masterclass 2h",
                "Mo ta chi tiet",
                "Ket qua dat duoc",
                120,
                false,
                150_000,
                false,
                MentorServiceDeliveryMode.ONE_TO_ONE
        );

        var response = mentorServiceManagementService.createService(mentorUserId, request);

        assertEquals(120, response.durationMinutes());
        assertEquals(150_000, response.basePriceScoin());
        verify(googleCalendarConnectionPort).requireActiveConnectionForServiceCreation(mentorUserId);
        verify(mentorServiceRepository).save(org.mockito.ArgumentMatchers.any(MentorService.class));
    }

    @Test
    void createService_freeServiceShouldForceZeroPriceInResponse() {
        when(mentorServiceRepository.save(org.mockito.ArgumentMatchers.any(MentorService.class)))
                .thenAnswer(inv -> {
                    MentorService service = inv.getArgument(0);
                    service.setId(UUID.randomUUID());
                    return service;
                });

        CreateMentorServiceRequest request = new CreateMentorServiceRequest(
                "Quick answer",
                "Mo ta",
                "Ket qua",
                30,
                true,
                0,
                false,
                MentorServiceDeliveryMode.ONE_TO_ONE
        );

        var response = mentorServiceManagementService.createService(mentorUserId, request);

        assertEquals(true, response.free());
        assertEquals(0, response.basePriceScoin());
        verify(googleCalendarConnectionPort).requireActiveConnectionForServiceCreation(mentorUserId);
    }

    @Test
    void createService_withoutActiveCalendar_shouldNotPersistService() {
        CreateMentorServiceRequest request = new CreateMentorServiceRequest(
                "Review project",
                "Mo ta",
                "Ket qua",
                60,
                false,
                72_000,
                false,
                MentorServiceDeliveryMode.ONE_TO_ONE
        );
        doThrow(new BaseException(
                com.fptu.exe.skillswap.shared.exception.ErrorCode.GOOGLE_CALENDAR_CONNECTION_REQUIRED,
                "Cần kết nối Google Calendar"
        )).when(googleCalendarConnectionPort).requireActiveConnectionForServiceCreation(mentorUserId);

        BaseException exception = assertThrows(
                BaseException.class,
                () -> mentorServiceManagementService.createService(mentorUserId, request)
        );

        assertEquals(
                com.fptu.exe.skillswap.shared.exception.ErrorCode.GOOGLE_CALENDAR_CONNECTION_REQUIRED,
                exception.getErrorCode()
        );
        verify(mentorServiceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
