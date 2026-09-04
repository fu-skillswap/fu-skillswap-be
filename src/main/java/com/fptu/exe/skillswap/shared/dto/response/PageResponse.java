package com.fptu.exe.skillswap.shared.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(description = "Response phân trang dạng page cho các API lấy danh sách. page bắt đầu từ 0.")
public class PageResponse<T> {
    @Schema(description = "Danh sách phần tử trong trang hiện tại")
    private List<T> content;

    @Schema(description = "Số thứ tự trang hiện tại, bắt đầu từ 0", example = "0")
    private int page;

    @Schema(description = "Số phần tử trong mỗi trang", example = "10")
    private int size;

    @Schema(description = "Tổng số phần tử phù hợp với bộ lọc trên tất cả các trang", example = "101")
    private long totalElements;

    @Schema(description = "Tổng số trang, tính từ totalElements và size", example = "11")
    private int totalPages;

    @Schema(description = "Cho biết đây có phải trang cuối hay không", example = "false")
    private boolean last;
}

