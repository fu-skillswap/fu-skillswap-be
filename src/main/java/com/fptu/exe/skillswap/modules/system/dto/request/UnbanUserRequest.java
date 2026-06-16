package com.fptu.exe.skillswap.modules.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnbanUserRequest {
    @NotBlank(message = "Lý do mở khóa tài khoản không được để trống")
    private String reason;
}
