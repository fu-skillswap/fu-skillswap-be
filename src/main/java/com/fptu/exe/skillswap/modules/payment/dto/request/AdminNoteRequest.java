package com.fptu.exe.skillswap.modules.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ghi chú ngắn từ admin cho các action payout")
public record AdminNoteRequest(
        @Schema(description = "Nội dung ghi chú", nullable = true)
        String note
) {
}
