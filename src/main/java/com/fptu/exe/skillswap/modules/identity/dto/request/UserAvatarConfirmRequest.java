package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Yêu cầu xác nhận upload ảnh đại diện mới sau khi FE đã upload ảnh lên URL tạm.")
public record UserAvatarConfirmRequest(
        @NotNull(message = "uploadIntentId không được để trống")
        @Schema(description = "ID upload intent trả về từ API tạo upload intent cho avatar.")
        UUID uploadIntentId
) {
}
