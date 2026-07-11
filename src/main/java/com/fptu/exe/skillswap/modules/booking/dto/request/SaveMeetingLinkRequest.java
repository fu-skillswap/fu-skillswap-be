package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload mentor lưu hoặc cập nhật meeting link cho booking đã được accept")
public record SaveMeetingLinkRequest(
        @Schema(description = "Nền tảng dùng để mentoring", example = "GOOGLE_MEET")
        @NotNull(message = "meetingPlatform không được để trống")
        MeetingPlatform meetingPlatform,

        @Schema(description = "Meeting link online", example = "https://meet.google.com/abc-defg-hij")
        @NotBlank(message = "meetingLink không được để trống")
        @Size(max = 1000, message = "meetingLink không được vượt quá 1000 ký tự")
        String meetingLink,

        @Schema(description = "Địa điểm hoặc ghi chú vị trí nếu mentoring offline", nullable = true, example = "Thư viện FPTU HCM - tầng 2")
        @Size(max = 500, message = "location không được vượt quá 500 ký tự")
        String location
) {
}
