package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.booking.support.AvailabilityCalendarWindowCalculator;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = false)
public class MentorSlotGenerationScheduler {

    private final MentorAvailabilityService mentorAvailabilityService;
    private final MentorProfileRepository mentorProfileRepository;
    private final AvailabilityCalendarWindowCalculator calendarWindowCalculator;

    @Scheduled(cron = "0 0 0 * * *") // Chạy vào 00:00 hàng ngày
    public void generateWeeklySlots() {
        log.info("Starting scheduled mentor availability window generation...");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        AvailabilityCalendarWindowCalculator.DateRange currentRange = calendarWindowCalculator.currentVisibleRange(today);
        AvailabilityCalendarWindowCalculator.DateRange nextRange = calendarWindowCalculator.nextPreparationRange(today);
        boolean shouldPrepareNextCycle = calendarWindowCalculator.shouldPrepareNextCycle(today);

        List<UUID> activeMentorUserIds = mentorProfileRepository.findActiveMentorUserIds(MentorStatus.ACTIVE);
        log.info("Found {} active mentors to generate slots", activeMentorUserIds.size());

        for (UUID mentorUserId : activeMentorUserIds) {
            mentorAvailabilityService.generateSlotsForMentorAsync(
                    mentorUserId,
                    currentRange.startDate(),
                    currentRange.endDate()
            );
            if (shouldPrepareNextCycle) {
                mentorAvailabilityService.generateSlotsForMentorAsync(
                        mentorUserId,
                        nextRange.startDate(),
                        nextRange.endDate()
                );
            }
        }
        log.info("Submitted scheduled mentor availability window generation jobs.");
    }
}
