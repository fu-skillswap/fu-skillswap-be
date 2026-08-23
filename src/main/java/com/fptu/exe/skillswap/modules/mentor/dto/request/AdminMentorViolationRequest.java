package com.fptu.exe.skillswap.modules.mentor.dto.request;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSeverity;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSource;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Admin ghi nhận một vi phạm đã được xác minh của mentor")
public record AdminMentorViolationRequest(
        @NotNull MentorViolationSource sourceModule,
        @NotNull UUID sourceReferenceId,
        @NotNull MentorViolationType type,
        @NotNull MentorViolationSeverity severity,
        @Size(min = 3, max = 500) String reason,
        @Size(max = 1000) String decisionNote
) {
}
