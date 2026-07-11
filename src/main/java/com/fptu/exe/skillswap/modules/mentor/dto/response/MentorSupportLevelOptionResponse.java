package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Label mức hỗ trợ mentor để FE không fix cứng")
public record MentorSupportLevelOptionResponse(
        Integer value,
        String label
) {
}
