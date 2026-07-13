package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

        @Schema(description = "Mentor co dang san sang nhan mentee khong. Neu null khi tao moi se mac dinh true.")
        Boolean isAvailable,

        @Schema(description = "Danh sách chủ đề mentor có thể hỗ trợ", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Danh sách chủ đề hỗ trợ không được để trống")
        @Size(max = 20, message = "Không được chọn quá 20 chủ đề hỗ trợ")
        List<@NotNull(message = "Chủ đề hỗ trợ không hợp lệ") UUID> helpTopicIds,

        @Schema(description = "Danh sách môn - điểm mentor dùng làm tín hiệu matching", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Danh sách môn - điểm không được để trống")
        @Size(max = 20, message = "Không được nhập quá 20 môn - điểm")
        List<@Valid MentorSubjectResultRequest> subjectResults,

        @Schema(description = "Mentor có thể giúp mentee lấy gốc tới mức nào, 1-4", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Mức hỗ trợ lấy gốc không được để trống")
        @Min(value = 1, message = "Mức hỗ trợ lấy gốc phải từ 1 đến 4")
        @Max(value = 4, message = "Mức hỗ trợ lấy gốc phải từ 1 đến 4")
        Integer foundationSupportLevel,

        @Schema(description = "Mentor có thể review bài nộp/project/CV/report tới mức nào, 1-4", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Mức hỗ trợ review output không được để trống")
        @Min(value = 1, message = "Mức hỗ trợ review output phải từ 1 đến 4")
        @Max(value = 4, message = "Mức hỗ trợ review output phải từ 1 đến 4")
        Integer outputReviewSupportLevel,

        @Schema(description = "Mentor có thể hỗ trợ định hướng/OJT/career tới mức nào, 1-4", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Mức hỗ trợ định hướng không được để trống")
        @Min(value = 1, message = "Mức hỗ trợ định hướng phải từ 1 đến 4")
        @Max(value = 4, message = "Mức hỗ trợ định hướng phải từ 1 đến 4")
        Integer directionSupportLevel,

        @Schema(example = "https://github.com/example")
        String githubUrl,

        @Schema(example = "https://example.dev")
        String portfolioUrl,

        @Schema(example = "0912345678", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Vui lòng nhập số điện thoại Việt Nam hợp lệ.")
        @Pattern(regexp = "^(0)(3|5|7|8|9)[0-9]{8}$", message = "Vui lòng nhập số điện thoại Việt Nam hợp lệ.")
        String phoneNumber,

        @Schema(description = "Số phút tối thiểu trước giờ bắt đầu mentor cho phép mentee đặt lịch", example = "120")
        @Min(value = 0, message = "Lead time đặt lịch phải lớn hơn hoặc bằng 0")
        Integer minimumBookingLeadTimeMinutes,

        @Schema(description = "Số ngày tối đa cho phép mentee đặt lịch tính từ hiện tại", example = "30")
        @Min(value = 1, message = "Horizon đặt lịch phải lớn hơn hoặc bằng 1")
        Integer maximumBookingHorizonDays,

        @Schema(description = "Timezone áp dụng cho policy đặt lịch", example = "Asia/Ho_Chi_Minh")
        @Size(max = 64, message = "Timezone không được quá 64 ký tự")
        String bookingTimezone
) {

    public MentorProfileUpsertRequest(
            String headline,
            String expertiseDescription,
            Boolean isAvailable,
            List<UUID> helpTopicIds,
            List<@Valid MentorSubjectResultRequest> subjectResults,
            Integer foundationSupportLevel,
            Integer outputReviewSupportLevel,
            Integer directionSupportLevel,
            String githubUrl,
            String portfolioUrl,
            String phoneNumber
    ) {
        this(headline,
                expertiseDescription,
                isAvailable,
                helpTopicIds,
                subjectResults,
                foundationSupportLevel,
                outputReviewSupportLevel,
                directionSupportLevel,
                githubUrl,
                portfolioUrl,
                phoneNumber,
                null,
                null,
                null);
    }
}
