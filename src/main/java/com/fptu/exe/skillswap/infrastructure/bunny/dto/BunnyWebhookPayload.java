package com.fptu.exe.skillswap.infrastructure.bunny.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BunnyWebhookPayload {
    private String VideoGuid;
    private String VideoLibraryId;
    private int Status;
}
