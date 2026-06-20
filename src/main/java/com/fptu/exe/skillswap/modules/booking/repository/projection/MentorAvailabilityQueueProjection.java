package com.fptu.exe.skillswap.modules.booking.repository.projection;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MentorAvailabilityQueueProjection {

    UUID getSlotId();

    LocalDateTime getStartTime();

    LocalDateTime getEndTime();

    String getTimezone();

    Boolean getRecurring();

    Long getPendingRequestCount();
}
