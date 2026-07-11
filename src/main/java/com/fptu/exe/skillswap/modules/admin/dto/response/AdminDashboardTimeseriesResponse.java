package com.fptu.exe.skillswap.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Chuỗi thời gian 30 ngày gần nhất cho admin dashboard.")
public record AdminDashboardTimeseriesResponse(
        @Schema(description = "Timezone dùng để cắt ngày cho timeseries.", example = "Asia/Ho_Chi_Minh")
        String timezone,
        @Schema(description = "Ngày bắt đầu của cửa sổ 30 ngày.", example = "2026-06-03")
        LocalDate fromDate,
        @Schema(description = "Ngày kết thúc của cửa sổ 30 ngày.", example = "2026-07-02")
        LocalDate toDate,
        @Schema(description = "Danh sách đúng 30 điểm dữ liệu theo ngày, đã zero-fill.")
        List<AdminDashboardTimeseriesPointResponse> points
) {
}
