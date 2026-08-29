package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePdfMaterialUploadRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 255) String filename,
        @NotNull Integer sortOrder,
        Boolean previewable,
        Boolean published
) {
}
