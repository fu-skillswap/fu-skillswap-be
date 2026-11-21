package com.fptu.exe.skillswap.modules.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Thông tin ảnh đại diện hiện tại của người dùng sau khi cập nhật hoặc gỡ.")
public record UserAvatarResponse(
        @Schema(description = "ID người dùng sở hữu ảnh đại diện.")
        UUID userId,
        @Schema(description = "ID asset của ảnh đại diện đã upload, null nếu đang dùng ảnh mặc định từ mail hoặc chưa có ảnh.", nullable = true)
        UUID avatarAssetId,
        @Schema(description = "URL công khai hiện tại của ảnh đại diện.", nullable = true)
        String avatarUrl,
        @Schema(description = "true nếu thao tác vừa khôi phục về ảnh đại diện từ tài khoản mail/Google.")
        boolean restoredFromMail
) {
}
