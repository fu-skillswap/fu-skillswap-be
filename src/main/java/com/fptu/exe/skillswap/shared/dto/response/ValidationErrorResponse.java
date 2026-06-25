package com.fptu.exe.skillswap.shared.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Chi tiết lỗi validation cho từng field đầu vào.")
public record ValidationErrorResponse(
        @Schema(description = "Tên field bị lỗi", example = "phoneNumber")
        String field,
        @Schema(description = "Thông điệp lỗi cụ thể", example = "Số điện thoại mentor không hợp lệ")
        String message,
        @Schema(description = "Giá trị client gửi lên nhưng bị từ chối", example = "abc123")
        Object rejectedValue
) {
}
