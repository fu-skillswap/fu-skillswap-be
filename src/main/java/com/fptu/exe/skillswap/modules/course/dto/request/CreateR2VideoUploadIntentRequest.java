package com.fptu.exe.skillswap.modules.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request used by the provider-neutral R2 video upload flow. */
@Schema(description = "Thông tin file video để backend tạo upload intent. FE không gửi courseId, chapterId hoặc userId trong body vì các giá trị này lấy từ URL và JWT.")
public record CreateR2VideoUploadIntentRequest(
        @Schema(description = "Tên video hiển thị trong khóa học.", example = "Giới thiệu Spring Boot", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "Tên file gốc chỉ dùng để kiểm tra loại file; không gửi đường dẫn local.", example = "spring-boot-intro.mp4", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String filename,
        @Schema(description = "MIME type của file. MVP chỉ hỗ trợ video/mp4.", example = "video/mp4", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String contentType,
        @Schema(description = "Kích thước file tính bằng byte; phải lớn hơn 0 và không vượt giới hạn cấu hình.", example = "52428800", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Long sizeBytes,
        @Schema(description = "Vị trí hiển thị trong chương.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer sortOrder,
        @Schema(description = "Cho phép người chưa có quyền đầy đủ xem trước hay không.", example = "false", nullable = true)
        Boolean previewable,
        @Schema(description = "Có công bố tài liệu cho người học hay chưa. Chỉ material đã READY mới có thể playback.", example = "false", nullable = true)
        Boolean published
) {
}
