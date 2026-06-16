package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
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
public class MentorSlotGenerationScheduler {

    private final MentorAvailabilityService mentorAvailabilityService;
    private final MentorProfileRepository mentorProfileRepository;

    @Scheduled(cron = "0 0 0 * * *") // Chạy vào 00:00 hàng ngày
    public void generateWeeklySlots() {
        log.info("Starting scheduled mentor availability slots generation...");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate endDate = today.plusDays(7); // Sinh lịch rảnh cho 7 ngày kế tiếp

        List<UUID> activeMentorUserIds = mentorProfileRepository.findActiveMentorUserIds(MentorStatus.ACTIVE);
        log.info("Found {} active mentors to generate slots", activeMentorUserIds.size());

        for (UUID mentorUserId : activeMentorUserIds) {
            mentorAvailabilityService.generateSlotsForMentorAsync(mentorUserId, today, endDate);
        }
        log.info("Submitted scheduled mentor availability slot generation jobs.");
    }
}
