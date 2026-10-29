package com.fptu.exe.skillswap.modules.identity.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả gửi mã xác thực đăng ký hoặc liên kết tài khoản")
public record EmailRegisterResponse(
        @Schema(description = "Địa chỉ email nhận mã xác thực", example = "student@fpt.edu.vn")
        String email,

        @Schema(description = "Cho biết đây là liên kết tài khoản Google đã có (true) hay đăng ký tài khoản mới (false)", example = "false")
        boolean isLinked,

        @Schema(description = "Thông báo hướng dẫn người dùng", example = "Mã xác thực đã được gửi tới email của bạn. Vui lòng kiểm tra hộp thư để kích hoạt tài khoản.")
        String message
) {
}
