package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import java.util.Objects;

public record RecommendationReason(
        RecommendationReasonCode code,
        Integer count
) {
    public RecommendationReason {
        Objects.requireNonNull(code, "code must not be null");
        if (count != null && count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }

    public static RecommendationReason of(RecommendationReasonCode code) {
        return new RecommendationReason(code, null);
    }

    public static RecommendationReason counted(RecommendationReasonCode code, int count) {
        return new RecommendationReason(code, count);
    }
}
