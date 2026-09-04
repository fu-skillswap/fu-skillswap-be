package com.fptu.exe.skillswap.modules.forum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Tạo hoặc cập nhật bài viết forum. Backend tự xác định tác giả từ tài khoản đăng nhập.")
public record ForumPostUpsertRequest(
        @NotBlank(message = "Tiêu đề bài viết không được để trống")
        @Size(max = 200, message = "Tiêu đề bài viết không được quá 200 ký tự")
        @Schema(description = "Tiêu đề bài viết.", example = "Làm thế nào để chuẩn bị cho phỏng vấn backend?")
        String title,

        @NotBlank(message = "Nội dung bài viết không được để trống")
        @Size(max = 5000, message = "Nội dung bài viết không được quá 5000 ký tự")
        @Schema(description = "Nội dung bài viết.")
        String content,

        @NotNull(message = "forumTopicId là bắt buộc")
        @Schema(description = "ID topic người dùng chọn; backend kiểm tra topic còn hoạt động.", example = "019f1234-aaaa-bbbb-cccc-1234567890ab")
        UUID forumTopicId,

        @Size(max = 4, message = "Mỗi bài viết chỉ được đính kèm tối đa 4 ảnh")
        @Schema(description = "Danh sách URL ảnh đã upload. Không gửi đường dẫn local; backend vẫn phải kiểm tra nguồn ảnh.", nullable = true)
        java.util.List<@Size(max = 2000, message = "URL ảnh quá dài") String> imageUrls
) {
}
