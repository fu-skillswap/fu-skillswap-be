package com.fptu.exe.skillswap.modules.booking.dto;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record BookingResponse(
        UUID bookingId,
        UUID sessionId,
        SessionStatus sessionStatus,
        UUID mentorUserId,
        String mentorDisplayName,
        String mentorAvatarUrl,
        UUID menteeUserId,
        String menteeDisplayName,
        String menteeAvatarUrl,
        UUID slotId,
        UUID serviceId,
        String serviceTitle,
        BookingStatus status,
        String learningGoalTitle,
        String learningGoalDescription,
        String mentorResponseNote,
        String rejectReason,
        LocalDateTime requestedStartTime,
        LocalDateTime requestedEndTime,
        LocalDateTime acceptedAt,
        LocalDateTime rejectedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
