package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tóm tắt hồ sơ học thuật của user cho admin.")
public record AdminUserSummaryAcademicProfileResponse(
        @Schema(description = "Mã sinh viên đã claim", example = "HE173001")
        String studentCode,
        @Schema(description = "Campus code", example = "HCM")
        String campusCode,
        @Schema(description = "Campus name", example = "FPT University Ho Chi Minh City")
        String campusName,
        @Schema(description = "Program code", example = "SE")
        String programCode,
        @Schema(description = "Program name", example = "Ky thuat phan mem")
        String programName,
        @Schema(description = "Specialization code", example = "SE_AI")
        String specializationCode,
        @Schema(description = "Specialization name", example = "AI")
        String specializationName,
        @Schema(description = "Semester hiện tại", example = "6")
        Integer semester,
        @Schema(description = "Cờ alumni", example = "false")
        Boolean isAlumni
) {
}
