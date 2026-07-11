package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Thông tin dịch vụ mentoring do mentor tạo")
public record MentorServiceUpsertRequest(
        @Schema(example = "Review CV xin thực tập")
        @NotBlank(message = "Tiêu đề dịch vụ không được để trống")
        @Size(max = 200, message = "Tiêu đề dịch vụ không được quá 200 ký tự")
        String title,

        @Schema(example = "Mình sẽ review CV, chỉ ra điểm yếu và gợi ý cách cải thiện theo vị trí backend intern.")
        @NotBlank(message = "Mô tả dịch vụ không được để trống")
        @Size(max = 1000, message = "Mô tả dịch vụ không được quá 1000 ký tự")
        String description,

        @Schema(example = "CV rõ điểm mạnh, có action items cụ thể sau buổi mentoring.")
        @NotBlank(message = "Kết quả kỳ vọng không được để trống")
        @Size(max = 1000, message = "Kết quả kỳ vọng không được quá 1000 ký tự")
        String expectedOutcome,

        @Schema(example = "60", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Thời lượng dịch vụ không được để trống")
        Integer durationMinutes,

        @Schema(description = "Dịch vụ miễn phí hay không")
        @NotNull(message = "Trạng thái miễn phí không được để trống")
        Boolean isFree,

        @Schema(example = "72000", nullable = true, description = "Giá dịch vụ tính theo SCoin. Nếu isFree=false thì giá tối thiểu = durationMinutes x 1.200. Nếu isFree=true thì phải bằng 0.")
        @NotNull(message = "Giá dịch vụ không được để trống")
        @Min(value = 0, message = "Giá dịch vụ không được nhỏ hơn 0")
        @jakarta.validation.constraints.Max(value = 45000000, message = "Giá dịch vụ không được vượt quá 45.000.000 SCoin")
        Integer priceScoin,

        @Schema(description = "Danh sách help topic mentor service có thể hỗ trợ")
        @NotEmpty(message = "Danh sách chủ đề hỗ trợ của dịch vụ không được để trống")
        @Size(max = 20, message = "Dịch vụ không được có quá 20 chủ đề hỗ trợ")
        List<@NotNull(message = "Chủ đề hỗ trợ không hợp lệ") UUID> helpTopicIds
) {
}
