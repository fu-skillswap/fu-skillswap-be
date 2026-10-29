package com.fptu.exe.skillswap.modules.identity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Thông tin đăng ký tài khoản bằng email và mật khẩu")
public record EmailRegisterRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        @Schema(description = "Địa chỉ email đăng ký", example = "student@fpt.edu.vn")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 64, message = "Mật khẩu phải từ 8 đến 64 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "Mật khẩu phải chứa ít nhất 1 chữ thường, 1 chữ hoa và 1 chữ số"
        )
        @Schema(description = "Mật khẩu đăng nhập", example = "SkillSwap@2026")
        String password,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 150, message = "Họ và tên phải từ 2 đến 150 ký tự")
        @Schema(description = "Họ và tên người dùng", example = "Nguyễn Văn A")
        String fullName
) {
}
