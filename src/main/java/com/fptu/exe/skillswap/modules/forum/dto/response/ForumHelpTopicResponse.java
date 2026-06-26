package com.fptu.exe.skillswap.modules.forum.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ForumHelpTopicResponse(
        UUID id,
        String code,
        String nameVi,
        String nameEn
) {
}
