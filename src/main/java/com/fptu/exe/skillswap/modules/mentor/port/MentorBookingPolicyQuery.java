package com.fptu.exe.skillswap.modules.mentor.port;

import java.time.LocalDateTime;
import java.util.UUID;

/** Booking-window policy exposed to the Booking module without exposing mentor services. */
public interface MentorBookingPolicyQuery {

    void validateBookingWindow(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now);

    boolean isBookableStartTime(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now);
}
