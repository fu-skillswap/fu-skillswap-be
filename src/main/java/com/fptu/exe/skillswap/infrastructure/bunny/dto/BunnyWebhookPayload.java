package com.fptu.exe.skillswap.infrastructure.bunny.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Internal/System - không dùng cho FE. Payload callback nguyên bản từ Bunny Video.")
public class BunnyWebhookPayload {
    @Schema(description = "Internal/System - ID video do Bunny gửi.")
    private String VideoGuid;
    @Schema(description = "Internal/System - ID thư viện video do Bunny gửi.")
    private String VideoLibraryId;
    @Schema(description = "Internal/System - trạng thái xử lý video do Bunny gửi.")
    private int Status;
}
