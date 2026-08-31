package com.fptu.exe.skillswap.modules.mentor.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Booking-owned availability view consumed by mentor discovery. */
public record MentorPublicAvailability(UUID slotId, LocalDateTime startTime, LocalDateTime endTime,
                                      String timezone, Integer durationMinutes, Integer pendingRequestCount,
                                      Integer acceptedSlotCount, Integer maxPendingRequests,
                                      Integer remainingRequestSlots, List<ServiceSlotCandidate> services) {
}
