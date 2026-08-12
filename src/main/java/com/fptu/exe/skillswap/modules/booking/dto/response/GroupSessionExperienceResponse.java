package com.fptu.exe.skillswap.modules.booking.dto.response;

import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record GroupSessionExperienceResponse(
        UUID groupSessionId,
        UUID sessionId,
        SessionStatus sessionStatus,
        UUID conversationId,
        MeetingPlatform meetingPlatform,
        String meetingLink,
        String location,
        LocalDateTime attendanceDeadlineAt
) {}
