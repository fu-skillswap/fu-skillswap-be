package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tổng quan user của hệ thống cho admin dashboard.")
public record AdminDashboardUsersOverviewResponse(
        @Schema(description = "Tổng số user trong bảng users, bao gồm cả admin/system admin và user đã bị soft delete.", example = "1280")
        long total,
        @Schema(description = "Số user đang active và chưa bị soft delete.", example = "1190")
        long active,
        @Schema(description = "Số user đang bị banned và chưa bị soft delete.", example = "12")
        long banned,
        @Schema(description = "Số user đã bị soft delete hoặc có trạng thái DELETED.", example = "8")
        long deleted,
        @Schema(description = "Số user chỉ có role MENTEE, không có MENTOR, ADMIN hoặc SYSTEM_ADMIN.", example = "920")
        long menteeOnly,
        @Schema(description = "Số user có role MENTOR và không có ADMIN hoặc SYSTEM_ADMIN.", example = "210")
        long mentor
) {
}
