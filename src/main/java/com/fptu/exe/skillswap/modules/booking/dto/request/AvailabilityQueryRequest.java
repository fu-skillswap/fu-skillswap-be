package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Query filter lấy parent availability slots của mentor trong phạm vi từ Thứ 2 tuần hiện tại đến Chủ nhật tuần sau")
public class AvailabilityQueryRequest {

    @Schema(description = "Ngày bắt đầu muốn xem availability slots. Nếu không truyền backend sẽ dùng Thứ 2 tuần hiện tại.", example = "2026-06-22")
    private LocalDate fromDate;
    @Schema(description = "Ngày kết thúc muốn xem availability slots. Nếu không truyền backend sẽ dùng Chủ nhật tuần sau.", example = "2026-07-05")
    private LocalDate toDate;

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }
}
