package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

/** Records an externally observed mentor-policy violation without exposing mentor domain enums. */
public interface MentorViolationCommandPort {

    void recordConfirmedViolation(MentorViolationCommand command);

    record MentorViolationCommand(
            UUID mentorUserId,
            String source,
            UUID sourceReferenceId,
            String type,
            String severity,
            UUID decidedByUserId,
            String reason,
            String decisionNote
    ) {
    }
}
