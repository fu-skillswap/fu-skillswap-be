package com.fptu.exe.skillswap.modules.identity.port;

import java.util.UUID;

public interface MentorCalendarEligibilityPort {

    void requireVerifiedMentor(UUID mentorUserId);

    boolean hasActiveOneToOneService(UUID mentorUserId);
}
