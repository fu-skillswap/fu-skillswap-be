package com.fptu.exe.skillswap.modules.booking.event;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class BookingEmailNotificationEvent {
    public enum EventType {
        BOOKING_ACCEPTED_EMAIL,
        BOOKING_REJECTED_EMAIL,
        BOOKING_CANCELLED_BY_MENTEE_EMAIL,
        BOOKING_CANCELLED_BY_MENTOR_EMAIL
    }

    private final UUID bookingId;
    private final EventType eventType;
    private final String recipientEmail;
    private final String recipientName;
    private final String actorName;
    private final LocalDateTime bookingStartTime;
    private final LocalDateTime bookingEndTime;
    private final String reason;
    private final String meetingLink;
    private final LocalDateTime createdAt;
}
