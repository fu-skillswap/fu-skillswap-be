package com.fptu.exe.skillswap.modules.forum.dto.response;

import java.util.UUID;

/** Immutable author-program snapshot shown with a Forum post. */
public record ForumProgramResponse(
        UUID id,
        String code,
        String nameVi,
        String nameEn
) {
}
