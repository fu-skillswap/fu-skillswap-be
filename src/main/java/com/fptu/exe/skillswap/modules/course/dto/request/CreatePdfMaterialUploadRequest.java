package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tạo tài liệu PDF và chuẩn bị upload. Sau khi upload thành công, FE gọi API confirm với objectKey do backend cấp.")
public record CreatePdfMaterialUploadRequest(
        @Schema(description = "Tên tài liệu hiển thị.", example = "Tài liệu tham khảo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,
        @Schema(description = "Tên file PDF, không gửi đường dẫn local.", example = "reference.pdf", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String filename,
        @Schema(description = "Vị trí hiển thị trong chương.", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer sortOrder,
        @Schema(description = "Cho phép xem trước hay không.", example = "true", nullable = true) Boolean previewable,
        @Schema(description = "Có công bố tài liệu hay chưa.", example = "true", nullable = true) Boolean published
) {
}
