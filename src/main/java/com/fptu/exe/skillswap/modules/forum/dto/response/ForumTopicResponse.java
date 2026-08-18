package com.fptu.exe.skillswap.modules.forum.dto.response;

import com.fptu.exe.skillswap.modules.forum.domain.ForumTopicCode;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ForumTopicResponse(
        UUID id,
        ForumTopicCode code,
        String nameVi,
        String nameEn,
        Integer displayOrder
) {
}
