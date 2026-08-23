package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;

@Schema(description = "Thông tin tạo mới dịch vụ mentoring")
public record CreateMentorServiceRequest(
        @Schema(description = "Tiêu đề dịch vụ", example = "Review CV & Mock Interview Java", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "Mô tả chi tiết dịch vụ", example = "Hướng dẫn tối ưu CV và phỏng vấn thử 1-1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000) String description,

        @Schema(description = "Kết quả cam kết sau buổi học", example = "Có checklist cải thiện CV và định hướng lộ trình rõ ràng", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 1000) String expectedOutcome,

        @Schema(description = "Thời lượng buổi mentoring (phút)", example = "60", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer durationMinutes,

        @Schema(description = "Dịch vụ miễn phí hay có phí", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean isFree,

        @Schema(description = "Giá dịch vụ theo SCoin (Nếu isFree=true thì giá phải bằng 0; nếu có phí thì tối thiểu = durationMinutes * 500)", example = "30000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) @Max(45_000_000) Integer priceScoin,

        @Schema(description = "Duy trì kênh chat hỗ trợ sau buổi mentoring", example = "true")
        Boolean maintainPostSessionChat,

        @Schema(
                description = "Hình thức cung cấp dịch vụ:<br/>"
                        + "• `ONE_TO_ONE`: Kèm 1-1 cá nhân<br/>"
                        + "• `GROUP`: Hướng dẫn theo nhóm",
                example = "ONE_TO_ONE",
                allowableValues = {"ONE_TO_ONE", "GROUP"}
        )
        MentorServiceDeliveryMode deliveryMode
) {}
