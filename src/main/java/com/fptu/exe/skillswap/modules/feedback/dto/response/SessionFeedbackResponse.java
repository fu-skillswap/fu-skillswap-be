package com.fptu.exe.skillswap.modules.feedback.dto.response;

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
public class SessionFeedbackResponse {
    private UUID id;
    private UUID sessionId;
    private UUID reviewerUserId;
    private String reviewerDisplayName;
    private UUID revieweeUserId;
    private String revieweeDisplayName;
    private Integer rating;
    private Integer satisfactionLevel;
    private String comment;
    private Boolean wouldRecommend;
    private boolean isPublic;
    private LocalDateTime createdAt;
}
