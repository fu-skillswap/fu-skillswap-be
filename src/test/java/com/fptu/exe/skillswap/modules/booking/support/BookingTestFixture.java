package com.fptu.exe.skillswap.modules.booking.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Test fixture providing standardized booking data and decoupled snapshots for tests. */
public final class BookingTestFixture {

    private BookingTestFixture() {}

    public static UUID randomBookingId() {
        return UUID.randomUUID();
    }

    public static UUID randomSlotId() {
        return UUID.randomUUID();
    }

    public static UUID randomSessionId() {
        return UUID.randomUUID();
    }

    public static BookingSnapshot createSampleSnapshot(UUID menteeId, UUID mentorId, UUID serviceId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new BookingSnapshot(
                UUID.randomUUID(),
                menteeId != null ? menteeId : UUID.randomUUID(),
                mentorId != null ? mentorId : UUID.randomUUID(),
                serviceId != null ? serviceId : UUID.randomUUID(),
                "PENDING",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                50000L,
                "Test booking request title",
                "Test learning goal description"
        );
    }

    public record BookingSnapshot(
            UUID bookingId,
            UUID menteeUserId,
            UUID mentorUserId,
            UUID serviceId,
            String status,
            Instant startAt,
            Instant endAt,
            long amountVnd,
            String note,
            String learningGoal
    ) {}
}
