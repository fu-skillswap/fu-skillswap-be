package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Tóm tắt user cho admin data center.")
public record AdminUserSummaryResponse(
        @Schema(description = "Id user", example = "019f1258-bdb6-7312-ac67-b289909329d1")
        UUID userId,
        @Schema(description = "Email user", example = "user@fpt.edu.vn")
        String email,
        @Schema(description = "Họ tên user", example = "Vo Quang Tam")
        String fullName,
        @Schema(description = "Avatar URL hiện tại của user")
        String avatarUrl,
        @Schema(description = "Trạng thái tài khoản", example = "ACTIVE")
        String status,
        @Schema(description = "Các role business visible của user", example = "[\"MENTEE\",\"MENTOR\"]")
        List<String> roles,
        @Schema(description = "Thời điểm login gần nhất", example = "2026-07-02T10:15:30")
        LocalDateTime lastLoginAt,
        @Schema(description = "Thời điểm tạo tài khoản", example = "2026-07-02T10:15:30")
        LocalDateTime createdAt,
        @Schema(description = "Tóm tắt academic profile nếu có")
        AdminUserSummaryAcademicProfileResponse academicProfile,
        @Schema(description = "Tóm tắt mentor profile")
        AdminUserSummaryMentorProfileResponse mentorProfile,
        @Schema(description = "Tóm tắt activity counts")
        AdminUserSummaryActivityResponse activitySummary
) {
}
