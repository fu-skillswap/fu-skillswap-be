package com.fptu.exe.skillswap.shared.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope used by SkillSwap for both success and error responses. Frontend integrations should always read the HTTP status together with the business code, message, and typed data payload inside this wrapper.")
public class ApiResponse<T> {
    
    @Schema(description = "Response timestamp in format yyyy-MM-dd HH:mm:ss", example = "2026-06-22 21:20:25")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP response status code", example = "200")
    private int status;

    @Schema(description = "Custom business success or error code. Frontend clients can use this together with the HTTP status to map product-specific error handling flows.", example = "SUCCESS_0200")
    private String code;

    @Schema(description = "Human-readable response message. This is intended to be safe for frontend display in common success or error flows.", example = "Thành công")
    private String message;

    @Schema(description = "Typed payload of the response. This is null for error responses and may also be null for successful operations that only update state.")
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .timestamp(DateTimeUtil.now())
                .status(200)
                .code("SUCCESS_0200")
                .message("Thành công")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .timestamp(DateTimeUtil.now())
                .status(201)
                .code("CREATED_0201")
                .message("Tạo mới thành công")
                .data(data)
                .build();
    }
}

