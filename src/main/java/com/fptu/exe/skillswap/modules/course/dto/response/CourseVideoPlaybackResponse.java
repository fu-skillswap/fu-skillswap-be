package com.fptu.exe.skillswap.modules.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CourseVideoPlaybackResponse {
    private UUID materialId;
    private String title;
    private String playbackUrl;
    private String thumbnailUrl;
    private Integer durationSeconds;
    private Instant expiresAt;
}
