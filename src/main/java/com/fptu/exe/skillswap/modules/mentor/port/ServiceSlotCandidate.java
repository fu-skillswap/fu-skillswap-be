package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

public record ServiceSlotCandidate(
        UUID serviceId,
        UUID mentorUserId,
        String title,
        String description,
        String expectedOutcome,
        Integer durationMinutes,
        Integer priceScoin,
        Boolean isFree,
        Boolean active,
        String deliveryMode,
        String teachingMode,
        boolean maintainPostSessionChat
) {
}
