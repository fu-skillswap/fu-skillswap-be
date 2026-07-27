package com.fptu.exe.skillswap.modules.filestorage.dto.response;

import java.util.UUID;

public record PublicAssetResponse(UUID assetId, String publicUrl, String contentType, long sizeBytes) {}
