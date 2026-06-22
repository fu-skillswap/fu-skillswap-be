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
@Schema(description = "Standard API Response envelope wrapper")
public class ApiResponse<T> {
    @Schema(description = "Response timestamp in format yyyy-MM-dd HH:mm:ss", example = "2026-06-22 21:20:25")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP response status code", example = "200")
    private int status;

    @Schema(description = "Custom business success/error code", example = "SUCCESS_0200")
    private String code;

    @Schema(description = "Descriptive response message", example = "success")
    private String message;

    @Schema(description = "Generic payload of the response")
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .timestamp(DateTimeUtil.now())
                .status(200)
                .code("SUCCESS_0200")
                .message("success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .timestamp(DateTimeUtil.now())
                .status(201)
                .code("CREATED_0201")
                .message("Created successfully")
                .data(data)
                .build();
    }
}

