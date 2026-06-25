package com.fptu.exe.skillswap.modules.booking.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.BookingViewRole;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingListRequest extends BasePageRequest {

    @Schema(description = "Xem danh sách booking theo góc nhìn mentee hoặc mentor", example = "MENTEE")
    private BookingViewRole role = BookingViewRole.MENTEE;

    @Schema(description = "Lọc theo trạng thái booking", example = "PENDING")
    private BookingStatus status;

    public BookingListRequest() {
        setSortBy("selectedStartTime");
        setDirection("DESC");
        setSize(10);
    }
}
