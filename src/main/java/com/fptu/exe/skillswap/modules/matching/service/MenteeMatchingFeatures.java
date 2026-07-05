package com.fptu.exe.skillswap.modules.matching.service;

public record MenteeMatchingFeatures(
        Integer foundationNeedLevel,
        Integer outputReviewNeedLevel,
        Integer directionNeedLevel,
        String mentorFitCode,
        String durationPreferenceCode
) {
    public boolean hasAnySignal() {
        return foundationNeedLevel != null
                || outputReviewNeedLevel != null
                || directionNeedLevel != null
                || mentorFitCode != null
                || durationPreferenceCode != null;
    }
}
