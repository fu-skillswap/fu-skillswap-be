package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record BookingResponse(
        UUID bookingId,
        UUID sessionId,
        BookingStatus sessionStatus,
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
        String cancelReason,
        MeetingPlatform meetingPlatform,
        String meetingLink,
        String location,
        LocalDateTime requestedStartTime,
        LocalDateTime requestedEndTime,
        LocalDateTime actualStartTime,
        LocalDateTime actualEndTime,
        LocalDateTime acceptedAt,
        LocalDateTime rejectedAt,
        LocalDateTime cancelledAt,
        LocalDateTime completedAt,
        String mentorNote,
        String menteeNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
