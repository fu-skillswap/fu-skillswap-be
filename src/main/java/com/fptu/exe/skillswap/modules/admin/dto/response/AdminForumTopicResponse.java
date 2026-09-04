package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Chủ đề forum trong màn hình moderation admin.")
public record AdminForumTopicResponse(
        UUID id,
        String code,
        String nameVi,
        String nameEn,
        Integer displayOrder
) {
}
