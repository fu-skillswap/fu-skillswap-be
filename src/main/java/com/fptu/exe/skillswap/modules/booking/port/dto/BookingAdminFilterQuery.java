package com.fptu.exe.skillswap.modules.booking.port.dto;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BookingAdminFilterQuery extends BasePageRequest {
    private BookingStatus status;
    private UUID mentorUserId;
    private UUID menteeUserId;

    public BookingAdminFilterQuery() {
        setSortBy("selectedStartTime");
        setDirection("DESC");
        setSize(20);
    }
}
