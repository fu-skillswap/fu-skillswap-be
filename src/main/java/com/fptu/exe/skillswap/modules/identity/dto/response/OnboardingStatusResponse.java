package com.fptu.exe.skillswap.modules.identity.dto.response;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Thông tin trạng thái onboarding của người dùng hiện tại")
public record OnboardingStatusResponse(
        @Schema(description = "true nếu người dùng đã hoàn thành hồ sơ học thuật", example = "true")
        boolean studentProfileCompleted,

        @Schema(description = "true nếu người dùng đã hoàn thành hồ sơ mentor", example = "false")
        boolean mentorProfileCompleted,

        @Schema(description = "Trạng thái xác thực mentor mới nhất: NOT_STARTED, PENDING_REVIEW, APPROVED, NEEDS_REVISION, REJECTED, etc.", example = "NOT_STARTED")
        String mentorVerificationStatus,

        @Schema(description = "Danh sách vai trò hiện tại của người dùng", example = "[\"MENTEE\"]")
        List<RoleCode> roles,

        @Schema(description = "Hành động gợi ý tiếp theo cho người dùng để hoàn thành onboarding", example = "COMPLETE_STUDENT_PROFILE")
        String nextRecommendedAction
) {
}
