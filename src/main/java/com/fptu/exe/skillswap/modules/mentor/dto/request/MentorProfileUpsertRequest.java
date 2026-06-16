package com.fptu.exe.skillswap.modules.mentor.dto.request;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Thong tin ho so mentor dung de onboarding va hien thi discovery")
public record MentorProfileUpsertRequest(
        @Schema(example = "Backend Developer | Spring Boot Mentor", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tiêu đề hồ sơ mentor không được để trống")
        @Size(max = 200, message = "Tiêu đề hồ sơ mentor không được quá 200 ký tự")
        String headline,

        @Schema(example = "Mình có kinh nghiệm xây dựng REST API với Spring Boot, PostgreSQL và triển khai Docker.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Mô tả chuyên môn không được để trống")
        @Size(max = 1000, message = "Mô tả chuyên môn không được quá 1000 ký tự")
        String expertiseDescription,

        @Schema(example = "Cơ sở dữ liệu, Lập trình Java, Kiến trúc API", nullable = true)
        @Size(max = 1000, message = "Các môn học có thể hỗ trợ không được quá 1000 ký tự")
        String supportingSubjects,

        @Schema(description = "Mentor co dang san sang nhan mentee khong. Neu null khi tao moi se mac dinh true.")
        Boolean isAvailable,

        @Schema(description = "Danh sách chủ đề mentor có thể hỗ trợ", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Danh sách chủ đề hỗ trợ không được để trống")
        @Size(max = 20, message = "Không được chọn quá 20 chủ đề hỗ trợ")
        List<@NotNull(message = "Chủ đề hỗ trợ không hợp lệ") UUID> helpTopicIds,

        @Schema(example = "ONLINE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Hình thức mentoring không được để trống")
        TeachingMode teachingMode,

        @Schema(example = "60", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Thời lượng phiên mentoring không được để trống")
        Integer sessionDuration,

        @Schema(example = "https://www.linkedin.com/in/example")
        String linkedinUrl,

        @Schema(example = "https://github.com/example")
        String githubUrl,

        @Schema(example = "https://example.dev")
        String portfolioUrl
) {
}
