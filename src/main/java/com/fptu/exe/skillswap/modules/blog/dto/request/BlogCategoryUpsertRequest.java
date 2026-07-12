package com.fptu.exe.skillswap.modules.blog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlogCategoryUpsertRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 160) String slug,
        String description,
        Boolean active,
        Integer displayOrder
) {
}
