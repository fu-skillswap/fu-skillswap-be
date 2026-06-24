package com.fptu.exe.skillswap.modules.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reason payload for banning a visible user account from admin moderation screens.")
public class BanUserRequest {
    @Schema(description = "Human-readable admin reason for banning the user", example = "Vi phạm quy định cộng đồng và cần tạm khóa tài khoản để kiểm tra.")
    @NotBlank(message = "Lý do khóa tài khoản không được để trống")
    private String reason;
}
