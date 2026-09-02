package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.port.MentorOnboardingQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorOnboardingQueryPortImpl implements MentorOnboardingQueryPort {

    private final MentorProfileService mentorProfileService;
    private final MentorVerificationService mentorVerificationService;

    @Override
    public boolean hasCompletedMentorProfile(UUID userId) {
        return mentorProfileService.hasCompletedMentorProfile(userId);
    }

    @Override
    public String getLatestVerificationStatus(UUID userId) {
        return mentorVerificationService.getLatestVerificationStatus(userId);
    }
}
