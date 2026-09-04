package com.fptu.exe.skillswap.modules.course.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu sắp xếp lại chương hoặc tài liệu. Backend kiểm tra quyền sở hữu và phiên bản hiện tại trước khi cập nhật.")
public record ReorderCurriculumRequest(
        @Schema(description = "Danh sách ID theo thứ tự hiển thị mới.", example = "[\"019f1234-aaaa-bbbb-cccc-1234567890ab\", \"019f2234-aaaa-bbbb-cccc-1234567890ab\"]")
        @NotEmpty List<UUID> orderedIds,
        @Schema(description = "Technical lifecycle field - phiên bản container mà FE đã đọc; dùng để tránh ghi đè cập nhật mới hơn.", example = "4")
        @NotNull Long expectedContainerVersion
) {
}
