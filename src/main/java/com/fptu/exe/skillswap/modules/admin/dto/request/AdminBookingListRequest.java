package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AdminBookingListRequest extends BasePageRequest {

    @Schema(description = "Lọc theo trạng thái booking", example = "ACCEPTED")
    private BookingStatus status;

    @Schema(description = "Lọc theo userId của mentor")
    private UUID mentorUserId;

    @Schema(description = "Lọc theo userId của mentee")
    private UUID menteeUserId;

    public AdminBookingListRequest() {
        setSortBy("selectedStartTime");
        setDirection("DESC");
        setSize(20);
    }
}
