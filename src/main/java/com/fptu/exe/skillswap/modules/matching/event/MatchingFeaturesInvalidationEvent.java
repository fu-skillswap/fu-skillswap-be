package com.fptu.exe.skillswap.modules.matching.event;

import java.util.UUID;

public record MatchingFeaturesInvalidationEvent(
        UUID userId,
        boolean invalidateAll,
        String source
) {
    public static MatchingFeaturesInvalidationEvent forUser(UUID userId, String source) {
        return new MatchingFeaturesInvalidationEvent(userId, false, source);
    }

    public static MatchingFeaturesInvalidationEvent invalidateAll(String source) {
        return new MatchingFeaturesInvalidationEvent(null, true, source);
    }
}
