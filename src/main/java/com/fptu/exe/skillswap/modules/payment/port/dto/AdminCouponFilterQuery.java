package com.fptu.exe.skillswap.modules.payment.port.dto;

import com.fptu.exe.skillswap.modules.payment.domain.CouponDiscountType;
import com.fptu.exe.skillswap.modules.payment.domain.CouponStatus;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCouponFilterQuery extends BasePageRequest {
    private CouponStatus status;
    private CouponDiscountType discountType;
    private String keyword;
}
