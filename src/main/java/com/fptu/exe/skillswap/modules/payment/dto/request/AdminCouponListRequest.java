package com.fptu.exe.skillswap.modules.payment.dto.request;

import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCouponListRequest extends BasePageRequest {

    @Schema(description = "Lọc theo trạng thái coupon", example = "ACTIVE")
    private CouponStatus status;

    @Schema(description = "Lọc theo loại giảm giá", example = "PERCENT")
    private CouponDiscountType discountType;

    @Schema(description = "Tìm kiếm theo mã coupon hoặc tiêu đề")
    private String keyword;

    public AdminCouponListRequest() {
        setSortBy("createdAt");
        setDirection("DESC");
        setSize(20);
    }
}
