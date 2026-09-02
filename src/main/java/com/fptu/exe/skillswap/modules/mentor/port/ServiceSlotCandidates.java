package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.List;
import java.util.UUID;

/** Immutable exact availability segments exposed to booking consumers. */
public record ServiceSlotCandidates(
        UUID slotId, UUID serviceId, Integer serviceDurationMinutes,
        List<ServiceSlotCandidateItem> candidateServiceSlots
) {}
