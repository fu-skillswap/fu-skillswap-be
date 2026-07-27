package com.fptu.exe.skillswap.modules.mentor.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MentorFunnelEventRequest(
        @NotNull MentorFunnelEventType eventType,
        @NotNull UUID mentorUserId,
        UUID serviceId,
        UUID slotId,
        @NotNull MentorFunnelSource source
) {}
