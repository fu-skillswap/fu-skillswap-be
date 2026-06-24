package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Mentoring service definition that the mentor manages and the frontend can show in mentor detail or booking flows.")
public record MentorServiceResponse(
        @Schema(description = "Service ID", example = "019f3234-aaaa-bbbb-cccc-1234567890ab")
        UUID serviceId,
        @Schema(description = "Mentor user ID that owns the service", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID mentorUserId,
        @Schema(description = "Service title shown in mentor detail and booking selection", example = "Review CV xin thực tập")
        String title,
        @Schema(description = "Service description shown to mentees before booking", example = "Mình sẽ review CV, chỉ ra điểm yếu và gợi ý cách cải thiện theo vị trí backend intern.")
        String description,
        @Schema(description = "Service duration in minutes", example = "60")
        Integer durationMinutes,
        @Schema(description = "Whether the service is free", example = "true")
        boolean free,
        @Schema(description = "Service price amount when the service is paid", nullable = true, example = "120000")
        BigDecimal priceAmount,
        @Schema(description = "Service currency code when pricing is used", nullable = true, example = "VND")
        String currency,
        @Schema(description = "Whether the service is currently active for future use", example = "true")
        boolean active,
        @Schema(description = "Help topics covered by this service")
        List<MentorTagResponse> helpTopics,
        @Schema(description = "Service creation time", example = "2026-06-20T09:00:00")
        LocalDateTime createdAt,
        @Schema(description = "Latest service update time", example = "2026-06-24T10:00:00")
        LocalDateTime updatedAt
) {
}
