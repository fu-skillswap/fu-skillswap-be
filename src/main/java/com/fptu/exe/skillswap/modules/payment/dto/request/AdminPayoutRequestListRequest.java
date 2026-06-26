package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AdminPayoutRequestListRequest extends BasePageRequest {

    @Schema(description = "Lọc theo trạng thái payout request", example = "REQUESTED")
    private PayoutRequestStatus status;

    @Schema(description = "Lọc theo mentorUserId")
    private UUID mentorUserId;

    public AdminPayoutRequestListRequest() {
        setSortBy("requestedAt");
        setDirection("DESC");
        setSize(20);
    }
}
