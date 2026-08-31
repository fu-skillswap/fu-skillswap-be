package com.fptu.exe.skillswap.modules.mentor.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MentorBookingCapability(
        UUID mentorUserId,
        String status,
        boolean available,
        Integer sessionDuration,
        BigDecimal averageRating,
        Integer totalCompletedSessions,
        LocalDateTime bookingSuspendedUntil,
        boolean isActiveMentor,
        boolean hasCompletedProfile
) {
    public boolean canAcceptBookings(LocalDateTime now) {
        return isActiveMentor
                && available
                && (bookingSuspendedUntil == null || !bookingSuspendedUntil.isAfter(now));
    }
}
