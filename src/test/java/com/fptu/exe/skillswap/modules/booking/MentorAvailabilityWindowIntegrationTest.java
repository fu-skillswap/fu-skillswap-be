package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.response.MentorManagedAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class MentorAvailabilityWindowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;

    @Autowired
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Autowired
    private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    @Autowired
    private MentorServiceRepository mentorServiceRepository;

    @Autowired
    private MentorAvailabilityService mentorAvailabilityService;

    private User mentorUser;
    private MentorProfile mentorProfile;
    private LocalDate queryStartDate;
    private LocalDateTime slotStartTime;
    private LocalDateTime slotEndTime;

    @BeforeEach
    void setUp() {
        mentorUser = userRepository.save(User.builder()
                .email("availability-window-" + System.nanoTime() + "@test.com")
                .fullName("Availability Window Mentor")
                .status(UserStatus.ACTIVE)
                .build());

        mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now().minusDays(1))
                .isAvailable(true)
                .headline("Night Shift Mentor")
                .expertiseDescription("Supports overlapping slot visibility checks")
                .foundationSupportLevel(3)
                .outputReviewSupportLevel(3)
                .directionSupportLevel(2)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build());

        queryStartDate = LocalDate.now().plusDays(1);
        slotStartTime = LocalDateTime.of(queryStartDate.minusDays(1), LocalTime.of(23, 30));
        slotEndTime = LocalDateTime.of(queryStartDate, LocalTime.of(1, 0));

        MentorAvailabilityRule rule = mentorAvailabilityRuleRepository.save(MentorAvailabilityRule.builder()
                .mentorProfile(mentorProfile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.NONE)
                .effectiveFrom(slotStartTime.toLocalDate())
                .effectiveTo(slotStartTime.toLocalDate())
                .startTime(slotStartTime.toLocalTime())
                .endTime(slotEndTime.toLocalTime())
                .timezone("Asia/Ho_Chi_Minh")
                .active(true)
                .build());

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.saveAndFlush(MentorAvailabilitySlot.builder()
                .mentorProfile(mentorProfile)
                .rule(rule)
                .startTime(slotStartTime)
                .endTime(slotEndTime)
                .timezone("Asia/Ho_Chi_Minh")
                .isActive(true)
                .isBooked(false)
                .note("Overnight slot")
                .build());

        MentorService mentorService = mentorServiceRepository.saveAndFlush(MentorService.builder()
                .mentorProfile(mentorProfile)
                .title("Overnight Support")
                .description("Service bound to overnight slot")
                .durationMinutes(60)
                .isFree(true)
                .priceScoin(0)
                .isActive(true)
                .build());

        availabilitySlotServiceRepository.saveAndFlush(AvailabilitySlotService.builder()
                .id(new AvailabilitySlotServiceId(slot.getId(), mentorService.getId()))
                .slot(slot)
                .service(mentorService)
                .build());
    }

    @Test
    void getMySlots_shouldIncludeSlotThatStartsBeforeWindowButOverlapsWindow() {
        List<MentorManagedAvailabilitySlotResponse> responses = mentorAvailabilityService.getMySlots(
                mentorUser.getId(),
                queryStartDate,
                queryStartDate.plusDays(6)
        );

        assertEquals(1, responses.size());
        assertEquals(slotStartTime, responses.getFirst().startTime());
        assertEquals(slotEndTime, responses.getFirst().endTime());
    }

    @Test
    void getAvailableSlots_shouldIncludeSlotThatStartsBeforeWindowButOverlapsWindow() {
        List<MentorAvailabilitySlotResponse> responses = mentorAvailabilityService.getAvailableSlots(
                mentorProfile,
                queryStartDate,
                queryStartDate.plusDays(6)
        );

        assertEquals(1, responses.size());
        assertEquals(slotStartTime, responses.getFirst().startTime());
        assertEquals(slotEndTime, responses.getFirst().endTime());
    }
}
