package com.fptu.exe.skillswap.modules.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Trạng thái kết nối Google Calendar của user hiện tại")
public record GoogleCalendarStatusResponse(
        @Schema(description = "True nếu đã kết nối Google Calendar", example = "true")
        boolean connected,
        @Schema(description = "True nếu sync tự động đang khả dụng", example = "true")
        boolean syncEnabled,
        @Schema(description = "Email Google đang liên kết", nullable = true, example = "mentor@fpt.edu.vn")
        String email,
        @Schema(description = "Danh sách scopes đã cấp", nullable = true)
        List<String> grantedScopes,
        @Schema(description = "True nếu cần reconnect do token/scope không hợp lệ", example = "false")
        boolean needsReconnect,
        @Schema(description = "Trạng thái sync gần nhất", nullable = true, example = "SYNCED")
        String lastSyncStatus,
        @Schema(description = "Thời điểm sync gần nhất", nullable = true)
        LocalDateTime lastSyncAt,
        @Schema(description = "Mã lỗi sync gần nhất", nullable = true)
        String lastSyncErrorCode,
        @Schema(description = "Thông điệp lỗi sync gần nhất", nullable = true)
        String lastSyncErrorMessage
) {
}
