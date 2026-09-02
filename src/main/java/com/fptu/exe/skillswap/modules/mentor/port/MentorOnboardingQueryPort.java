package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

/** Narrow onboarding status queries exposed to other modules. */
public interface MentorOnboardingQueryPort {

    boolean hasCompletedMentorProfile(UUID userId);

    String getLatestVerificationStatus(UUID userId);
}
