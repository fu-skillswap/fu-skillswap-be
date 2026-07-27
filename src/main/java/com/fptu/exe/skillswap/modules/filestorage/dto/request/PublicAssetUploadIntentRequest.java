package com.fptu.exe.skillswap.modules.filestorage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicAssetUploadIntentRequest(@NotBlank @Size(max = 180) String filename, @NotBlank @Size(max = 120) String contentType) {}
