package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Tạo report cho bài viết hoặc bình luận forum. Backend tự xác định người report từ tài khoản đăng nhập và kiểm tra target có tồn tại hay không.")
public record ForumReportCreateRequest(
        @NotNull(message = "targetType là bắt buộc")
        @Schema(description = "Loại nội dung bị report: POST hoặc COMMENT.", example = "POST", requiredMode = Schema.RequiredMode.REQUIRED)
        ForumReportTargetType targetType,

        @NotNull(message = "targetId là bắt buộc")
        @Schema(description = "ID bài viết hoặc bình luận bị report.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID targetId,

        @NotNull(message = "reasonType là bắt buộc")
        @Schema(description = "Lý do report do người dùng chọn.", example = "SPAM", requiredMode = Schema.RequiredMode.REQUIRED)
        ForumReportReasonType reasonType,

        @Size(max = 1000, message = "Mô tả report không được quá 1000 ký tự")
        String description
) {
}
