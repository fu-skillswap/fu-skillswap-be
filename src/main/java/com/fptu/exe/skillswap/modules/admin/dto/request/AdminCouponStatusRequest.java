package com.fptu.exe.skillswap.modules.admin.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request thay đổi trạng thái coupon cho admin.")
public record AdminCouponStatusRequest(
        @Schema(description = "Trạng thái mới của coupon", example = "ACTIVE")
        @NotNull(message = "Trạng thái không được để trống")
        CouponStatus status
) {
}
