package com.fptu.exe.skillswap.modules.course.service;

import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollment;
import com.fptu.exe.skillswap.modules.course.domain.CourseEnrollmentSettlement;
import com.fptu.exe.skillswap.modules.course.domain.CourseSession;
import com.fptu.exe.skillswap.modules.course.repository.CourseEnrollmentSettlementRepository;
import com.fptu.exe.skillswap.modules.course.repository.CourseSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseSettlementService {

    private final CourseEnrollmentSettlementRepository settlementRepository;
    private final CourseSessionRepository sessionRepository;

    @Transactional
    public void generateSettlements(CourseEnrollment enrollment) {
        List<CourseSession> sessions = sessionRepository.findByCourseIdOrderByScheduledStartAtAsc(enrollment.getCourse().getId());
        if (sessions.isEmpty()) {
            // Cannot divide by 0
            log.warn("Course {} has no sessions, skipping settlement generation", enrollment.getCourse().getId());
            return;
        }

        int totalSessions = sessions.size();
        int baseMentorPayout = enrollment.getMentorPayoutScoin() / totalSessions;
        int remainderMentorPayout = enrollment.getMentorPayoutScoin() % totalSessions;

        int basePlatformFee = enrollment.getMentorCommissionScoin() / totalSessions;
        int remainderPlatformFee = enrollment.getMentorCommissionScoin() % totalSessions;

        for (int i = 0; i < totalSessions; i++) {
            CourseSession session = sessions.get(i);
            int mentorPayout = baseMentorPayout;
            int platformFee = basePlatformFee;

            // Dangle the remainder on the last session
            if (i == totalSessions - 1) {
                mentorPayout += remainderMentorPayout;
                platformFee += remainderPlatformFee;
            }

            CourseEnrollmentSettlement settlement = CourseEnrollmentSettlement.builder()
                    .enrollment(enrollment)
                    .courseSession(session)
                    .mentorPayoutScoin(mentorPayout)
                    .platformFeeScoin(platformFee)
                    .status("HELD")
                    .build();

            settlementRepository.save(settlement);
        }
    }
}
