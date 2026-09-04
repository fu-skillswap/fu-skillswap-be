package com.fptu.exe.skillswap.shared.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Response phân trang bằng cursor, phù hợp với infinite scroll. FE phải giữ nguyên cursor do server trả về.")
public record CursorPageResponse<T>(
        @Schema(description = "Danh sách phần tử trong lượt lấy dữ liệu hiện tại")
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
        @Schema(description = "Cho biết còn lượt dữ liệu tiếp theo hay không", example = "true")
        boolean hasNext,
        @Schema(description = "Cho biết còn lượt dữ liệu phía trước hay không", example = "false")
        boolean hasPrev,
        @Schema(description = "Số phần tử thực tế tối đa trong lượt hiện tại", example = "20")
        int limit
) {
}
