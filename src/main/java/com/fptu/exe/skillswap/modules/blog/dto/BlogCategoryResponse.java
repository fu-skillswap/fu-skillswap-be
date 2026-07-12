package com.fptu.exe.skillswap.modules.blog.dto;

import java.util.UUID;

public record BlogCategoryResponse(
        UUID id,
        String code,
        String name,
        String slug,
        String description,
        boolean active,
        Integer displayOrder
) {
}
