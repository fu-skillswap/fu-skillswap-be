package com.fptu.exe.skillswap.modules.mentor.dto.response;

import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import lombok.Builder;

import java.util.UUID;

@Builder
public record MentorTagResponse(
        UUID id,
        String code,
        String nameVi,
        String nameEn,
        TagType type,
        boolean primary
) {
}
