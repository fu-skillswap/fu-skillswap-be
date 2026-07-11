package com.fptu.exe.skillswap.shared.cursor;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(description = "Opaque cursor token payload used internally for keyset pagination.")
public record CursorTokenPayload(
        @Schema(description = "Primary sort key value serialized as string")
        String sortKey,
        @Schema(description = "Secondary tie-breaker key value serialized as string")
        String secondaryKey,
        @Schema(description = "Cursor direction such as NEXT or PREVIOUS")
        String direction,
        @Schema(description = "Hash of the active filter signature")
        String filterHash,
        @Schema(description = "Optional issuance timestamp of the cursor")
        Instant issuedAt
) {
}
