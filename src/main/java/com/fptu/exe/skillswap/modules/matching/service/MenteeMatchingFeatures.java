package com.fptu.exe.skillswap.modules.matching.service;

import java.time.LocalDateTime;

public record MenteeMatchingFeatures(
        Integer foundationNeedLevel,
        Integer outputReviewNeedLevel,
        Integer directionNeedLevel,
        String mentorFitCode,
        String durationPreferenceCode,
        LocalDateTime latestAnsweredAt
) {
    public boolean hasAnySignal() {
        return foundationNeedLevel != null
                || outputReviewNeedLevel != null
                || directionNeedLevel != null
                || mentorFitCode != null
                || durationPreferenceCode != null;
    }
}
