package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.port.MentorDisciplineCommandPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorDisciplineCommandPortImpl implements MentorDisciplineCommandPort {

    private final MentorViolationService mentorViolationService;

    @Override
    @Transactional
    public void recordLateCancellation(UUID mentorUserId, UUID bookingId, String reason) {
        mentorViolationService.record(mentorUserId, bookingId, MentorViolationType.LATE_CANCELLATION, reason);
    }

    @Override
    @Transactional
    public void recordMentorNoShow(UUID mentorUserId, UUID bookingId, String reason) {
        mentorViolationService.record(mentorUserId, bookingId, MentorViolationType.MENTOR_NO_SHOW, reason);
    }

    @Override
    @Transactional
    public void recordCompletionOverdue(UUID mentorUserId, UUID bookingId, String reason) {
        mentorViolationService.record(mentorUserId, bookingId, MentorViolationType.COMPLETION_OVERDUE, reason);
    }
}
