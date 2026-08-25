package com.fptu.exe.skillswap.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminBookingIssueEvidenceVisibilityRequest(
        boolean hidden,
        @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự") String reason
) {
}
