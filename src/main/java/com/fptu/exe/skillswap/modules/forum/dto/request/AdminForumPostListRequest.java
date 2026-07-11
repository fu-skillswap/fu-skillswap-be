package com.fptu.exe.skillswap.modules.forum.dto.request;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Bộ lọc danh sách forum posts cho admin")
public record AdminForumPostListRequest(
        @Schema(
                description = "Opaque cursor string. Frontend không được decode hay tự tạo chuỗi này; chỉ dùng nextCursor từ response trước đó để truyền lại.",
                nullable = true,
                example = "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I"
        )
        String cursor,
        @Schema(description = "Số lượng item mong muốn cho một lần lấy dữ liệu, mặc định 20 và tối đa 50.", example = "20", defaultValue = "20")
        Integer limit,
        @Schema(description = "Keyword tìm theo title, content hoặc tên tác giả", nullable = true)
        String keyword,
        @Schema(description = "Lọc theo help topic", nullable = true)
        UUID helpTopicId,
        @Schema(description = "Lọc theo author user id", nullable = true)
        UUID authorId,
        @Schema(description = "Lọc theo trạng thái forum post", nullable = true)
        ForumPostStatus status
) {
}
