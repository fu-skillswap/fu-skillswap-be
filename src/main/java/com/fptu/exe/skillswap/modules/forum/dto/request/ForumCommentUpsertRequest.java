package com.fptu.exe.skillswap.modules.forum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload tạo hoặc cập nhật comment forum")
public record ForumCommentUpsertRequest(
        @NotBlank(message = "Nội dung bình luận không được để trống")
        @Size(max = 2000, message = "Nội dung bình luận không được quá 2000 ký tự")
        String content,

        @Size(max = 1, message = "Mỗi bình luận chỉ được đính kèm tối đa 1 ảnh")
        java.util.List<@Size(max = 2000, message = "URL ảnh quá dài") String> imageUrls
) {
}
