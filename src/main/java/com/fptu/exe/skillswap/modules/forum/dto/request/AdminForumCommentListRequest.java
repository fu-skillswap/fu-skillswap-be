package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Bộ lọc danh sách forum comments cho admin")
public record AdminForumCommentListRequest(
        @Schema(
                description = "Opaque cursor string. Frontend không được decode hay tự tạo chuỗi này; chỉ dùng nextCursor từ response trước đó để truyền lại.",
                nullable = true,
                example = "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I"
        )
        String cursor,
        @Schema(description = "Số lượng item mong muốn cho một lần lấy dữ liệu, mặc định 20 và tối đa 50.", example = "20", defaultValue = "20")
        Integer limit,
        String keyword,
        UUID postId,
        UUID authorId,
        ForumCommentStatus status
) {
}
