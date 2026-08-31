package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

public interface MentorDisciplineCommandPort {

    void recordLateCancellation(UUID mentorUserId, UUID bookingId, String reason);

    void recordMentorNoShow(UUID mentorUserId, UUID bookingId, String reason);

    void recordCompletionOverdue(UUID mentorUserId, UUID bookingId, String reason);
}
