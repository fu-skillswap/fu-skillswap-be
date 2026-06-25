package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.projection.BookingSegmentPendingCountProjection;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.booking.support.AvailabilityCalendarWindowCalculator;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    @Mock
    private MentorServiceRepository mentorServiceRepository;

    @Mock
    private BookingRepository bookingRepository;

    private final AvailabilityCalendarWindowCalculator calendarWindowCalculator = new AvailabilityCalendarWindowCalculator();

    private MentorAvailabilityService mentorAvailabilityService;

    private UUID mentorUserId;
    private MentorProfile mentorProfile;
    private User user;

    @BeforeEach
    void setUp() {
        mentorAvailabilityService = new MentorAvailabilityService(
                mentorProfileRepository,
                mentorAvailabilityRuleRepository,
                mentorAvailabilitySlotRepository,
                availabilitySlotServiceRepository,
                mentorServiceRepository,
                bookingRepository,
                calendarWindowCalculator
        );
        mentorUserId = UUID.randomUUID();
        user = new User();
        user.setId(mentorUserId);
        user.setStatus(UserStatus.ACTIVE);

        mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorUserId);
        mentorProfile.setUser(user);
        mentorProfile.setStatus(MentorStatus.ACTIVE);
        mentorProfile.setVerifiedAt(LocalDateTime.now().minusDays(1));
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
    void createRule_successful_generatesCalendarWindow() {
        LocalDate effectiveDate = LocalDate.now().plusDays(1);
        UpsertAvailabilityRuleRequest request = new UpsertAvailabilityRuleRequest(
                AvailabilityRuleType.OPEN,
                AvailabilityRepeatType.DAILY,
                null,
                effectiveDate,
                effectiveDate.plusDays(2),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                "Daily open window"
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilityRuleRepository.save(any(MentorAvailabilityRule.class)))
                .thenAnswer(invocation -> {
                    MentorAvailabilityRule saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });
        when(mentorAvailabilityRuleRepository.findActiveRulesOverlapping(eq(mentorUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    LocalDate fromDate = invocation.getArgument(1);
                    LocalDate toDate = invocation.getArgument(2);
                    MentorAvailabilityRule rule = MentorAvailabilityRule.builder()
                            .id(UUID.randomUUID())
                            .mentorProfile(mentorProfile)
                            .ruleType(AvailabilityRuleType.OPEN)
                            .repeatType(AvailabilityRepeatType.DAILY)
                            .effectiveFrom(fromDate)
                            .effectiveTo(toDate)
                            .startTime(LocalTime.of(9, 0))
                            .endTime(LocalTime.of(11, 0))
                            .active(true)
                            .build();
                    return List.of(rule);
                });
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(eq(mentorUserId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(false);
        when(mentorAvailabilitySlotRepository.save(any(MentorAvailabilitySlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AvailabilityRuleResponse response = mentorAvailabilityService.createRule(mentorUserId, request);

        assertNotNull(response);
        verify(mentorAvailabilitySlotRepository, org.mockito.Mockito.atLeastOnce()).save(any(MentorAvailabilitySlot.class));
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
    void availability_shouldShowWindowWithLessThanThreePendingRequests() {
        MentorAvailabilitySlot availableSlot = new MentorAvailabilitySlot();
        availableSlot.setId(UUID.randomUUID());
        availableSlot.setMentorProfile(mentorProfile);
        availableSlot.setStartTime(LocalDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0));
        availableSlot.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        availableSlot.setTimezone("Asia/Ho_Chi_Minh");
        availableSlot.setActive(true);
        availableSlot.setBooked(false);

        MentorService attachedService = MentorService.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .title("Spring Boot Mentoring")
                .durationMinutes(120)
                .isActive(true)
                .build();

        when(mentorAvailabilitySlotRepository.findVisibleSlotsByMentorUserId(
                eq(mentorUserId), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(availableSlot));
        when(availabilitySlotServiceRepository.findBySlotIdInOrderByCreatedAtAsc(List.of(availableSlot.getId())))
                .thenReturn(List.of(AvailabilitySlotService.builder().slot(availableSlot).service(attachedService).build()));
        when(bookingRepository.countPendingSegmentsBySlotId(availableSlot.getId(), BookingStatus.PENDING))
                .thenReturn(List.of(pendingCount(availableSlot.getStartTime(), availableSlot.getEndTime(), 2L)));

        AvailabilityCalendarWindowCalculator.DateRange visibleRange = calendarWindowCalculator.currentVisibleRange(LocalDate.now());
        List<MentorAvailabilitySlotResponse> response = mentorAvailabilityService.getAvailableSlots(
                mentorProfile,
                visibleRange.startDate().plusDays(1),
                visibleRange.startDate().plusDays(1)
        );

        assertEquals(1, response.size());
        assertEquals(120, response.getFirst().durationMinutes());
        assertEquals(2, response.getFirst().pendingRequestCount());
        assertEquals(3, response.getFirst().maxPendingRequests());
        assertEquals(1, response.getFirst().remainingRequestSlots());
    }

    @Test
    void getAvailableSlots_outsideCurrentCalendarWindow_shouldThrowBadRequest() {
        AvailabilityCalendarWindowCalculator.DateRange visibleRange = calendarWindowCalculator.currentVisibleRange(LocalDate.now());

        BaseException exception = assertThrows(BaseException.class, () -> mentorAvailabilityService.getAvailableSlots(
                mentorProfile,
                visibleRange.startDate().minusDays(1),
                visibleRange.endDate()
        ));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void generateSlotsForDateRange_overlappingWindow_shouldNotCreateNewWindow() {
        MentorAvailabilityRule openRule = MentorAvailabilityRule.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.DAILY)
                .effectiveFrom(LocalDate.now().plusDays(1))
                .effectiveTo(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
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

    private BookingSegmentPendingCountProjection pendingCount(LocalDateTime startTime, LocalDateTime endTime, long pendingCount) {
        return new BookingSegmentPendingCountProjection() {
            @Override
            public LocalDateTime getStartTime() {
                return startTime;
            }

            @Override
            public LocalDateTime getEndTime() {
                return endTime;
            }

            @Override
            public long getPendingCount() {
                return pendingCount;
            }
        };
    }
}
