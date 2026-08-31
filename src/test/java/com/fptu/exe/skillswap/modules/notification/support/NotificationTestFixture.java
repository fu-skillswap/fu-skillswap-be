package com.fptu.exe.skillswap.modules.notification.support;

import java.util.Map;
import java.util.UUID;

/** Test fixture providing standardized notification and email outbox payload snapshots. */
public final class NotificationTestFixture {

    private NotificationTestFixture() {}

    public static UUID randomNotificationId() {
        return UUID.randomUUID();
    }

    public static NotificationIntent createBookingNotificationIntent(UUID recipientId, UUID bookingId) {
        return new NotificationIntent(
                UUID.randomUUID(),
                recipientId != null ? recipientId : UUID.randomUUID(),
                "BOOKING_CREATED",
                "Your booking request has been submitted",
                Map.of("bookingId", bookingId != null ? bookingId.toString() : UUID.randomUUID().toString())
        );
    }

    public record NotificationIntent(
            UUID intentId,
            UUID recipientUserId,
            String templateKey,
            String subject,
            Map<String, String> parameters
    ) {}
}
