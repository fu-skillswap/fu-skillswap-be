package com.fptu.exe.skillswap.modules.mentor.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Public catalog reference. Null when the referenced catalog item is unavailable or inactive.")
public record PublicEducationReferenceResponse(
        UUID id,
        String name
) {
}
