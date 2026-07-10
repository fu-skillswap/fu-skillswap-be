package com.fptu.exe.skillswap.shared.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Cursor-based pagination response wrapper for infinite scroll and keyset pagination.")
public record CursorPageResponse<T>(
        @Schema(description = "List of items in the current cursor window")
        List<T> items,
        @Schema(
                description = "Opaque cursor to fetch the next window. Client phải truyền lại nguyên giá trị này và không được decode, sửa hoặc tự tạo cursor.",
                nullable = true,
                example = "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I"
        )
        String nextCursor,
        @Schema(
                description = "Opaque cursor to fetch the previous window nếu endpoint hỗ trợ backward pagination. Ở các endpoint chưa hỗ trợ, field này sẽ là null.",
                nullable = true
        )
        String prevCursor,
        @Schema(description = "True if a next window exists", example = "true")
        boolean hasNext,
        @Schema(description = "True if a previous window exists", example = "false")
        boolean hasPrev,
        @Schema(description = "Resolved limit for this cursor window", example = "20")
        int limit
) {
}
