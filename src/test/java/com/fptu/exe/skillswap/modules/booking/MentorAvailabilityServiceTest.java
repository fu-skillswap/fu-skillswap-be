package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorAvailabilityServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;

    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @InjectMocks
    private MentorAvailabilityService mentorAvailabilityService;

    private UUID mentorUserId;
    private MentorProfile mentorProfile;
    private User user;

    @BeforeEach
    void setUp() {
        mentorUserId = UUID.randomUUID();
        user = new User();
        user.setId(mentorUserId);
        user.setStatus(UserStatus.ACTIVE);

        mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorUserId);
        mentorProfile.setUser(user);
        mentorProfile.setStatus(MentorStatus.ACTIVE);
        mentorProfile.setVerifiedAt(LocalDateTime.now().minusDays(1));
        mentorProfile.setSessionDuration(60);
        mentorProfile.setTeachingMode(TeachingMode.ONLINE);
    }

    @Test
    void getMyRules_successful() {
        MentorAvailabilityRule rule = new MentorAvailabilityRule();
        rule.setId(UUID.randomUUID());
        rule.setRuleType(AvailabilityRuleType.OPEN);
        rule.setRepeatType(AvailabilityRepeatType.DAILY);
        rule.setEffectiveFrom(LocalDate.now());

        when(mentorAvailabilityRuleRepository.findByMentorProfileUserIdAndActiveTrueOrderByEffectiveFromAscStartTimeAsc(mentorUserId))
                .thenReturn(List.of(rule));

        List<AvailabilityRuleResponse> results = mentorAvailabilityService.getMyRules(mentorUserId);

        assertEquals(1, results.size());
    }

    @Test
    void createRule_successful_resetsFutureSlots() {
        UpsertAvailabilityRuleRequest request = new UpsertAvailabilityRuleRequest(
                AvailabilityRuleType.OPEN,
                AvailabilityRepeatType.DAILY,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "Daily open slot"
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilityRuleRepository.save(any(MentorAvailabilityRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AvailabilityRuleResponse response = mentorAvailabilityService.createRule(mentorUserId, request);

        assertNotNull(response);
        verify(mentorAvailabilitySlotRepository).deactivateFutureUnbookedSlots(eq(mentorUserId), any());
    }

    @Test
    void createRule_weeklyWithoutDays_shouldThrowBadRequest() {
        UpsertAvailabilityRuleRequest request = new UpsertAvailabilityRuleRequest(
                AvailabilityRuleType.OPEN,
                AvailabilityRepeatType.WEEKLY,
                List.of(),
                LocalDate.now(),
                null,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                null
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));

        BaseException exception = assertThrows(BaseException.class, () -> mentorAvailabilityService.createRule(mentorUserId, request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void createRule_nonWeeklyWithDays_shouldThrowBadRequest() {
        UpsertAvailabilityRuleRequest request = new UpsertAvailabilityRuleRequest(
                AvailabilityRuleType.OPEN,
                AvailabilityRepeatType.DAILY,
                List.of(DayOfWeek.MONDAY),
                LocalDate.now(),
                null,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                null
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));

        BaseException exception = assertThrows(BaseException.class, () -> mentorAvailabilityService.createRule(mentorUserId, request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void createRule_unverifiedMentor_shouldThrowConflict() {
        mentorProfile.setVerifiedAt(null);
        UpsertAvailabilityRuleRequest request = new UpsertAvailabilityRuleRequest(
                AvailabilityRuleType.OPEN,
                AvailabilityRepeatType.DAILY,
                null,
                LocalDate.now(),
                null,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                null
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));

        BaseException exception = assertThrows(BaseException.class, () -> mentorAvailabilityService.createRule(mentorUserId, request));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void availability_shouldShowSlotWithLessThanThreePendingRequests() {
        MentorAvailabilitySlot availableSlot = new MentorAvailabilitySlot();
        availableSlot.setId(UUID.randomUUID());
        availableSlot.setStartTime(LocalDateTime.now().plusDays(1).withHour(8).withMinute(0));
        availableSlot.setEndTime(availableSlot.getStartTime().plusMinutes(60));
        availableSlot.setTimezone("Asia/Ho_Chi_Minh");
        availableSlot.setBooked(false);
        availableSlot.setActive(true);

        when(mentorAvailabilitySlotRepository.findQueueAvailableSlots(
                eq(mentorUserId), any(LocalDateTime.class), any(LocalDateTime.class), any(), eq(3L)
        )).thenReturn(List.of(availableSlot));

        List<MentorAvailabilitySlotResponse> response = mentorAvailabilityService.getAvailableSlots(
                mentorProfile,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2)
        );

        assertEquals(1, response.size());
        assertEquals(60, response.getFirst().durationMinutes());
    }

    @Test
    void availability_shouldHideSlotWithThreePendingRequests() {
        when(mentorAvailabilitySlotRepository.findQueueAvailableSlots(
                eq(mentorUserId), any(LocalDateTime.class), any(LocalDateTime.class), any(), eq(3L)
        )).thenReturn(List.of());

        List<MentorAvailabilitySlotResponse> response = mentorAvailabilityService.getAvailableSlots(
                mentorProfile,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2)
        );

        assertTrue(response.isEmpty());
    }

    @Test
    void getAvailableSlots_moreThan31Days_shouldThrowBadRequest() {
        BaseException exception = assertThrows(BaseException.class, () -> mentorAvailabilityService.getAvailableSlots(
                mentorProfile,
                LocalDate.now(),
                LocalDate.now().plusDays(40)
        ));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void generateSlotsForDateRange_invalidSessionDuration_shouldThrowBadRequest() {
        mentorProfile.setSessionDuration(45);
        MentorAvailabilityRule openRule = MentorAvailabilityRule.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.DAILY)
                .effectiveFrom(LocalDate.now().plusDays(1))
                .effectiveTo(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .active(true)
                .build();

        when(mentorAvailabilityRuleRepository.findActiveRulesOverlapping(eq(mentorUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(openRule));

        BaseException exception = assertThrows(BaseException.class, () -> mentorAvailabilityService.generateSlotsForDateRange(
                mentorProfile,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1)
        ));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void generateSlotsForDateRange_overlappingSlot_shouldNotCreateNewSlot() {
        MentorAvailabilityRule openRule = MentorAvailabilityRule.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.DAILY)
                .effectiveFrom(LocalDate.now().plusDays(1))
                .effectiveTo(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .active(true)
                .build();

        when(mentorAvailabilityRuleRepository.findActiveRulesOverlapping(eq(mentorUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(openRule));
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(eq(mentorUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(true);

        mentorAvailabilityService.generateSlotsForDateRange(
                mentorProfile,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(1)
        );

        verify(mentorAvailabilitySlotRepository, never()).save(any(MentorAvailabilitySlot.class));
    }
}
