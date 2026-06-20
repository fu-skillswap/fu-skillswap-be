package com.fptu.exe.skillswap.modules.feedback.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload mentee gửi feedback sau buổi mentoring")
public class SubmitFeedbackRequest {
    @Schema(description = "Điểm rating tổng quát cho mentor", example = "5")
    @NotNull(message = "Đánh giá sao không được trống")
    @Min(value = 1, message = "Đánh giá sao tối thiểu là 1")
    @Max(value = 5, message = "Đánh giá sao tối đa là 5")
    private Integer rating;

    @Schema(description = "Mức độ hài lòng chi tiết", nullable = true, example = "5")
    @Min(value = 1, message = "Mức độ hài lòng tối thiểu là 1")
    @Max(value = 5, message = "Mức độ hài lòng tối đa là 5")
    private Integer satisfactionLevel;

    @Schema(description = "Nội dung feedback bằng văn bản", nullable = true, example = "Mentor giải thích rõ ràng, góp ý CV rất sát với mục tiêu intern backend.")
    private String comment;

    @Schema(description = "Người gửi có sẵn sàng recommend mentor này không", nullable = true, example = "true")
    private Boolean wouldRecommend;

    @Schema(description = "Có cho phép hiển thị feedback công khai trên trang mentor hay không", nullable = true, example = "true")
    private Boolean isPublic;
}
