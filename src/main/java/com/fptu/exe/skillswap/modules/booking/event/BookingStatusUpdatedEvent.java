package com.fptu.exe.skillswap.modules.booking.event;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingStatusUpdatedEvent(
    UUID bookingId,
    UUID menteeUserId,
    UUID mentorUserId,
    BookingStatus status,
    String message,
    LocalDateTime updatedAt
) {}
