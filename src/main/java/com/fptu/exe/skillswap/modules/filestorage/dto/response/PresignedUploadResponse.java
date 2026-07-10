package com.fptu.exe.skillswap.modules.filestorage.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PresignedUploadResponse {
    private String uploadUrl;
    private String publicUrl;
    private String objectKey;
}
