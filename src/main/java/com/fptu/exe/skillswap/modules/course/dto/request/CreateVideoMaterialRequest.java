package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Tạo tài liệu video trong chương. Backend tự gắn course/chapter từ URL và tài khoản mentor hiện tại.")
public class CreateVideoMaterialRequest {
    @Schema(description = "Tên video hiển thị.", example = "Giới thiệu Spring Boot", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Title is required")
    private String title;
    @Schema(description = "Vị trí hiển thị trong chương.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "sortOrder is required")
    private Integer sortOrder;

    @Schema(description = "Cho phép người chưa có quyền đầy đủ xem trước video hay không.", example = "false", nullable = true)
    private Boolean previewable;

    @Schema(description = "Có công bố tài liệu cho người học hay chưa.", example = "true", nullable = true)
    private Boolean published;
}
