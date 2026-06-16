package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveMeetingLinkRequest(
        @NotNull(message = "meetingPlatform không được để trống")
        MeetingPlatform meetingPlatform,

        @NotBlank(message = "meetingLink không được để trống")
        @Size(max = 1000, message = "meetingLink không được vượt quá 1000 ký tự")
        String meetingLink,

        @Size(max = 500, message = "location không được vượt quá 500 ký tự")
        String location
) {
}
