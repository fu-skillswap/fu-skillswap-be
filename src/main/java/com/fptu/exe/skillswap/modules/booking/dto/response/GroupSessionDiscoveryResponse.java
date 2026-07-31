package com.fptu.exe.skillswap.modules.booking.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public learner view. It intentionally excludes attendee and payment data. */
public record GroupSessionDiscoveryResponse(
        UUID groupSessionId,
        UUID mentorUserId,
        String mentorDisplayName,
        String mentorAvatarUrl,
        UUID serviceId,
        String serviceTitle,
        String serviceDescription,
        String serviceExpectedOutcome,
        Integer durationMinutes,
        Boolean isFree,
        Integer basePriceScoin,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Instant registrationClosesAt,
        int maxParticipants,
        int reservedSeatCount,
        int availableSeats,
        boolean joinable,
        String sessionNote
) {}
