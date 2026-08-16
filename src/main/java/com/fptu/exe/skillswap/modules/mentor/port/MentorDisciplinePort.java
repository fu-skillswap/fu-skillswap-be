package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

public interface MentorDisciplinePort {

    void incrementMentorNoShowCount(UUID mentorUserId);

    void incrementMentorCompletionOverdueCount(UUID mentorUserId);
}
