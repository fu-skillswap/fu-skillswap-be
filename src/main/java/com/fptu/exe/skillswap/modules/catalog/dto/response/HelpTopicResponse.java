package com.fptu.exe.skillswap.modules.catalog.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record HelpTopicResponse(
        UUID id,
        String code,
        String nameVi,
        String nameEn,
        Integer weight
) {
}
