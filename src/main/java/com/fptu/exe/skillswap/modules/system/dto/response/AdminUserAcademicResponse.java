package com.fptu.exe.skillswap.modules.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Thông tin học thuật và trạng thái xác thực mã số sinh viên")
public record AdminUserAcademicResponse(
        @Schema(description = "Mã số sinh viên người dùng tự nhập (có thể trùng)")
        String claimedStudentCode,
        @Schema(description = "Mã số sinh viên đã được hệ thống/admin xác thực (duy nhất)")
        String verifiedStudentCode,
        @Schema(description = "Trạng thái xác thực mã số sinh viên")
        boolean studentCodeVerified,
        @Schema(description = "Cờ báo hiệu mã số sinh viên tự nhập đang bị trùng với người khác")
        boolean studentCodeConflict
) {
}
