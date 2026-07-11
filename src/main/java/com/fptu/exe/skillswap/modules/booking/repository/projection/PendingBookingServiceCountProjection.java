package com.fptu.exe.skillswap.modules.booking.repository.projection;

import java.util.UUID;

public interface PendingBookingServiceCountProjection {
    UUID getMentorUserId();

    String getMentorEmail();

    String getMentorName();

    String getServiceTitle();

    long getPendingCount();
}
