package com.fptu.exe.skillswap.modules.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record GoogleLoginNonceResponse(
        @Schema(description = "Nonce một lần phải truyền vào cấu hình GIS.", example = "pBt_T5mF...")
        String nonce,
        @Schema(description = "Thời điểm nonce hết hạn theo UTC.")
        Instant expiresAt
) {
}
