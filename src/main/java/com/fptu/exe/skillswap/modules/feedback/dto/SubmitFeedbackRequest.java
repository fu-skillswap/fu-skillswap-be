package com.fptu.exe.skillswap.modules.feedback.dto;

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
public class SubmitFeedbackRequest {
    @NotNull(message = "Đánh giá sao không được trống")
    @Min(value = 1, message = "Đánh giá sao tối thiểu là 1")
    @Max(value = 5, message = "Đánh giá sao tối đa là 5")
    private Integer rating;

    @Min(value = 1, message = "Mức độ hài lòng tối thiểu là 1")
    @Max(value = 5, message = "Mức độ hài lòng tối đa là 5")
    private Integer satisfactionLevel;

    private String comment;

    private Boolean wouldRecommend;

    private Boolean isPublic;
}
