package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Thông tin chương trình học của tác giả trong màn hình moderation admin.")
public record AdminForumProgramResponse(
        UUID id,
        String code,
        String nameVi,
        String nameEn
) {
}
