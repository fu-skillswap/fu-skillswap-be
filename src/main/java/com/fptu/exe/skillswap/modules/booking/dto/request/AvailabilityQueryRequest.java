package com.fptu.exe.skillswap.modules.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Query filter lấy availability của mentor theo khoảng ngày")
public class AvailabilityQueryRequest {

    @Schema(description = "Ngày bắt đầu muốn xem slot", example = "2026-06-21")
    private LocalDate fromDate;
    @Schema(description = "Ngày kết thúc muốn xem slot", example = "2026-06-28")
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
