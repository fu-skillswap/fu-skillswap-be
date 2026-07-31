package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Mentor-managed group-session supply record. Learner booking is intentionally unavailable until Phase 2.")
public record GroupSessionResponse(
        UUID groupSessionId,
        UUID serviceId,
        UUID sourceSlotId,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        int maxParticipants,
        int reservedSeatCount,
        GroupSessionStatus status,
        GroupSessionRegistrationStatus registrationStatus,
        Instant registrationClosesAt,
        String sessionNote,
        String serviceTitleSnapshot,
        String serviceDescriptionSnapshot,
        String serviceExpectedOutcomeSnapshot,
        Integer serviceDurationSnapshot,
        Boolean serviceIsFreeSnapshot,
        Integer servicePriceScoinSnapshot,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant cancelledAt
) {}
