package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload mentor chấp nhận booking")
public record AcceptBookingRequest(
        @Schema(description = "Ghi chú tùy chọn của mentor gửi cho mentee khi accept", nullable = true, example = "Anh đã xem mục tiêu của em, mình sẽ tập trung vào phần REST API và mock interview.")
        @Size(max = 2000, message = "mentorResponseNote không được vượt quá 2000 ký tự")
        String mentorResponseNote,

        @Schema(description = "Nền tảng phòng học (Bắt buộc nếu mentor chưa kết nối Google Calendar; Tùy chọn nếu đã kết nối)", nullable = true, example = "GOOGLE_MEET")
        MeetingPlatform meetingPlatform,

        @Schema(description = "Link phòng học online (Bắt buộc nếu mentor chưa kết nối Google Calendar và không phải OFFLINE; Tùy chọn nếu đã kết nối)", nullable = true, example = "https://meet.google.com/abc-defg-hij")
        @Size(max = 1000, message = "meetingLink không được vượt quá 1000 ký tự")
        String meetingLink,

        @Schema(description = "Địa điểm hoặc ghi chú phòng học nếu mentoring offline", nullable = true, example = "Thư viện FPTU HCM - tầng 2")
        @Size(max = 500, message = "location không được vượt quá 500 ký tự")
        String location
) {
    public AcceptBookingRequest(String mentorResponseNote) {
        this(mentorResponseNote, null, null, null);
    }
}

