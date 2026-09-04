package com.fptu.exe.skillswap.modules.forum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Tạo hoặc cập nhật bình luận forum. Backend tự xác định tác giả từ tài khoản đăng nhập.")
public record ForumCommentUpsertRequest(
        @NotBlank(message = "Nội dung bình luận không được để trống")
        @Size(max = 2000, message = "Nội dung bình luận không được quá 2000 ký tự")
        @Schema(description = "Nội dung bình luận.")
        String content,

        @Size(max = 1, message = "Mỗi bình luận chỉ được đính kèm tối đa 1 ảnh")
        @Schema(description = "Danh sách URL ảnh đã upload; không gửi đường dẫn local.", nullable = true)
        java.util.List<@Size(max = 2000, message = "URL ảnh quá dài") String> imageUrls,

        @Schema(description = "ID của bình luận muốn reply. Truyền null nếu là bình luận gốc.")
        java.util.UUID replyToCommentId
) {
}
