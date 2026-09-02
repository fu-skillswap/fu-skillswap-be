package com.fptu.exe.skillswap.modules.mentor.port;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable exact availability segment exposed to booking consumers. */
public record ServiceSlotCandidateItem(
        LocalDateTime startTime, LocalDateTime endTime, Instant startAt, Instant endAt,
        int pendingCount, int remainingPendingQuota, boolean selectable, String reasonIfBlocked,
        boolean blockedByAcceptedBooking, UUID blockingBookingId, UUID blockingServiceId,
        String blockingServiceTitle, boolean blockedBySameService, boolean blockedByDifferentService,
        String bookingConflictNote
) {}
