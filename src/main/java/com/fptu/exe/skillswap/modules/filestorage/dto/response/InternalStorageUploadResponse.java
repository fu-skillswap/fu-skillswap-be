package com.fptu.exe.skillswap.modules.filestorage.dto.response;

import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response boundary for local/system storage tools. JSON fields match the
 * legacy response because local integrations still rely on objectKey.
 */
@Schema(description = "Internal/System - kết quả thao tác storage kỹ thuật, không dùng cho FE nghiệp vụ. objectKey và provider metadata chỉ dành cho công cụ hệ thống.")
public record InternalStorageUploadResponse(
        String uploadUrl,
        String publicUrl,
        String objectKey,
        ProviderNeutralUploadMetadata uploadMetadata
) {
}
