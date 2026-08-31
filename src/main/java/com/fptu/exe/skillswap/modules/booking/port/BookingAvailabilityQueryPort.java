package com.fptu.exe.skillswap.modules.booking.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingAvailabilityQueryPort {

    List<UUID> findMentorUserIdsWithActiveSlotsInFuture(Collection<UUID> mentorUserIds, Instant fromInstant);

    boolean isSlotOwnedByMentor(UUID slotId, UUID mentorUserId);

    boolean hasPaidFutureBookingsForMentor(UUID mentorUserId, Instant afterUtc);
}
