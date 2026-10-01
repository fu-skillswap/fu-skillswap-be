package com.fptu.exe.skillswap.modules.system.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Thông tin kiểm soát phiên bản và trạng thái bảo trì ứng dụng di động")
public record AppVersionResponse(
        @Schema(description = "Mã nền tảng (android, ios)", example = "android")
        String platform,

        @Schema(description = "Mã phiên bản tối thiểu bắt buộc để chạy ứng dụng (Hard Update nếu client < minVersionCode)", example = "1")
        int minVersionCode,

        @Schema(description = "Mã phiên bản mới nhất trên Store (Soft Update nếu minVersionCode <= client < latestVersionCode)", example = "1")
        int latestVersionCode,

        @Schema(description = "Cờ trạng thái bảo trì hệ thống cho app (chặn truy cập nếu true)", example = "false")
        boolean isMaintenance,

        @Schema(description = "Đường dẫn tải/cập nhật ứng dụng trên Store", example = "https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap")
        String updateUrl,

        @Schema(description = "Thông điệp bảo trì hiển thị trên app khi isMaintenance = true", example = "Hệ thống đang bảo trì nâng cấp, vui lòng quay lại sau.")
        String maintenanceMessage
) {}
