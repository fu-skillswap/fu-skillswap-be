package com.fptu.exe.skillswap.modules.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Thông tin học thuật của user")
public record AdminUserAcademicResponse(
        @Schema(description = "Mã số sinh viên người dùng tự nhập (có thể trùng)")
        String claimedStudentCode
) {
}
