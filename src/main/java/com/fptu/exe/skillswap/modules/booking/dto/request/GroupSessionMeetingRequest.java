package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupSessionMeetingRequest(
        @NotNull MeetingPlatform meetingPlatform,
        @Size(max = 2000) String meetingLink,
        @Size(max = 500) String location
) {
}
