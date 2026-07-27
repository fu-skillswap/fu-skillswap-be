package com.fptu.exe.skillswap.modules.mentor.dto.response;

import java.util.Set;

/** Read-only platform limits used to build valid mentor-service forms. */
public record MentorServiceConstraintsResponse(
        Set<Integer> allowedDurationMinutes,
        int minimumPriceScoinPerMinute,
        int maximumPriceScoinPerMinute
) {
}
