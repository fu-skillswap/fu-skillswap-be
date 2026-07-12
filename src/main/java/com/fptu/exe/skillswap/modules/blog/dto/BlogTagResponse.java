package com.fptu.exe.skillswap.modules.blog.dto;

import java.util.UUID;

public record BlogTagResponse(
        UUID id,
        String name,
        String slug,
        boolean active
) {
}
