package com.fptu.exe.skillswap.modules.forum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Payload tạo hoặc cập nhật bài viết forum")
public record ForumPostUpsertRequest(
        @NotBlank(message = "Tiêu đề bài viết không được để trống")
        @Size(max = 200, message = "Tiêu đề bài viết không được quá 200 ký tự")
        String title,

        @NotBlank(message = "Nội dung bài viết không được để trống")
        @Size(max = 5000, message = "Nội dung bài viết không được quá 5000 ký tự")
        String content,

        @NotNull(message = "helpTopicId là bắt buộc")
        UUID helpTopicId,

        @Size(max = 4, message = "Mỗi bài viết chỉ được đính kèm tối đa 4 ảnh")
        java.util.List<@Size(max = 2000, message = "URL ảnh quá dài") String> imageUrls
) {
}
