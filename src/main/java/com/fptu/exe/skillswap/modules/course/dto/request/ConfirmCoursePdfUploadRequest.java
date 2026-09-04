package com.fptu.exe.skillswap.modules.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** The client confirms only the immutable upload target issued by the server. */
@Schema(description = "FE gọi sau khi upload PDF vào uploadUrl thành công. FE chỉ gửi lại đúng objectKey do backend cấp, không tự tạo hoặc chỉnh sửa giá trị này.")
public record ConfirmCoursePdfUploadRequest(@NotBlank String objectKey) {
}
