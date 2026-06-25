package com.fptu.exe.skillswap.modules.booking.repository.projection;

import java.time.LocalDateTime;

public interface BookingSegmentPendingCountProjection {
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
    long getPendingCount();
}
