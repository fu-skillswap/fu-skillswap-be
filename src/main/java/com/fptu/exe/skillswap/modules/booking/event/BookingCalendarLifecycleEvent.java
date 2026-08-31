package com.fptu.exe.skillswap.modules.booking.event;

import java.time.Instant;
import java.util.UUID;

/** Versioned booking lifecycle fact consumed by calendar adapters after commit. */
public record BookingCalendarLifecycleEvent(UUID eventId, UUID bookingId, UUID mentorUserId,
                                            Action action, Instant occurredAtUtc, int schemaVersion) {
    public enum Action { CREATE, UPDATE, CANCEL }

    public static BookingCalendarLifecycleEvent of(UUID bookingId, UUID mentorUserId, Action action) {
        return new BookingCalendarLifecycleEvent(UUID.randomUUID(), bookingId, mentorUserId, action, Instant.now(), 1);
    }
}
