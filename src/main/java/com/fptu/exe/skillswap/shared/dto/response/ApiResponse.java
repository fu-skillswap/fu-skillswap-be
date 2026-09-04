package com.fptu.exe.skillswap.shared.dto.response;

import java.time.Instant;
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
@Schema(description = "Mẫu response chung của SkillSwap cho cả thành công và lỗi. FE nên đọc đồng thời HTTP status, code, message và data.")
public class ApiResponse<T> {
    
    @Schema(description = "Thời điểm trả response theo UTC và định dạng ISO-8601", example = "2026-06-22T14:20:25Z")
    private Instant timestamp;

    @Schema(description = "Mã HTTP của response", example = "200")
    private int status;

    @Schema(description = "Mã nghiệp vụ cho kết quả thành công hoặc lỗi; dùng cùng HTTP status để xử lý đúng luồng", example = "SUCCESS_0200")
    private String code;

    @Schema(description = "Thông báo dễ hiểu dành cho FE hoặc người dùng trong các trường hợp thông thường", example = "Thành công")
    private String message;

    @Schema(description = "Dữ liệu kết quả theo kiểu của API; thường null khi lỗi hoặc khi thao tác chỉ cập nhật trạng thái")
    private T data;

    @Schema(description = "Số giây FE nên chờ trước khi thử lại; chỉ có trong response HTTP 429", example = "35", nullable = true)
    private Long retryAfterSeconds;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .timestamp(DateTimeUtil.instantNow())
                .status(200)
                .code("SUCCESS_0200")
                .message("Thành công")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .timestamp(DateTimeUtil.instantNow())
                .status(201)
                .code("CREATED_0201")
                .message("Tạo mới thành công")
                .data(data)
                .build();
    }
}

