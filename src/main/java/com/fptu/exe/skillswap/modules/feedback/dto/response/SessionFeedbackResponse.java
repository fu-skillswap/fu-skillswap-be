package com.fptu.exe.skillswap.modules.feedback.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin feedback đã được tạo cho một booking completed")
public class SessionFeedbackResponse {
    @Schema(description = "ID feedback")
    private UUID id;
    @Schema(description = "ID booking/session được đánh giá")
    private UUID sessionId;
    @Schema(description = "userId người gửi feedback")
    private UUID reviewerUserId;
    @Schema(description = "Tên hiển thị người gửi feedback")
    private String reviewerDisplayName;
    @Schema(description = "userId người được đánh giá")
    private UUID revieweeUserId;
    @Schema(description = "Tên hiển thị người được đánh giá")
    private String revieweeDisplayName;
    @Schema(description = "Điểm rating", example = "5")
    private Integer rating;
    @Schema(description = "Mức độ hài lòng chi tiết", nullable = true, example = "5")
    private Integer satisfactionLevel;
    @Schema(description = "Nội dung feedback", nullable = true)
    private String comment;
    @Schema(description = "Có sẵn sàng recommend hay không", nullable = true)
    private Boolean wouldRecommend;
    @Schema(description = "Feedback có công khai hay không")
    private boolean isPublic;
    @Schema(description = "Thời điểm tạo feedback")
    private LocalDateTime createdAt;
}
