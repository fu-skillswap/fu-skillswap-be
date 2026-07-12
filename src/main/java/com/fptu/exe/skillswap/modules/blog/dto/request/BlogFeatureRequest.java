package com.fptu.exe.skillswap.modules.blog.dto.request;

import java.time.LocalDateTime;

public record BlogFeatureRequest(
        Integer featuredOrder,
        LocalDateTime featuredUntil
) {
}
