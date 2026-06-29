package com.fptu.exe.skillswap.modules.academic.scheduler;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudentSemesterScheduler {

    private final AcademicService academicService;

    @Scheduled(cron = "0 0 1 15 4,8,12 *", zone = "Asia/Ho_Chi_Minh")
    public void incrementEligibleSemesters() {
        log.info("Starting scheduled student semester increment...");
        try {
            academicService.incrementEligibleSemesters();
        } catch (Exception ex) {
            log.error("Failed to increment student semesters", ex);
        }
    }
}
